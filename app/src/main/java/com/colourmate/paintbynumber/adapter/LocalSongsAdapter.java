package com.colourmate.paintbynumber.adapter;

import android.app.Activity;
import android.content.Context;
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

import java.util.ArrayList;

/**
 * StorageMusicAdapter
 * -------------------
 * Shows device/local songs (picked from MediaStore).
 *
 * - Play/Pause button: preview play/stop (uses SoundUtils.playSong/stopSong)
 * - Row click: set as BACKGROUND music (saved in prefs), then start background music
 */
public class LocalSongsAdapter extends BaseAdapter {

    private final Context context;
    private final LayoutInflater inflater;
    private final ArrayList<SongModel> songs;

    /** Which item is currently playing preview (for icon state) */
    private int playingPosition = -1;

    public static class ViewHolder {
        TextView titleText;
        TextView selectIcon;
        ImageView playPauseIcon;
    }

    public LocalSongsAdapter(Context context, ArrayList<SongModel> dataList) {
        this.context = context;
        this.songs = dataList;
        this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public int getCount() {
        return songs == null ? 0 : songs.size();
    }

    @Override
    public SongModel getItem(int position) {
        return songs.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {

        final ViewHolder holder;

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.ietm_glcolor_music, parent, false);

            holder = new ViewHolder();
            holder.titleText = convertView.findViewById(R.id.tvTitleGL);
            holder.selectIcon = convertView.findViewById(R.id.ivChooseGL);
            holder.playPauseIcon = convertView.findViewById(R.id.ivPlayGL);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final SongModel song = getItem(position);
        holder.titleText.setText(song.getName());

        // ✅ Play/Pause icon based on current playingPosition
        holder.playPauseIcon.setImageResource(
                (position == playingPosition) ? R.drawable.ic_pause : R.drawable.icon_play
        );

        // -------------------------------
        // Play / Pause preview button
        // -------------------------------
        holder.playPauseIcon.setOnClickListener(v -> {

            // When previewing a song, stop background music
            SoundUtils.stopBackgroundMusic();

            // If user clicked a different song => start new preview
            if (playingPosition != position) {
                playingPosition = position;

                // Stop previous preview if any
                SoundUtils.stopSong();

                // Start preview
                SoundUtils.playSong(context, song.getPath());

                notifyDataSetChanged();
                return;
            }

            // Same item clicked => toggle preview play/stop
            if (SoundUtils.songPlayer != null) {
                try {
                    if (SoundUtils.songPlayer.isPlaying()) {
                        SoundUtils.stopSong();
                        holder.playPauseIcon.setImageResource(R.drawable.icon_play);
                        playingPosition = -1; // reset UI state
                        notifyDataSetChanged();
                    } else {
                        holder.playPauseIcon.setImageResource(R.drawable.ic_pause);
                        SoundUtils.playSong(context, song.getPath());
                    }
                } catch (Exception e) {
                    // safety fallback
                    SoundUtils.stopSong();
                    holder.playPauseIcon.setImageResource(R.drawable.icon_play);
                    playingPosition = -1;
                    notifyDataSetChanged();
                }
            } else {
                holder.playPauseIcon.setImageResource(R.drawable.ic_pause);
                SoundUtils.playSong(context, song.getPath());
            }
        });

        // -------------------------------
        // Row click: Set as background music
        // -------------------------------
        convertView.setOnClickListener(v -> {

            v.startAnimation(AnimationUtils.loadAnimation(context, R.anim.anim_bounce));

            // Stop preview & background first
            SoundUtils.stopSong();
            SoundUtils.stopBackgroundMusic();

            // Toast safely (context may not be Activity)
            if (context instanceof Activity) {
                Activity act = (Activity) context;
                if (!act.isFinishing()) {
                    try {
                        Toast.makeText(context, song.getName() + " set successfully", Toast.LENGTH_SHORT).show();
                    } catch (WindowManager.BadTokenException ignored) { }
                }
            } else {
                Toast.makeText(context, song.getName() + " set successfully", Toast.LENGTH_SHORT).show();
            }

            // Save selected file path as background music
            Constants.saveBackgroundMusic(context, song.getPath());

            // Start background music system (preferred)
            SoundUtils.playBackgroundMusic(context);

            // Reset playing UI since now it's background
            playingPosition = -1;
            notifyDataSetChanged();
        });

        return convertView;
    }
}
