package com.colourmate.paintbynumber.adapter;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;


import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.Utils.ColorForgeApi;
import com.colourmate.paintbynumber.Utils.Manager;
import com.colourmate.paintbynumber.model.model.Level;
import com.colourmate.paintbynumber.util.DataManager;
import com.colourmate.paintbynumber.util.FavoritesManager;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Adapter for displaying coloring book levels in a RecyclerView.
 * Handles thumbnail loading, new item indicators, and click events.
 */
public class ColoringsAdapter extends BaseRestAdapter<ColoringsAdapter.ColoringViewHolder, Level> {

    private static final String DATE_FORMAT = "yyyy-MM-dd";

    private final View loadingView;
    private final LinearLayout emptyStateLayout;
    private final SimpleDateFormat dateFormat;

    private ColoringClickListener coloringClickListener;
    private LevelAdapterListener levelAdapterListener;

    /**
     * Interface for handling coloring item clicks
     */
    public interface ColoringClickListener {
        void onColoringClicked(Level level);
    }

    /**
     * Interface for notifying when items are loaded
     */
    public interface LevelAdapterListener {
        void itemsLoaded(int itemCount);
    }

    /**
     * ViewHolder for coloring book level items
     */
    public class ColoringViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private final ImageView thumbnailImageView;
        private final ProgressBar loadingProgressBar;
        private final ImageView favoriteIcon;

        private Level currentLevel;

        ColoringViewHolder(@NonNull View itemView) {
            super(itemView);

            thumbnailImageView = itemView.findViewById(R.id.imageViewResult);
            loadingProgressBar = itemView.findViewById(R.id.loading_view);
            favoriteIcon = itemView.findViewById(R.id.iv_favorite);
            if (thumbnailImageView != null) {
                thumbnailImageView.setClipToOutline(true);
            }
            itemView.setOnClickListener(this);
        }

        private Activity getActivity(Context context) {
            while (context instanceof ContextWrapper) {
                if (context instanceof Activity)
                    return (Activity) context;
                context = ((ContextWrapper) context).getBaseContext();
            }
            return null;
        }

        @Override
        public void onClick(View view) {
            currentLevel = (Level) getItem(getAdapterPosition(), view.getContext());
            if (coloringClickListener != null && currentLevel != null) {
                coloringClickListener.onColoringClicked(currentLevel);
            }
        }

        /**
         * Binds level data to the view holder
         */
        public void bind(Level level) {
            this.currentLevel = level;

            if (level == null) {
                showEmptyState();
                return;
            }

            loadThumbnail(level);
            updateNewIndicator(level);
            updateFavoriteIcon(level);
            updateProgressChip(level);
        }

        /**
         * Shows empty state when level is null
         */
        private void showEmptyState() {
            thumbnailImageView.setImageBitmap(null);
        }

        /**
         * Loads the thumbnail image for the level
         */
        private void loadThumbnail(Level level) {
            thumbnailImageView.setImageBitmap(null);
            loadingProgressBar.setVisibility(View.VISIBLE);

            // Every thumbnail gets a white plate.
            //
            // The old code cleared it for the paid tabs on the belief that their thumbnails are
            // opaque. They are not: they are black RGB with the picture in the ALPHA channel
            // (CLAUDE.md §2), and 8% of each one is a soft alpha wash. Composited straight onto
            // the lilac page that wash rendered as a grey haze around every tile — measured at
            // rgb(193,189,198), which is exactly soft black over the paper ground. On white the
            // same pixels read as the light grey shading the artist drew.
            thumbnailImageView.setBackgroundColor(android.graphics.Color.WHITE);

            File cachedPreview = DataManager.getInstance().getPreview(level);

            if (cachedPreview != null) {
                loadImageFromFile(cachedPreview);
            } else {
                loadImageFromUrl(level);
            }
        }

        /**
         * Loads image from cached file
         */
        private void loadImageFromFile(File imageFile) {
            Picasso.get()
                    .load(imageFile)
                    // fit() = decode straight to the cell size instead of full-res
                    .fit()
                    .centerCrop() // matches scaleType in item_coloring.xml
                    .into(thumbnailImageView, new ImageLoadCallback());
        }

        /**
         * Loads image from remote URL
         */
        private void loadImageFromUrl(Level level) {
            String thumbnailUrl = buildThumbnailUrl(level);
            Picasso.get()
                    .load(thumbnailUrl)
                    .fit()
                    .centerCrop()
                    .into(thumbnailImageView, new ImageLoadCallback());
        }

        /**
         * Builds the thumbnail URL for a level
         */
        private String buildThumbnailUrl(Level level) {
            // "New 2026" levels are served by ColorForge, everything else by ionicsolutions.
            if (level.isDiamond()) {
                // Diamond pictures have no line art — the gallery shows the finished piece.
                String url = ColorForgeApi.diamondThumbUrl(itemView.getContext(),
                        level.getCategory(), level.getFileName());
                if (!url.isEmpty()) return url;
            } else if (level.isColorForge()) {
                // The clean artistic border the reveal game draws — NOT the noisy colour-contour
                // line-art. Transparent PNG, shown on a white cell (set in loadThumbnail).
                String url = ColorForgeApi.borderThumbUrl(itemView.getContext(),
                        level.getCategory(), level.getFileName());
                if (!url.isEmpty()) return url;
            }
            return Manager.BASE_URL + "thumb/" + level.getCategory() + "/t/" +
                    level.getFileName() + ".png";
        }

