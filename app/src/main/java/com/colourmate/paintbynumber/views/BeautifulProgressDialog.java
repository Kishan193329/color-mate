package com.colourmate.paintbynumber.views;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import com.airbnb.lottie.LottieAnimationView;
import com.bumptech.glide.Glide;
import com.colourmate.paintbynumber.R;

import java.util.Objects;

public class BeautifulProgressDialog {

    public static final String withImage = "withImage";
    public static final String withGIF = "withGIF";
    public static final String withLottie = "withLottie";

    private static String viewType;
    private static boolean lottieCompactPadding = false;

    private final Activity mContext;
    private final Dialog alertDialog;
    private final View dialogView;

    private final CardView parent;
    private int topPadding, bottomPadding, leftPadding, rightPadding, overallPadding;

    private final LinearLayout viewInteriorLayout;
    private final ImageView viewImageView;
    private final TextView viewMessage;
    private final LottieAnimationView viewAnimationView;

    @SuppressLint("ResourceAsColor")
    public BeautifulProgressDialog(Activity activity, String type, String message) {
        this.mContext = activity;

        alertDialog  = new Dialog(activity, R.style.RoundedCornersDialog_Mid);
        alertDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialogView = LayoutInflater.from(activity).inflate(R.layout.layout_progress_dialog, null);
        alertDialog.setContentView(dialogView);
        Objects.requireNonNull(alertDialog.getWindow()).setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        // Default behavior from your original code
        alertDialog.setCanceledOnTouchOutside(false);
        alertDialog.setCancelable(false);

        setUpPadding();

        parent = dialogView.findViewById(R.id.parent);

        // ✅ fix: was using android.R.color.white (resource id). Use real color.
        parent.setCardBackgroundColor(Color.WHITE);
        parent.setCardElevation(activity.getResources().getDimension(R.dimen.progress_dialog_elevation));
        parent.setRadius(activity.getResources().getDimension(R.dimen.progress_dialog_corner_radius));

        viewInteriorLayout = dialogView.findViewById(R.id.interior_layout);
        viewImageView = dialogView.findViewById(R.id.imageView);
        viewMessage = dialogView.findViewById(R.id.message);
        viewAnimationView = dialogView.findViewById(R.id.lottie);

        if (viewAnimationView != null) {
            viewAnimationView.setRepeatCount(ValueAnimator.INFINITE);
        }

        viewType = type;
        applyViewType(viewType);

        if (message != null) {
            setMessage(message);
        } else {
            removeMessage();
        }

        // Keep your cancel animation stop
        alertDialog.setOnCancelListener(dialog -> {
            if (viewAnimationView != null && viewAnimationView.isAnimating()) {
                viewAnimationView.cancelAnimation();
            }
        });
    }

    private void applyViewType(String type) {
        if (type == null) return;

        switch (type) {
            case withImage:
            case withGIF:
                if (viewImageView != null) viewImageView.setVisibility(View.VISIBLE);
                if (viewAnimationView != null) viewAnimationView.setVisibility(View.GONE);
                break;

            case withLottie:
                if (viewImageView != null) viewImageView.setVisibility(View.GONE);
                if (viewAnimationView != null) {
                    viewAnimationView.setVisibility(View.VISIBLE);
                    viewAnimationView.setRepeatCount(ValueAnimator.INFINITE);
                }
                break;
        }
    }

    private void setUpPadding() {
        int basePadding = (int) mContext.getResources().getDimension(R.dimen.progress_dialog_padding);
        topPadding = basePadding + dpToPx(5);
        bottomPadding = basePadding + dpToPx(5);
        leftPadding = basePadding + dpToPx(20);
        rightPadding = basePadding + dpToPx(20);
        overallPadding = basePadding + dpToPx(10);
    }

    // ---------------- Added APIs ----------------

    /** ✅ Same as AlertDialog/Dialog */
    public boolean isShowing() {
        return alertDialog != null && alertDialog.isShowing();
    }

    /** ✅ Same as AlertDialog/Dialog */
    public void cancel() {
        try {
            if (alertDialog != null) alertDialog.cancel();
        } catch (Exception ignored) {}
    }

    /** ✅ Same as AlertDialog/Dialog */
    public void setOnDismissListener(DialogInterface.OnDismissListener listener) {
        if (alertDialog != null) alertDialog.setOnDismissListener(listener);
    }

