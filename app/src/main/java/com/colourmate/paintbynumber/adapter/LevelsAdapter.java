package com.colourmate.paintbynumber.adapter;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;


import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.Utils.Manager;
import com.colourmate.paintbynumber.model.model.Level;
import com.colourmate.paintbynumber.util.AchievementManager;
import com.colourmate.paintbynumber.util.DataManager;
import com.colourmate.paintbynumber.util.FavoritesManager;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LevelsAdapter extends RecyclerView.Adapter<LevelsAdapter.LevelHolder> {


    private List<Level> items = new ArrayList<>();
    private ColoringsAdapter.ColoringClickListener coloringClickListener;

    public void setOnClickColoringListner(ColoringsAdapter.ColoringClickListener listener) {
        this.coloringClickListener = listener;
    }

    @NonNull
    @Override
    public LevelHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        return new LevelHolder(inflater.inflate(R.layout.item_daily_number_color, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull LevelHolder holder, int position) {
        bindLevel(holder, items.get(position));
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    public void setList(List<Object> list) {
        // list ma only Level add karso to aa safe chhe
        List<Level> levels = new ArrayList<>();
        if (list != null) {
            for (Object o : list) {
                if (o instanceof Level) levels.add((Level) o);
            }
        }
        this.items = levels;
        notifyDataSetChanged();
    }

    public class LevelHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        ImageView ivResult;
        ProgressBar progressBar;
        ImageView ivFavorite;

        LevelHolder(@NonNull View v) {
            super(v);

            ivResult = v.findViewById(R.id.imageViewResult);
            progressBar = v.findViewById(R.id.loading_view);
            ivFavorite = v.findViewById(R.id.iv_favorite);
            if (ivResult != null) {
                ivResult.setClipToOutline(true);
            }
            v.setOnClickListener(this);
        }


        @Override
        public void onClick(View v) {
            int pos = getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            Level level = items.get(pos);
            if (coloringClickListener != null) coloringClickListener.onColoringClicked(level);
        }
    }

    private static Activity getActivity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }


    private void bindLevel(LevelHolder h, Level level) {
        h.progressBar.setVisibility(View.VISIBLE);
        h.ivResult.setImageBitmap(null);

        // Our own pictures (mixed into the daily list) come from our server, and their progress
        // lives in the reveal game — the flat game's preview file never exists for them.
        boolean ours = level.isColorForge();
        File preview = ours ? null : DataManager.getInstance().getPreview(level);
        String url = ours
                ? com.colourmate.paintbynumber.Utils.ColorForgeApi.borderThumbUrl(
                        h.itemView.getContext(), level.getCategory(), level.getFileName())
                : Manager.BASE_URL + "thumb/" + level.getCategory() + "/t/" + level.getFileName() + ".png";
        h.ivResult.setBackgroundColor(ours ? android.graphics.Color.WHITE : android.graphics.Color.TRANSPARENT);
        android.widget.TextView chip = h.itemView.findViewById(R.id.tv_progress_chip);
        if (chip != null && !com.colourmate.paintbynumber.util.PictureUnlock.showBadge(chip, level)) {
            chip.setVisibility(View.GONE);
        }

        if (preview != null) {
            // fit()+centerCrop = decode to the cell size, matching the layout's scaleType
            Picasso.get().load(preview).fit().centerCrop().into(h.ivResult, new Callback() {
                @Override
                public void onSuccess() { h.progressBar.setVisibility(View.GONE); }
                @Override
                public void onError(Exception e) { h.progressBar.setVisibility(View.VISIBLE); }
            });
        } else {
            Picasso.get().load(url).fit().centerCrop().into(h.ivResult, new Callback() {
                @Override
                public void onSuccess() { h.progressBar.setVisibility(View.GONE); }
                @Override
                public void onError(Exception e) { h.progressBar.setVisibility(View.VISIBLE); }
            });
        }

        // Favorites
        if (h.ivFavorite != null && level.getFileName() != null) {
            boolean isFav = FavoritesManager.isFavorite(
                    h.itemView.getContext(), level.getFileName());
            applyFavoriteState(h.ivFavorite, isFav);

            h.ivFavorite.setOnClickListener(v -> {
                FavoritesManager.toggleFavorite(v.getContext(), level.getFileName());
                boolean nowFav = FavoritesManager.isFavorite(
                        v.getContext(), level.getFileName());
                applyFavoriteState(h.ivFavorite, nowFav);
                v.animate().scaleX(1.3f).scaleY(1.3f).setDuration(150)
                        .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f)
                                .setDuration(150).start()).start();
                if (nowFav) {
                    AchievementManager.recordFavoriteAdded(v.getContext());
                }
            });
        }
    }

    private void applyFavoriteState(ImageView iv, boolean isFav) {
        iv.setImageResource(isFav ? R.drawable.ic_favorite_fill : R.drawable.ic_favorite_border);
        iv.setColorFilter(isFav ? 0xFFEF4444 : 0xFFBDBDBD);
    }
}

