package com.colourmate.paintbynumber.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import com.colourmate.paintbynumber.Utils.ColorForgeApi;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Refer-a-friend, client side (server: platform/backend/referral.py).
 *
 * <p>The device is identified by its Android ID, which survives a reinstall — so the server can
 * tell a genuinely new phone from one that was simply reinstalled. Every call is a small JSON POST
 * on one background thread; answers come back on the main thread.
 */
public final class Referral {

    public interface Callback { void done(JSONObject result); }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Referral() {}

    @SuppressLint("HardwareIds")
    private static String device(Context c) {
        String id = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ANDROID_ID);
        return id == null ? "" : id;
    }

    /** This phone's own code (cached after the first answer). */
    public static void myCode(Context c, Callback cb) {
        String cached = c.getSharedPreferences("referral", Context.MODE_PRIVATE).getString("code", null);
        if (cached != null) {
            try { cb.done(new JSONObject().put("code", cached)); } catch (Exception ignored) { }
            return;
        }
        post(c, "register", null, r -> {
            if (r != null && r.has("code")) {
                c.getSharedPreferences("referral", Context.MODE_PRIVATE).edit()
                        .putString("code", r.optString("code")).apply();
            }
            cb.done(r);
        });
    }

    public static void redeem(Context c, String code, Callback cb) {
        post(c, "redeem", new String[]{"code", code}, cb);
    }

    public static void reportEarned(Context c, int amount) {
        post(c, "earned", new String[]{"amount", String.valueOf(amount)}, r -> { });
    }

    /** Coins this phone's friends have earned it since the last claim. */
    public static void claim(Context c, Callback cb) {
        post(c, "claim", null, cb);
    }

    private static void post(Context ctx, String action, String[] extra, Callback cb) {
        final Context c = ctx.getApplicationContext();
        if (!ColorForgeApi.hasExplicitServer(c)) { cb.done(null); return; }
        final String url = ColorForgeApi.baseUrl(c) + "index.php/App/ref/" + action;
        IO.execute(() -> {
            JSONObject result = null;
            try {
                JSONObject body = new JSONObject().put("device", device(c));
                if (extra != null) body.put(extra[0], extra[1]);
                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setConnectTimeout(15000);
                con.setReadTimeout(15000);
                con.setRequestMethod("POST");
                con.setDoOutput(true);
                con.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = con.getOutputStream()) {
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                if (con.getResponseCode() == 200) {
                    try (InputStream in = con.getInputStream()) {
                        result = new JSONObject(new String(readAll(in), StandardCharsets.UTF_8));
                    }
                }
                con.disconnect();
            } catch (Exception ignored) {
                // offline or server down: the caller treats null as "try later"
            }
            final JSONObject r = result;
            MAIN.post(() -> cb.done(r));
        });
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        for (int n; (n = in.read(chunk)) > 0; ) buf.write(chunk, 0, n);
        return buf.toByteArray();
    }
}
