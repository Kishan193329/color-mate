package com.colourmate.paintbynumber.util;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.view.Window;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * One way to go edge-to-edge (2026-10-14 design pass). Home, Daily and the secondary screens
 * used to each do this differently — or not at all, which is why Favourites, How-to-play and
 * the pixel game wore a solid band across the top while Home did not.
 *
 * <p>{@link #apply}: transparent status bar, dark status icons (every ground in the app is
 * light), {@code top} gets the status-bar inset added to its padding, {@code bottom} gets the
 * navigation-bar inset. Either may be null.
 */
public final class EdgeToEdge {

    private EdgeToEdge() {}

    public static void apply(Activity activity, View top, View bottom) {
        Window window = activity.getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        WindowCompat.setDecorFitsSystemWindows(window, false);
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(window, window.getDecorView());
        c.setAppearanceLightStatusBars(true);
        c.setAppearanceLightNavigationBars(true);

        if (top != null) {
            final int basePad = top.getPaddingTop();
            ViewCompat.setOnApplyWindowInsetsListener(top, (v, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
                v.setPadding(v.getPaddingLeft(), basePad + bars.top, v.getPaddingRight(), v.getPaddingBottom());
                return insets;
            });
        }
        if (bottom != null) {
            final int basePad = bottom.getPaddingBottom();
            ViewCompat.setOnApplyWindowInsetsListener(bottom, (v, insets) -> {
                Insets nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), basePad + nav.bottom);
                return insets;
            });
        }
    }
}
