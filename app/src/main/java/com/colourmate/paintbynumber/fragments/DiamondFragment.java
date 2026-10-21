package com.colourmate.paintbynumber.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.colourmate.paintbynumber.BuildConfig;
import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.Utils.CategoryStyle;
import com.colourmate.paintbynumber.Utils.ColorForgeApi;
import com.colourmate.paintbynumber.Utils.Manager;
import com.colourmate.paintbynumber.activity.DiamondActivity;
import com.colourmate.paintbynumber.adapter.ColoringsAdapter;
import com.google.android.material.tabs.TabLayout;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Diamond painting — ColorForge's bead-grid content, in its own section.
 *
 * <p>Deliberately the same screen as {@link New2026Fragment}: categories -> grid -> tap to play,
 * on the same {@link ColoringsAdapter}, so the two ColorForge tabs feel like one product. Three
 * things differ, and all three come from the backend's separate diamond feed
 * ({@code diamond_tab.py}): the REST root ({@code index.php/Diamond/}), the thumbnail (the
 * finished piece — a bead grid has no line art), and the payload (one {@code grid.json}, played
 * by {@link DiamondActivity}).
 */
public class DiamondFragment extends Fragment implements ColoringsAdapter.ColoringClickListener {

    private static final String TAG = "DiamondFragment";

    /** Generated from cat.png by the backend's diamond_builder — 80x80, 24 colours. */
    private static final String SAMPLE_ASSET = "diamond_sample.json";


    private TabLayout tabLayout;
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressBar;
    private LinearLayout messageLayout;
    private TextView messageText;

    private ColoringsAdapter adapter;
    private Manager.RestDataSource<com.colourmate.paintbynumber.model.model.Level> dataSource;

    private final List<String> categories = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // Longer timeouts than the default: a cloudflared quick tunnel adds a hop, and the first
    // request right after launch can hit a cold DNS cache.
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();
    private Call categoriesCall;
    private int categoryAttempts = 0;
    /** Remote Config is async at startup; ask it once before deciding there is no server. */
    private boolean configRefreshed = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_diamond, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tabLayout = view.findViewById(R.id.tabs_diamond);
        recyclerView = view.findViewById(R.id.rv_diamond);
        swipeRefresh = view.findViewById(R.id.srl_diamond);
        progressBar = view.findViewById(R.id.pb_diamond);
        messageLayout = view.findViewById(R.id.ll_diamond_message);
        messageText = view.findViewById(R.id.tv_diamond_message);

        setupRecyclerView();

        // The sample lives in src/debug/assets, so it exists in debug builds only — hide the
        // button in release rather than offer a picture that is not in the APK.
        View sampleButton = view.findViewById(R.id.btn_diamond_sample);
        if (BuildConfig.DEBUG) {
            sampleButton.setOnClickListener(v ->
                    startActivity(DiamondActivity.fromAsset(requireContext(), SAMPLE_ASSET)));
        } else {
            sampleButton.setVisibility(View.GONE);
        }

