package com.colourmate.paintbynumber.adapter;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.activity.MainActivity;
import com.colourmate.paintbynumber.fragments.DrawingDialogFragment;
import com.colourmate.paintbynumber.library.Library;
import com.colourmate.paintbynumber.util.PurchaseManager;
import com.koushikdutta.ion.Ion;
import com.sumoing.recolor.library.LibraryItem;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import eightbitlab.com.blurview.RenderScriptBlur;

public class LibraryItemAdapter extends RecyclerView.Adapter<LibraryItemAdapter.LibraryItemViewHolder> {

    // ──────────────────────────────────────────────
    // DiffUtil ItemCallback
    // ──────────────────────────────────────────────
    private static final DiffUtil.ItemCallback<LibraryItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<LibraryItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull LibraryItem a, @NonNull LibraryItem b) {
                    return Objects.equals(a.thumbUrl, b.thumbUrl);
                }

                @Override
                public boolean areContentsTheSame(@NonNull LibraryItem a, @NonNull LibraryItem b) {
                    return Objects.equals(a.thumbUrl, b.thumbUrl)
                            && a.isFreeContent() == b.isFreeContent()
                            && Objects.equals(
                            a.getColoredPicture() != null
                                    ? a.getColoredPicture().getThumbFile() : null,
                            b.getColoredPicture() != null
                                    ? b.getColoredPicture().getThumbFile() : null
                    );
                }
            };

    // ──────────────────────────────────────────────
    // Fields
    // ──────────────────────────────────────────────
    private List<LibraryItem> libraryItems = new ArrayList<>();
    private final LibraryItemListener listener;
    private boolean hasSubscription;
    private boolean isHidden = false;
    private boolean useFolderMode = false;
    private String categoryTag;

    // ⚡ FIX: Track whether image was already loaded to skip fadeIn
    private final Set<String> alreadyLoadedUrls = Collections.newSetFromMap(
            new WeakHashMap<>()
    );

    // ⚡ FIX: Use volatile for thread safety
    private volatile boolean isScrolling = false;

    /** Bumped per list submission so a late DiffUtil result can tell it has been superseded. */
    private int diffGeneration = 0;

    // Background thread for DiffUtil calculation
    private final ExecutorService diffExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Future<?> pendingDiffTask;

    public interface LibraryItemListener {
        void onContinueColoring(Library.ColoredPicture coloredPicture);
        void onStartEditing(LibraryItem libraryItem, boolean isFromFolder);
    }

    // ══════════════════════════════════════════════
    //  ViewHolder - HEAVILY Optimized
    // ══════════════════════════════════════════════
    public class LibraryItemViewHolder extends RecyclerView.ViewHolder {

        private final ImageView thumbnailImageView;
        private final ImageView premiumBadgeImageView;
        private final ProgressBar loadingProgressBar;
        // ⚡ FIX 1: REMOVED BlurView completely from ViewHolder
        // BlurView in every grid item is the #1 cause of lag.
        // Each BlurView creates a RenderScript context, captures the
        // background, and re-renders it — extremely expensive in a
        // RecyclerView that recycles 50+ items.

        private String currentLoadingUrl = null;
        private com.koushikdutta.async.future.Future<ImageView> currentImageFuture;

        public LibraryItemViewHolder(View itemView) {
            super(itemView);

            thumbnailImageView = itemView.findViewById(R.id.iv_book_item_image);
            premiumBadgeImageView = itemView.findViewById(R.id.iv_book_item_badge);
            loadingProgressBar = itemView.findViewById(R.id.loading_view);

            if (thumbnailImageView != null) {
                thumbnailImageView.setClipToOutline(true);
            }

            setupClickListener();
        }

        private void setupClickListener() {
            itemView.setOnClickListener(view -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION
                        && position < libraryItems.size()) {
                    handleItemClick(view, libraryItems.get(position));
                }
            });
        }

        public void bind(LibraryItem item) {
            updatePremiumBadge(item);

            // ⚡ FIX 3: Skip image loading during fast scroll
            if (isScrolling) {
                // The tile must NOT keep the previous item's picture while we skip the load.
                // A recycled holder still holds the old bitmap, so leaving it on screen shows one
                // artwork on a cell that belongs to a different item — tap it and "another image
                // opens". Blank it: an empty tile cannot be mis-tapped.
                clearImage();
                showLoading();
                return;
            }

            loadThumbnail(item);
        }

        /**
         * Drops whatever artwork this holder is showing. Cheap, and the only thing that keeps the
         * picture on screen honest about which item the cell now represents.
         */
        private void clearImage() {
            if (thumbnailImageView != null) {
                thumbnailImageView.setImageDrawable(null);
            }
            currentLoadingUrl = null;
        }

        private void updatePremiumBadge(LibraryItem item) {
            boolean shouldShowBadge = !item.isFreeContent() && !hasSubscription;
            int targetVisibility = shouldShowBadge ? View.VISIBLE : View.INVISIBLE;
            if (premiumBadgeImageView.getVisibility() != targetVisibility) {
                premiumBadgeImageView.setVisibility(targetVisibility);
            }
        }

        private void loadThumbnail(LibraryItem item) {
            Library.ColoredPicture coloredPicture = item.getColoredPicture();
            File thumbnailFile = coloredPicture != null
                    ? coloredPicture.getThumbFile() : null;

            String loadKey;
            if (shouldLoadFromFile(thumbnailFile)) {
                loadKey = "file://" + thumbnailFile.getAbsolutePath();
            } else {
                loadKey = item.thumbUrl;
            }

            // Skip if same image is already loaded/loading
            if (loadKey != null && loadKey.equals(currentLoadingUrl)) {
                return;
            }

            cancelCurrentLoad();
            currentLoadingUrl = loadKey;
            showLoading();

            if (shouldLoadFromFile(thumbnailFile)) {
                loadThumbnailFromFile(thumbnailFile, loadKey);
            } else {
                loadThumbnailFromUrl(item.thumbUrl, loadKey);
            }
        }

        private void cancelCurrentLoad() {
            if (currentImageFuture != null && !currentImageFuture.isDone()) {
                currentImageFuture.cancel(true);
                currentImageFuture = null;
            }
            thumbnailImageView.setImageDrawable(null);
        }

        private boolean shouldLoadFromFile(File file) {
            return file != null && file.exists();
        }

        private void showLoading() {
            if (loadingProgressBar.getVisibility() != View.VISIBLE) {
                loadingProgressBar.setVisibility(View.VISIBLE);
            }
        }

        private void hideLoading() {
            if (loadingProgressBar.getVisibility() != View.GONE) {
                loadingProgressBar.setVisibility(View.GONE);
            }
        }

        private void loadThumbnailFromUrl(String url, String loadKey) {
            if (url == null || url.isEmpty()) {
                hideLoading();
                return;
            }

            // ⚡ FIX 4: Use crossfade only for FIRST load, not rebind
            currentImageFuture = Ion.with(thumbnailImageView.getContext())
                    .load(url)
                    .withBitmap()
                    .resize(300, 300)
                    .centerCrop()
                    .animateIn(shouldAnimate(loadKey)
                            ? R.anim.fade_in : 0)  // ⚡ No animation on re-bind
                    .intoImageView(thumbnailImageView);

            currentImageFuture.setCallback((exception, imageView) -> {
                hideLoading();
                if (exception == null) {
                    alreadyLoadedUrls.add(loadKey);
                }
            });
        }

        private void loadThumbnailFromFile(File file, String loadKey) {
            currentImageFuture = Ion.with(thumbnailImageView.getContext())
                    .load(file)
                    .noCache()
                    .withBitmap()
                    .resize(300, 300)
                    .centerCrop()
                    .animateIn(shouldAnimate(loadKey)
                            ? R.anim.fade_in : 0)
                    .intoImageView(thumbnailImageView);

            currentImageFuture.setCallback((exception, imageView) -> {
                hideLoading();
                if (exception == null) {
                    alreadyLoadedUrls.add(loadKey);
                }
            });
        }

        // ⚡ FIX 5: Only animate the FIRST time an image loads
        // Repeated fadeIn on every scroll is a major jank source
        private boolean shouldAnimate(String loadKey) {
            return !alreadyLoadedUrls.contains(loadKey);
        }

        public void recycle() {
            cancelCurrentLoad();
            // Clear the artwork as well as the load state. Without this the recycled cell carries
            // the old item's picture into its next position, which is how a tap could open an
            // image the user was not looking at.
            clearImage();
            hideLoading();
        }
    }

    // ══════════════════════════════════════════════
    //  Constructors
    // ══════════════════════════════════════════════

    public LibraryItemAdapter(LibraryItemListener listener) {
        this.listener = listener;
        this.hasSubscription = PurchaseManager.getInstance().hasSubscription();
        setHasStableIds(true);
    }

    public LibraryItemAdapter(boolean useFolderMode, String categoryTag,
                              LibraryItemListener listener) {
        this.listener = listener;
        this.useFolderMode = useFolderMode;
        this.categoryTag = categoryTag;
        setHasStableIds(true);
        refresh();
    }

    // ══════════════════════════════════════════════
    //  Scroll State - Called from Fragment
    // ══════════════════════════════════════════════

    /**
     * ⚡ FIX 6: Let adapter know scroll state to skip
     * expensive image loads during fling
     */
    public void setScrolling(boolean scrolling) {
        boolean wasScrolling = this.isScrolling;
        this.isScrolling = scrolling;

        // When scrolling stops, rebind visible items
        if (wasScrolling && !scrolling) {
            notifyDataSetChanged();
        }
    }

    // ══════════════════════════════════════════════
    //  Data Updates - Background DiffUtil
    // ══════════════════════════════════════════════

    public void setList(List<LibraryItem> items) {
        final List<LibraryItem> newList = items != null
                ? new ArrayList<>(items) : new ArrayList<>();
        calculateDiffAsync(newList);
    }

    public void refresh() {
        final List<LibraryItem> newList;
        if (useFolderMode) {
            newList = Library.getInstance().getLibraryItemsByFolder(categoryTag);
            hasSubscription = true;
        } else {
            newList = Library.getInstance().getLibraryItemsByTag(categoryTag);
            hasSubscription = PurchaseManager.getInstance().hasSubscription();
        }
        final List<LibraryItem> safeNew = newList != null
                ? new ArrayList<>(newList) : new ArrayList<>();
        calculateDiffAsync(safeNew);
    }

    private void calculateDiffAsync(List<LibraryItem> newList) {
        if (pendingDiffTask != null && !pendingDiffTask.isDone()) {
            pendingDiffTask.cancel(true);
        }

        // ⚡ FIX 7: Snapshot the old list IMMEDIATELY on main thread
        final List<LibraryItem> oldList = new ArrayList<>(this.libraryItems);

        // Which submission this is. cancel(true) only interrupts — a task that has already posted
        // its result to the main thread still lands. Applying a stale diff would leave
        // RecyclerView's positions describing a list we no longer hold, and a tap would then
        // resolve to the wrong item, so a superseded result is dropped instead.
        final int generation = ++diffGeneration;

        pendingDiffTask = diffExecutor.submit(() -> {
            // Check cancellation
            if (Thread.currentThread().isInterrupted()) return;

            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(
                    new DiffUtil.Callback() {
                        @Override
                        public int getOldListSize() { return oldList.size(); }

                        @Override
                        public int getNewListSize() { return newList.size(); }

                        @Override
                        public boolean areItemsTheSame(int oldPos, int newPos) {
                            return DIFF_CALLBACK.areItemsTheSame(
                                    oldList.get(oldPos), newList.get(newPos));
                        }

                        @Override
                        public boolean areContentsTheSame(int oldPos, int newPos) {
                            return DIFF_CALLBACK.areContentsTheSame(
                                    oldList.get(oldPos), newList.get(newPos));
                        }
                    }, false); // ⚡ FIX 8: detectMoves=false is faster

            if (Thread.currentThread().isInterrupted()) return;

            mainHandler.post(() -> {
                if (generation != diffGeneration) {
                    return;   // a newer list is already on its way; this diff describes the past
                }
                libraryItems = newList;
                diffResult.dispatchUpdatesTo(LibraryItemAdapter.this);
            });
        });
    }

    // ══════════════════════════════════════════════
    //  RecyclerView.Adapter Methods
    // ══════════════════════════════════════════════

    @NonNull
    @Override
    public LibraryItemViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.frag_bookitem, parent, false);
        return new LibraryItemViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(
            @NonNull LibraryItemViewHolder holder, int position) {
        holder.bind(libraryItems.get(position));
    }

    @Override
    public void onViewRecycled(@NonNull LibraryItemViewHolder holder) {
        super.onViewRecycled(holder);
        holder.recycle();
    }

    @Override
    public long getItemId(int position) {
        // ⚡ FIX 9: Better stable ID to reduce hash collisions
        LibraryItem item = libraryItems.get(position);
        if (item.thumbUrl != null) {
            // Use full 64-bit hash instead of 32-bit hashCode()
            long hash = 0;
            for (int i = 0; i < item.thumbUrl.length(); i++) {
                hash = 31 * hash + item.thumbUrl.charAt(i);
            }
            return hash;
        }
        return RecyclerView.NO_ID;
    }

    @Override
    public int getItemCount() {
        return libraryItems.size();
    }

    // ══════════════════════════════════════════════
    //  Getters / Setters
    // ══════════════════════════════════════════════

    public boolean isHidden() { return isHidden; }
    public void setHidden(boolean hidden) { this.isHidden = hidden; }

    // ══════════════════════════════════════════════
    //  Click Handling
    // ══════════════════════════════════════════════

    private void handleItemClick(View view, LibraryItem item) {
        Library.ColoredPicture cp = item.getColoredPicture();
        if (cp != null && cp.getThumbFile().exists()) {
            if (listener != null) {
                listener.onContinueColoring(cp);
            } else {
                Context ctx = view.getContext();
                if (ctx instanceof AppCompatActivity) {
                    DrawingDialogFragment.show(
                            (AppCompatActivity) ctx, cp, false);
                }
            }
        } else {
            if (listener != null) {
                listener.onStartEditing(item, false);
            } else {
                Context ctx = view.getContext();
                if (ctx instanceof MainActivity) {
                    ((MainActivity) ctx).startEditing(item, false);
                }
            }
        }
    }

    // ══════════════════════════════════════════════
    //  Cleanup
    // ══════════════════════════════════════════════

    public void cleanup() {
        if (pendingDiffTask != null) {
            pendingDiffTask.cancel(true);
        }
        diffExecutor.shutdownNow();
        alreadyLoadedUrls.clear();
        // ⚡ FIX 10: Don't pass null context to Ion
        // Ion.getDefault(null).cancelAll() will crash
    }
}
