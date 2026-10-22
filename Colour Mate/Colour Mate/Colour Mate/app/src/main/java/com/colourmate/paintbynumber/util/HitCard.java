package com.colourmate.paintbynumber.util;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.Utils.ColorForgeApi;
import com.colourmate.paintbynumber.Utils.Manager;
import com.colourmate.paintbynumber.activity.RevealActivity;
import com.colourmate.paintbynumber.ads.MediationManager;
import com.google.gson.JsonObject;

import java.util.Locale;

/**
 * The featured "Hit" picture at the top of By Number (admin → drawer → Make Hit image).
 *
 * <p>Fetches {@code /index.php/App/hit}. Nothing configured, or no server → the card stays hidden.
 * Before {@code unlockAt} the card shows a dimmed colour preview, a padlock and a live countdown;
 * after it, Play opens the reveal game. The countdown runs on the SERVER's clock ({@code now} in
 * the payload), so changing the phone's time does not unlock it early.
 */
public final class HitCard {

    private final View card;
    private final ImageView image;
    private final View lock;
    private final TextView title, countdown, play;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String fileName, category;
    private long unlockAt;           // server epoch seconds
    private long clockOffset;        // server - device, seconds
    private boolean running;

    private HitCard(View root) {
        card = root.findViewById(R.id.hit_card);
        image = root.findViewById(R.id.hit_image);
        lock = root.findViewById(R.id.hit_lock);
        title = root.findViewById(R.id.hit_title);
        countdown = root.findViewById(R.id.hit_countdown);
        play = root.findViewById(R.id.hit_play);
    }

    /** Call once from the fragment's onViewCreated. Safe if the layout has no hit card. */
    public static void attach(View root) {
        if (root == null || root.findViewById(R.id.hit_card) == null) return;
        HitCard hc = new HitCard(root);
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { hc.load(); }
            @Override public void onViewDetachedFromWindow(View v) { hc.stop(); }
        });
        if (root.isAttachedToWindow()) hc.load();
    }

    private void load() {
        if (!ColorForgeApi.hasExplicitServer(card.getContext())) return;
        String base = ColorForgeApi.baseUrl(card.getContext()) + "index.php/";
        Manager.getInstance().executeApiRequestWithCall(base, "App/hit", this::onLoaded, c -> { });
    }

    private void onLoaded(JsonObject json) {
        if (json == null || !json.has("fileName")) {
            card.setVisibility(View.GONE);
            return;
        }
        fileName = json.get("fileName").getAsString();
        category = json.has("category") ? json.get("category").getAsString() : "";
        unlockAt = json.has("unlockAt") ? json.get("unlockAt").getAsLong() : 0L;
        long now = json.has("now") ? json.get("now").getAsLong() : System.currentTimeMillis() / 1000L;
        clockOffset = now - System.currentTimeMillis() / 1000L;
        String t = json.has("title") ? json.get("title").getAsString() : "";
        title.setText(t.isEmpty() ? fileName : capitalise(t));

        // Colour preview as the teaser — the reveal game starts from line art, so this is a
        // glimpse of the finished picture.
        String thumb = ColorForgeApi.baseUrl(card.getContext()) + "thumb/" + category + "/t/" + fileName + ".png";
        com.squareup.picasso.Picasso.get().load(thumb).fit().centerCrop().into(image);
        card.setVisibility(View.VISIBLE);
        card.setOnClickListener(v -> onTap());
        play.setOnClickListener(v -> onTap());
        start();
    }

    private long secondsLeft() {
        return unlockAt - (System.currentTimeMillis() / 1000L + clockOffset);
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            long left = secondsLeft();
            boolean locked = left > 0;
            lock.setVisibility(locked ? View.VISIBLE : View.GONE);
            play.setText(locked ? R.string.hit_locked : R.string.hit_play);
            play.setAlpha(locked ? 0.55f : 1f);
            countdown.setText(locked
                    ? card.getContext().getString(R.string.hit_unlocks_in, format(left))
                    : card.getContext().getString(R.string.hit_unlocked));
            if (locked) handler.postDelayed(this, 1000L);
        }
    };

    private void start() { running = true; handler.removeCallbacks(tick); handler.post(tick); }

    private void stop() { running = false; handler.removeCallbacks(tick); }

    private void onTap() {
        long left = secondsLeft();
        if (left > 0) {
            Toast.makeText(card.getContext(),
                    card.getContext().getString(R.string.hit_unlocks_in, format(left)),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        String base = ColorForgeApi.revealBaseUrl(card.getContext(), category, fileName);
        if (base.isEmpty()) return;
        android.content.Context ctx = card.getContext();
        Runnable open = () -> ctx.startActivity(RevealActivity.fromUrl(ctx, base, fileName,
                new String[0], new String[0]));
        Activity a = ctx instanceof Activity ? (Activity) ctx : null;
        if (a instanceof AppCompatActivity) MediationManager.showInterstitial((AppCompatActivity) a, open::run);
        else open.run();
    }

    static String format(long s) {
        long d = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60, sec = s % 60;
        return d > 0 ? String.format(Locale.US, "%dd %02d:%02d:%02d", d, h, m, sec)
                : String.format(Locale.US, "%02d:%02d:%02d", h, m, sec);
    }

    private static String capitalise(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
