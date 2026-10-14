package com.colourmate.paintbynumber.adapter;

import android.content.Context;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.Utils.Manager;
import com.colourmate.paintbynumber.util.EndlessRecyclerViewScrollListener;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;

/**
 * Base Recycler Adapter with Remote/Firestore data source.
 * <p>
 * Handles:
 * - Live updates from RestDataSource
 * - Endless scrolling / pagination
 * - Optional reverse ordering of list
 * - Swipe refresh
 * - Retry with snackbar
 * - Ad insertion flags (legacy)
 */
public abstract class BaseRestAdapter<VH extends RecyclerView.ViewHolder, T>
        extends RecyclerView.Adapter<VH> {


    // Legacy global ad flags (kept unchanged)
    public static boolean isAdAdded1 = false;
    public static boolean isAdAdded2 = false;
    public static boolean isAdded = false;

    // -------------------------------------------------------------------------
    // Member variables
    // -------------------------------------------------------------------------
    protected ArrayList<T> mItems;
    protected ArrayList<String> mKeys;

    private LinearLayout retryLayout;
    private SwipeRefreshLayout swipeRefreshLayout;
    private Snackbar snackbar;

    private boolean reverseOrder = false;
    private boolean isAdAdded = false;

    private Manager.RestDataSource<T> source;

    // Listener from remote data source
    private final Manager.RestDataSource.RestDataSourceListener dataListener =
            () -> {
                refreshDone();

                // RestDataSource.getItems() hands back a COPY of its list. setSource() took that
                // copy while the source was still empty, so without re-reading it here the adapter
                // keeps showing the empty snapshot no matter what the fetch returned — the "grid
                // stays blank even though the request succeeded" bug.
                if (source != null) {
                    mItems = source.getItems();
                    mKeys = source.getKeys();
                }

                itemsLoaded();

                // Was notifyItemInserted(mItems.size() - 1), i.e. notifyItemInserted(-1) whenever
                // the list had been empty. A refresh replaces the whole list, so say that.
                notifyDataSetChanged();
            };


    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    public BaseRestAdapter(Manager.RestDataSource<T> ds) {
        init(ds);
        handleInitialLoad();
    }

    public BaseRestAdapter(Manager.RestDataSource<T> ds, LinearLayout retryLayout) {
        this.retryLayout = retryLayout;
        setupRetrySnackbar();
        init(ds);
        handleInitialLoad();
    }

    // Common initialization for both constructors
    private void init(Manager.RestDataSource<T> ds) {
        setSource(ds);
    }

    private void handleInitialLoad() {
        if (!mKeys.isEmpty()) {
            new Handler().postDelayed(() -> {
                refreshDone();
                itemsLoaded();
                notifyDataSetChanged();
            }, 200);
        } else {
            refresh();
        }
    }


    // -------------------------------------------------------------------------
    // Abstract Methods
    // -------------------------------------------------------------------------

    @Override
    public abstract void onBindViewHolder(VH holder, int position);

    @Override
    public abstract VH onCreateViewHolder(ViewGroup parent, int viewType);

    public void itemsLoaded() {
        // For subclasses
    }


    // -------------------------------------------------------------------------
    // Data Source Handling
    // -------------------------------------------------------------------------

    public void setSource(Manager.RestDataSource<T> ds) {
        if (source != null && source != ds) {
            source.abort();
        }

        this.source = ds;
        this.source.addListener(dataListener);

        this.mItems = ds.getItems();
        this.mKeys = ds.getKeys();
    }

    public void refresh() {
        if (source != null) source.refresh();
    }

    public boolean canLoadMore() {
        return source != null && source.canLoadMore();
    }


    // -------------------------------------------------------------------------
    // RecyclerView Overrides
    // -------------------------------------------------------------------------

    @Override
    public int getItemCount() {
        return mItems != null ? mItems.size() : 0;
    }

    @Override
    public long getItemId(int position) {
        if (mItems == null) return RecyclerView.NO_ID;

        int index = mapPosition(position);
        if (index < 0 || index >= mItems.size()) return RecyclerView.NO_ID;

        String key = stableKeyFor(mItems.get(index));
        if (key == null) return index; // old behaviour for adapters without stable ids

        // 64-bit hash of the key, same idea as LibraryItemAdapter.getItemId()
        long hash = 0;
        for (int i = 0; i < key.length(); i++) {
            hash = 31 * hash + key.charAt(i);
        }
        return hash;
    }

    /**
     * A value that identifies the item independently of its position. Subclasses that call
     * {@code setHasStableIds(true)} MUST override this — otherwise RecyclerView keys views by
     * position and recycled cells keep the previous item's thumbnail. Return null to keep the
     * old position-based id.
     */
    protected String stableKeyFor(T item) {
        return null;
    }

    @Override
    public void onAttachedToRecyclerView(RecyclerView rv) {
        super.onAttachedToRecyclerView(rv);

        rv.addOnScrollListener(new EndlessRecyclerViewScrollListener(rv.getLayoutManager()) {
            @Override
            public void onLoadMore(int page, int totalItems, RecyclerView recyclerView) {
                if (source != null && source.getItems().size() > 50) {
                    source.loadMore();
                }
            }
        });
    }

    public void destroy() {
        if (source != null) {
            source.removeListener(dataListener);
            source = null;
        }
        swipeRefreshLayout = null;
    }


    // -------------------------------------------------------------------------
    // Item Helpers
    // -------------------------------------------------------------------------

    private int mapPosition(int index) {
        if (!reverseOrder) return index;
        if (index < 0) return -1;

        return (mItems.size() - index) - 1;
    }

    public void setReverseOrder(boolean reverse) {
        this.reverseOrder = reverse;
    }

    public ArrayList<T> getItems() {
        return mItems;
    }

    public ArrayList<String> getKeys() {
        return mKeys;
    }

    public T getItemForKey(String key) {
        int idx = mKeys.indexOf(key);
        return idx >= 0 ? mItems.get(idx) : null;
    }

    public void removeItem(int index) {
        int mapped = mapPosition(index);
        if (mapped >= 0) {
            mItems.remove(mapped);
            mKeys.remove(mapped);
        }
    }

    public String getKey(int index) {
        return mKeys.get(mapPosition(index));
    }

    public int getPositionForKey(String key) {
        return mapPosition(mKeys.indexOf(key));
    }

    public int getPositionForItem(T item) {
        return mapPosition(mItems.indexOf(item));
    }

    public boolean contains(T item) {
        return mItems != null && mItems.contains(item);
    }


    // -------------------------------------------------------------------------
    // Ad Logic (unchanged but cleaned)
    // -------------------------------------------------------------------------

    public T getItem(int index, Context ctx) {

        // Reset flag when loading first item
        if (index == 0 && ctx != null) {
            isAdAdded1 = false;
        }

        // Run ad insertion logic only once
        if (!isAdded) {

//            T item = mItems.get(index);

//            if (item instanceof GalleryPost && ctx != null && !isAdAdded1) {

//                Log.d(TAG, "getItem: isRecent = " + BookSiloGridFragment.isRecents);

//                if (!BookSiloGridFragment.isRecents && BookSiloView.currentPosition == -1) {
//                    isAdAdded1 = true;
//                }
//            }

            if (isAdAdded && (isAdAdded1 || isAdAdded2)) {
                isAdded = true;
            }
        }

        return mItems.get(mapPosition(index));
    }


    // -------------------------------------------------------------------------
    // Swipe Refresh
    // -------------------------------------------------------------------------

    private void refreshDone() {
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    public void setRefreshView(SwipeRefreshLayout srl) {
        this.swipeRefreshLayout = srl;
        srl.setOnRefreshListener(() -> {
            try {
                swipeRefreshLayout.setRefreshing(false);
            } catch (Exception ignored) {
            }
        });
    }


    // -------------------------------------------------------------------------
    // Retry Snackbar
    // -------------------------------------------------------------------------

    private void setupRetrySnackbar() {
        snackbar = Snackbar.make(
                retryLayout,
                R.string.internet_conn_failed,
                Snackbar.LENGTH_SHORT
        ).setAction("Retry", v -> retryLoad());
    }

    private void retryLoad() {
        if (swipeRefreshLayout == null || source == null || mItems == null) {
            Toast.makeText(
                    swipeRefreshLayout.getContext(),
                    "Data not found",
                    Toast.LENGTH_SHORT
            ).show();
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        if (retryLayout != null) retryLayout.setVisibility(View.GONE);

        if (Manager.getInstance().hasInternetConnection(swipeRefreshLayout.getContext())) {
            swipeRefreshLayout.setRefreshing(true);
            refresh();
        } else {
            swipeRefreshLayout.setRefreshing(false);
            snackbar.show();
            if (retryLayout != null) retryLayout.setVisibility(View.VISIBLE);
        }
    }
}

