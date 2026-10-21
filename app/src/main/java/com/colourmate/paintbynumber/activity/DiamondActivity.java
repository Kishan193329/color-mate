package com.colourmate.paintbynumber.activity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.adapter.DiamondPaletteAdapter;
import com.colourmate.paintbynumber.ads.AdController;
import com.colourmate.paintbynumber.ads.MediationManager;
import com.colourmate.paintbynumber.model.diamond.DiamondLevel;
import com.colourmate.paintbynumber.model.diamond.DiamondView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * The diamond-painting game screen.
 *
 * <p>Takes either an asset name (the bundled sample) or a URL to a ColorForge diamond payload.
 * Parsing happens off the UI thread — the lesson from §8.6 H2, applied from the start rather than
 * retro-fitted.
 */
public class DiamondActivity extends AppCompatActivity
        implements DiamondView.Listener, DiamondPaletteAdapter.OnColourSelected {

    private static final String TAG = "DiamondActivity";

    private static final String EXTRA_ASSET = "diamond_asset";
    private static final String EXTRA_URL = "diamond_url";
    private static final String EXTRA_KEY = "diamond_key";

    /** How long the bottom slot may sit on its "Loading Ads" placeholder before it is dropped. */
    private static final long BANNER_TIMEOUT_MS = 12_000L;

    private static final String PREFS = "diamond_progress";

    private DiamondView diamondView;
    private RecyclerView paletteView;
    private ProgressBar loadingView;
    private TextView progressText;

    private DiamondPaletteAdapter paletteAdapter;
    private DiamondLevel level;
    private String progressKey;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static Intent fromAsset(Context context, String assetName) {
        Intent intent = new Intent(context, DiamondActivity.class);
        intent.putExtra(EXTRA_ASSET, assetName);
        intent.putExtra(EXTRA_KEY, assetName);
        return intent;
    }

    public static Intent fromUrl(Context context, String url, String key) {
        Intent intent = new Intent(context, DiamondActivity.class);
        intent.putExtra(EXTRA_URL, url);
        intent.putExtra(EXTRA_KEY, key);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diamond);

        diamondView = findViewById(R.id.diamond_view);
        paletteView = findViewById(R.id.rv_diamond_palette);
        loadingView = findViewById(R.id.pb_diamond_loading);
        progressText = findViewById(R.id.tv_diamond_progress);

        findViewById(R.id.fl_diamond_back).setOnClickListener(v -> leaveWithAd());

        // Tools, mirroring the game screen's rail: zoom back out, and jump to the next colour.
        findViewById(R.id.fl_diamond_fit).setOnClickListener(v -> diamondView.fitToScreen());
        findViewById(R.id.btn_diamond_loupe).setOnClickListener(v -> diamondView.fitToScreen());
        findViewById(R.id.btn_diamond_next).setOnClickListener(v -> {
            if (level == null) return;
            int next = firstUnfinishedColour();
            diamondView.setSelectedColour(next);
            if (paletteAdapter != null) {
                paletteAdapter.setSelected(next);
                paletteView.scrollToPosition(Math.max(0, next - 1));
            }
        });

        paletteView.setLayoutManager(
                new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        // No setHasFixedSize: the strip is height=wrap_content and lint (InvalidSetHasFixedSize)
        // treats that as a fatal release error. A palette of a few dozen chips does not need it.
        paletteView.setItemViewCacheSize(4);

        diamondView.setListener(this);
        setupBannerAd();

        progressKey = getIntent().getStringExtra(EXTRA_KEY);
        loadLevel();
    }

    /** Bottom banner, the same one the By Number and reveal games carry. */
    private void setupBannerAd() {
        View adStrip = findViewById(R.id.ll_diamond_ads);
        ViewGroup slot = findViewById(R.id.container_banner);
        if (AdController.shouldShowAds(this) && slot != null) {
            MediationManager.getBannerAd(this, slot);
            AdController.hideBannerSlotIfEmpty(adStrip, slot, BANNER_TIMEOUT_MS);
        } else if (adStrip != null) {
            adStrip.setVisibility(View.GONE);
        }
    }

    // ─── Loading ───

    private void loadLevel() {
        final String asset = getIntent().getStringExtra(EXTRA_ASSET);
        final String url = getIntent().getStringExtra(EXTRA_URL);

        new Thread(() -> {
            DiamondLevel loaded = null;
            String error = null;

            try {
                String json = asset != null ? readAsset(asset) : readUrl(url);
                loaded = DiamondLevel.fromJson(json);
                loaded.restoreProgress(savedProgress());
            } catch (Exception e) {
                Log.e(TAG, "Diamond level load failed", e);
                error = e.getMessage();
            }

            final DiamondLevel result = loaded;
            final String failure = error;

            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;

                loadingView.setVisibility(View.GONE);

                if (result == null) {
                    Toast.makeText(this, "Could not open this picture", Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Load failed: " + failure);
                    finish();
                    return;
                }

                showLevel(result);
            });
        }).start();
    }

    private void showLevel(DiamondLevel loaded) {
        this.level = loaded;

        diamondView.setLevel(loaded);
        diamondView.setSelectedColour(firstUnfinishedColour());

        paletteAdapter = new DiamondPaletteAdapter(loaded, this);
        paletteAdapter.setSelected(diamondView.getSelectedColour());
        paletteView.setAdapter(paletteAdapter);

        updateProgressText();
    }

    private int firstUnfinishedColour() {
        for (int n = 1; n <= level.colourCount(); n++) {
            if (!level.isColourFinished(n)) return n;
        }
        return 1;
    }

    private String readAsset(String name) throws Exception {
        try (InputStream in = getAssets().open(name)) {
            return readAll(in);
        }
    }

    private String readUrl(String url) throws Exception {
        // One dropped packet used to lose the picture: the board is fetched once, and any
        // failure ends as "Could not open this picture". Three tries with a growing pause
        // costs nothing when the network is fine and saves the open when it blinks.
        Exception last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                try (InputStream in = connection.getInputStream()) {
                    return readAll(in);
                }
            } catch (Exception e) {
                last = e;
                if (attempt < 3) {
                    try {
                        Thread.sleep(400L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        throw last;
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toString("UTF-8");
    }

    // ─── Play callbacks ───

    @Override
    public void onColourSelected(int colourNumber) {
        diamondView.setSelectedColour(colourNumber);
    }

    @Override
    public void onBeadsPlaced(int colourNumber, int placedOfColour, int totalOfColour) {
        updateProgressText();

        if (paletteAdapter != null) {
            paletteAdapter.notifyItemChanged(colourNumber - 1);
        }

        // Colour done → hop to the next one that still needs beads, so play never stalls.
        if (placedOfColour >= totalOfColour) {
            int next = firstUnfinishedColour();
            if (next != colourNumber) {
                diamondView.setSelectedColour(next);
                if (paletteAdapter != null) paletteAdapter.setSelected(next);
            }
        }
    }

    @Override
    public void onLevelFinished() {
        // A sheet, like the reveal game — the finish used to be a two-second Toast.
        com.colourmate.paintbynumber.util.SoundUtils.playSound(
                this, com.colourmate.paintbynumber.util.SoundUtils.GameSound.TAP);
        android.view.View sheet = findViewById(R.id.fl_diamond_done);
        android.view.View card = findViewById(R.id.ll_diamond_done_card);
        if (sheet == null || card == null) return;
        findViewById(R.id.btn_diamond_done_close).setOnClickListener(v -> leaveWithAd());
        sheet.setAlpha(0f);
        sheet.setVisibility(android.view.View.VISIBLE);
        card.setTranslationY(160f * getResources().getDisplayMetrics().density);
        sheet.animate().alpha(1f).setStartDelay(700).setDuration(260).start();
        card.animate().translationY(0f).setStartDelay(700).setDuration(320)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }

    private void updateProgressText() {
        if (level == null) return;
        int percent = Math.round(level.placedCells() * 100f / level.totalCells());
        progressText.setText(percent + "%");
    }

    // ─── Progress persistence ───

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private String savedProgress() {
        return progressKey == null ? null : prefs().getString(progressKey, null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (level != null && progressKey != null) {
            prefs().edit().putString(progressKey, level.encodeProgress()).apply();
        }
    }

    /**
     * Leaving a picture is the natural break, and it is exactly where By Number shows its
     * interstitial (Diamond had none at all — only a banner). MediationManager applies the
     * usual frequency rule, so this is not an ad on every exit, and the callback runs whether
     * an ad showed, failed or was skipped, so the screen always closes.
     */
    private void leaveWithAd() {
        if (leavingViaAd) return;
        leavingViaAd = true;
        MediationManager.showInterstitial(this, this::finish);
    }

    private boolean leavingViaAd = false;

    @Override
    public void onBackPressed() {
        leaveWithAd();
    }
}
