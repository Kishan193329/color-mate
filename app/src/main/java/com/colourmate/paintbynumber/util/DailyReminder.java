package com.colourmate.paintbynumber.util;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.colourmate.paintbynumber.GlobalApplication;
import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.activity.MainActivity;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/**
 * One local reminder a day (2026-10-14). FCM is wired but nothing ever sends; this is the
 * retention nudge coloring apps live on, done on-device so it needs no server.
 *
 * <p>Rules: fires around {@link #HOUR}:00 local time; stays silent if the app was opened in the
 * last {@link #QUIET_MS} (a nag to someone who just played is worse than nothing); respects the
 * Android 13+ notification permission (no permission → no notification, never a crash). One
 * notification id, so a missed day never stacks up.
 */
public final class DailyReminder {

    private static final String WORK = "daily_reminder";
    private static final String PREFS = "reminder";
    private static final String K_LAST_OPEN = "last_open";
    private static final int NOTIF_ID = 4242;

    /** Evening — after work, before bed: when people colour. */
    public static final int HOUR = 19;
    private static final long QUIET_MS = 6L * 3600_000L;

    private DailyReminder() {}

    /** Call from the home screen's onResume — it is what "opened the app" means here. */
    public static void recordAppOpen(Context c) {
        prefs(c).edit().putLong(K_LAST_OPEN, System.currentTimeMillis()).apply();
    }

    /** Idempotent — KEEP means a second call does not reset the schedule. */
    public static void schedule(Context c) {
        try {
            PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(ReminderWorker.class, 24, TimeUnit.HOURS)
                    .setInitialDelay(millisUntilNext(HOUR), TimeUnit.MILLISECONDS)
                    .build();
            WorkManager.getInstance(c.getApplicationContext())
                    .enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request);
        } catch (Exception e) {
            LogUtils.w("DailyReminder", "Could not schedule: " + e.getMessage());
        }
    }

    static long millisUntilNext(int hour) {
        Calendar now = Calendar.getInstance();
        Calendar next = (Calendar) now.clone();
        next.set(Calendar.HOUR_OF_DAY, hour);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (!next.after(now)) next.add(Calendar.DAY_OF_YEAR, 1);
        return next.getTimeInMillis() - now.getTimeInMillis();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static class ReminderWorker extends Worker {
        public ReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
            super(context, params);
        }

        @NonNull
        @Override
        public Result doWork() {
            Context c = getApplicationContext();
            long lastOpen = prefs(c).getLong(K_LAST_OPEN, 0L);
            if (System.currentTimeMillis() - lastOpen < QUIET_MS) return Result.success();
            if (Build.VERSION.SDK_INT >= 33
                    && ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                return Result.success();
            }
            show(c);
            return Result.success();
        }
    }

    private static void show(Context c) {
        // The channel is normally created by GlobalApplication on a background thread; a worker
        // can start the process and reach here first, and creating it twice is a no-op.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (nm != null && nm.getNotificationChannel(GlobalApplication.CHANNEL_ID_DEFAULT) == null) {
                nm.createNotificationChannel(new NotificationChannel(GlobalApplication.CHANNEL_ID_DEFAULT,
                        "Default Notifications", NotificationManager.IMPORTANCE_DEFAULT));
            }
        }

        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, NOTIF_ID, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String[] bodies = {
                "Today's daily picture is ready — a few minutes of colour before the day ends.",
                "Your unfinished picture is waiting. Pick up where you left off.",
                "A fresh picture and your daily coins are in. Come and collect them.",
        };
        String body = bodies[Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % bodies.length];

        NotificationCompat.Builder b = new NotificationCompat.Builder(c, GlobalApplication.CHANNEL_ID_DEFAULT)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Your picture is waiting 🎨")
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pi);
        try {
            NotificationManagerCompat.from(c).notify(NOTIF_ID, b.build());
        } catch (SecurityException ignored) {
            // Permission revoked between the check and the post — nothing to do.
        }
    }
}
