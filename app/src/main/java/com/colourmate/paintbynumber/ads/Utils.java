package com.colourmate.paintbynumber.ads;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;
import com.colourmate.paintbynumber.R;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Utility class for common app operations like sharing, rating, and showing dialogs.
 */
public class Utils {

    // Constants
    private static final String PLAY_STORE_BASE_URL = "https://play.google.com/store/apps/details?id=";
    private static final String MARKET_BASE_URL = "market://details?id=";
    private static final String MARKET_SEARCH_URL = "market://search?q=pub:";
    private static final int MIN_VERSION_CODE = 2;

    // ==================== App Version Management ====================

    /**
     * Check if app version is outdated and show update dialog if needed
     *
     * @param activity Current activity
     * @return true if version is old and dialog is shown, false otherwise
     */
    public static boolean isOldAppVersion(final AppCompatActivity activity) {
        int savedVersionCode = AppPreference.getInt(activity, Keys.KEY_VERSION_CODE, MIN_VERSION_CODE);

        if (savedVersionCode <= MIN_VERSION_CODE) {
            return false;
        }

        showUpdateDialog(activity);
        return true;
    }

    private static void showUpdateDialog(final AppCompatActivity activity) {
        new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog)
                .setTitle("Update Your App")
                .setMessage("New App Version is Available on Play Store, Please Update to access this app.")
                .setCancelable(false)
                .setPositiveButton("Update", (dialog, which) -> openPlayStore(activity))
                .setNegativeButton("Exit", (dialog, which) -> activity.finishAffinity())
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    // ==================== Warning Messages ====================

    // showWarningMessage() — the remote-config "(Ad)" cross-promo dialog — was REMOVED on
    // 2026-10-14 (Play Disruptive Ads strike). It was non-dismissible, its only button opened
    // another app's Play Store page and then finishAffinity()'d this one: a forced ad click
    // gating use of the app. Do not bring it back in any form.

    // ==================== Exit Dialog ====================

    /**
     * Exit is a plain exit. The old sheet carried a native ad — an ad shown on the way out of
     * the app, which Play's Disruptive Ads policy names explicitly. The double-back in
     * MainActivity is the confirmation; nothing is shown here.
     */
    public static void showExitDialog(final AppCompatActivity activity) {
        activity.finishAffinity();
    }

    // ==================== App Actions ====================

    /**
     * Open app rating page on Play Store
     *
     * @param context Application context
     */
    public static void rateApp(Context context) {
        try {
            // Try to open in Play Store app
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(MARKET_BASE_URL + context.getPackageName()));
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            // Fallback to web browser
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE_BASE_URL + context.getPackageName()));
            context.startActivity(intent);
        }
    }

    /**
     * Share app link via other apps
     *
     * @param context Application context
     */
    public static void shareApp(Context context) {
        try {
            String appName = context.getString(R.string.app_name);
            String shareText = appName + "\n" + PLAY_STORE_BASE_URL + context.getPackageName();

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_SUBJECT, appName);
            intent.putExtra(Intent.EXTRA_TEXT, shareText);

            context.startActivity(Intent.createChooser(intent, "Share App using"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Show more apps by the same developer
     *
     * @param context Application context
     */
    public static void moreApps(Context context) {
        String developerName = AppPreference.getString(context, Keys.KEY_DEV_NAME, "");

        if (developerName.isEmpty()) {
            Toast.makeText(context, "No more apps to show.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(MARKET_SEARCH_URL + developerName));
            context.startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "Unable to open Play Store", Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== Policy & Terms ====================

    /**
     * Open privacy policy URL in browser
     *
     * @param context Application context
     */
    public static void openPolicy(Context context) {
        String policyUrl = AppPreference.getString(context, Keys.KEY_POLICY, "");
        openUrlInBrowser(context, policyUrl, "No policy URL found.");
    }

    /**
     * Open terms and conditions URL in browser
     *
     * @param context Application context
     */
    public static void openTerms(Context context) {
        // Currently uses same URL as policy
        openPolicy(context);
    }

    private static void openUrlInBrowser(Context context, String url, String errorMessage) {
        try {
            if (url.isEmpty()) {
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setData(Uri.parse(url));
            context.startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "Unable to open URL", Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== Feedback ====================

    /**
     * Open email client to send feedback
     *
     * @param context Application context
     */
    public static void writeSuggestions(Context context) {
        writeSuggestions(context, "");
    }

    /**
     * Open email client to send feedback with pre-filled text
     *
     * @param context      Application context
     * @param feedbackText Pre-filled feedback text
     */
    public static void writeSuggestions(Context context, String feedbackText) {
        String developerEmail = AppPreference.getString(context, Keys.KEY_DEV_EMAIL, "");

        if (developerEmail.isEmpty()) {
            Toast.makeText(context, "No email found.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Intent intent = new Intent(Intent.ACTION_SENDTO,
                    Uri.fromParts("mailto", developerEmail, null));
            intent.putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.app_name));
            intent.putExtra(Intent.EXTRA_TEXT, feedbackText);

            context.startActivity(Intent.createChooser(intent, "Send Feedback"));
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "No email client found", Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== Helper Methods ====================

    private static void openPlayStore(AppCompatActivity activity) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse(PLAY_STORE_BASE_URL + activity.getPackageName()));
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            showPlayStoreNotInstalledToast(activity);
        }
        activity.finishAffinity();
    }

    private static void showPlayStoreNotInstalledToast(Context context) {
        Toast.makeText(context, "You don't have Google Play installed",
                Toast.LENGTH_LONG).show();
    }
}