package com.colourmate.paintbynumber.activity;

import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.colourmate.paintbynumber.GlobalApplication;
import com.colourmate.paintbynumber.R;

public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        GlobalApplication.lockPortrait(this);

        // ✅ Handle back safely for button + gesture
        getOnBackPressedDispatcher().addCallback(
                this,
                new OnBackPressedCallback(true) {

                    @Override
                    public void handleOnBackPressed() {
                        // Allow default back behavior
                        setEnabled(false);
                        getOnBackPressedDispatcher().onBackPressed();

                        // Apply exit animation
                        applyExitTransition();
                    }
                });

    }

    @Override
    public void finish() {
        super.finish();
        applyExitTransition();
    }

    /**
     * Applies default exit transition for all activities in the app.
     */
    protected void applyExitTransition() {
        overridePendingTransition(
                R.anim.slide_from_left,
                R.anim.slide_to_right);
    }
}