        swipeRefresh.setOnRefreshListener(this::loadCategories);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                styleChip(tab, true);
                loadCategory(categories.get(tab.getPosition()));
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                styleChip(tab, false);
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        loadCategories();
    }

    private void setupRecyclerView() {
        int columns = getResources().getInteger(R.integer.library_grid_columns);
        if (columns <= 0) columns = 2;

        recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), columns));
        // Same list config as the other grids — see §8.6 M7 in CLAUDE.md.
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(4);
    }

    // ─── Categories ───

    private void loadCategories() {
        if (!ColorForgeApi.isConfigured(requireContext())) {
            // Do NOT conclude "no server" here. Manager fetches Remote Config asynchronously at
            // startup, so on a cold launch the URL usually has not activated yet — the tab used to
            // show a permanent "Coming soon" even when the owner HAD configured a server. Ask
            // Remote Config directly, then decide.
            if (!configRefreshed) {
                configRefreshed = true;
                showLoading();
                ColorForgeApi.refresh(requireContext(), baseUrl -> {
                    if (!isAdded()) return;
                    if (baseUrl.isEmpty()) {
                        showMessage("No server set up yet — tap to retry");
                    } else {
                        loadCategories();
                    }
                });
                return;
            }
            showMessage("No server set up yet — tap to retry");
            swipeRefresh.setRefreshing(false);
            return;
        }

        showLoading();
        categoryAttempts++;

        String categoriesUrl = ColorForgeApi.diamondCategoriesUrl(requireContext());
        Log.i(TAG, "Categories URL (attempt " + categoryAttempts + "): " + categoriesUrl);

        Request request = new Request.Builder()
                .url(categoriesUrl)
                .get()
                .build();

        if (categoriesCall != null) categoriesCall.cancel();
        categoriesCall = client.newCall(request);

        categoriesCall.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (call.isCanceled()) return;
                // Message inline: a bare Log.e(..., e) was not surfacing the cause in logcat.
                Log.e(TAG, "Category list failed: " + e.getClass().getName() + ": " + e.getMessage());
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    // The very first request after launch can lose the DNS race (host not yet
                    // resolvable). Retry a couple of times with a short back-off before giving up.
                    if (categoryAttempts < 3) {
                        mainHandler.postDelayed(DiamondFragment.this::loadCategories, 1500);
                    } else {
                        showMessage("Could not reach the server — tap to retry");
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                final List<String> parsed = new ArrayList<>();
                try (Response r = response) {
                    if (r.body() != null) {
                        JsonObject json = JsonParser.parseString(r.body().string()).getAsJsonObject();
                        if (json.has("categories")) {
                            json.getAsJsonArray("categories")
                                    .forEach(el -> parsed.add(el.getAsString()));
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Category list parse failed", e);
                }

                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    applyCategories(parsed);
                });
            }
        });
    }

    private void applyCategories(List<String> parsed) {
        swipeRefresh.setRefreshing(false);

        categories.clear();
        categories.addAll(parsed);

        if (categories.isEmpty()) {
            tabLayout.removeAllTabs();
            showMessage("No pictures yet");
            return;
        }

        tabLayout.removeAllTabs();
        for (String category : categories) {
            TabLayout.Tab tab = tabLayout.newTab();
            tab.setCustomView(buildCategoryChip(category));
            tabLayout.addTab(tab);
        }

        // Selecting the first tab triggers the listener, which loads its images.
        showContent();
        TabLayout.Tab first = tabLayout.getTabAt(0);
        if (first != null) first.select();
    }

    // ─── Category chips ───

    /** Builds one category pill — soft-3D icon ball plus the name, per the design system. */
    private View buildCategoryChip(String category) {
        View chip = LayoutInflater.from(requireContext())
                .inflate(R.layout.cm_item_category_chip, tabLayout, false);

        ((TextView) chip.findViewById(R.id.cm_chip_text))
                .setText(CategoryStyle.labelFor(category));
        chip.findViewById(R.id.cm_chip_ball)
                .setBackgroundResource(CategoryStyle.ballFor(category));
        ((ImageView) chip.findViewById(R.id.cm_chip_glyph))
                .setImageResource(CategoryStyle.glyphFor(category));

        // Start at the unselected size; the tab that gets selected animates up to full.
        chip.setScaleX(.96f);
        chip.setScaleY(.96f);
        return chip;
    }

    /**
     * Selection is shown by the pill going solid white with a violet edge and lifting — the icon
     * keeps its own colour either way, so the row stays readable as a set of categories rather
     * than turning into one highlighted blob.
     */
    private void styleChip(TabLayout.Tab tab, boolean selected) {
        View chip = tab.getCustomView();
        if (chip == null) return;

        chip.setBackgroundResource(selected
                ? R.drawable.cm_cat_chip_bg_selected : R.drawable.cm_cat_chip_bg);
        chip.setElevation(selected ? dp(3) : 0f);

        TextView label = chip.findViewById(R.id.cm_chip_text);
        label.setTextColor(getResources().getColor(
                selected ? R.color.cm_ink : R.color.cm_ink_2, null));

        chip.animate().scaleX(selected ? 1f : .96f).scaleY(selected ? 1f : .96f)
                .setDuration(getResources().getInteger(R.integer.cm_dur_press))
                .start();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    // ─── One category's images ───

    private void loadCategory(String category) {
        if (dataSource != null) {
            dataSource.abort();
            dataSource = null;
        }

        dataSource = Manager.getInstance()
                .getDiamondImagesByCategory(requireContext(), category);

        if (dataSource == null) {
            // Only happens when the base URL vanished between the category list and this call.
            showMessage("No server set up yet — tap to retry");
            return;
        }

        showLoading();

        adapter = new ColoringsAdapter(dataSource);
        adapter.setOnClickColoringListener(this);
        recyclerView.setAdapter(adapter);

        dataSource.addListener(() -> {
            if (!isAdded()) return;

            if (dataSource != null && dataSource.hasData()) {
                showContent();
                // BaseRestAdapter.setSource() snapshots getItems() at construction time — when the
                // source is still empty — and getItems() returns a COPY, not a live reference. So
                // after the fetch fills the source, the adapter still holds the old empty list and
                // the grid stays blank despite 200s in the server log. Re-point it at the now-full
                // source, then refresh.
                if (adapter != null) {
                    adapter.setSource(dataSource);
                    adapter.notifyDataSetChanged();
                }
            } else {
                showMessage("No pictures yet");
            }
        });

        dataSource.refresh();
    }

    @Override
    public void onColoringClicked(com.colourmate.paintbynumber.model.model.Level level) {
        if (level == null || !isAdded()) return;

        String gridUrl = ColorForgeApi.diamondGridUrl(requireContext(),
                level.getCategory(), level.getFileName());
        if (gridUrl.isEmpty()) {
            Toast.makeText(requireContext(), "Server not configured", Toast.LENGTH_SHORT).show();
            return;
        }
        // fileName is the progress key: globally unique and never reused, so one picture's beads
        // can never be restored onto another (DiamondLevel.restoreProgress re-checks the grid).
        startActivity(DiamondActivity.fromUrl(requireContext(), gridUrl, level.getFileName()));
    }

    // ─── View state ───

    private void showLoading() {
        progressBar.setVisibility(View.VISIBLE);
        messageLayout.setVisibility(View.GONE);
    }

    private void showContent() {
        progressBar.setVisibility(View.GONE);
        messageLayout.setVisibility(View.GONE);
        swipeRefresh.setRefreshing(false);
    }

    private void showMessage(String message) {
        progressBar.setVisibility(View.GONE);
        messageText.setText(message);
        messageLayout.setVisibility(View.VISIBLE);
        swipeRefresh.setRefreshing(false);

        // Tapping the message tries again from scratch — handy after a dropped network or a
        // tunnel URL change, without leaving the tab.
        messageLayout.setOnClickListener(v -> {
            categoryAttempts = 0;
            configRefreshed = false;      // a manual retry re-asks Remote Config as well
            loadCategories();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (categoriesCall != null) categoriesCall.cancel();
        if (dataSource != null) {
            dataSource.abort();
            dataSource = null;
        }
        mainHandler.removeCallbacksAndMessages(null);
        recyclerView = null;
        adapter = null;
    }
}
