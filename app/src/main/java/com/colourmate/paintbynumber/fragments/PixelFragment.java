package com.colourmate.paintbynumber.fragments;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.RecyclerView;

import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.Utils.CompatUtils;
import com.colourmate.paintbynumber.Utils.ImageInfo;
import com.colourmate.paintbynumber.Utils.ImageManager;
import com.colourmate.paintbynumber.Utils.Manager;
import com.colourmate.paintbynumber.activity.PixelArtActivity;
import com.colourmate.paintbynumber.ads.MediationManager;
import com.colourmate.paintbynumber.library.TokenManager;
import com.colourmate.paintbynumber.util.AchievementManager;
import com.colourmate.paintbynumber.views.CustomGridlayoutManager;
import com.google.android.material.snackbar.Snackbar;

import pl.droidsonroids.gif.GifDrawable;

public class PixelFragment extends Fragment {

    private CustomGridlayoutManager lm;
    private RecyclerView menuRV;
    private RVAdapterImage rva = null;

    private final BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (rva != null) rva.notifyDataSetChanged();
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_pixel, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        menuRV = view.findViewById(R.id.rv_images);

        AchievementManager.recordModeTried(requireContext(), "pixel");

        int pixelGridCols = getResources().getInteger(R.integer.library_grid_columns);
        if (pixelGridCols <= 0) pixelGridCols = 2;
        lm = new CustomGridlayoutManager(requireContext(), pixelGridCols);
        lm.setOrientation(RecyclerView.VERTICAL);
        menuRV.setLayoutManager(lm);
        // The grid is match_parent, so its size cannot change with the content — same list config
        // as the other galleries (CLAUDE.md §8.6 M7).
        menuRV.setHasFixedSize(true);
        menuRV.setItemViewCacheSize(4);

        loadData();

