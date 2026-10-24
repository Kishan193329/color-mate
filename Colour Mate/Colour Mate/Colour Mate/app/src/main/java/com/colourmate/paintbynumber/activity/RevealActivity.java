package com.colourmate.paintbynumber.activity;

import android.content.Context;
import android.content.Intent;
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
import com.colourmate.paintbynumber.adapter.RevealPaletteAdapter;
import com.colourmate.paintbynumber.ads.AdController;
import com.colourmate.paintbynumber.ads.MediationManager;
import com.colourmate.paintbynumber.model.reveal.RevealAssetCache;
import com.colourmate.paintbynumber.model.reveal.RevealLevel;
import com.colourmate.paintbynumber.model.reveal.RevealView;
import com.colourmate.paintbynumber.util.Multiplayer;


/**
 * The ColorForge photo-reveal game — the "New 2026" way of playing, matching what the admin's
 * review preview shows. {@link GameActivity} (flat fill) is untouched and still serves the four
 * original tabs, whose paid content has no original photo to reveal.
 */
public class RevealActivity extends AppCompatActivity
        implements RevealView.Listener, RevealPaletteAdapter.OnNumberSelected {

    private static final String TAG = "RevealActivity";

    private static final String EXTRA_ASSET_DIR = "reveal_assets";
    private static final String EXTRA_BASE_URL = "reveal_base_url";
    private static final String EXTRA_FILE_NAME = "reveal_file_name";
    // The rest of the gallery, so the completion sheet can offer "Next picture" without
    // going back through the grid. Parallel arrays: url[i] plays under name[i].
    private static final String EXTRA_QUEUE_URLS = "reveal_queue_urls";
    private static final String EXTRA_QUEUE_NAMES = "reveal_queue_names";
    // Multiplayer (util/Multiplayer): mode challenge|race|coop, the code, and for a challenge
    // the time to beat and who set it.
    private static final String EXTRA_MP_MODE = "mp_mode";
    private static final String EXTRA_MP_CODE = "mp_code";
    private static final String EXTRA_MP_TARGET_MS = "mp_target_ms";
    private static final String EXTRA_MP_TARGET_BY = "mp_target_by";

    private static final String PREFS = "reveal_progress";

    /** How long the bottom slot may sit on its "Loading Ads" placeholder before it is dropped. */
    private static final long BANNER_TIMEOUT_MS = 12_000L;

    private RevealView revealView;
    private RecyclerView paletteView;
    private ProgressBar loadingView;
    private TextView progressText;

    private RevealPaletteAdapter paletteAdapter;
    private RevealLevel level;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** Null when playing alone. */
    private RevealMultiplayer multiplayer;
    /** Solo play time for this picture, kept across sittings so a challenge carries the real time. */
    private long soloMs;
    private long soloResumedAt = -1;

    /** Opens a picture bundled in assets, e.g. the sample under {@code assets/reveal/}. */
    public static Intent fromAssets(Context context, String assetDirectory) {
        Intent intent = new Intent(context, RevealActivity.class);
        intent.putExtra(EXTRA_ASSET_DIR, assetDirectory);
        return intent;
    }

    /** Opens a picture from a ColorForge bundle URL (the directory holding regions.json etc). */
    public static Intent fromUrl(Context context, String baseUrl) {
        Intent intent = new Intent(context, RevealActivity.class);
        intent.putExtra(EXTRA_BASE_URL, baseUrl);
        return intent;
    }

    /** As {@link #fromUrl}, carrying the gallery so the finish sheet can chain to the next
     *  unfinished picture. {@code fileName} also names the saved artwork in My Works. */
    public static Intent fromUrl(Context context, String baseUrl, String fileName,
                                 String[] queueUrls, String[] queueNames) {
        Intent intent = fromUrl(context, baseUrl);
        intent.putExtra(EXTRA_FILE_NAME, fileName);
        intent.putExtra(EXTRA_QUEUE_URLS, queueUrls);
        intent.putExtra(EXTRA_QUEUE_NAMES, queueNames);
        return intent;
    }

    /** A multiplayer game on a picture: a fresh board, never the player's solo progress. */
    public static Intent forMultiplayer(Context context, String baseUrl, String fileName,
                                        String mode, String code, long targetMs, String targetBy) {
        Intent intent = fromUrl(context, baseUrl);
        intent.putExtra(EXTRA_FILE_NAME, fileName);
        intent.putExtra(EXTRA_MP_MODE, mode);
        intent.putExtra(EXTRA_MP_CODE, code);
        intent.putExtra(EXTRA_MP_TARGET_MS, targetMs);
        intent.putExtra(EXTRA_MP_TARGET_BY, targetBy);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reveal);
        hideStatusBar();   // immersive, like the By Number game — top controls not clipped

        revealView = findViewById(R.id.reveal_view);
        paletteView = findViewById(R.id.rv_reveal_palette);
        loadingView = findViewById(R.id.pb_reveal_loading);
        progressText = findViewById(R.id.tv_reveal_progress);

        findViewById(R.id.fl_reveal_back).setOnClickListener(v -> leaveWithAd());
        findViewById(R.id.btn_done_close).setOnClickListener(v -> leaveWithAd());
        findViewById(R.id.btn_done_next).setOnClickListener(v -> openNextPicture());
        findViewById(R.id.btn_done_share).setOnClickListener(v -> shareFinishedArtwork());
        findViewById(R.id.btn_reveal_fit).setOnClickListener(v -> revealView.fitToScreen());
        findViewById(R.id.btn_reveal_next).setOnClickListener(v -> requestHint());
        findViewById(R.id.btn_reveal_friends).setOnClickListener(v -> askPlayWithFriends());
        findViewById(R.id.btn_done_challenge).setOnClickListener(v -> createChallenge());

        String mpMode = getIntent().getStringExtra(EXTRA_MP_MODE);
        if (mpMode != null) {
            multiplayer = new RevealMultiplayer(this, revealView, mpMode,
                    getIntent().getStringExtra(EXTRA_MP_CODE),
                    getIntent().getLongExtra(EXTRA_MP_TARGET_MS, 0),
                    getIntent().getStringExtra(EXTRA_MP_TARGET_BY));
        }

        paletteView.setLayoutManager(
                new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        // No setHasFixedSize: the strip is height=wrap_content and lint (InvalidSetHasFixedSize)
        // treats that as a fatal release error. A palette of a few dozen chips does not need it.
        paletteView.setItemViewCacheSize(4);

        // The palette floats over the bottom of the board; measure it once it has a height and
        // tell the board, so no number is drawn where the strip will cover it.
        final View paletteBar = findViewById(R.id.fl_reveal_palette);
        if (paletteBar != null) {
            paletteBar.post(() -> {
                int[] board = new int[2], strip = new int[2];
                revealView.getLocationOnScreen(board);
                paletteBar.getLocationOnScreen(strip);
                float overlap = (board[1] + revealView.getHeight()) - strip[1];
                revealView.setLabelBottomInset(Math.max(0f, overlap));
            });
        }

        revealView.setListener(this);
        setupBannerAd();
        loadLevel();
    }

    /**
     * The layout reserves a bottom banner slot (and shows a "Loading Ads" placeholder inside it)
     * but nothing ever asked for an ad, so the placeholder sat there for good. Request the banner
     * the same way the By Number game does, and drop the whole strip when ads are off.
     */
    private void setupBannerAd() {
        View adStrip = findViewById(R.id.ll_reveal_ads);
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
        final String assetDirectory = getIntent().getStringExtra(EXTRA_ASSET_DIR);
        final String baseUrl = getIntent().getStringExtra(EXTRA_BASE_URL);

        // Decoding three full-size images and building the id map is far too heavy for the UI
        // thread — the same lesson as §8.6 H2.
        new Thread(() -> {
            RevealLevel loaded = null;
            try {
                // Cached on disk: a picture is opened again and again to finish it, and
                // every open used to re-download the whole 2 MB set. See RevealAssetCache.
                RevealLevel.Source source = assetDirectory != null
                        ? name -> getAssets().open(assetDirectory + "/" + name)
                        : RevealAssetCache.source(this, baseUrl);
                loaded = RevealLevel.load(source);
                if (multiplayer == null) loaded.restoreProgress(savedProgress());
            } catch (Exception e) {
                Log.e(TAG, "Reveal level load failed", e);
            }

            final RevealLevel result = loaded;
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;

                loadingView.setVisibility(View.GONE);
                if (result == null) {
                    Toast.makeText(this, "Could not open this picture", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                showLevel(result);
            });
        }).start();
    }

    private void showLevel(RevealLevel loaded) {
        this.level = loaded;

        revealView.setLevel(loaded);
        int first = firstUnfinishedNumber();
        revealView.setSelectedNumber(first);

        paletteAdapter = new RevealPaletteAdapter(loaded, this);
        paletteAdapter.setSelected(first);
        paletteView.setAdapter(paletteAdapter);

        updateProgress();

        if (multiplayer != null) {
            multiplayer.start(loaded);
        } else {
            soloResumedAt = android.os.SystemClock.elapsedRealtime();
            String key = progressKey();
            if (key != null) soloMs = getSharedPreferences(PREFS, MODE_PRIVATE).getLong(key + "#ms", 0);
            findViewById(R.id.btn_reveal_friends).setVisibility(
                    canPlayTogether() ? View.VISIBLE : View.GONE);
        }
    }

    private int firstUnfinishedNumber() {
        for (int n = 1; n <= level.numberCount(); n++) {
            if (level.totalOf(n) > 0 && !level.isNumberFinished(n)) return n;
        }
        return 1;
    }

    private void jumpToNextNumber() {
        if (level == null) return;

        int next = firstUnfinishedNumber();
        revealView.setSelectedNumber(next);
        if (paletteAdapter != null) {
            paletteAdapter.setSelected(next);
            int position = paletteAdapter.positionOf(next);   // sparse palette: number != position
            if (position >= 0) paletteView.scrollToPosition(position);
        }
    }

    // ─── Play callbacks ───

    @Override
    public void onNumberSelected(int number) {
        revealView.setSelectedNumber(number);
    }

    /** The board picked a colour for us (double-tap / long-press on a region) — mirror it in the
     *  palette so the strip highlights and scrolls to the same number. */
    @Override
    public void onNumberPicked(int number) {
        if (paletteAdapter != null) {
            paletteAdapter.setSelected(number);
            int position = paletteAdapter.positionOf(number);
            if (position >= 0) paletteView.scrollToPosition(position);
        }
    }

    @Override
    public void onRegionRevealed(int number) {
        updateProgress();
        if (paletteAdapter != null) {
            // refreshCounts() also drops a colour once its last region is filled, and reports
            // whether the strip changed — a plain notifyItemChanged would leave the finished
            // colour sitting there.
            if (!paletteAdapter.refreshCounts()) {
                int position = paletteAdapter.positionOf(number);
                if (position >= 0) paletteAdapter.notifyItemChanged(position);
            }
        }

        if (level.isNumberFinished(number)) jumpToNextNumber();
        if (multiplayer != null) multiplayer.onProgress(level.progressPercent());
    }

    @Override
    public void onRegionFilled(RevealLevel.Region region, boolean byFriend) {
        if (multiplayer != null) multiplayer.onFilled(region, byFriend);
    }

    /**
     * Hint: find a region still waiting to be filled, select its colour, and let the board glide
     * onto it with a pulsing ring.
     *
     * <p>Prefers the colour the player already has in hand — a hint that changes the selection
     * under them is disorienting — and only moves on to another colour when that one is done.
     */
    /** How many wand hints a picture gives before one costs a rewarded ad. */
    private static final int FREE_HINTS = 3;

    /**
     * The wand was free and unlimited, which made it worthless as a mechanic and worthless
     * as inventory. Now: the first FREE_HINTS per picture are free; after that a rewarded ad
     * buys the next one — the same trade the By Number game offers. Premium (ads off)
     * keeps unlimited hints: that is part of what was paid for.
     * If no video can be shown, the hint is GRANTED, not refused — a fill error on the ad
     * network’s side must never lock a player out of help (§8.5ai’s lesson).
     */
    private void requestHint() {
        if (!AdController.shouldShowAds(this)) { showHint(); return; }
        String key = progressKey();
        if (key == null) { showHint(); return; }
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int used = prefs.getInt(key + "#hints", 0);
        if (used < FREE_HINTS) {
            prefs.edit().putInt(key + "#hints", used + 1).apply();
            showHint();
            return;
        }
        // Coins first (2026-10-14): a hint costs HINT_COST from the purse the daily bonus and
        // rewarded videos fill. Only an empty purse falls through to the video.
        if (com.colourmate.paintbynumber.util.CoinManager.trySpend(this,
                com.colourmate.paintbynumber.util.CoinManager.HINT_COST)) {
            android.widget.Toast.makeText(this, "-" + com.colourmate.paintbynumber.util.CoinManager.HINT_COST
                    + " coins", android.widget.Toast.LENGTH_SHORT).show();
            showHint();
            return;
        }
        MediationManager.showRewardedAd(this, new com.colourmate.paintbynumber.ads.RewardedAdListener() {
            @Override public void onRewardGranted() { runOnUiThread(RevealActivity.this::showHint); }
            @Override public void onRewardNull() { runOnUiThread(RevealActivity.this::showHint); }
            @Override public void onRewardProgress() { }
            @Override public void onVideoSkipped() { }
        });
    }

    private void showHint() {
        if (level == null || revealView == null) return;

        RevealLevel.Region target = level.hintRegion(revealView.getSelectedNumber());
        if (target == null) target = level.hintRegion(0);          // 0 = any colour
        if (target == null) return;                                // picture is finished

        if (target.number != revealView.getSelectedNumber()) {
            revealView.setSelectedNumber(target.number);
            if (paletteAdapter != null) {
                paletteAdapter.setSelected(target.number);
                int position = paletteAdapter.positionOf(target.number);
                if (position >= 0) paletteView.scrollToPosition(position);
            }
        }
        revealView.showHint(target);
    }

    @Override
    public void onFinished() {
        // Opened from the Daily tab: finishing marks that date done, same as the By Number game.
        String dailyDate = getIntent().getStringExtra(GameActivity.EXTRA_DAILY_DATE);
        if (dailyDate != null) {
            com.colourmate.paintbynumber.util.DailyChallengeManager.markCompleted(this, dailyDate);
        }
        // The best moment of the whole session used to be a two-second Toast. Now: the full
        // picture (zoomed to fit), a particle burst over it, the artwork saved into My Works,
        // and a sheet with "Next picture" — which is also a natural ad break.
        revealView.fitToScreen();
        revealView.celebrate();
        saveFinishedArtwork();
        rewardCoins(getIntent().getStringExtra(EXTRA_FILE_NAME));

        setDoneText(getString(R.string.reveal_done_title), getString(R.string.reveal_done_sub));
        if (multiplayer != null) {
            multiplayer.onFinished();
            findViewById(R.id.btn_done_next).setVisibility(View.GONE);
        } else {
            pauseSoloClock();
            findViewById(R.id.btn_done_challenge).setVisibility(
                    canPlayTogether() ? View.VISIBLE : View.GONE);
        }

        View sheet = findViewById(R.id.fl_reveal_done);
        View card = findViewById(R.id.ll_done_card);
        if (multiplayer == null) {
            findViewById(R.id.btn_done_next).setVisibility(
                    nextUnfinished() != null ? View.VISIBLE : View.GONE);
        }
        sheet.setAlpha(0f);
        sheet.setVisibility(View.VISIBLE);
        // Let the player look at their finished picture first; the sheet slides up after.
        card.setTranslationY(dp(160));
        sheet.animate().alpha(1f).setStartDelay(900).setDuration(260).start();
        card.animate().translationY(0f).setStartDelay(900).setDuration(320)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    /**
     * The finished artwork joins My Works — the same folder and naming the By Number game
     * uses (GameViewModel), so the Saved tab and FavoritesActivity list it with no changes.
     * fileName is date-prefixed and globally unique (§3.9), so it cannot collide.
     */
    private void saveFinishedArtwork() {
        final RevealLevel done = level;
        final String fileName = artworkName();
        if (done == null || fileName == null) return;
        new Thread(() -> {
            try {
                android.graphics.Bitmap art = android.graphics.Bitmap.createBitmap(
                        done.width, done.height, android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas c = new android.graphics.Canvas(art);
                if (done.vector != null) {
                    done.vector.renderFinished(c, "soft".equals(done.look) ? 0xFFA3A3A3 : 0xFF000000);
                } else {
                    art.setPixels(done.display, 0, done.width, 0, 0, done.width, done.height);
                    if (done.border != null) c.drawBitmap(done.border, 0f, 0f, null);
                }
                java.io.File dir = new java.io.File(getFilesDir(),
                        com.colourmate.paintbynumber.viewmodel.GameViewModel.COLOR_NUMBER_PATH);
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(
                        new java.io.File(dir, fileName + ".png"))) {
                    art.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
                }
                art.recycle();
            } catch (Exception e) {
                Log.w(TAG, "could not save the finished artwork", e);
            }
        }, "reveal-save").start();
    }

    /** The finished PNG through the app’s existing share screen (ArtworkShareActivity —
     *  the same one Favourites uses). The finish saves the file on a background thread, so
     *  a quick tap can arrive first; retry once shortly after instead of failing. */
    private void shareFinishedArtwork() {
        String name = artworkName();
        if (name == null) return;
        java.io.File img = new java.io.File(new java.io.File(getFilesDir(),
                com.colourmate.paintbynumber.viewmodel.GameViewModel.COLOR_NUMBER_PATH),
                name + ".png");
        if (img.exists()) {
            Intent share = new Intent(this, ArtworkShareActivity.class);
            share.putExtra("path", img.getAbsolutePath());
            startActivity(share);
        } else {
            mainHandler.postDelayed(() -> {
                if (!isFinishing() && img.exists()) {
                    Intent share = new Intent(this, ArtworkShareActivity.class);
                    share.putExtra("path", img.getAbsolutePath());
                    startActivity(share);
                }
            }, 600);
        }
    }

    /** +20 coins the first time a picture is finished (util/CoinManager). */
    private void rewardCoins(String fileName) {
        int got = com.colourmate.paintbynumber.util.CoinManager.rewardCompletion(this, fileName);
        if (got > 0) Toast.makeText(this, "Picture finished: +" + got + " coins 🪙", Toast.LENGTH_SHORT).show();
    }

    /** What the saved PNG is called: the app fileName, or the sample asset folder. */
    private String artworkName() {
        String name = getIntent().getStringExtra(EXTRA_FILE_NAME);
        if (name != null && !name.isEmpty()) return name;
        String asset = getIntent().getStringExtra(EXTRA_ASSET_DIR);
        return asset == null ? null : asset.replace('/', '_');
    }

    // ─── "Next picture" ───

    /** The first queued picture after this one that is not already finished. */
    private String[] nextUnfinished() {
        String[] urls = getIntent().getStringArrayExtra(EXTRA_QUEUE_URLS);
        String[] names = getIntent().getStringArrayExtra(EXTRA_QUEUE_NAMES);
        String current = getIntent().getStringExtra(EXTRA_BASE_URL);
        if (urls == null || names == null || current == null) return null;
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int at = -1;
        for (int i = 0; i < urls.length; i++) {
            if (current.equals(urls[i])) { at = i; break; }
        }
        for (int step = 1; step <= urls.length; step++) {         // wraps, skips finished
            int i = ((at < 0 ? -1 : at) + step + urls.length) % urls.length;
            if (urls[i].equals(current)) continue;
            if (prefs.getInt(urls[i] + "#pct", 0) < 100) return new String[]{urls[i], names[i]};
        }
        return null;
    }

    private void openNextPicture() {
        String[] next = nextUnfinished();
        if (next == null) { leaveWithAd(); return; }
        // The same natural break the By Number game monetises; frequency rules still apply.
        if (leavingViaAd) return;
        leavingViaAd = true;
        MediationManager.showInterstitial(this, () -> {
            startActivity(fromUrl(this, next[0], next[1],
                    getIntent().getStringArrayExtra(EXTRA_QUEUE_URLS),
                    getIntent().getStringArrayExtra(EXTRA_QUEUE_NAMES)));
            finish();
        });
    }

    private void updateProgress() {
        if (level != null) progressText.setText(level.progressPercent() + "%");
    }

    // ─── Progress persistence ───

    /** One entry per picture: the asset folder or the bundle URL identifies it. */
    private String progressKey() {
        String asset = getIntent().getStringExtra(EXTRA_ASSET_DIR);
        return asset != null ? asset : getIntent().getStringExtra(EXTRA_BASE_URL);
    }

    private String savedProgress() {
        String key = progressKey();
        return key == null ? null
                : getSharedPreferences(PREFS, MODE_PRIVATE).getString(key, null);
    }

    // ─── Multiplayer ───

    /** The finish sheet's two lines; multiplayer rewrites them as results arrive. */
    void setDoneText(CharSequence title, CharSequence sub) {
        ((TextView) findViewById(R.id.tv_done_title)).setText(title);
        ((TextView) findViewById(R.id.tv_done_sub)).setText(sub);
    }

    /** Only a published server picture can be shared: friends need its fileName and category. */
    private boolean canPlayTogether() {
        return getIntent().getStringExtra(EXTRA_FILE_NAME) != null && category() != null
                && com.colourmate.paintbynumber.Utils.ColorForgeApi.hasExplicitServer(this);
    }

    /** The category segment of {base}index.php/Number/reveal/{category}/{fileName}/. */
    private String category() {
        String url = getIntent().getStringExtra(EXTRA_BASE_URL);
        if (url == null) return null;
        java.util.List<String> parts = android.net.Uri.parse(url).getPathSegments();
        int at = parts.indexOf("reveal");
        return at >= 0 && at + 1 < parts.size() ? parts.get(at + 1) : null;
    }

    private void askPlayWithFriends() {
        String[] items = {getString(R.string.mp_race) + "\n" + getString(R.string.mp_race_desc),
                getString(R.string.mp_coop) + "\n" + getString(R.string.mp_coop_desc)};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.mp_play_with_friends)
                .setItems(items, (d, which) -> JoinActivity.withName(this, () ->
                        openRoom(which == 0 ? Multiplayer.RACE : Multiplayer.COOP)))
                .show();
    }

    private void openRoom(String mode) {
        final String fileName = getIntent().getStringExtra(EXTRA_FILE_NAME);
        final String baseUrl = getIntent().getStringExtra(EXTRA_BASE_URL);
        Multiplayer.createRoom(this, mode, fileName, category(), r -> {
            if (isFinishing()) return;
            if (r == null || !r.has("code")) {
                Toast.makeText(this, R.string.mp_no_server, Toast.LENGTH_LONG).show();
                return;
            }
            String code = r.optString("code");
            startActivity(forMultiplayer(this, baseUrl, fileName, mode, code, 0, null));
        });
    }

    /** Solo finish: send this picture and its time to friends to beat. */
    private void createChallenge() {
        JoinActivity.withName(this, () -> {
            long ms = Math.max(5000, soloMs);
            Multiplayer.createChallenge(this, getIntent().getStringExtra(EXTRA_FILE_NAME), category(), ms,
                    r -> {
                        if (isFinishing()) return;
                        if (r == null || !r.has("code")) {
                            Toast.makeText(this, R.string.mp_no_server, Toast.LENGTH_LONG).show();
                            return;
                        }
                        Multiplayer.share(this, r.optString("code"), Multiplayer.CHALLENGE);
                    });
        });
    }

    private void pauseSoloClock() {
        if (soloResumedAt >= 0) {
            soloMs += android.os.SystemClock.elapsedRealtime() - soloResumedAt;
            soloResumedAt = -1;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (multiplayer == null && level != null && !level.isFinished() && soloResumedAt < 0) {
            soloResumedAt = android.os.SystemClock.elapsedRealtime();
        }
    }

    @Override
    protected void onDestroy() {
        if (multiplayer != null) multiplayer.stop();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();

        // A multiplayer board is the room's, not the player's own copy of this picture.
        if (multiplayer != null) return;
        pauseSoloClock();
        String key = progressKey();
        if (level != null && key != null) {
            // The percent is stored beside the progress string so the GRID can show it
            // without decoding region lists (ColoringsAdapter reads key + "#pct").
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(key, level.encodeProgress())
                    .putInt(key + "#pct", level.progressPercent())
                    .putLong(key + "#ms", soloMs).apply();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideStatusBar();   // re-hide if the bar peeks back after a dialog/ad
    }

    /** Immersive: hide the status bar and draw edge-to-edge, so the top back/progress controls sit at
     *  the true top instead of being clipped under the status bar. Same as {@code GameActivity}. */
    private void hideStatusBar() {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        androidx.core.view.WindowInsetsControllerCompat controller =
                androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars());
            controller.setSystemBarsBehavior(
                    androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    /**
     * Leaving a picture is the natural break, and it is exactly where By Number shows its
     * interstitial (Reveal had none at all — only a banner). MediationManager applies the
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
