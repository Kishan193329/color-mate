package com.colourmate.paintbynumber.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Runs ColorForge's OWN playable page — the exact file the admin's "Open playable preview" opens.
 *
 * <p>Why this exists: {@code RevealView} was a hand port of that page, and every round of testing
 * found another detail it had missed (numbers drawn over everything, a sparse palette listing
 * numbers with no regions…). A port will always trail the original. Loading the page itself means
 * the phone runs the same code, so it looks and behaves the same by construction — and any future
 * improvement to the ColorForge engine reaches the app for free.
 *
 * <p>The page is self-contained (the data is inlined by {@code reveal_game_builder}), so it plays
 * from assets with no server. A URL can be passed instead once the server has a home.
 */
public class WebGameActivity extends AppCompatActivity {

    private static final String EXTRA_ASSET = "web_game_asset";
    private static final String EXTRA_URL = "web_game_url";

    private WebView webView;

    /** Plays a page bundled in assets, e.g. {@code play_color.html}. */
    public static Intent fromAsset(Context context, String assetName) {
        Intent intent = new Intent(context, WebGameActivity.class);
        intent.putExtra(EXTRA_ASSET, assetName);
        return intent;
    }

    public static Intent fromUrl(Context context, String url) {
        Intent intent = new Intent(context, WebGameActivity.class);
        intent.putExtra(EXTRA_URL, url);
        return intent;
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // The page paints its own dark background; matching it avoids a white flash on open.
        webView.setBackgroundColor(Color.parseColor("#17161a"));
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);          // the game IS JavaScript
        settings.setDomStorageEnabled(true);          // so the page can keep its own progress
        settings.setAllowFileAccess(true);
        settings.setBuiltInZoomControls(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        String asset = getIntent().getStringExtra(EXTRA_ASSET);
        String url = getIntent().getStringExtra(EXTRA_URL);
        webView.loadUrl(asset != null ? "file:///android_asset/" + asset : url);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
