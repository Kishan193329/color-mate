package com.colourmate.paintbynumber.activity;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;

import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.Utils.CompatUtils;
import com.colourmate.paintbynumber.ads.MediationManager;
import com.colourmate.paintbynumber.ads.RewardedAdListener;
import com.colourmate.paintbynumber.library.Library;
import com.colourmate.paintbynumber.util.PurchaseManager;
import com.colourmate.paintbynumber.views.DrawingView;
import com.sumoing.recolor.editor.Editor;

/**
 * This activity hosts the main coloring canvas ({@link DrawingView}) and handles user interactions
 * during a coloring session, including saving progress and navigating to the export screen.
 */
public class DrawingActivity extends BaseActivity implements Editor.GLUIListener {

    /**
     * Name of the {@link com.sumoing.recolor.library.LibraryItem} the caller intends to open, so
     * this screen can verify it matches {@link Library#mCurrentDrawing} instead of trusting a
     * global that anything can overwrite. Optional — the Saved-works flow does not send it.
     */
    public static final String EXTRA_EXPECTED_ITEM = "expected_item_name";


    public static final String TAG = "EditorBookSheetActivity";

    private DrawingView drawingView;

    // -------------------------------------------------------------------------
    //                                  LIFECYCLE
    // -------------------------------------------------------------------------

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Library.mCurrentDrawing == null) {
            finish();
            return;
        }

        // The picture to draw arrives through the global Library.mCurrentDrawing, not through the
        // Intent — so if anything overwrites that global between the tap and this screen opening,
        // the wrong artwork loads silently. The caller sends the name it expects; a mismatch means
        // the selection was replaced under us, and opening the wrong picture is worse than not
        // opening one.
        String expected = getIntent().getStringExtra(EXTRA_EXPECTED_ITEM);
        if (expected != null
                && Library.mCurrentDrawing.mItem != null
                && !expected.equals(Library.mCurrentDrawing.mItem.getName())) {
            Log.e(TAG, "Refusing to open: asked for " + expected
                    + " but the current drawing is " + Library.mCurrentDrawing.mItem.getName());
            finish();
            return;
        }

        if (!Editor.isNativeLoaded()) {
            Log.e(TAG, "Native editor libraries not available: " + Editor.getLoadError());
            Toast.makeText(this, "Editor not available on this device", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_editor);
        CompatUtils.setupStatusBar(this, true, Color.WHITE);  // Light status bar with white background

        MediationManager.getBannerAd(this);
        Editor.setListener(this);

        drawingView = findViewById(R.id.sv_draw);
        drawingView.requestNewDrawing();


//        AdShow.getInstance(this)
//                .ShowNativeAd(findViewById(R.id.nativeAds), AdUtils.NativeType.BOTTOM_BANNER);


        getOnBackPressedDispatcher().addCallback(
                this,
                new OnBackPressedCallback(true) {

                    @Override
                    public void handleOnBackPressed() {

                        // 🔴 Your custom cleanup logic
                        endColoringSession();

                        // 🔴 Allow normal back navigation
                        setEnabled(false);
                        getOnBackPressedDispatcher().onBackPressed();
                    }
                }
        );

    }

    @Override
    public void onPause() {
        super.onPause();
        saveDrawingProgress();
    }

    public void onClickHome(View view) {

        MediationManager.showBackInterstitial(DrawingActivity.this, () -> {
            endColoringSession();
            finish();
        });

    }

    // -------------------------------------------------------------------------
    //                              HANDLE EXIT / SAVE
    // -------------------------------------------------------------------------

    /**
     * Ends the current coloring session, saving the drawing if specified.
     */
    private void endColoringSession() {
        if (drawingView != null) {
            drawingView.save(true);
        }

    }

    /**
     * Safely saves the drawing progress. Catches and logs any exceptions.
     */
    private void saveDrawingProgress() {
        try {
            if (drawingView != null) {
                drawingView.saveProgress();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving drawing state", e);
        }
    }

    // -------------------------------------------------------------------------
    //                         DONE / EXPORT ACTION
    // -------------------------------------------------------------------------

    /**
     * Called when the user finishes coloring. It saves the work and transitions to the export screen.
     */
    private void onClickDone() {
        if (drawingView == null) return;

        drawingView.save(false);

        MediationManager.showInterstitial(this, () -> {
            finish();
//            Intent intent = new Intent(this, EffectSelectionActivity.class);
//            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//            startActivity(intent);
            overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left);
        });

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            Log.d(TAG, "Pressed toolbar home");
            endColoringSession();
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // -------------------------------------------------------------------------
    //                    SUBSCRIPTION DIALOG (IF NEEDED)
    // -------------------------------------------------------------------------

    private void showPurchaseDialog() {
        if (com.colourmate.paintbynumber.ads.AppPreference.getBoolean(this, com.colourmate.paintbynumber.ads.Keys.KEY_IS_PREMIUM, false)) {
            // Already premium, should theoretically not happen if native code checks properly
            drawingView.queueEvent(() -> Editor.gluiSetHasSubscription(true));
            drawingView.requestRender();
            return;
        }

        MediationManager.showRewardedAd(this, new RewardedAdListener() {
            @Override
            public void onRewardGranted() {
                if (drawingView != null) {
                    drawingView.queueEvent(() -> {
                        Editor.gluiSetHasSubscription(true);
                    });
                    drawingView.requestRender();
                    Toast.makeText(DrawingActivity.this, "Premium content unlocked for this session! 🎨", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onRewardNull() {
                Toast.makeText(DrawingActivity.this, "Ad failed to load. Please try again.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onRewardProgress() {}

            @Override
            public void onVideoSkipped() {}
        });
    }

    // -------------------------------------------------------------------------
    //                        ACTIVITY RESULT HANDLER
    // -------------------------------------------------------------------------

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode != 1001) return;

        if (drawingView == null) return;

        drawingView.queueEvent(() -> Editor.gluiSetHasSubscription(PurchaseManager.getInstance().hasSubscription()));

        drawingView.requestRender();
    }

    // -------------------------------------------------------------------------
    //                        GLUIListener IMPLEMENTATION
    // -------------------------------------------------------------------------

    @Override
    public void drawingReady() {
        runOnUiThread(() -> {
            View progress = findViewById(R.id.progressView);
            if (progress != null) {
                animateHideProgress(progress);
            }
        });
    }

    /**
     * Animates the hiding of the progress view with a fade-out effect.
     *
     * @param progressView The view to animate and remove.
     */
    private void animateHideProgress(View progressView) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(progressView, "alpha", 1f, 0f);
        animator.setDuration(500);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (progressView.getParent() instanceof ViewGroup) {
                    ((ViewGroup) progressView.getParent()).removeView(progressView);
                }
            }
        });
        animator.start();
    }

    @Override
    public void donePressedCallback() {
        runOnUiThread(this::onClickDone);
    }

    @Override
    public void openSubsribtionDialogCallback() {
        runOnUiThread(this::showPurchaseDialog);
    }

    @Override
    public void wakeupCallback() {
        if (drawingView != null) {
            drawingView.requestRender();
        }
    }
}
