package com.colourmate.paintbynumber.activity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.colourmate.paintbynumber.R;

/**
 * First-run welcome (roadmap item 10): shown once, before the app itself, so a brand-new player
 * knows what the game is before being dropped into a grid of pictures.
 */
public class WelcomeActivity extends AppCompatActivity {

    private static final String PREFS = "welcome";
    private static final String KEY_SEEN = "seen";

    /** True until the player has been through this screen once. */
    /** The age screen already introduces the app; one intro screen per install is enough. */
    public static void markSeen(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SEEN, true).apply();
    }

    public static boolean shouldShow(Context context) {
        return !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_SEEN, false);
    }

    public static Intent newIntent(Context context) {
        return new Intent(context, WelcomeActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);
        com.colourmate.paintbynumber.util.EdgeToEdge.apply(this, null, null);

        // Marked as seen on arrival, not on the button: however the player leaves this screen,
        // it must not come back and block them a second time.
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_SEEN, true).apply();

        findViewById(R.id.btn_welcome_start).setOnClickListener(v -> finish());

        playIntro();
    }

    /** The same spring the app-open title uses, so the two intros feel like one product. */
    private void playIntro() {
        View[] items = {
                findViewById(R.id.tv_welcome_title),
                findViewById(R.id.tv_welcome_line1),
                findViewById(R.id.tv_welcome_line2),
                findViewById(R.id.btn_welcome_start),
        };

        for (int i = 0; i < items.length; i++) {
            View item = items[i];
            if (item == null) continue;

            item.setAlpha(0f);
            item.setTranslationY(34f);
            item.setScaleX(0.9f);
            item.setScaleY(0.9f);
            item.animate()
                    .alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                    .setStartDelay(120 + i * 110L)
                    .setDuration(520)
                    .setInterpolator(new OvershootInterpolator(1.6f))
                    .start();
        }
    }
}