        /**
         * "42%" while a ColorForge picture is being played, a tick once finished. The
         * percent is written by RevealActivity on every pause (key = reveal base URL +
         * "#pct"), so the grid can show it without decoding any region lists. The paid
         * tabs keep their own progress system and show nothing here.
         */
        private void updateProgressChip(Level level) {
            android.widget.TextView chip = itemView.findViewById(R.id.tv_progress_chip);
            if (chip == null) return;
            if (level == null || !level.isColorForge() || level.isDiamond()
                    || level.getFileName() == null) {
                chip.setVisibility(android.view.View.GONE);
                return;
            }
            android.content.Context ctx = itemView.getContext();
            String key = com.colourmate.paintbynumber.Utils.ColorForgeApi.revealBaseUrl(
                    ctx, level.getCategory(), level.getFileName()) + "#pct";
            int pct = ctx.getSharedPreferences("reveal_progress", android.content.Context.MODE_PRIVATE)
                    .getInt(key, 0);
            if (pct <= 0) {
                if (!com.colourmate.paintbynumber.util.PictureUnlock.showBadge(chip, level)) {
                    chip.setVisibility(android.view.View.GONE);
                }
            } else {
                chip.setVisibility(android.view.View.VISIBLE);
                chip.setText(pct >= 100 ? "✓" : pct + "%");
            }
        }

        private void updateNewIndicator(Level level) {
            if (isLevelNewToday(level)) {
                level.setNewToday(true);
            }
        }

        private void updateFavoriteIcon(Level level) {
            if (favoriteIcon == null || level == null || level.getFileName() == null)
                return;

            boolean isFav = FavoritesManager.isFavorite(
                    itemView.getContext(), level.getFileName());
            applyFavoriteState(favoriteIcon, isFav);

            favoriteIcon.setOnClickListener(v -> {
                FavoritesManager.toggleFavorite(v.getContext(), level.getFileName());
                boolean nowFav = FavoritesManager.isFavorite(
                        v.getContext(), level.getFileName());
                applyFavoriteState(favoriteIcon, nowFav);

                v.animate().scaleX(1.3f).scaleY(1.3f).setDuration(150)
                        .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f)
                                .setDuration(150).start())
                        .start();

                if (nowFav) {
                    com.colourmate.paintbynumber.util.AchievementManager
                            .recordFavoriteAdded(v.getContext());
                }
            });
        }

        private void applyFavoriteState(ImageView iv, boolean isFav) {
            iv.setImageResource(isFav
                    ? R.drawable.ic_favorite_fill
                    : R.drawable.ic_favorite_border);
            // Blossom, not alarm-red — a favourite is affection, not an error.
            iv.setColorFilter(isFav ? 0xFFFF6BA9 : 0xFF9A93B4);
        }

        /**
         * Checks if the level was added today
         */
        private boolean isLevelNewToday(Level level) {
            try {
                Date levelDate = dateFormat.parse(level.getTimeStamp());
                Date today = dateFormat.parse(dateFormat.format(new Date()));
                return levelDate != null && today != null && levelDate.compareTo(today) == 0;
            } catch (ParseException e) {
                e.printStackTrace();
                return false;
            }
        }

        /**
         * Callback for image loading
         */
        private class ImageLoadCallback implements Callback {
            @Override
            public void onSuccess() {
                loadingProgressBar.setVisibility(View.GONE);
            }

            @Override
            public void onError(Exception exception) {
                loadingProgressBar.setVisibility(View.VISIBLE);
            }
        }
    }

    public ColoringsAdapter(Manager.RestDataSource<Level> dataSource) {
        super(dataSource);
        this.loadingView = null;
        this.emptyStateLayout = null;
        this.dateFormat = new SimpleDateFormat(DATE_FORMAT, Locale.getDefault());
        setHasStableIds(true); // lets RecyclerView skip rebinds for unchanged items
    }

    /** Required because this adapter uses stable ids — see BaseRestAdapter.getItemId(). */
    @Override
    protected String stableKeyFor(Level level) {
        return level != null ? level.getFileName() : null;
    }

    // Public Methods
    public void setListener(LevelAdapterListener listener) {
        this.levelAdapterListener = listener;
    }

    public void setOnClickColoringListener(ColoringClickListener listener) {
        this.coloringClickListener = listener;
    }

    // RecyclerView.Adapter Methods
    @NonNull
    @Override
    public ColoringViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_coloring, parent, false);
        return new ColoringViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull ColoringViewHolder holder, int position) {
        Level level = (Level) getItem(position, holder.itemView.getContext());
        holder.bind(level);
    }

    // BookRecyclerRestAdapter Methods
    @Override
    public void itemsLoaded() {
        hideLoadingView();
        notifyAdapterListener();
        updateEmptyStateVisibility();
    }

    // Private Helper Methods
    private void showLoadingView() {
        if (loadingView != null) {
            loadingView.setVisibility(View.VISIBLE);
        }
    }

    private void hideLoadingView() {
        if (loadingView != null) {
            loadingView.setVisibility(View.GONE);
        }
    }

    private void notifyAdapterListener() {
        if (levelAdapterListener != null) {
            levelAdapterListener.itemsLoaded(getItemCount());
        }
    }

    private void updateEmptyStateVisibility() {
        if (emptyStateLayout == null) {
            return;
        }

        if (getItemCount() == 0) {
            emptyStateLayout.setVisibility(View.VISIBLE);
        } else {
            emptyStateLayout.setVisibility(View.GONE);
        }
    }
}