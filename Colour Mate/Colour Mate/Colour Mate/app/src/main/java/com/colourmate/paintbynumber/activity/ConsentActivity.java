package com.colourmate.paintbynumber.activity;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.colourmate.paintbynumber.R;

/**
 * First-launch age / mature-content gate. The app can carry horror and other 18+ artwork, so the
 * user must confirm they are 18 or older before entering. Shown once (persisted); "Exit" closes the
 * app. Sits in the flow as Splash → Consent → Premium → Main.
 */
public class ConsentActivity extends AppCompatActivity {

    private static final String PREFS = "app_prefs";
    private static final String KEY = "age_consent_v1";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_consent);
        com.colourmate.paintbynumber.util.EdgeToEdge.apply(this, null, null);

        findViewById(R.id.btn_consent_agree).setOnClickListener(v -> {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY, true).apply();
            Intent intent = new Intent(this, PremiumActivity.class);
            intent.putExtra(PremiumActivity.EXTRA_LAUNCHED_FROM_SPLASH, true);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });

        findViewById(R.id.btn_consent_exit).setOnClickListener(v -> finishAffinity());
    }

    @Override
    public void onBackPressed() {
        finishAffinity();   // consent is required — back = exit
    }
}
