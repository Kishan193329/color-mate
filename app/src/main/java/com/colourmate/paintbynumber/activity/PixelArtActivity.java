package com.colourmate.paintbynumber.activity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.colourmate.paintbynumber.util.LogUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.colourmate.paintbynumber.GlobalApplication;
import com.colourmate.paintbynumber.OpenGL.OnPaintingEventListener;
import com.colourmate.paintbynumber.OpenGL.PaintingView;
import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.Utils.CompatUtils;
import com.colourmate.paintbynumber.Utils.ImageManager;
import com.colourmate.paintbynumber.ads.AdController;
import com.colourmate.paintbynumber.ads.AppPreference;
import com.colourmate.paintbynumber.ads.Keys;
import com.colourmate.paintbynumber.ads.MediationManager;
import com.colourmate.paintbynumber.ads.RewardedAdListener;
import com.colourmate.paintbynumber.databinding.ActivityPixelsByNumberBinding;
import com.colourmate.paintbynumber.views.WrapContentLinearLayoutManager;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import pl.droidsonroids.gif.GifDrawable;

public class PixelArtActivity extends AppCompatActivity {

    public static final String ARG_IMAGE_ID = "imageId";

    private ActivityPixelsByNumberBinding binding;
    private boolean autoOpenedShare = false;
    private String imageID;

    private PaintingView paintingView;

    private RecyclerView rv;
    private RVAdapterColors rva;

    private int strokeWidthPx;

