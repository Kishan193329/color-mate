package com.colourmate.paintbynumber.Utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

/**
 * Where the "New 2026" tab gets its content: our own ColorForge server.
 *
 * <p>This is deliberately separate from {@link Manager#BASE_URL} / {@link Manager#B_URL}, which
 * point at the paid ionicsolutions backend, come from native code ({@code GetMainUrl()}) and must
 * not be touched — the "By Number" tab depends on them.
 *
 * <p>The ColorForge server has the same API shape as the Number tab (see the backend's
 * {@code number_tab.py}), so the existing REST + image flow works unchanged once the base URL is
 * swapped:
 * <pre>
 *   {base}index.php/Number/category/        → category list
 *   {base}index.php/Number/gimg/c/{cat}     → gallery page (optional ?next=)
 *   {base}thumb/{cat}/t/{file}.png          → grid thumbnail
 *   {base}thumb/{cat}/{file}.png            → line art
 *   {base}thumb/{cat}/{file}.colored.png    → solution
 * </pre>
 *
 * <p><b>The URL is resolved at runtime, never hard-coded into a release:</b>
 * <ol>
 *   <li>a local override (developer/debug, set via {@link #setLocalOverride}),</li>
 *   <li>Firebase Remote Config key {@value #REMOTE_CONFIG_KEY} — lets the owner move the server
 *       without shipping an app update,</li>
 *   <li>{@link #DEFAULT_BASE_URL}.</li>
 * </ol>
 * When none of them is set, {@link #isConfigured} is false and the tab shows its empty state
 * instead of firing requests at a bogus host.
 */
public final class ColorForgeApi {

    private static final String TAG = "ColorForgeApi";

    /** Firebase Remote Config key holding the ColorForge base URL. */
    public static final String REMOTE_CONFIG_KEY = "colorforge_base_url";

    /**
     * Baked-in fallback, used only when the override and Remote Config are both empty.
     *
     * <p>This is the production server — a phone with nothing configured works out of the box.
     * It used to be empty (release) / {@code 127.0.0.1} (debug), which meant a fresh install
     * only worked after either the Firebase Remote Config key was published or an override was
     * written by hand: the test phone had the override, every other phone said "could not
     * reach the server". The server is a permanently owned VPS with TLS now, so baking its URL
     * is safe — and the override and Remote Config still take precedence, so it can be moved
     * later without an app update, exactly as before.
     */
    private static String defaultBaseUrl() {
        return "https://srv1919471.hstgr.cloud/";
    }

    private static final String PREFS = "colorforge_prefs";
    private static final String KEY_OVERRIDE = "base_url_override";

    /** Path suffix that turns the base URL into the Number-tab REST root. */
    private static final String REST_SUFFIX = "index.php/Number/";

    private ColorForgeApi() {
    }

    // ─── Configuration ───

    /**
     * Base URL with a guaranteed trailing slash, or "" when nothing is configured.
     */
    public static String baseUrl(Context context) {
        String url = localOverride(context);

        if (TextUtils.isEmpty(url)) {
            url = fromRemoteConfig();
        }
        if (TextUtils.isEmpty(url)) {
            url = defaultBaseUrl();
        }
        if (TextUtils.isEmpty(url)) {
            return "";
        }

        return url.endsWith("/") ? url : url + "/";
    }

    public static boolean isConfigured(Context context) {
        return !TextUtils.isEmpty(baseUrl(context));
    }

    /**
     * True when the resolved server is a real, reachable host — not a developer loopback.
     *
     * <p>Now that {@link #defaultBaseUrl()} is the production server, the only case this must
     * still exclude is a LOCAL override pointing at the developer machine: things like the live
     * ad config (§8.5az) and the default-tab choice must never be driven by a host that only
     * answers while {@code adb reverse} is running.
     */
    public static boolean hasExplicitServer(Context context) {
        String url = baseUrl(context);
        return !TextUtils.isEmpty(url) && !url.contains("127.0.0.1")
                && !url.contains("localhost") && !url.contains("10.0.2.2");
    }

