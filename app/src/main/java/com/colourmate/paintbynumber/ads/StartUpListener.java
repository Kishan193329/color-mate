package com.colourmate.paintbynumber.ads;

/**
 * Listener interface for application startup completion events.
 * Invoked when the app initialization process (including ad loading) is complete.
 */
public interface StartUpListener {

    /**
     * Called when the startup process is completed.
     * This includes Firebase data loading, ad initialization, and splash ads.
     * The app should proceed to the main screen after this callback.
     */
    void onCompleted();
}