    // 0 = eraser, 1..N = groups
    private int selectedGroup = 1;

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("imageID", imageID);
        out.putInt("selectedGroup", selectedGroup);
        out.putBoolean("autoOpenedShare", autoOpenedShare);
    }

    public static class ColorHolder extends RecyclerView.ViewHolder {
        View colorHolder;
        ImageView imageCheck;
        TextView textViewNumber;

        public ColorHolder(@NonNull View view) {
            super(view);
            this.textViewNumber = view.findViewById(R.id.txt_textViewNumber);
            this.colorHolder = view.findViewById(R.id.view_colorHolder);
            this.imageCheck = view.findViewById(R.id.iv_imageCheck);
        }
    }

    public class RVAdapterColors extends RecyclerView.Adapter<ColorHolder> {

        private final List<Integer> colorList = new ArrayList<>();

        public void submitColors(@NonNull LinkedHashSet<Integer> set) {
            colorList.clear();
            colorList.addAll(set); 

            if (!colorList.isEmpty()) {
                colorList.remove(0);
            }
            notifyDataSetChanged();
        }

        private class ColorOnClickListener implements View.OnClickListener {
            @Override
            public void onClick(View view) {
                int childLayoutPosition = rv.getChildLayoutPosition(view);
                ColorHolder colorHolder = (ColorHolder) rv.findViewHolderForAdapterPosition(selectedGroup - 1);
                if (colorHolder != null && colorHolder.itemView.getBackground() instanceof GradientDrawable) {
                    ((GradientDrawable) colorHolder.itemView.getBackground()).setStroke(strokeWidthPx, 0);
                }
                int i = childLayoutPosition + 1;
                selectedGroup = i;
                if (paintingView != null) paintingView.setSelectedGroup(i);
                
                ColorHolder colorHolder2 = (ColorHolder) rv.findViewHolderForAdapterPosition(selectedGroup - 1);
                if (colorHolder2 != null && colorHolder2.itemView.getBackground() instanceof GradientDrawable) {
                    ((GradientDrawable) colorHolder2.itemView.getBackground()).setStroke(strokeWidthPx, 0xFFCCCCCC);
                }
                
                if (binding.flImageViewEraserHolder.getBackground() instanceof GradientDrawable) {
                    ((GradientDrawable) binding.flImageViewEraserHolder.getBackground()).setStroke(strokeWidthPx, 0);
                }
                if (paintingView != null) paintingView.autoZoomIfNeeded();
            }
        }

        @NonNull
        @Override
        public ColorHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.row_item_color, parent, false);
            view.setOnClickListener(new ColorOnClickListener());
            return new ColorHolder(view);
        }

        public void onBindViewHolder(ColorHolder holder, int position) {
            int visibilityState = 0;
            int colorIndex = position + 1;

            if (holder.itemView.getBackground() instanceof GradientDrawable) {
                GradientDrawable gradientDrawable = (GradientDrawable) holder.itemView.getBackground();
                gradientDrawable.setStroke(strokeWidthPx, position == selectedGroup - 1 ? 0xFFCCCCCC : 0);
                gradientDrawable.setColor(0);
            }

            if (holder.colorHolder.getBackground() instanceof GradientDrawable) {
                GradientDrawable colorSwatchBackground = (GradientDrawable) holder.colorHolder.getBackground();
                colorSwatchBackground.setColor(((Integer) paintingView.colorSet().toArray()[colorIndex]).intValue());
                colorSwatchBackground.setStroke(Math.max(1, strokeWidthPx / 2), 0xFFCCCCCC);
            }
            holder.textViewNumber.setText("" + colorIndex);
            if (!paintingView.checkCompleted(colorIndex)) {
                visibilityState = 8;
            }
            holder.imageCheck.setVisibility(visibilityState);
        }

        @Override
        public int getItemCount() {
            if (paintingView == null) return 0;
            return paintingView.colorSet().size() - 1;
        }
    }

    private OnBackPressedCallback onBackPressedCallback;

    private void setupBackPressHandler() {
        onBackPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                onBackClicked();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, onBackPressedCallback);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        GlobalApplication.lockPortrait(this);
        overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left);
        
        binding = ActivityPixelsByNumberBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        // Same chrome as the reveal/diamond screens: the canvas owns the screen, the status bar
        // is hidden, glass controls float over the art (design pass 2026-10-14).
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        androidx.core.view.WindowInsetsControllerCompat insets =
                androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insets.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars());
        insets.setSystemBarsBehavior(
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        insets.setAppearanceLightNavigationBars(true);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.llAds, (v, wi) -> {
            androidx.core.graphics.Insets nav = wi.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), nav.bottom);
            return wi;
        });

        strokeWidthPx = (int) getResources().getDimension(R.dimen.color_row_stroke_width);

        if (AdController.shouldShowAds(this)) {
            MediationManager.getBannerAd(this);
        }

        if (savedInstanceState != null) {
            imageID = savedInstanceState.getString("imageID", null);
            selectedGroup = savedInstanceState.getInt("selectedGroup", 1);
            autoOpenedShare = savedInstanceState.getBoolean("autoOpenedShare", false);
        }

        if (imageID == null && getIntent() != null && getIntent().hasExtra(ARG_IMAGE_ID)) {
            imageID = getIntent().getStringExtra(ARG_IMAGE_ID);
        }

        initViews();
        loadPreviewAndPainting();
    }

    private void initViews() {
        rv = binding.rvColors;
        setupBackPressHandler();

        WrapContentLinearLayoutManager llm = new WrapContentLinearLayoutManager(this);
        llm.setOrientation(LinearLayoutManager.HORIZONTAL);
        rv.setLayoutManager(llm);

        rva = new RVAdapterColors();
        rv.setAdapter(rva);

        if (binding.flImageViewEraserHolder.getBackground() instanceof GradientDrawable) {
            GradientDrawable gd = (GradientDrawable) binding.flImageViewEraserHolder.getBackground();
            gd.setStroke(strokeWidthPx, 0);
            gd.setColor(0);
        }

        binding.flImageViewShareHolder.setVisibility(View.GONE);
        binding.flImageViewShareRipple.setOnClickListener(v -> exportAndShare());
        binding.ivImageViewBack.setOnClickListener(v -> onBackClicked());

        binding.flImageViewEraserRipple.setOnClickListener(v -> {
            selectedGroup = 0;
            if (paintingView != null) {
                paintingView.setSelectedGroup(selectedGroup);
                paintingView.autoZoomIfNeeded();
            }
            if (binding.flImageViewEraserHolder.getBackground() instanceof GradientDrawable) {
                ((GradientDrawable) binding.flImageViewEraserHolder.getBackground()).setStroke(strokeWidthPx, 0xFFCCCCCC);
            }
            rv.post(() -> rva.notifyDataSetChanged());
        });

        boolean isPremium = AppPreference.getBoolean(this, Keys.KEY_IS_PREMIUM, false);
        // Always show the Magic Wand tool, non-premium will use rewarded ads
        binding.flImageViewMagicWandHolder.setVisibility(View.VISIBLE);

        if (isPremium || !AdController.shouldShowAds(this)) {
            binding.llAds.setVisibility(View.GONE);
        } else {
            binding.llAds.setVisibility(View.VISIBLE);
        }

        binding.flImageViewMagicWandRipple.setOnClickListener(v -> {
            if (selectedGroup == 0) {
                Toast.makeText(this, R.string.select_color_first, Toast.LENGTH_SHORT).show();
                return;
            }
            if (paintingView != null && paintingView.checkCompleted(selectedGroup)) {
                Toast.makeText(this, R.string.color_already_complete, Toast.LENGTH_SHORT).show();
                return;
            }

            if (isPremium) {
                if (paintingView != null) {
                    paintingView.autoPaintGroup(selectedGroup);
                    Toast.makeText(this, R.string.magic_wand_activated, Toast.LENGTH_SHORT).show();
                }
            } else {
                MediationManager.showRewardedAd(this, new RewardedAdListener() {
                    @Override
                    public void onRewardGranted() {
                        if (paintingView != null) {
                            paintingView.autoPaintGroup(selectedGroup);
                            Toast.makeText(PixelArtActivity.this, R.string.magic_wand_activated, Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override public void onRewardNull() {}
                    @Override public void onRewardProgress() {}
                    @Override public void onVideoSkipped() {}
                });
            }
        });
    }

    private void loadPreviewAndPainting() {
        new Thread(() -> {
            Bitmap previewBitmap = null;
            Bitmap colorImage = null;
            GifDrawable gifLocal = null;

            try {
                ImageManager im = ImageManager.getInstance();
                colorImage = im.getColorImage(imageID);
                gifLocal = im.getGifLocal(imageID);
            } catch (Throwable t) {
                LogUtils.e("PixelsByNumber", "Error obtaining images", t);
            }

            if (gifLocal != null) {
                previewBitmap = gifLocal.getCurrentFrame();
            } else if (colorImage != null) {
                previewBitmap = colorImage;
            } else {
                try {
                    previewBitmap = ImageManager.getInstance().getImageLocal(imageID);
                } catch (Exception e) {
                    LogUtils.w("PixelsByNumber", "Failed to load local image"+ e);
                }
            }

            if (previewBitmap == null) {
                previewBitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
            }

            final Bitmap finalPreview = previewBitmap;
            final Bitmap finalColor = colorImage;

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    if (finalPreview != null) finalPreview.recycle();
                    return;
                }

                BitmapDrawable previewDrawable = new BitmapDrawable(getResources(),
                        (finalColor != null) ? finalColor : finalPreview);
                previewDrawable.getPaint().setFilterBitmap(false);
                binding.ivImageViewPreview.setImageDrawable(previewDrawable);

                if (finalColor == null) {
                    ColorMatrix cm = new ColorMatrix();
                    cm.setSaturation(0f);
                    binding.ivImageViewPreview.setColorFilter(new ColorMatrixColorFilter(cm));
                } else {
                    binding.ivImageViewPreview.clearColorFilter();
                }
                
                loadPaintingView(finalPreview);
            });
        }).start();
    }

    private void exportAndShare() {
        if (paintingView == null) return;

        paintingView.requestExport(true, true, true, (bmpTimelapse, bmpProgress, bmpFinal) -> {
            if (bmpFinal != null) ImageManager.getInstance().saveColorImage(bmpFinal, imageID);
            if (bmpTimelapse != null) ImageManager.getInstance().saveTimelapse(bmpTimelapse, imageID);

            runOnUiThread(() -> {
                MediationManager.showBackInterstitial(this, () -> {
                    Intent intent = new Intent(PixelArtActivity.this, ShareImageActivity.class);
                    intent.putExtra(PixelArtActivity.ARG_IMAGE_ID, imageID);
                    startActivity(intent);
                });
            });
        });
    }

    private void loadPaintingView(final Bitmap bitmap) {
        new Handler(Looper.getMainLooper()).post(() -> {
            paintingView = new PaintingView(PixelArtActivity.this, bitmap, imageID, new OnPaintingEventListener() {
                @Override
                public void onUserPaintedRegion(int i, int i2, boolean showHint, boolean showMistake) {
                    paintingView.requestExport(showHint, true, showMistake, (bmpTimelapse, bmpProgress, bmpFinal) -> {
                        if (bmpTimelapse != null) ImageManager.getInstance().saveTimelapse(bmpTimelapse, imageID);
                        if (bmpProgress != null) ImageManager.getInstance().saveProgressImage(bmpProgress, imageID);
                        if (bmpFinal != null) ImageManager.getInstance().saveColorImage(bmpFinal, imageID);
                    });
                }

                @Override
                public void onReadyToPaint() {
                    paintingView.setSelectedGroup(selectedGroup);
                    LinkedHashSet<Integer> set = paintingView.colorSet();
                    if (set != null) rva.submitColors(set);

                    Animation fadeOut = AnimationUtils.loadAnimation(PixelArtActivity.this, R.anim.fadeout);
                    fadeOut.setAnimationListener(new Animation.AnimationListener() {
                        @Override public void onAnimationStart(Animation animation) {}
                        @Override public void onAnimationRepeat(Animation animation) {}
                        @Override
                        public void onAnimationEnd(Animation animation) {
                            binding.ivImageViewPreview.setVisibility(View.GONE);
                            paintingView.invalidateAutoZoom();
                        }
                    });
                    binding.ivImageViewPreview.startAnimation(fadeOut);

                    runOnUiThread(() -> {
                        if (paintingView.checkCompletedAll()) {
                            binding.flImageViewShareHolder.setVisibility(View.VISIBLE);
                            autoOpenedShare = true;
                        } else {
                            binding.flImageViewShareHolder.setVisibility(View.GONE);
                        }
                    });
                }

                @Override
                public void onLongPressDisable() {
                    runOnUiThread(() -> binding.ivImageViewAim.setVisibility(View.GONE));
                }

                @Override
                public void onLongPressEnable(RectF rectF) {
                    runOnUiThread(() -> {
                        binding.ivImageViewAim.setVisibility(View.VISIBLE);
                        binding.ivImageViewAim.setX(rectF.left - binding.ivImageViewAim.getWidth() / 2f);
                        binding.ivImageViewAim.setY(rectF.top - binding.ivImageViewAim.getHeight() / 2f);
                    });
                }

                @Override
                public void onNeedsToUpdateColors() {
                    runOnUiThread(() -> rva.notifyDataSetChanged());
                    runOnUiThread(() -> {
                        if (paintingView.checkCompletedAll()) {
                            if (binding.flImageViewShareHolder.getVisibility() != View.VISIBLE) {
                                showCelebrationUI();
                            }
                            binding.flImageViewShareHolder.setVisibility(View.VISIBLE);
                            autoOpenShare();
                        } else {
                            binding.flImageViewShareHolder.setVisibility(View.GONE);
                        }
                    });
                }

                private void showCelebrationUI() {
                    binding.flImageViewShareHolder.setScaleX(0f);
                    binding.flImageViewShareHolder.setScaleY(0f);
                    binding.flImageViewShareHolder.animate()
                            .scaleX(1.1f).scaleY(1.1f)
                            .setDuration(400)
                            .setInterpolator(new android.view.animation.OvershootInterpolator())
                            .withEndAction(() -> binding.flImageViewShareHolder.animate().scaleX(1f).scaleY(1f).setDuration(200).start())
                            .start();
                    Toast.makeText(PixelArtActivity.this, R.string.masterpiece_completed_toast, Toast.LENGTH_LONG).show();
                }

                @Override
                public void goToShare() {
                    exportAndShare();
                }
            });

            ViewGroup container = binding.flPaintingContainer;
            if (container != null) {
                container.removeAllViews();
                container.addView(paintingView);
            }
        });
    }

    public void autoOpenShare() {
        if (!autoOpenedShare) {
            autoOpenedShare = true;
            if (paintingView != null) paintingView.autoZoomOut();
        }
    }

    public void onBackClicked() {
        if (paintingView == null) {
            onBackPressedCallback.setEnabled(false);
            getOnBackPressedDispatcher().onBackPressed();
            return;
        }
        MediationManager.showBackInterstitial(this, this::exitWithAd);
    }

    public void exitWithAd() {
        paintingView.requestExport(false, false, true, (bmpBase, bmpPaint, bmpExport) -> {
            if (bmpExport != null) ImageManager.getInstance().saveColorImage(bmpExport, imageID);
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("requestMenuReload"));
            runOnUiThread(this::finish);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (paintingView != null) paintingView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (paintingView != null) paintingView.onPause();
    }
}
