package com.colourmate.paintbynumber.views;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * A custom {@link GridLayoutManager} that safely handles potential IndexOutOfBoundsExceptions
 * during the layout phase, which can occur in some edge cases with RecyclerView.
 * It also disables predictive item animations by default.
 */
public class CustomGridlayoutManager extends GridLayoutManager {

    private static final String TAG = "CustomGridLayoutManager";

    public CustomGridlayoutManager(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public CustomGridlayoutManager(Context context, int spanCount) {
        super(context, spanCount);
    }

    public CustomGridlayoutManager(Context context, int spanCount, int orientation, boolean reverseLayout) {
        super(context, spanCount, orientation, reverseLayout);
    }

    @Override
    public boolean supportsPredictiveItemAnimations() {
        return false;
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        try {
            super.onLayoutChildren(recycler, state);
        } catch (IndexOutOfBoundsException e) {
            // This try-catch is a safeguard against a known issue in RecyclerView where
            // rapid data changes can lead to an IndexOutOfBoundsException.
            Log.e(TAG, "Caught an IndexOutOfBoundsException in onLayoutChildren", e);
        }
    }
}
