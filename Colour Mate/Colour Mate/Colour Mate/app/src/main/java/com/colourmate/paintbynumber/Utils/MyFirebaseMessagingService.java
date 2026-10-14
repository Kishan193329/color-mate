package com.colourmate.paintbynumber.Utils;


import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.colourmate.paintbynumber.GlobalApplication;
import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.activity.MainActivity;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCMService";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "New FCM Token: " + token);

        // Send token to your server
        sendTokenToServer(token);

        // Save token locally
        saveTokenLocally(token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "Message received from: " + remoteMessage.getFrom());

        // Check if message contains data payload
        if (remoteMessage.getData().size() > 0) {
            Log.d(TAG, "Data Payload: " + remoteMessage.getData());
            handleDataMessage(remoteMessage.getData());
        }

        // Check if message contains notification payload
        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Notification Body: " + remoteMessage.getNotification().getBody());
            handleNotificationMessage(remoteMessage);
        }
    }

    private void handleDataMessage(Map<String, String> data) {
        String title = data.get("title");
        String body = data.get("body");
        String imageUrl = data.get("image");
        String clickAction = data.get("click_action");
        String type = data.get("type");

        if (title != null && body != null) {
            if (imageUrl != null && !imageUrl.isEmpty()) {
                sendBigPictureNotification(title, body, imageUrl, clickAction, data);
            } else {
                sendNotification(title, body, clickAction, data);
            }
        }
    }

    private void handleNotificationMessage(RemoteMessage remoteMessage) {
        RemoteMessage.Notification notification = remoteMessage.getNotification();

        String title = notification.getTitle();
        String body = notification.getBody();
        String imageUrl = null;

        if (notification.getImageUrl() != null) {
            imageUrl = notification.getImageUrl().toString();
        }

        if (imageUrl != null && !imageUrl.isEmpty()) {
            sendBigPictureNotification(title, body, imageUrl, null, remoteMessage.getData());
        } else {
            sendNotification(title, body, null, remoteMessage.getData());
        }
    }

    private void sendNotification(String title, String body, String clickAction,
                                  Map<String, String> data) {

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // Pass data to activity
        if (data != null) {
            for (Map.Entry<String, String> entry : data.entrySet()) {
                intent.putExtra(entry.getKey(), entry.getValue());
            }
        }

        int flags = PendingIntent.FLAG_ONE_SHOT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, flags);

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, GlobalApplication.CHANNEL_ID_DEFAULT)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setAutoCancel(true)
                        .setSound(defaultSoundUri)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(pendingIntent)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(body));

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        int notificationId = (int) System.currentTimeMillis();
        notificationManager.notify(notificationId, notificationBuilder.build());
    }

    private void sendBigPictureNotification(String title, String body, String imageUrl,
                                            String clickAction, Map<String, String> data) {

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        if (data != null) {
            for (Map.Entry<String, String> entry : data.entrySet()) {
                intent.putExtra(entry.getKey(), entry.getValue());
            }
        }

        int flags = PendingIntent.FLAG_ONE_SHOT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, flags);

        Bitmap bitmap = getBitmapFromUrl(imageUrl);
        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, GlobalApplication.CHANNEL_ID_HIGH)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setAutoCancel(true)
                        .setSound(defaultSoundUri)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(pendingIntent);

        if (bitmap != null) {
            notificationBuilder.setLargeIcon(bitmap)
                    .setStyle(new NotificationCompat.BigPictureStyle()
                            .bigPicture(bitmap)
                            .bigLargeIcon((Bitmap) null));
        } else {
            notificationBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(body));
        }

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        int notificationId = (int) System.currentTimeMillis();
        notificationManager.notify(notificationId, notificationBuilder.build());
    }

    private Bitmap getBitmapFromUrl(String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            return BitmapFactory.decodeStream(input);
        } catch (IOException e) {
            Log.e(TAG, "Error getting bitmap from URL: " + e.getMessage());
            return null;
        }
    }

    private void sendTokenToServer(String token) {
        // TODO: Implement your server API call here
        Log.d(TAG, "Sending token to server: " + token);
    }

    private void saveTokenLocally(String token) {
        getSharedPreferences("FCM_PREFS", MODE_PRIVATE)
                .edit()
                .putString("fcm_token", token)
                .apply();
    }
}
