package com.colourmate.paintbynumber.util;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Build;

import com.colourmate.paintbynumber.Constants;
import com.colourmate.paintbynumber.R;

import java.io.IOException;
import java.util.EnumMap;

/**
 * SoundUtils
 * ----------
 * Handles:
 * 1) Short SFX using SoundPool (tap, etc.)
 * 2) Background looping music (game bg music)
 * 3) Optional external song playback (user-selected music)
 */
public class SoundUtils {

    private static final int MAX_STREAMS = 5;
    private static final float SFX_VOLUME = 1.0f;
    private static final float MUSIC_VOLUME = 0.6f;

    private static SoundPool soundPool;
    private static final EnumMap<GameSound, Integer> soundIds = new EnumMap<>(GameSound.class);

    private static MediaPlayer backgroundPlayer;   // game bg music
    public static MediaPlayer songPlayer;          // custom song (kept public to match your project)

    /** All short game sounds */
    public enum GameSound {
        TAP(R.raw.game_tap);

        final int resId;
        GameSound(int resId) {
            this.resId = resId;
        }
    }

    // =================== SoundPool (SFX) ===================

    /** Must be called once (ex: in Application or first Activity) */
    public static void initSounds(Context context) {
        if (soundPool != null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            soundPool = new SoundPool.Builder()
                    .setMaxStreams(MAX_STREAMS)
                    .setAudioAttributes(attrs)
                    .build();
        } else {
            // Deprecated constructor for older devices
            soundPool = new SoundPool(MAX_STREAMS, AudioManager.STREAM_MUSIC, 0);
        }

        // Load all sounds
        for (GameSound sound : GameSound.values()) {
            int id = soundPool.load(context, sound.resId, 1);
            soundIds.put(sound, id);
        }
    }

    /** Plays a short sound effect if Sound is enabled */
    public static void playSound(Context context, GameSound sound) {
        if (!GameSaver.isSoundEnabled(context)) return;
        if (soundPool == null) initSounds(context);

        Integer soundId = soundIds.get(sound);
        if (soundId == null) return;

        soundPool.play(soundId, SFX_VOLUME, SFX_VOLUME, 1, 0, 1.0f);
    }

    /** Release SoundPool (optional, call in onDestroy of final activity if you want) */
    public static void releaseSounds() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
            soundIds.clear();
        }
    }

    // =================== Background Music ===================

    /** Start or resume background music */
    public static void playBackgroundMusic(Context context) {
        if (!GameSaver.isMusicEnabled(context)) return;

        // If already playing, do nothing
        if (backgroundPlayer != null) {
            try {
                if (backgroundPlayer.isPlaying()) return;
                backgroundPlayer.start();
                return;
            } catch (IllegalStateException ignored) {
                // Recreate if player is in a bad state
                safeRelease(backgroundPlayer);
                backgroundPlayer = null;
            }
        }

        String bgPath = Constants.getBackgroundMusic(context);

        try {
            if (bgPath == null || bgPath.trim().isEmpty()) {
                // Default bundled music
                backgroundPlayer = MediaPlayer.create(context, R.raw.game_backgroung);
            } else {
                // Music from Uri string
                backgroundPlayer = new MediaPlayer();
                backgroundPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                backgroundPlayer.setDataSource(context, Uri.parse(bgPath));
                backgroundPlayer.prepare();
            }

            if (backgroundPlayer != null) {
                backgroundPlayer.setLooping(true);
                backgroundPlayer.setVolume(MUSIC_VOLUME, MUSIC_VOLUME);
                backgroundPlayer.start();
            }
        } catch (Exception e) {
            // Fallback to default if Uri failed
            try {
                safeRelease(backgroundPlayer);
                backgroundPlayer = MediaPlayer.create(context, R.raw.game_backgroung);
                if (backgroundPlayer != null) {
                    backgroundPlayer.setLooping(true);
                    backgroundPlayer.setVolume(MUSIC_VOLUME, MUSIC_VOLUME);
                    backgroundPlayer.start();
                }
            } catch (Exception ignored) {
            }
        }
    }

    /** Stop and release background music */
    public static void stopBackgroundMusic() {
        safeStopAndReleaseBackground();
    }

    private static void safeStopAndReleaseBackground() {
        if (backgroundPlayer != null) {
            try {
                if (backgroundPlayer.isPlaying()) backgroundPlayer.stop();
            } catch (Exception ignored) {
            }
            safeRelease(backgroundPlayer);
            backgroundPlayer = null;
        }
    }

    // =================== External Song Player ===================

    /** Play a custom song (Uri string) in loop */
    public static void playSong(Context context, String uriString) {
        if (uriString == null || uriString.trim().isEmpty()) return;

        if (songPlayer != null) {
            try {
                if (songPlayer.isPlaying()) return;
                songPlayer.start();
                return;
            } catch (IllegalStateException ignored) {
                safeRelease(songPlayer);
                songPlayer = null;
            }
        }

        try {
            songPlayer = new MediaPlayer();
            songPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            songPlayer.setDataSource(context, Uri.parse(uriString));
            songPlayer.prepare();
            songPlayer.setLooping(true);
            songPlayer.setVolume(MUSIC_VOLUME, MUSIC_VOLUME);
            songPlayer.start();
        } catch (IOException | IllegalArgumentException | SecurityException | IllegalStateException e) {
            e.printStackTrace();
            safeRelease(songPlayer);
            songPlayer = null;
        }
    }

    /** Stop and release song */
    public static void stopSong() {
        if (songPlayer != null) {
            try {
                if (songPlayer.isPlaying()) songPlayer.stop();
            } catch (Exception ignored) {
            }
            safeRelease(songPlayer);
            songPlayer = null;
        }
    }

    // =================== Helpers ===================

    private static void safeRelease(MediaPlayer player) {
        if (player == null) return;
        try {
            player.release();
        } catch (Exception ignored) {
        }
    }
}
