package com.colourmate.paintbynumber.util;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.views.CustomGridlayoutManager;
import com.colourmate.paintbynumber.views.WrapContentLinearLayoutManager;

import java.util.HashMap;
import java.util.Map;

public class UIHelpers {
    private static final String TAG = "UIHelpers";

    public static class FontCache {
        private static final Map<String, Typeface> fontMap = new HashMap<>();

        public static Typeface getFont(Context context, String assetPath) {
            if (context == null || assetPath == null) {
                throw new IllegalArgumentException("Context and assetPath must not be null");
            }

            synchronized (fontMap) {
                if (fontMap.containsKey(assetPath)) {
                    return fontMap.get(assetPath);
                }

                Typeface typeface = Typeface.createFromAsset(context.getAssets(), assetPath);
                fontMap.put(assetPath, typeface);
                return typeface;
            }
        }
    }

    public static void setupGridRecyclerView(Context context, RecyclerView recyclerView,
            RecyclerView.Adapter<?> adapter) {
        if (recyclerView == null) {
            Log.e(TAG, "RecyclerView is null!");
            return;
        }

        if (context == null) {
            Log.e(TAG, "Context is null!");
            return;
        }

        // Get number of columns from resources, fallback to 3 if invalid
        int columns = context.getResources().getInteger(R.integer.library_grid_columns);
        if (columns <= 0) {
            columns = 3;
        }

        recyclerView.setLayoutManager(new CustomGridlayoutManager(context, columns));
//        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(20);
        recyclerView.setDrawingCacheEnabled(true);
        recyclerView.setDrawingCacheQuality(android.view.View.DRAWING_CACHE_QUALITY_HIGH);
        recyclerView.setAdapter(adapter);

        // Scroll to top
        recyclerView.scrollToPosition(0);
    }


    /**
     * ✅ Optimized RecyclerView setup for smooth scrolling
     */
    public static void setListRecyclerView(Context context,
                                           RecyclerView recyclerView,
                                           RecyclerView.Adapter<?> adapter,
                                           boolean vertical) {

        int spanCount = context.getResources().getInteger(R.integer.library_grid_columns);
        if (spanCount <= 0) {
            spanCount = 2;
        }

        GridLayoutManager layoutManager = new GridLayoutManager(
                context, spanCount) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }

            // ⚡ FIX 14: Reduce extra layout space
            // 600 is arbitrary. Use actual screen height fraction.
            @Override
            protected int getExtraLayoutSpace(RecyclerView.State state) {
                return 300; // ~half screen is enough
            }
        };

        layoutManager.setOrientation(vertical
                ? RecyclerView.VERTICAL
                : RecyclerView.HORIZONTAL);

        // ⚡ FIX 15: Reduce prefetch - too many causes frame drops
        layoutManager.setInitialPrefetchItemCount(spanCount * 2);

        recyclerView.setLayoutManager(layoutManager);

        // ⚡ FIX 16: Completely disable ItemAnimator
        // Change animations cause full rebind which triggers image reload
        recyclerView.setItemAnimator(null);

        recyclerView.setAdapter(adapter);
    }


    public static int lighterColor(int color, int amount) {
        int alpha = Color.alpha(color);
        int red = Math.min(Color.red(color) + amount, 255);
        int green = Math.min(Color.green(color) + amount, 255);
        int blue = Math.min(Color.blue(color) + amount, 255);

        return Color.argb(alpha, red, green, blue);
    }

    public static int darkerColor(int color, int amount) {
        int alpha = Color.alpha(color);
        int red = Math.max(Color.red(color) - amount, 0);
        int green = Math.max(Color.green(color) - amount, 0);
        int blue = Math.max(Color.blue(color) - amount, 0);

        return Color.argb(alpha, red, green, blue);
    }

}
