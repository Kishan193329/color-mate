package com.colourmate.paintbynumber.util;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

/**
 * RecyclerView OnScrollListener that supports endless scrolling (pagination).
 * Supports LinearLayoutManager, GridLayoutManager, and StaggeredGridLayoutManager.
 */
public abstract class EndlessRecyclerViewScrollListener extends RecyclerView.OnScrollListener {

    // The minimum amount of items to have below current scroll position before loading more
    private int visibleThreshold = 5;

    // The current page of data loaded
    private int currentPage = 0;

    // True if we are still waiting for the last set of data to load
    private boolean loading = true;

    // The total number of items in the dataset after the last load
    private int previousTotalItemCount = 0;

    // The starting page index
    private final int startingPageIndex = 0;

    // LayoutManager being used
    private final RecyclerView.LayoutManager mLayoutManager;

    public EndlessRecyclerViewScrollListener(RecyclerView.LayoutManager layoutManager) {
        this.mLayoutManager = layoutManager;

        if (layoutManager instanceof GridLayoutManager) {
            visibleThreshold *= ((GridLayoutManager) layoutManager).getSpanCount();
        } else if (layoutManager instanceof StaggeredGridLayoutManager) {
            visibleThreshold *= ((StaggeredGridLayoutManager) layoutManager).getSpanCount();
        }
    }

    /**
     * Defines the process for actually loading more data based on page
     */
    public abstract void onLoadMore(int page, int totalItemsCount, RecyclerView view);

    /**
     * Helper method to get the last visible item from an array (used for StaggeredGridLayoutManager)
     */
    private int getLastVisibleItem(int[] lastVisibleItemPositions) {
        int max = 0;
        for (int i = 0; i < lastVisibleItemPositions.length; i++) {
            if (i == 0) {
                max = lastVisibleItemPositions[i];
            } else if (lastVisibleItemPositions[i] > max) {
                max = lastVisibleItemPositions[i];
            }
        }
        return max;
    }

    @Override
    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
        int lastVisibleItemPosition = 0;
        int totalItemCount = mLayoutManager.getItemCount();

        if (mLayoutManager instanceof StaggeredGridLayoutManager) {
            int[] lastVisibleItemPositions = ((StaggeredGridLayoutManager) mLayoutManager).findLastVisibleItemPositions(null);
            lastVisibleItemPosition = getLastVisibleItem(lastVisibleItemPositions);
        } else if (mLayoutManager instanceof GridLayoutManager) {
            lastVisibleItemPosition = ((GridLayoutManager) mLayoutManager).findLastVisibleItemPosition();
        } else if (mLayoutManager instanceof LinearLayoutManager) {
            lastVisibleItemPosition = ((LinearLayoutManager) mLayoutManager).findLastVisibleItemPosition();
        }

        // Reset state if dataset count has changed
        if (totalItemCount < previousTotalItemCount) {
            this.currentPage = startingPageIndex;
            this.previousTotalItemCount = totalItemCount;
            this.loading = totalItemCount == 0;
        }

        // If loading and dataset count has increased, loading finished
        if (loading && totalItemCount > previousTotalItemCount) {
            loading = false;
            previousTotalItemCount = totalItemCount;
        }

        // Adjust visible threshold based on some external condition
        // TODO: Consider passing this as a parameter instead of relying on fragment static
//        visibleThreshold = BookSiloGridFragment.siloGrid ? 20 : 5;

        // Trigger load more if not currently loading and close to the end
        if (!loading && (lastVisibleItemPosition + visibleThreshold) > totalItemCount) {
            currentPage++;
            onLoadMore(currentPage, totalItemCount, recyclerView);
            loading = true;
        }
    }

    /**
     * Resets the listener state to initial values
     */
    public void resetState() {
        this.currentPage = startingPageIndex;
        this.previousTotalItemCount = 0;
        this.loading = true;
    }
}
