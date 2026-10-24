package com.colourmate.paintbynumber.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
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
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Playing a picture with friends — client side of platform/backend/multiplayer.py.
 *
 * <p>Three modes, all reached only through a 6-letter code (never strangers):
 * <ul>
 *   <li><b>challenge</b> — finish a picture, share the code, friends try to beat the time;</li>
 *   <li><b>race</b> — a live room, everyone on their own copy, first to 100% wins;</li>
 *   <li><b>coop</b> — a live room, everyone fills one shared picture.</li>
 * </ul>
 * HTTP calls follow {@link Referral}: one background thread, answers on the main thread, null on
 * any failure.
 */
public final class Multiplayer {

    public static final String CHALLENGE = "challenge";
    public static final String RACE = "race";
    public static final String COOP = "coop";

    public interface Callback { void done(JSONObject result); }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String PREFS = "multiplayer";

    private Multiplayer() {}

    @SuppressLint("HardwareIds")
    public static String device(Context c) {
        String id = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ANDROID_ID);
        return id == null ? "" : id;
    }

    /** The name friends see; empty until the player has typed one. */
    public static String name(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("name", "");
    }

    public static void setName(Context c, String name) {
        String clean = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (clean.length() > 16) clean = clean.substring(0, 16);
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("name", clean).apply();
    }

    /** Keeps only the characters a code can contain, so "ab-c 2d3" still works. */
    public static String normaliseCode(String code) {
        StringBuilder out = new StringBuilder();
        if (code != null) {
            for (char ch : code.toUpperCase(java.util.Locale.ROOT).toCharArray()) {
                if ("ABCDEFGHJKLMNPQRSTUVWXYZ23456789".indexOf(ch) >= 0) out.append(ch);
                if (out.length() == 6) break;
            }
        }
        return out.toString();
    }

    // ─── HTTP ───

    public static void createChallenge(Context c, String fileName, String category, long ms,
                                       Callback cb) {
        call(c, "POST", "index.php/App/mp/challenge", body(c)
                .put2("fileName", fileName).put2("category", category).put2("ms", ms), cb);
    }

    public static void postResult(Context c, String code, long ms, Callback cb) {
        call(c, "POST", "index.php/App/mp/challenge/" + code + "/result",
                body(c).put2("ms", ms), cb);
    }

    public static void createRoom(Context c, String mode, String fileName, String category,
                                  Callback cb) {
        call(c, "POST", "index.php/App/mp/room", body(c)
                .put2("mode", mode).put2("fileName", fileName).put2("category", category), cb);
    }

    /** What a code is: {kind: challenge|race|coop, fileName, category, ...}, or null. */
    public static void lookup(Context c, String code, Callback cb) {
        call(c, "GET", "index.php/App/mp/" + normaliseCode(code), null, cb);
    }

    /** The invite: the code, and a link that opens the app (or the Play Store). */
    public static void share(Activity activity, String code, String mode) {
        String what = COOP.equals(mode) ? "colour a picture together with me"
                : RACE.equals(mode) ? "race me on a picture"
                : "beat my time on a picture";
        String link = ColorForgeApi.baseUrl(activity) + "j/" + code;
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "Come " + what + " in Colour Mate! 🎨\nCode: " + code
                        + "\n" + link);
        activity.startActivity(Intent.createChooser(send, "Invite a friend"));
    }

    /** The code inside a colourmate://j/CODE (or https://host/j/CODE) link, or null. */
    public static String codeFromLink(Uri uri) {
        if (uri == null) return null;
        String code = uri.getLastPathSegment();
        if ("j".equals(code)) return null;
        String clean = normaliseCode(code);
        return clean.length() == 6 ? clean : null;
    }

    public static String formatTime(long ms) {
        long s = Math.max(0, ms / 1000);
        return s >= 3600 ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format(java.util.Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    private static final class Body extends JSONObject {
        Body put2(String k, Object v) {
            try { put(k, v); } catch (Exception ignored) { }
            return this;
        }
    }

    private static Body body(Context c) {
        return new Body().put2("device", device(c)).put2("name", name(c));
    }

    private static void call(Context ctx, String method, String path, JSONObject body, Callback cb) {
        final Context c = ctx.getApplicationContext();
        if (!ColorForgeApi.hasExplicitServer(c)) { cb.done(null); return; }
        final String url = ColorForgeApi.baseUrl(c) + path;
        IO.execute(() -> {
            JSONObject result = null;
            try {
                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setConnectTimeout(15000);
                con.setReadTimeout(15000);
                con.setRequestMethod(method);
                if (body != null) {
                    con.setDoOutput(true);
                    con.setRequestProperty("Content-Type", "application/json");
                    try (OutputStream out = con.getOutputStream()) {
                        out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                    }
                }
                if (con.getResponseCode() == 200) {
                    try (InputStream in = con.getInputStream()) {
                        result = new JSONObject(new String(readAll(in), StandardCharsets.UTF_8));
                    }
                }
                con.disconnect();
            } catch (Exception ignored) {
                // offline or server down: the caller shows "try again"
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

    // ─── Live room ───

    /** Callbacks arrive on the main thread. */
    public interface RoomListener {
        /** Every message from the server: state, players, fill, progress, start, done. */
        void onMessage(JSONObject message);

        /** The connection dropped; {@code permanent} = the room is gone or full, stop trying. */
        void onDisconnected(boolean permanent, int code);
    }

    private static final OkHttpClient WS_CLIENT = new OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build();

    /**
     * One player's connection to a room. Reconnects by itself after a drop (phones switch
     * networks, screens lock) — the server keeps the player's seat and sends the full state again,
     * so nothing is lost. A close code of 4404 (no room) or 4409 (full) ends it for good.
     */
    public static final class Room {
        private static final int MAX_RETRIES = 6;

        private final Context context;
        private final String url;
        private final RoomListener listener;
        private WebSocket socket;
        private boolean closed;
        private int retries;

        public Room(Context ctx, String code, RoomListener listener) {
            this.context = ctx.getApplicationContext();
            this.listener = listener;
            String base = ColorForgeApi.baseUrl(context).replaceFirst("^http", "ws");
            this.url = base + "index.php/App/mp/" + normaliseCode(code) + "/ws?device="
                    + Uri.encode(device(context)) + "&name=" + Uri.encode(name(context));
            connect();
        }

        private void connect() {
            if (closed) return;
            socket = WS_CLIENT.newWebSocket(new Request.Builder().url(url).build(),
                    new WebSocketListener() {
                        @Override
                        public void onOpen(WebSocket ws, Response response) {
                            retries = 0;
                        }

                        @Override
                        public void onMessage(WebSocket ws, String text) {
                            try {
                                JSONObject msg = new JSONObject(text);
                                MAIN.post(() -> { if (!closed) listener.onMessage(msg); });
                            } catch (Exception ignored) { }
                        }

                        @Override
                        public void onClosing(WebSocket ws, int code, String reason) {
                            ws.close(1000, null);
                            dropped(ws, code);
                        }

                        @Override
                        public void onFailure(WebSocket ws, Throwable t, Response response) {
                            dropped(ws, 0);
                        }
                    });
        }

        private void dropped(WebSocket ws, int code) {
            if (ws != socket || closed) return;
            boolean permanent = code == 4404 || code == 4409 || code == 4400 || code == 4000
                    || retries >= MAX_RETRIES;
            MAIN.post(() -> {
                if (closed) return;
                listener.onDisconnected(permanent, code);
                if (!permanent) {
                    long delay = Math.min(15_000L, 1000L << retries++);
                    MAIN.postDelayed(this::connect, delay);
                }
            });
        }

        public void send(JSONObject message) {
            if (socket != null && !closed) socket.send(message.toString());
        }

        public void send(String type, String key, Object value) {
            JSONObject m = new JSONObject();
            try {
                m.put("t", type);
                if (key != null) m.put(key, value);
            } catch (Exception ignored) { }
            send(m);
        }

        public void close() {
            closed = true;
            if (socket != null) socket.close(1000, "bye");
        }
    }
}