    /** ✅ Same as AlertDialog/Dialog */
    public void setOnCancelListener(DialogInterface.OnCancelListener listener) {
        if (alertDialog != null) alertDialog.setOnCancelListener(listener);
    }

    /**
     * ✅ setButton like ProgressDialog/AlertDialog
     * Example:
     * setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", (d,w)-> {...});
     */
    public void setButton(int whichButton, CharSequence text, DialogInterface.OnClickListener listener) {
//        if (alertDialog != null) {
//            alertDialog.setButton(whichButton, text, listener);
//        }
    }

    public void setCancelableOnTouchOutside(boolean value) {
        if (alertDialog != null) alertDialog.setCanceledOnTouchOutside(value);
    }

    /** ✅ FIXED: your old code always set false */
    public void setCancelable(boolean value) {
        if (alertDialog != null) alertDialog.setCancelable(value);
    }

    // ---------------- Existing APIs ----------------

    public void setFont(String font) {
        try {
            Typeface face = Typeface.createFromAsset(mContext.getAssets(), font);
            if (viewMessage != null) viewMessage.setTypeface(face);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setViewType(String type) {
        viewType = type;
        applyViewType(type);
    }

    public void setLayoutColor(int color) {
        if (parent != null) parent.setCardBackgroundColor(color);
    }

    public void showMessage(boolean value) {
        if (viewMessage == null || viewInteriorLayout == null) return;

        if (value) {
            viewMessage.setVisibility(View.VISIBLE);
            viewInteriorLayout.setPadding(leftPadding, topPadding, rightPadding, bottomPadding);
        } else {
            viewMessage.setVisibility(View.GONE);
            applyPaddingForNoMessage();
        }
    }

    public void setMessage(String text) {
        if (viewMessage == null || viewInteriorLayout == null) return;

        viewMessage.setText(text);
        viewMessage.setVisibility(View.VISIBLE);
        viewInteriorLayout.setPadding(leftPadding, topPadding, rightPadding, bottomPadding);
    }

    public void removeMessage() {
        if (viewMessage == null || viewInteriorLayout == null) return;

        viewMessage.setVisibility(View.GONE);
        applyPaddingForNoMessage();
    }

    private void applyPaddingForNoMessage() {
        if (viewInteriorLayout == null) return;

        if (!withLottie.equalsIgnoreCase(viewType)) {
            viewInteriorLayout.setPadding(overallPadding, overallPadding, overallPadding, overallPadding);
        } else {
            if (lottieCompactPadding) {
                viewInteriorLayout.setPadding(0, 0, 0, 0);
            } else {
                viewInteriorLayout.setPadding(overallPadding, overallPadding, overallPadding, overallPadding);
            }
        }
    }

    public void setImageLocation(Drawable drawable) {
        if (viewImageView != null) viewImageView.setImageDrawable(drawable);
    }

    public void setImageLocation(Bitmap bitmap) {
        if (viewImageView != null) viewImageView.setImageBitmap(bitmap);
    }

    public void setImageLocation(Uri location) {
        if (viewImageView != null) viewImageView.setImageURI(location);
    }

    public void setGifLocation(Uri gifLocation) {
        if (viewImageView == null) return;
        Glide.with(mContext).load(gifLocation).into(viewImageView);
    }

    public void setLottieLocation(String url) {
        if (viewAnimationView != null) viewAnimationView.setAnimation(url);
    }

    public void setLottieLoop(boolean value) {
        if (viewAnimationView != null) {
            viewAnimationView.setRepeatCount(value ? ValueAnimator.INFINITE : 0);
        }
    }

    public void setLottieCompactPadding(boolean value) {
        lottieCompactPadding = value;
        applyPaddingForNoMessage();
    }

    public void show() {
        if (viewType == null) {
            Log.e("View Type", "Null");
            return;
        }

        if (withLottie.equalsIgnoreCase(viewType) && viewAnimationView != null) {
            viewAnimationView.playAnimation();
        }

        if (alertDialog != null) alertDialog.show();
    }

    public void dismiss() {
        try {
            if (withLottie.equalsIgnoreCase(viewType) && viewAnimationView != null) {
                viewAnimationView.cancelAnimation();
            }
            if (alertDialog != null) alertDialog.dismiss();
        } catch (Exception ignored) {}
    }

    public static int dpToPx(int dp) {
        return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
    }
}

