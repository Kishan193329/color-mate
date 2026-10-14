package com.colourmate.paintbynumber.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ScrollView;

import androidx.annotation.Nullable;

/**
 * A custom {@link ScrollView} that allows for enabling or disabling of scrolling.
 * When scrolling is disabled, this view will not intercept touch events, allowing
 * child views to handle them.
 */
public class LockableScrollView extends ScrollView {

    private boolean isScrollingEnabled = true;

    public LockableScrollView(Context context) {
        super(context);
    }

    public LockableScrollView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public LockableScrollView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * Enables or disables scrolling for this ScrollView.
     *
     * @param enabled true to enable scrolling, false to disable it.
     */
    public void setScrollingEnabled(boolean enabled) {
        this.isScrollingEnabled = enabled;
    }

    /**
     * Checks if scrolling is currently enabled.
     *
     * @return true if scrolling is enabled, false otherwise.
     */
    public boolean isScrollingEnabled() {
        return isScrollingEnabled;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Only allow touch events to be processed if scrolling is enabled.
        // If not enabled, returning false means this view doesn't handle the event.
        return isScrollingEnabled && super.onTouchEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        // Only intercept touch events to start a scroll if scrolling is enabled.
        // Otherwise, let child views handle the touch events.
        return isScrollingEnabled && super.onInterceptTouchEvent(event);
    }
}
