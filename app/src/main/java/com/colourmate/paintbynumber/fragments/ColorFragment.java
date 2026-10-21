package com.colourmate.paintbynumber.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.activity.MainActivity;
import com.colourmate.paintbynumber.adapter.LibraryItemAdapter;
import com.colourmate.paintbynumber.library.Library;
import com.colourmate.paintbynumber.util.AchievementManager;

import android.graphics.drawable.Drawable;

import androidx.appcompat.app.AppCompatActivity;
import com.colourmate.paintbynumber.util.UIHelpers;

import eightbitlab.com.blurview.BlurView;
import eightbitlab.com.blurview.RenderScriptBlur;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.gson.JsonParser;
import com.sumoing.recolor.library.LibraryItem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ColorFragment extends Fragment {

    private static final String TAG = "ColorFragment";

    // Single shared OkHttpClient
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient();

    // Shared RecyclerView pool
    private final RecyclerView.RecycledViewPool sharedRecycledViewPool = new RecyclerView.RecycledViewPool();

    private Handler mainHandler;
    private final List<LibraryItemAdapter> adapters = new ArrayList<>();

    private View rootView;
    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private ProgressBar loadingView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_color, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rootView = view;

        tabLayout = view.findViewById(R.id.book_all_tab);
        viewPager = view.findViewById(R.id.viewPager_allbook);
        loadingView = view.findViewById(R.id.loading_view);

        mainHandler = new Handler(Looper.getMainLooper());

        BlurView tabBlur = view.findViewById(R.id.tab_blur_view);
        initBlurView(tabBlur);

        AchievementManager.recordModeTried(requireContext(), "color");

        loadingView.setVisibility(View.VISIBLE);
        fetchLibraryData();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (Library.getInstance().mLibraryItemsChanged) {
            refreshGrid();
            Library.getInstance().mLibraryItemsChanged = false;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
            mainHandler = null;
        }

        // ⚡ FIX 1: MEMORY LEAK PREVENTION
        // Clean up adapters (cancels image loads & background threads)
        for (LibraryItemAdapter adapter : adapters) {
            if (adapter != null) {
                adapter.cleanup();
            }
        }
        adapters.clear(); // Clear list so old fragments don't hold references

        rootView = null;
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && getView() != null) {
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

    private void fetchLibraryData() {
        String url = Library.B_TAG + Library.U1_TAG.substring(8) + "." + Library.U2_TAG.substring(8) + "."
                + Library.U3_TAG + Library.TAG + "/" + Library.TAG + Library.TAG1.substring(3, 6) + Library.FORMAT;

        Request request = new Request.Builder().url(url).build();
        HTTP_CLIENT.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Network request failed", e);
                safeInitData();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        String bodyString = response.body().string();
                        Library.mMap = JsonParser.parseString(bodyString).getAsJsonObject();
                        Library.getInstance().flushCache();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing response", e);
                } finally {
                    response.close();
                    safeInitData();
                }
            }
        });
    }

    private void safeInitData() {
        if (mainHandler == null) return;
        mainHandler.post(() -> {
            if (!isAdded()) return;
            initData();
        });
    }

    private void initData() {
        if (!isAdded() || rootView == null) return;

        loadingView.setVisibility(View.GONE);

        List<String> tagList;
        try {
            tagList = Library.getInstance().getTagPriorityList();
        } catch (Exception e) {
            Log.e(TAG, "Error getting tag list", e);
            tagList = Library.getInstance().getTagPriorityListoff();
        }

        if (tagList == null || tagList.isEmpty()) {
            return;
        }

        setupViewPager(tagList);
    }

    private void setupViewPager(List<String> tagList) {
        if (!isAdded() || rootView == null) return;

        sharedRecycledViewPool.setMaxRecycledViews(0, 30);

        // ⚡ FIX 4: Removed useless sharedPool parameter from Adapter
        SectionsPagerAdapter pagerAdapter = new SectionsPagerAdapter(this, tagList);
        viewPager.setAdapter(pagerAdapter);
        viewPager.setOffscreenPageLimit(1);

        try {
            RecyclerView innerRecyclerView = (RecyclerView) viewPager.getChildAt(0);
            if (innerRecyclerView != null) {
                innerRecyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
                innerRecyclerView.setItemViewCacheSize(3);
            }
        } catch (Exception e) {
            Log.e(TAG, "ViewPager2 inner RV config failed", e);
        }

        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(tagList.get(position))).attach();
    }

    public void setRecyclerViewByTag(RecyclerView recyclerView,
                                     String tag, boolean vertical) {
        recyclerView.setRecycledViewPool(sharedRecycledViewPool);
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(4); // ⚡ Reduced from 10

        RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
        if (lm instanceof LinearLayoutManager) {
            ((LinearLayoutManager) lm).setInitialPrefetchItemCount(6);
        }

        // ⚡ FIX 13: DO NOT disable nested scrolling in ViewPager2!
        // recyclerView.setNestedScrollingEnabled(false); ← REMOVED

        LibraryItemAdapter adapter = new LibraryItemAdapter(
                false, tag, new LibraryItemAdapter.LibraryItemListener() {
            @Override
            public void onContinueColoring(Library.ColoredPicture cp) {
                if (isAdded() && getActivity() instanceof AppCompatActivity) {
                    DrawingDialogFragment.show(
                            (AppCompatActivity) requireActivity(), cp, false);
                }
            }

            @Override
            public void onStartEditing(LibraryItem item, boolean isFromFolder) {
                startEditing(item, false);
            }
        });
        adapters.add(adapter);
        UIHelpers.setListRecyclerView(requireContext(), recyclerView, adapter, vertical);
    }

    public boolean startEditing(LibraryItem item, boolean makeCopy) {
        if (getActivity() instanceof MainActivity) {
            return ((MainActivity) getActivity()).startEditing(item, makeCopy);
        }
        return false;
    }

    private void initBlurView(BlurView blurView) {
        if (blurView == null || !isAdded()) return;
        try {
            View decorView = requireActivity().getWindow().getDecorView();
            ViewGroup rootView = (ViewGroup) decorView.findViewById(android.R.id.content);
            Drawable windowBackground = decorView.getBackground();

            blurView.setupWith(rootView, new RenderScriptBlur(requireContext()))
                    .setFrameClearDrawable(windowBackground)
                    .setBlurRadius(20f)
                    .setBlurAutoUpdate(false);
        } catch (Exception e) {
            Log.e(TAG, "BlurView setup failed", e);
        }
    }

    public void refreshGrid() {
        for (LibraryItemAdapter adapter : adapters) {
            if (adapter != null) {
                // ⚡ FIX 3: Use the highly optimized background DiffUtil refresh!
                adapter.refresh();
            }
        }
    }

    public static class SectionsPagerAdapter extends FragmentStateAdapter {
        private final List<String> titles;

        public SectionsPagerAdapter(@NonNull Fragment fragment, @NonNull List<String> list) {
            super(fragment);
            this.titles = new ArrayList<>(list);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            Fragment fragment = new ColorListFragment();
            Bundle args = new Bundle();
            args.putString("ALL_BOOKS", titles.get(position));
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public int getItemCount() { return titles.size(); }
    }
}