        LocalBroadcastManager.getInstance(requireContext())
                .registerReceiver(mMessageReceiver, new IntentFilter("requestMenuReload"));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (rva != null) rva.notifyDataSetChanged();
    }

    private void loadData() {
        rva = new RVAdapterImage(this);
        menuRV.setAdapter(rva);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && getView() != null) {
            // ✅ Smooth entrance animation
            getView().setAlpha(0f);
            getView().setTranslationY(30f);
            getView().animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(400)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }
    }

    @Override
    public void onDestroyView() {
        try {
            LocalBroadcastManager.getInstance(requireContext())
                    .unregisterReceiver(mMessageReceiver);
        } catch (Exception ignored) {}
        super.onDestroyView();
    }

    public static class ImageHolder extends RecyclerView.ViewHolder {
        ImageInfo info;
        ImageView itemImage;
        ProgressBar progressBar;

        public ImageHolder(View view) {
            super(view);
            this.itemImage = view.findViewById(R.id.imageView);
            this.progressBar = view.findViewById(R.id.loading_view);
            if (itemImage != null) {
                itemImage.setClipToOutline(true);
            }
        }

        private Activity getActivity(Context context) {
            while (context instanceof ContextWrapper) {
                if (context instanceof Activity) return (Activity) context;
                context = ((ContextWrapper) context).getBaseContext();
            }
            return null;
        }
    }

    public static class RVAdapterImage extends RecyclerView.Adapter<ImageHolder> {

        private final PixelFragment fragment;
        private final LayoutInflater inflater;

        public RVAdapterImage(PixelFragment fragment) {
            this.fragment = fragment;
            this.inflater = LayoutInflater.from(fragment.requireContext());
        }

        private class ImageClickListener implements View.OnClickListener {
            private final ImageHolder holder;
            public ImageClickListener(ImageHolder holder) { this.holder = holder; }

            @Override
            public void onClick(View v) {
                int pos = holder.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;

                ImageInfo info = ImageManager.getInstance().getImageInfoList().get(pos);
                boolean exists = ImageManager.getInstance().getImageLocalExists(info.getImageID())
                        || ImageManager.getInstance().getImageLocalExists(info.getImageID(), "gif", com.bumptech.glide.Registry.BUCKET_GIF);

                if (!exists) return;

                Context ctx = v.getContext();

                if (TokenManager.getInstance().isPurchased(info.getImageID())) {
                    openPixelScreen(ctx, info.getImageID());
                } else if (Manager.getInstance().hasInternetConnection(fragment.requireActivity())) {
                    openPixelScreen(ctx, info.getImageID());
                } else {
                    Snackbar.make(v, R.string.internet_conn_failed, Snackbar.LENGTH_SHORT)
                            .setAction("Retry", vv -> notifyDataSetChanged()).show();
                }
            }

            private void openPixelScreen(Context context, String id) {
                MediationManager.showInterstitial((AppCompatActivity) fragment.requireActivity(), () -> {
                    Intent intent = new Intent(context, PixelArtActivity.class);
                    intent.putExtra(PixelArtActivity.ARG_IMAGE_ID, id);
                    context.startActivity(intent);
                });
            }
        }

        @NonNull
        @Override
        public ImageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = inflater.inflate(R.layout.row_item_image, parent, false);
            ImageHolder holder = new ImageHolder(view);

            int size = (int) (parent.getMeasuredWidth() / (fragment.lm.getSpanCount() * 1.1f));
            holder.itemView.setMinimumWidth(size);
            holder.itemView.setMinimumHeight(size);
            holder.itemImage.setMinimumWidth(size);
            holder.itemImage.setMinimumHeight(size);

            view.setOnClickListener(new ImageClickListener(holder));

            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull ImageHolder holder, int position) {
            ImageInfo info = ImageManager.getInstance().getImageInfoList().get(position);
            holder.info = info;
            holder.itemImage.setImageDrawable(null);
            holder.progressBar.setVisibility(View.VISIBLE);

            Bitmap cached = ImageManager.getInstance().getColorImage(info.getImageID());
            if (cached != null) {
                BitmapDrawable d = new BitmapDrawable(holder.itemView.getResources(), cached);
                d.getPaint().setFilterBitmap(false);
                holder.itemImage.setImageDrawable(d);
                holder.itemImage.setColorFilter(null);
                holder.progressBar.setVisibility(View.GONE);
            } else {
                loadAsyncImage(holder, info);
            }
            holder.itemView.setId(info.hashCode());
        }

        private void loadAsyncImage(ImageHolder holder, ImageInfo info) {
            ImageManager.getInstance().getImage(info.getImageID(), new ImageManager.OnImageLoadedUI() {
                @Override
                public void onFail() {}

                @Override
                public void onSuccess(Object result, boolean animate) {
                    if (!fragment.isAdded()) return;

                    int pos = holder.getAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return;

                    ImageInfo current = ImageManager.getInstance().getImageInfoList().get(pos);
                    if (!current.equals(info)) return;

                    Drawable drawable;
                    if (result instanceof Bitmap) {
                        BitmapDrawable bd = new BitmapDrawable(holder.itemView.getResources(), (Bitmap) result);
                        bd.getPaint().setFilterBitmap(false);
                        drawable = bd;
                    } else if (result instanceof GifDrawable) {
                        GifDrawable gif = (GifDrawable) result;
                        gif.getPaint().setFilterBitmap(false);
                        gif.stop();
                        drawable = gif;
                    } else {
                        drawable = null;
                    }

                    if (drawable == null) return;

                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (!fragment.isAdded()) return;
                        holder.progressBar.setVisibility(View.GONE);
                        if (animate) {
                            TransitionDrawable td = new TransitionDrawable(
                                    new Drawable[]{new ColorDrawable(Color.TRANSPARENT), drawable});
                            holder.itemImage.setImageDrawable(td);
                            td.startTransition(500);
                        } else {
                            holder.itemImage.setImageDrawable(drawable);
                        }
                        ColorMatrix cm = new ColorMatrix();
                        cm.setSaturation(0f);
                        holder.itemImage.setColorFilter(new ColorMatrixColorFilter(cm));
                    });
                }
            });
        }

        @Override
        public int getItemCount() {
            return ImageManager.getInstance().getImageInfoList().size();
        }
    }
}
