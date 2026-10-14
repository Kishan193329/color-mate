package com.colourmate.paintbynumber.ads;

import android.app.Activity;
import android.util.Log;

import com.google.android.ump.ConsentDebugSettings;
import com.google.android.ump.ConsentForm;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

/**
 * Google's consent form (UMP).
 *
 * <p><b>Why this exists:</b> AdMob will not serve a personalised ad — and in practice often no ad
 * at all — to a user in the EEA or the UK unless a consent message has been shown. The app had no
 * consent code anywhere, so every one of those users was earning nothing, and they are the highest
 * CPM audience the app has. It is also a policy requirement, not merely an optimisation.
 *
 * <p>Outside those regions {@code requestConsentInfoUpdate} simply reports that no form is
 * required and the callback runs immediately, so this costs users elsewhere nothing.
 *
 * <p>The callback is invoked <b>exactly once</b> and in every outcome — consent given, declined,
 * not required, or the request failed — because ad initialisation waits on it and must never be
 * left hanging.
 */
public final class AdConsent {

    private static final String TAG = "AdConsent";

    /** Set to a real device id to preview the EEA form from India while testing. */
    private static final String DEBUG_DEVICE_HASH = "";

    private static boolean gathering = false;

    public interface Done {
        void onFinished(boolean canRequestAds);
    }

    private AdConsent() {
    }

    public static void gather(final Activity activity, final Done done) {
        if (activity == null || activity.isFinishing()) {
            if (done != null) done.onFinished(true);
            return;
        }
        if (gathering) return;               // the splash can be recreated; only run one flow
        gathering = true;

        final ConsentInformation info = UserMessagingPlatform.getConsentInformation(activity);
        ConsentRequestParameters.Builder params = new ConsentRequestParameters.Builder()
                .setTagForUnderAgeOfConsent(false);
        if (!DEBUG_DEVICE_HASH.isEmpty()) {
            params.setConsentDebugSettings(new ConsentDebugSettings.Builder(activity)
                    .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                    .addTestDeviceHashedId(DEBUG_DEVICE_HASH).build());
        }

        info.requestConsentInfoUpdate(activity, params.build(),
                () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity,
                        (ConsentForm.OnConsentFormDismissedListener) error -> {
                            if (error != null) {
                                Log.w(TAG, "Consent form: " + error.getErrorCode() + " " + error.getMessage());
                            }
                            finish(info, done);
                        }),
                error -> {
                    // No network, or Google could not be reached. Carry on: outside the EEA this
                    // changes nothing, and blocking ads on a failed lookup would cost every user.
                    Log.w(TAG, "Consent lookup failed: " + error.getErrorCode() + " " + error.getMessage());
                    finish(info, done);
                });
    }

    private static void finish(ConsentInformation info, Done done) {
        gathering = false;
        boolean can = true;
        try {
            can = info.canRequestAds();
        } catch (Throwable t) {
            Log.w(TAG, "canRequestAds unavailable", t);
        }
        Log.i(TAG, "Consent finished — canRequestAds=" + can);
        if (done != null) done.onFinished(can);
    }

    /** True once the user has made a choice (or none was needed) — safe to request ads. */
    public static boolean canRequestAds(Activity activity) {
        try {
            return UserMessagingPlatform.getConsentInformation(activity).canRequestAds();
        } catch (Throwable t) {
            return true;
        }
    }
}
