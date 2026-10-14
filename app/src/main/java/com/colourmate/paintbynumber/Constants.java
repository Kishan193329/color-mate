package com.colourmate.paintbynumber;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaPlayer;

public class Constants {

    private static final String PREF_BGMUSIC = "bg_music_prefs";
    private static final String KEY_BGMUSIC_PATH = "bg_music_path";

    public static void musicPlayer(Context context, int soundResId) {
        try {
            MediaPlayer mediaPlayer = MediaPlayer.create(context, soundResId);

            if (mediaPlayer == null) return;

            mediaPlayer.setLooping(false);
            mediaPlayer.setVolume(1.0f, 1.0f); // Max volume (range: 0.0f - 1.0f)
            mediaPlayer.start();

            mediaPlayer.setOnCompletionListener(player -> {
                player.stop();
                player.release();
            });

        } catch (IllegalStateException ignored) {}
    }

    public static void saveBackgroundMusic(Context context, String musicPath) {
        SharedPreferences.Editor editor =
                context.getSharedPreferences(PREF_BGMUSIC, Context.MODE_PRIVATE).edit();
        editor.putString(KEY_BGMUSIC_PATH, musicPath);
        editor.apply(); // Non-blocking, recommended
    }

    public static String getBackgroundMusic(Context context) {
        return context
                .getSharedPreferences(PREF_BGMUSIC, Context.MODE_PRIVATE)
                .getString(KEY_BGMUSIC_PATH, "");
    }

}
