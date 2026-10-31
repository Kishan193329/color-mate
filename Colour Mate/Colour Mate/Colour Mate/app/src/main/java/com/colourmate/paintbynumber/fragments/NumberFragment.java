package com.colourmate.paintbynumber.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.Utils.Manager;
import com.colourmate.paintbynumber.Utils.ColorForgeApi;
import com.colourmate.paintbynumber.activity.GameActivity;
import com.colourmate.paintbynumber.activity.RevealActivity;
import com.colourmate.paintbynumber.ads.MediationManager;
import com.colourmate.paintbynumber.model.model.Level;
import com.colourmate.paintbynumber.adapter.BannerSlideAdapter;
import com.colourmate.paintbynumber.viewmodel.NumberViewModel;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.tbuonomo.viewpagerdotsindicator.DotsIndicator;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import android.graphics.drawable.Drawable;

import eightbitlab.com.blurview.BlurView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NumberFragment extends Fragment {

    /** Marks BlurView setup scheduled (post) but not finished — avoids duplicate setupWith. */
    private static final Object BLUR_SETUP_PENDING = new Object();

    private NumberViewModel viewModel;

    private LinearLayout llRefresh;
    private TabLayout mTabLayout;
    private ViewPager2 mViewPager;
    private ProgressBar progressBar;
    private Snackbar snackbar;
    private SwipeRefreshLayout swiperefresh;
    private EditText etSearch;
    private ImageView ivSearchClear;

    private View dailyBanner;
    private ViewPager2 bannerViewPager;
    private DotsIndicator bannerIndicator;
    private final Handler bannerHandler = new Handler(Looper.getMainLooper());
    private Runnable bannerRunnable;

    private ImageView ivPreview1, ivPreview2;
    private ProgressBar pbDaily1, pbDaily2;

    private TabLayoutMediator mTabLayoutMediator;

    /**
     * A category another screen asked us to open, held until the tabs exist. The categories arrive
     * asynchronously, so a request made before that can only be honoured once they land.
     */
    private String pendingCategory;
    private NumberPagerAdapter mAdapter;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_number, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(NumberViewModel.class);

        bindViews(view);
        setupSnackbar(view);
        com.colourmate.paintbynumber.util.HitCard.attach(view);   // featured Hit picture + countdown
        setupSearchBar();
        setupDailyBanner(view);
        setupSwipeRefresh();
        observeViewModel();

        BlurView tabBlur = view.findViewById(R.id.tab_blur_view);
        initBlurView(tabBlur);

        if (viewModel.getLoadState().getValue() == NumberViewModel.LoadState.IDLE) {
            viewModel.initialize();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mTabLayoutMediator != null) {
            mTabLayoutMediator.detach();
            mTabLayoutMediator = null;
        }
        searchHandler.removeCallbacksAndMessages(null);
        bannerHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && getView() != null) {
            // ✅ Smooth entrance animation
            getView().setAlpha(0f);
            getView().setTranslationY(30f);
            getView().animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(400)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }
    }

    private void bindViews(View view) {
        mViewPager = view.findViewById(R.id.viewPager_allbook);
        mTabLayout = view.findViewById(R.id.book_all_tab);
        progressBar = view.findViewById(R.id.loading_view);
        llRefresh = view.findViewById(R.id.ll_msg_refresh);
        swiperefresh = view.findViewById(R.id.swiperefresh);
        etSearch = view.findViewById(R.id.et_search);
        ivSearchClear = view.findViewById(R.id.iv_search_clear);
    }

    private void setupSnackbar(View view) {
        snackbar = Snackbar.make(
                view.findViewById(R.id.number_coordintor),
                R.string.internet_conn_failed,
                Snackbar.LENGTH_SHORT)
                .setAction("Retry", v -> viewModel.onRetryClicked());
    }

    private void setupSwipeRefresh() {
        swiperefresh.setOnRefreshListener(() -> viewModel.onRefresh());
        llRefresh.setOnClickListener(v -> viewModel.onRetryClicked());
    }

    private void setupSearchBar() {
        if (etSearch == null)
            return;

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                if (ivSearchClear != null) {
                    ivSearchClear.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                }
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }
                searchRunnable = () -> viewModel.search(query);
                searchHandler.postDelayed(searchRunnable, 300);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                viewModel.search(etSearch.getText().toString().trim());
                return true;
            }
            return false;
        });

        if (ivSearchClear != null) {
            ivSearchClear.setOnClickListener(v -> {
                etSearch.setText("");
                viewModel.search("");
            });
        }
    }

    private void setupDailyBanner(View root) {
        dailyBanner = root.findViewById(R.id.daily_banner);
        if (dailyBanner == null)
            return;

        bannerViewPager = dailyBanner.findViewById(R.id.banner_view_pager);
        bannerIndicator = dailyBanner.findViewById(R.id.banner_indicator);

        if (bannerViewPager != null) {
            BannerSlideAdapter bannerAdapter = new BannerSlideAdapter(new BannerSlideAdapter.BannerListener() {
                @Override
                public void onOfferClicked() {
                    // Navigate to premium activity or similar
                    try {
                        startActivity(new android.content.Intent(requireContext(),
                                Class.forName("com.colourmate.paintbynumber.activity.PremiumActivity")));
                    } catch (ClassNotFoundException e) {
                        Log.e("NumberFragment", "PremiumActivity not found", e);
                    }
                }

                @Override
                public void onBindDailyBanner(View itemView) {
                    bindDailyBannerUI(itemView);
                }

                @Override
                public void onBindOfferCard(View itemView) {
                    BlurView offerBlur = itemView.findViewById(R.id.offer_blur_view);
                    initBlurView(offerBlur);
                }
            });
            bannerViewPager.setAdapter(bannerAdapter);

            if (bannerIndicator != null) {
                bannerIndicator.attachTo(bannerViewPager);
            }

            startBannerAutoScroll();
        }
    }

    private void bindDailyBannerUI(View bannerView) {
        TextView tvDay = bannerView.findViewById(R.id.tv_daily_day);
        TextView tvMonth = bannerView.findViewById(R.id.tv_daily_month);
        View btnStart = bannerView.findViewById(R.id.tv_daily_start);
        ivPreview1 = bannerView.findViewById(R.id.iv_daily_preview1);
        ivPreview2 = bannerView.findViewById(R.id.iv_daily_preview2);
        pbDaily1 = bannerView.findViewById(R.id.pb_daily1);
        pbDaily2 = bannerView.findViewById(R.id.pb_daily2);

        BlurView dailyBlur = bannerView.findViewById(R.id.daily_blur_view);
        initBlurView(dailyBlur);

        Date today = new Date();
        tvDay.setText(new SimpleDateFormat("d", Locale.getDefault()).format(today));
        tvMonth.setText(new SimpleDateFormat("MMMM", Locale.getDefault()).format(today));

        btnStart.setOnClickListener(v -> viewModel.onDailyStartClicked());
        ivPreview1.setOnClickListener(v -> viewModel.onDailyPreview1Clicked());
        ivPreview2.setOnClickListener(v -> viewModel.onDailyPreview2Clicked());

        List<Level> levels = viewModel.getDailyLevels().getValue();
        if (levels != null && !levels.isEmpty()) {
            showDailyPreviews(levels);
        } else {
            viewModel.loadDailyImages();
        }
    }

    private void startBannerAutoScroll() {
        if (bannerRunnable != null) {
            bannerHandler.removeCallbacks(bannerRunnable);
        }
        bannerRunnable = () -> {
            if (bannerViewPager != null && isAdded()) {
                int current = bannerViewPager.getCurrentItem();
                int totalCount = 2; // only 2 pages
                int next = (current + 1) % totalCount;
                bannerViewPager.setCurrentItem(next, true);
                bannerHandler.postDelayed(bannerRunnable, 5000);
            }
        };
        bannerHandler.postDelayed(bannerRunnable, 5000);
    }

    /**
     * Uses BlurView's default algorithm (RenderEffectBlur on API 31+, RenderScript below).
     * Deferred until layout so ViewPager2 items have size; one-time setup per view.
     */
    private void initBlurView(BlurView blurView) {
        if (blurView == null || !isAdded())
            return;
        if (Boolean.TRUE.equals(blurView.getTag()) || blurView.getTag() == BLUR_SETUP_PENDING)
            return;

        blurView.setTag(BLUR_SETUP_PENDING);
        blurView.post(() -> {
            if (!isAdded() || blurView.getParent() == null) {
                blurView.setTag(null);
                return;
            }
            if (Boolean.TRUE.equals(blurView.getTag()))
                return;

            try {
                View decorView = requireActivity().getWindow().getDecorView();
                ViewGroup rootView = (ViewGroup) decorView.findViewById(android.R.id.content);
                Drawable windowBackground = decorView.getBackground();

                blurView.setupWith(rootView)
                        .setFrameClearDrawable(windowBackground)
                        .setBlurRadius(20f)
                        .setBlurAutoUpdate(true);
                blurView.setTag(Boolean.TRUE);
            } catch (Exception e) {
                Log.e("NumberFragment", "BlurView setup failed", e);
                blurView.setTag(null);
            }
        });
    }

    private void showDailyPreviews(List<Level> levels) {
        if (!isAdded())
            return;
        if (levels.size() > 0)
            loadPreview(levels.get(0), ivPreview1, pbDaily1);
        if (levels.size() > 1)
            loadPreview(levels.get(1), ivPreview2, pbDaily2);
    }

    private void loadPreview(Level level, ImageView iv, ProgressBar pb) {
        if (level == null || iv == null)
            return;
        String url;
        if (level.isColorForge()) {
            // our picture: the line-art thumbnail, on white (border_thumb has a clear background)
            url = ColorForgeApi.borderThumbUrl(requireContext(), level.getCategory(), level.getFileName());
            iv.setBackgroundColor(android.graphics.Color.WHITE);
        } else {
            url = Manager.BASE_URL + "thumb/" + level.getCategory()
                    + "/t/" + level.getFileName() + ".png";
        }
        // fit()+centerCrop = decode to the preview size, matching the layout's scaleType
        Picasso.get().load(url).fit().centerCrop().into(iv, new Callback() {
            @Override
            public void onSuccess() {
                if (pb != null)
                    pb.setVisibility(View.GONE);
            }

            @Override
            public void onError(Exception e) {
                Log.e("NumberFragment", "Preview failed: " + url, e);
                if (pb != null)
                    pb.setVisibility(View.GONE);
            }
        });
    }

    private void observeViewModel() {
        viewModel.getLoadState().observe(getViewLifecycleOwner(), state -> {
            switch (state) {
                case LOADING:
                    progressBar.setVisibility(View.VISIBLE);
                    llRefresh.setVisibility(View.GONE);
                    break;
                case SUCCESS:
                    progressBar.setVisibility(View.GONE);
                    llRefresh.setVisibility(View.GONE);
                    break;
                case ERROR:
                case TIMEOUT:
                    progressBar.setVisibility(View.GONE);
                    llRefresh.setVisibility(View.VISIBLE);
                    break;
                case NO_INTERNET:
                    progressBar.setVisibility(View.GONE);
                    llRefresh.setVisibility(View.VISIBLE);
                    snackbar.show();
                    break;
                case IDLE:
                    break;
            }
        });

        viewModel.getDisplayCategories().observe(getViewLifecycleOwner(), categories -> {
            if (categories == null || categories.isEmpty())
                return;
            updateViewPager(categories);
        });

        viewModel.getIsRefreshing().observe(getViewLifecycleOwner(), refreshing -> {
            if (swiperefresh != null)
                swiperefresh.setRefreshing(refreshing);
        });

        viewModel.getDailyLevels().observe(getViewLifecycleOwner(), levels -> {
            if (levels != null && !levels.isEmpty())
                showDailyPreviews(levels);
        });

        viewModel.getSnackbarMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && snackbar != null)
                snackbar.show();
        });

        viewModel.getToastMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && isAdded()) {
                android.widget.Toast.makeText(requireContext(), msg,
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getNavigateToGame().observe(getViewLifecycleOwner(), level -> {
            if (level != null && isAdded()) {
                MediationManager.showInterstitial((AppCompatActivity) requireActivity(), () -> {
                    if (!isAdded()) return;
                    if (level.isColorForge()) {
                        // our picture plays the reveal game, like the Daily tab and the mixed grid
                        String base = ColorForgeApi.revealBaseUrl(requireContext(),
                                level.getCategory(), level.getFileName());
                        if (base.isEmpty()) return;
                        startActivity(RevealActivity.fromUrl(requireContext(), base,
                                level.getFileName(), new String[0], new String[0]));
                    } else {
                        startActivity(GameActivity.newIntent(requireContext(), level));
                    }
                });
            }
        });

        viewModel.getShowBackAd().observe(getViewLifecycleOwner(), v -> {
            // No back ad in fragment context
        });

        viewModel.getScrollToFirstTab().observe(getViewLifecycleOwner(), v -> {
            if (mTabLayout != null && mTabLayout.getTabCount() > 0) {
                mViewPager.setCurrentItem(0, true);
            }
        });
    }

    /**
     * Opens a category by name. Safe to call at any time: if the tabs are not built yet the request
     * is remembered and applied when the categories arrive.
     */
    public void selectCategory(String category) {
        if (category == null || category.isEmpty()) return;
        pendingCategory = category;
        applyPendingCategory();
    }

    private void applyPendingCategory() {
        if (pendingCategory == null || mAdapter == null || mViewPager == null) return;

        int index = mAdapter.indexOf(pendingCategory);
        if (index < 0) return;                 // not in this list; leave the tab where it is

        mViewPager.setCurrentItem(index, false);
        pendingCategory = null;
    }

    private void updateViewPager(ArrayList<String> categories) {
        if (!isAdded())
            return;
        try {
            if (mAdapter == null) {
                mAdapter = new NumberPagerAdapter(this, categories);
                mViewPager.setAdapter(mAdapter);
                mViewPager.setOffscreenPageLimit(1);
            } else {
                mAdapter.setCategories(categories);
            }

            if (mTabLayoutMediator != null)
                mTabLayoutMediator.detach();

            mTabLayoutMediator = new TabLayoutMediator(mTabLayout, mViewPager,
                    (tab, position) -> tab.setText(categories.get(position)));
            mTabLayoutMediator.attach();

            // Honour a category another screen asked for, now that the tabs exist.
            applyPendingCategory();

            swiperefresh.setEnabled(true);
        } catch (Exception e) {
            Log.e("NumberFragment", "Error setting up ViewPager", e);
        }
    }

    public static class NumberPagerAdapter extends FragmentStateAdapter {
        private ArrayList<String> categories;

        /** Position of a category, ignoring case — the server is inconsistent about it. */
        int indexOf(String category) {
            if (categories == null || category == null) return -1;
            for (int i = 0; i < categories.size(); i++) {
                if (categories.get(i).equalsIgnoreCase(category)) return i;
            }
            return -1;
        }

        public NumberPagerAdapter(@NonNull Fragment fragment, ArrayList<String> categories) {
            super(fragment);
            this.categories = categories;
        }

        public void setCategories(ArrayList<String> newCategories) {
            this.categories = newCategories;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return NumberListFragment.newInstance(categories.get(position));
        }

        @Override
        public int getItemCount() {
            return categories.size();
        }

        @Override
        public long getItemId(int position) {
            return categories.get(position).hashCode();
        }

        @Override
        public boolean containsItem(long itemId) {
            for (String cat : categories) {
                if (cat.hashCode() == itemId)
                    return true;
            }
            return false;
        }
    }
}
