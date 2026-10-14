package com.colourmate.paintbynumber.adapter;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.colourmate.paintbynumber.Constants;
import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.model.SongModel;
import com.colourmate.paintbynumber.util.SoundUtils;
import com.google.android.material.snackbar.Snackbar;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ServerMusicAdapter
 * ------------------
 * Shows songs coming from server.
 *
 * - Tap play button: plays/pauses local downloaded song.
 * - Tap row: if downloaded -> set as background music, else download.
 */
public class OnlineSongAdapter extends BaseAdapter {

    private static final String TAG = "ServerMusicAdapter";
    private static final int BUFFER_SIZE = 8 * 1024;

    private final Context context;
    private final LayoutInflater inflater;
    private final ArrayList<SongModel> musicList;

    /** Which row is currently playing (for UI icon state) */
    private int playingPosition = -1;

    /** Background thread for downloads */
    private final ExecutorService downloadExecutor = Executors.newSingleThreadExecutor();

    /** Main thread handler for UI updates */
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static class ViewHolder {
        TextView titleText;
        TextView downloadIcon;
        ImageView playPauseIcon;
    }

    public OnlineSongAdapter(Context context, ArrayList<SongModel> list) {
        this.context = context;
        this.musicList = list;
        this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public int getCount() {
        return musicList == null ? 0 : musicList.size();
    }

    @Override
    public SongModel getItem(int position) {
        return musicList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    /**
     * Folder where songs will be downloaded.
     * NOTE: This uses Environment.getExternalStorageDirectory() like your code.
     * Make sure permissions are handled in Activity (READ/WRITE external storage on old Android).
     */
    private File getMusicFolder() {
        // App-specific folder: /storage/emulated/0/Android/data/[your.package.name]/files/Music/Drashti
        File folder = new File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "colorbynumber");

        if (!folder.exists()) {
            boolean ok = folder.mkdirs();
            if (!ok) {
                Log.w(TAG, "Failed to create folder: " + folder.getAbsolutePath());
            } else {
                Log.d(TAG, "Folder created: " + folder.getAbsolutePath());
            }
        }
        return folder;
    }

    private File getLocalSongFile(SongModel song) {
        return new File(getMusicFolder(), song.getName());
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {

        final ViewHolder holder;

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.ietm_glcolor_music, parent, false);

            holder = new ViewHolder();
            holder.titleText = convertView.findViewById(R.id.tvTitleGL);
            holder.downloadIcon = convertView.findViewById(R.id.ivChooseGL);
            holder.playPauseIcon = convertView.findViewById(R.id.ivPlayGL);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final SongModel song = getItem(position);
        final File localFile = getLocalSongFile(song);

        // Title
        holder.titleText.setText(song.getName());

        holder.downloadIcon.setText(localFile.exists()
                ? "Choose"
                : "Download");

        // Play/Pause icon
        holder.playPauseIcon.setImageResource(position == playingPosition
                ? R.drawable.ic_pause
                : R.drawable.icon_play);

        // -------------------------------
        // Play / Pause button click
        // -------------------------------
        holder.playPauseIcon.setOnClickListener(v -> {

            // Stop background music when playing song preview
            SoundUtils.stopBackgroundMusic();

            if (!localFile.exists()) {
                Toast.makeText(context, "First download the song", Toast.LENGTH_SHORT).show();
                return;
            }

            String path = localFile.getAbsolutePath();

            // If user clicked a different song => start new
            if (playingPosition != position) {
                playingPosition = position;

                // Stop any previous song safely
                SoundUtils.stopSong();

                // Play new song
                SoundUtils.playSong(context, path);

                notifyDataSetChanged();
                return;
            }

            // Same song clicked => toggle play/pause (your old logic used stop+release, we keep simple stop)
            if (SoundUtils.songPlayer != null) {
                try {
                    if (SoundUtils.songPlayer.isPlaying()) {
                        SoundUtils.stopSong();
                        holder.playPauseIcon.setImageResource(R.drawable.icon_play);
                    } else {
                        holder.playPauseIcon.setImageResource(R.drawable.ic_pause);
                        SoundUtils.playSong(context, path);
                    }
                } catch (Exception e) {
                    // fallback safety
                    SoundUtils.stopSong();
                    holder.playPauseIcon.setImageResource(R.drawable.icon_play);
                }
            } else {
                holder.playPauseIcon.setImageResource(R.drawable.ic_pause);
                SoundUtils.playSong(context, path);
            }
        });

        // -------------------------------
        // Row click: Set as background / Download
        // -------------------------------
        convertView.setOnClickListener(v -> {
            v.startAnimation(AnimationUtils.loadAnimation(context, R.anim.anim_bounce));

            if (localFile.exists()) {
                // Set as BG music
                try {
                    Snackbar.make(v, song.getName() + " set successfully", Snackbar.LENGTH_SHORT).show();
                } catch (WindowManager.BadTokenException ignored) {
                }

                // Stop current song preview and save BG
                SoundUtils.stopSong();
                Constants.saveBackgroundMusic(context, localFile.getAbsolutePath());

                // Now play background song (your original code used playSong here; better to use background)
                SoundUtils.playBackgroundMusic(context);

            } else {
                // Download
                downloadSong(song, localFile);
            }
        });

        return convertView;
    }

    // ------------------------------------------------------------
    // Download task
    // ------------------------------------------------------------

    private void downloadSong(SongModel song, File outputFile) {
        ProgressDialog dialog = new ProgressDialog(context);
        dialog.setMessage("Downloading...");
        dialog.setCancelable(false);

        try {
            dialog.show();
        } catch (Exception e) {
            dialog = null;
        }

        final ProgressDialog finalDialog = dialog;

        downloadExecutor.execute(() -> {
            boolean success = false;
            String errorMessage = "";
            HttpURLConnection connection = null;

            try {
                // ✅ CREATE DIRECTORY IF IT DOESN'T EXIST
                File parentDir = outputFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    boolean created = parentDir.mkdirs();
                    Log.d(TAG, "Directory created: " + created + " -> " + parentDir.getAbsolutePath());

                    if (!created && !parentDir.exists()) {
                        throw new Exception("Failed to create directory: " + parentDir.getAbsolutePath());
                    }
                }

                // Validate URL
                String urlString = song.getPath();
                if (urlString == null || !urlString.startsWith("http")) {
                    throw new Exception("Invalid URL: " + urlString);
                }

                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.connect();

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new Exception("Server error: " + connection.getResponseCode());
                }

                try (InputStream input = new BufferedInputStream(connection.getInputStream());
                     FileOutputStream output = new FileOutputStream(outputFile)) {

                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = input.read(buffer)) != -1) {
                        output.write(buffer, 0, len);
                    }
                    output.flush();
                }

                success = true;
                Log.d(TAG, "Download complete: " + outputFile.getAbsolutePath());

            } catch (Exception e) {
                errorMessage = e.getMessage();
                Log.e(TAG, "Download error: " + e.getMessage(), e);

                // Delete incomplete file
                if (outputFile.exists()) {
                    outputFile.delete();
                }
            } finally {
                if (connection != null) connection.disconnect();
            }

            final boolean finalSuccess = success;
            final String finalError = errorMessage;

            mainHandler.post(() -> {
                try {
                    if (finalDialog != null && finalDialog.isShowing()) {
                        finalDialog.dismiss();
                    }
                } catch (Exception ignored) {}

                Toast.makeText(context,
                        finalSuccess ? "Downloaded successfully!" : "Failed: " + finalError,
                        Toast.LENGTH_LONG).show();

                if (finalSuccess) notifyDataSetChanged();
            });
        });
    }}
