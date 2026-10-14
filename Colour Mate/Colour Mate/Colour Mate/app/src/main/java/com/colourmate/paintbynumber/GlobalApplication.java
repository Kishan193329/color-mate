package com.colourmate.paintbynumber;

import android.app.Activity;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Build;

import com.colourmate.paintbynumber.Utils.Manager;
import com.colourmate.paintbynumber.util.LogUtils;
import com.colourmate.paintbynumber.util.RatingManager;
import com.colourmate.paintbynumber.util.SoundUtils;
import com.downloader.PRDownloader;
import com.downloader.PRDownloaderConfig;
import com.jakewharton.threetenabp.AndroidThreeTen;

import java.io.File;

public class GlobalApplication extends Application {
    public static final String TAG = "ColoringBookApplication";
    private static Context context;

    public static final String CHANNEL_ID_DEFAULT = "default_channel";
    public static final String CHANNEL_ID_HIGH = "high_priority_channel";
    public static final String CHANNEL_ID_LOW = "low_priority_channel";

    public static boolean isTablet() {
        return false;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Save application context
        context = getApplicationContext();

        // Starts the 3-day clock for the rating prompt on the true first run. One tiny write, and
        // only ever the first time; done here so a first launch that is killed early still has its
        // anchor date.
        RatingManager.recordFirstLaunchIfNeeded(context, System.currentTimeMillis());

        installPicasso();

        // Everything else moves off the main thread. Measured 2026-07-30: this method used to run
        // the timezone database load, a JSON parse, a Firebase handshake, a SoundPool decode and
        // three notification channels inline — 3.2s of frozen screen before the splash could paint
        // its first frame. None of it is needed to draw that frame.
        Thread startup = new Thread(this::initInBackground, "cm-startup");
        startup.setPriority(Thread.NORM_PRIORITY - 1);
        startup.start();
    }

    /**
     * Points Picasso at the app's own HTTP client so thumbnails share the API's connection pool
     * and get a real disk cache — see {@link Manager#imageClient()} for why that matters.
     *
     * <p>Must run here, on the main thread, before any screen exists: {@code setSingletonInstance}
     * throws once something has called {@code Picasso.get()}. It is cheap — building the client
     * opens no sockets and touches no disk; the cache directory is opened on first use. If it does
     * throw, image loading still works, just with Picasso's own defaults, so it is not worth
     * taking the app down over.
     */
    private void installPicasso() {
        try {
            com.squareup.picasso.Picasso.setSingletonInstance(
                    new com.squareup.picasso.Picasso.Builder(context)
                            .downloader(new com.squareup.picasso.OkHttp3Downloader(
                                    Manager.getInstance().imageClient()))
                            .build());
        } catch (Exception | Error e) {
            android.util.Log.w("GlobalApplication",
                    "Picasso already initialised; keeping its default downloader", e);
        }
    }

    /**
     * Deferred startup work, in the order things are actually first needed.
     *
     * <p>Each piece is safe to arrive late: downloads only begin after a tap, the image list is
     * read by the Pixel tab (and {@code ImageManager.getInstance()} initialises itself if it is
     * somehow asked first), Firebase config is read when an ad is requested, and notification
     * channels only matter when a notification is posted.
     *
     * <p>Sounds are no longer initialised here at all — {@code SoundUtils.playSound()} already
     * builds its SoundPool on first use, so eagerly decoding every effect at launch bought
     * nothing.
     */
    private void initInBackground() {
        try {
            PRDownloaderConfig config = PRDownloaderConfig.newBuilder()
                    .setDatabaseEnabled(false)
                    .setReadTimeout(60_000)
                    .setConnectTimeout(60_000)
                    .build();
            PRDownloader.initialize(context, config);

            AndroidThreeTen.init(this);

            com.colourmate.paintbynumber.Utils.ImageManager.init(this);

            Manager.getInstance().initialize();

            createNotificationChannels();
        } catch (Exception e) {
            LogUtils.e(TAG, "Deferred startup failed", e);
        }
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = getSystemService(NotificationManager.class);

            // Default Channel
            NotificationChannel defaultChannel = new NotificationChannel(
                    CHANNEL_ID_DEFAULT,
                    "Default Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT);
            defaultChannel.setDescription("Default notification channel");
            defaultChannel.enableVibration(true);
            defaultChannel.setVibrationPattern(new long[]{0, 250, 250, 250});

            // High Priority Channel
            NotificationChannel highChannel = new NotificationChannel(
                    CHANNEL_ID_HIGH,
                    "High Priority Notifications",
                    NotificationManager.IMPORTANCE_HIGH);
            highChannel.setDescription("Important notifications");
            highChannel.enableVibration(true);
            highChannel.setVibrationPattern(new long[]{0, 500, 500, 500});

            highChannel.setSound(null, null);

            // Low Priority Channel
            NotificationChannel lowChannel = new NotificationChannel(
                    CHANNEL_ID_LOW,
                    "Low Priority Notifications",
                    NotificationManager.IMPORTANCE_LOW);
            lowChannel.setDescription("Silent notifications");
            lowChannel.enableVibration(false);
            lowChannel.setSound(null, null);

            // Register channels
            notificationManager.createNotificationChannel(defaultChannel);
            notificationManager.createNotificationChannel(highChannel);
            notificationManager.createNotificationChannel(lowChannel);
        }
    }

    public static Context getAppContext() {
        return context;
    }

    /**
     * The app is portrait-only on every screen and every device size. The manifest declares this
     * for each activity; this is the runtime belt-and-braces for anything started without it.
     */
    public static void lockPortrait(Activity activity) {

        if (activity == null)
            return;

        try {
            if (activity.getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
        } catch (Exception e) {
            LogUtils.e(TAG, "Failed to lock portrait orientation", e);
        }
    }

    public static File getShareDirectory() {
        Context context = getAppContext();
        if (context == null) {
            LogUtils.w(TAG, "Application context is null");
            return null;
        }

        File shareDir = context.getExternalCacheDir();

        if (shareDir == null || !shareDir.exists()) {
            shareDir = context.getCacheDir();
        }

        if (shareDir != null) {
            LogUtils.d(TAG, "Share Directory Path: " + shareDir.getAbsolutePath());
        } else {
            LogUtils.w(TAG, "Could not get share directory");
        }

        return shareDir;
    }
}