    /**
     * Points this build at a specific server (a dev tunnel, a LAN address, …).
     * Pass null/empty to clear it and fall back to Remote Config.
     */
    public static void setLocalOverride(Context context, String url) {
        prefs(context).edit().putString(KEY_OVERRIDE, url == null ? "" : url.trim()).apply();
    }

    public static String localOverride(Context context) {
        return prefs(context).getString(KEY_OVERRIDE, "");
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Reports the base URL once Remote Config has had a chance to deliver it. */
    public interface ConfigCallback {
        void onReady(String baseUrl);
    }

    /**
     * Fetches + activates Remote Config, then reports the freshly resolved base URL.
     *
     * <p><b>Why this exists:</b> {@code Manager}'s constructor kicks off {@code fetch()} +
     * {@code activate()} asynchronously at startup, so on a cold launch a screen that checks
     * {@link #isConfigured} straight away can see "no server" a second before the URL lands — and
     * then sit on that answer forever. Callers use this to ask again instead of dead-ending.
     *
     * <p>The callback runs on the main thread, and always runs: if Firebase is unavailable it
     * reports whatever {@link #baseUrl} resolves to right now rather than leaving the caller
     * waiting.
     */
    public static void refresh(Context context, ConfigCallback callback) {
        final Context app = context.getApplicationContext();
        try {
            FirebaseRemoteConfig.getInstance().fetchAndActivate()
                    .addOnCompleteListener(task -> callback.onReady(baseUrl(app)));
        } catch (Exception e) {
            Log.d(TAG, "Remote Config refresh unavailable: " + e.getMessage());
            callback.onReady(baseUrl(app));
        }
    }

    private static String fromRemoteConfig() {
        try {
            return FirebaseRemoteConfig.getInstance().getString(REMOTE_CONFIG_KEY);
        } catch (Exception e) {
            // Remote Config not ready (or Firebase unavailable) — not fatal, fall through.
            Log.d(TAG, "Remote Config unavailable: " + e.getMessage());
            return "";
        }
    }

    // ─── URL builders ───

    /** REST root the RestDataSource paths ("gimg/c/{cat}") are appended to. */
    /** Ad settings served by the ColorForge admin — the replacement for the Firebase node. */
    public static String adsUrl(Context context) {
        return baseUrl(context) + "index.php/App/ads";
    }

    public static String restRoot(Context context) {
        String base = baseUrl(context);
        return TextUtils.isEmpty(base) ? "" : base + REST_SUFFIX;
    }

    public static String categoriesUrl(Context context) {
        String base = baseUrl(context);
        return TextUtils.isEmpty(base) ? "" : base + REST_SUFFIX + "category/";
    }

    /**
     * The admin's own playable page for this picture — the photo-reveal game the review preview
     * shows. Loaded in a WebView so the app plays the exact same page (backend route in
     * {@code number_tab} / {@code app.py}: {@code /index.php/Number/play/{cat}/{file}}).
     *
     * <p>Superseded by {@link #revealBaseUrl}: New 2026 now plays the reveal game NATIVELY
     * ({@link com.colourmate.paintbynumber.activity.RevealActivity}) instead of in a WebView, so
     * it matches the app's theme and the colour-by-number mechanics. Kept for reference/fallback.
     */
    public static String playUrl(Context context, String category, String fileName) {
        String base = baseUrl(context);
        return TextUtils.isEmpty(base) ? ""
                : base + REST_SUFFIX + "play/" + category + "/" + fileName;
    }

    /**
     * Directory URL the native reveal game downloads its four files from — {@code regions.json},
     * {@code id_map.png}, {@code border.png}, {@code original.png}. The backend maps this
     * category+fileName to the picture's CDN bundle (route
     * {@code /index.php/Number/reveal/{cat}/{file}/{asset}}), so the app needs no bundle id.
     */
    /** All our published pictures, newest first, for the Daily tab (number_tab.daily_payload). */
    public static String dailyUrl(Context context) {
        String base = baseUrl(context);
        return TextUtils.isEmpty(base) ? "" : base + REST_SUFFIX + "daily/";
    }

    /** The featured Hit picture + unlock time (number_tab.hit_payload). */
    public static String hitUrl(Context context) {
        String base = baseUrl(context);
        return TextUtils.isEmpty(base) ? "" : base + "index.php/App/hit";
    }

    public static String revealBaseUrl(Context context, String category, String fileName) {
        String base = baseUrl(context);
        return TextUtils.isEmpty(base) ? ""
                : base + REST_SUFFIX + "reveal/" + category + "/" + fileName + "/";
    }

    // ─── Diamond tab ───
    //
    // Same request shape as the Number tab with its own root (backend: diamond_tab.py), so the
    // gallery reuses the identical REST list code:
    //   {base}index.php/Diamond/category/          {base}index.php/Diamond/gimg/c/{cat}
    //   {base}dthumb/{cat}/t/{file}.png            {base}index.php/Diamond/data/{cat}/{file}.json

    private static final String DIAMOND_SUFFIX = "index.php/Diamond/";

    /** REST root the diamond RestDataSource paths ("gimg/c/{cat}") are appended to. */
    public static String diamondRestRoot(Context context) {
        String base = baseUrl(context);
        return TextUtils.isEmpty(base) ? "" : base + DIAMOND_SUFFIX;
    }

    public static String diamondCategoriesUrl(Context context) {
        String base = baseUrl(context);
        return TextUtils.isEmpty(base) ? "" : base + DIAMOND_SUFFIX + "category/";
    }

    /** The bead grid DiamondLevel parses: {cols, rows, palette, grid}. */
    public static String diamondGridUrl(Context context, String category, String fileName) {
        String base = baseUrl(context);
        return TextUtils.isEmpty(base) ? ""
                : base + DIAMOND_SUFFIX + "data/" + category + "/" + fileName + ".json";
    }

    public static String diamondThumbUrl(Context context, String category, String fileName) {
        String base = baseUrl(context);
        return TextUtils.isEmpty(base) ? ""
                : base + "dthumb/" + category + "/t/" + fileName + ".png";
    }

    /** Grid thumbnail. Mirrors NumberFragment's thumb URL shape. */
    public static String thumbUrl(Context context, String category, String fileName) {
        String base = baseUrl(context);
        return TextUtils.isEmpty(base) ? "" : base + "thumb/" + category + "/t/" + fileName + ".png";
    }

    /** Black line art (the playable outline). */
    public static String lineArtUrl(Context context, String category, String fileName) {
        String base = baseUrl(context);
        return TextUtils.isEmpty(base) ? "" : base + "thumb/" + category + "/" + fileName + ".png";
    }

    /**
     * The clean ARTISTIC line-art the reveal game draws ({@code border.png}) — the outline the owner
     * wants in the grid, not the noisy colour-contour {@link #lineArtUrl}. Transparent background, so
     * show it on a white cell.
     */
    public static String borderUrl(Context context, String category, String fileName) {
        String base = baseUrl(context);
        return TextUtils.isEmpty(base) ? ""
                : base + REST_SUFFIX + "reveal/" + category + "/" + fileName + "/border.png";
    }

    /**
     * The 420px line-art thumbnail for grid tiles. The grid used to load the FULL
     * {@code border.png} per tile — measured 554 KB each, ~6 MB for one screen of tiles —
     * which is most of why the list "took time". The server falls back to the full border
     * for items published before the thumbnail existed, so this can never 404 a tile.
     */
    public static String borderThumbUrl(Context context, String category, String fileName) {
        String base = baseUrl(context);
        return TextUtils.isEmpty(base) ? ""
                : base + REST_SUFFIX + "reveal/" + category + "/" + fileName + "/border_thumb.png";
    }

    /** Solution image the game reads the region colours from. */
    public static String coloredUrl(Context context, String category, String fileName) {
        String base = baseUrl(context);
        return TextUtils.isEmpty(base) ? ""
                : base + "thumb/" + category + "/" + fileName + ".colored.png";
    }
}
