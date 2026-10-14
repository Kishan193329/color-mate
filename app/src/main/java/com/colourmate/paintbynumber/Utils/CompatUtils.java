package com.colourmate.paintbynumber.Utils;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;

public class CompatUtils {

    /**
     * Sets elevation in dp units in a backward-compatible way.
     *
     * @param view The view to elevate
     * @param dp   Elevation in dp
     */
    public static void setElevationCompat(View view, float dp) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.setElevation(dpToPx(view.getContext(), dp));
        }
    }

    /**
     * Converts dp to actual pixels using real screen density.
     *
     * @param dp Value in dp
     * @return pixels
     */
    public static float dpToPx(Context context, float dp) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.getResources().getDisplayMetrics()
        );
    }

    public static void setupStatusBar(Activity activity, boolean lightStatusBar, int statusBarColor) {
        Window window = activity.getWindow();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+
            window.setStatusBarColor(statusBarColor);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                if (lightStatusBar) {
                    controller.setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    );
                } else {
                    controller.setSystemBarsAppearance(
                            0,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    );
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6.0+
            window.setStatusBarColor(statusBarColor);
            View decorView = window.getDecorView();
            int flags = decorView.getSystemUiVisibility();
            if (lightStatusBar) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            decorView.setSystemUiVisibility(flags);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Android 5.0+
            window.setStatusBarColor(statusBarColor);
        }
    }

}

