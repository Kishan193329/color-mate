package com.colourmate.paintbynumber.fragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.activity.ArtworkShareActivity;
import com.colourmate.paintbynumber.activity.GameActivity;
import com.colourmate.paintbynumber.adapter.ColoringsAdapter;
import com.colourmate.paintbynumber.adapter.LevelsAdapter;
import com.colourmate.paintbynumber.ads.MediationManager;
import com.colourmate.paintbynumber.model.model.Level;
import com.colourmate.paintbynumber.viewmodel.ColoringListViewModel;
import com.colourmate.paintbynumber.views.CustomGridlayoutManager;
import com.google.android.material.snackbar.Snackbar;

public class NumberListFragment extends Fragment implements ColoringsAdapter.ColoringClickListener {

    private static final String ARG_SECTION_CATEGORY = "section_category";

    // ViewModel
    private ColoringListViewModel viewModel;

    // Views
    private RecyclerView recyclerView;
    private SwipeRefreshLayout refreshLayout;
    private View loadingView;
    private View errorLayout;

    // Adapters
    private ColoringsAdapter coloringsAdapter;
    private LevelsAdapter dailyNumberAdapter;

    // ─── Factory ───

    public static NumberListFragment newInstance(String category) {
        NumberListFragment fragment = new NumberListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SECTION_CATEGORY, category);
        fragment.setArguments(args);
        return fragment;
    }

    // ─── Lifecycle ───

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_view_pager, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(ColoringListViewModel.class);

        bindViews(view);
        setupListeners();
        observeViewModel();

        // Initialize with category
        String category = getArguments() != null
                ? getArguments().getString(ARG_SECTION_CATEGORY, "")
                : "";

        viewModel.initialize(category);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (recyclerView != null)
            recyclerView.setAdapter(null);

        coloringsAdapter = null;
        dailyNumberAdapter = null;
        recyclerView = null;
        refreshLayout = null;
        loadingView = null;
        errorLayout = null;
    }

    // ─── View Binding ───

    private void bindViews(View root) {
        recyclerView = root.findViewById(R.id.recyclerView);
        refreshLayout = root.findViewById(R.id.srl_reload);
        loadingView = root.findViewById(R.id.loading_view_progressbar);
        errorLayout = root.findViewById(R.id.ll_msg_refresh);
    }

    // ─── Listeners ───

    private void setupListeners() {
        refreshLayout.setOnRefreshListener(() -> viewModel.onRefresh());

        errorLayout.setOnClickListener(v -> viewModel.onRetryClicked());
    }

    // ─── ViewModel Observers ───

    private void observeViewModel() {

        // UI State
        viewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
            switch (state) {
                case LOADING:
                    showLoading();
                    break;
                case SUCCESS:
                    showList();
                    break;
                case ERROR:
                case EMPTY:
                    showError();
                    break;
            }
        });

        // Daily items
        viewModel.getDailyItems().observe(getViewLifecycleOwner(), items -> {
            if (items == null || items.isEmpty())
                return;

            setupDailyRecyclerView();
            dailyNumberAdapter.setList(items);
            recyclerView.scrollToPosition(0);
        });

        // Normal items
        viewModel.getNormalItems().observe(getViewLifecycleOwner(), items -> {
            if (items == null || items.isEmpty())
                return;

            setupNormalRecyclerView();

            if (coloringsAdapter == null) {
                coloringsAdapter = new ColoringsAdapter(viewModel.getDataSource());
                coloringsAdapter.setOnClickColoringListener(this);
                recyclerView.setAdapter(coloringsAdapter);
            } else {
                // Only refresh changed range instead of full redraw
                coloringsAdapter.notifyItemRangeChanged(0, coloringsAdapter.getItemCount());
            }
        });

        // Refreshing state
        viewModel.getIsRefreshing().observe(getViewLifecycleOwner(), refreshing -> {
            if (refreshLayout != null) {
                refreshLayout.setRefreshing(refreshing);
            }
        });

        // Navigation
        viewModel.getNavigationAction().observe(getViewLifecycleOwner(), action -> {
            if (action == null || getActivity() == null)
                return;

            switch (action.type) {
                case GAME:
                    navigateToGame(action.level);
                    break;
                case SHARE:
                    navigateToShare(action.imagePath);
                    break;
            }
        });

        // Snackbar
        viewModel.getSnackbarMessage().observe(getViewLifecycleOwner(), message -> {
            if (message != null && recyclerView != null) {
                Snackbar.make(recyclerView, message, Snackbar.LENGTH_SHORT).show();
            }
        });
    }

    // ─── RecyclerView Setup ───

    private void setupDailyRecyclerView() {
        if (recyclerView.getLayoutManager() == null) {
            recyclerView.setLayoutManager(createGridLayoutManager());
        }
        // Safe despite wrap_content in fragment_view_pager.xml: SwipeRefreshLayout
        // always measures its child EXACTLY to its own (match_parent) size.
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(4); // ⚡ Reduced from 20 (like ColorFragment)

        if (dailyNumberAdapter == null) {
            dailyNumberAdapter = new LevelsAdapter();
            dailyNumberAdapter.setOnClickColoringListner(this);
            recyclerView.setAdapter(dailyNumberAdapter);
        }
    }

    private void setupNormalRecyclerView() {
        if (recyclerView.getLayoutManager() == null) {
            int gridCols = requireContext().getResources()
                    .getInteger(R.integer.library_grid_columns);
            if (gridCols <= 0)
                gridCols = 2;
            recyclerView.setLayoutManager(
                    new CustomGridlayoutManager(requireContext(), gridCols));
        }
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(4); // ⚡ Reduced from 20 (like ColorFragment)
    }

    @NonNull
    private CustomGridlayoutManager createGridLayoutManager() {
        int columns = requireContext().getResources()
                .getInteger(R.integer.library_grid_columns);
        if (columns <= 0)
            columns = 2;

        CustomGridlayoutManager layoutManager = new CustomGridlayoutManager(requireContext(), columns);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return 1;
            }
        });
        return layoutManager;
    }

    // ─── Navigation ───

    private void navigateToGame(Level level) {
        Context ctx = getActivity();
        if (ctx == null)
            return;

        MediationManager.showInterstitial((AppCompatActivity) getActivity(),
                () -> startActivity(GameActivity.newIntent(ctx, level)));
    }

    private void navigateToShare(String imagePath) {
        Context ctx = getActivity();
        if (ctx == null || imagePath == null)
            return;

        MediationManager.showInterstitial((AppCompatActivity) getActivity(), () -> {
            Intent shareIntent = new Intent(ctx, ArtworkShareActivity.class);
            shareIntent.putExtra("path", imagePath);
            startActivity(shareIntent);
        });
    }

    // ─── Click Callback (from Adapter) ───

    @Override
    public void onColoringClicked(Level level) {
        viewModel.onColoringClicked(level);
    }

    // ─── UI State Helpers ───

    private void showLoading() {
        if (!isAdded())
            return;
        if (loadingView != null)
            loadingView.setVisibility(View.VISIBLE);
        if (errorLayout != null)
            errorLayout.setVisibility(View.GONE);
        if (recyclerView != null)
            recyclerView.setVisibility(View.GONE);
    }

    private void showError() {
        if (!isAdded())
            return;
        if (loadingView != null)
            loadingView.setVisibility(View.GONE);
        if (errorLayout != null)
            errorLayout.setVisibility(View.VISIBLE);
        if (recyclerView != null)
            recyclerView.setVisibility(View.GONE);
    }

    private void showList() {
        if (!isAdded())
            return;
        if (loadingView != null)
            loadingView.setVisibility(View.GONE);
        if (errorLayout != null)
            errorLayout.setVisibility(View.GONE);
        if (recyclerView != null)
            recyclerView.setVisibility(View.VISIBLE);
    }
}