package com.colourmate.paintbynumber.adapter;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.model.game.ColorItem;
import com.colourmate.paintbynumber.util.GameSaver;
import com.mikhaellopez.circularprogressbar.CircularProgressBar;

import java.util.ArrayList;
import java.util.List;

public class ColorListAdapter extends RecyclerView.Adapter<ColorListAdapter.ColorViewHolder> {
    private ColorClickListener colorClickListener;
    private List<ColorItem> colors;
    private int selectedColor = 0;

    public interface ColorClickListener {
        void onColorClicked(ColorItem colorItem);
    }

    public static class ColorViewHolder extends RecyclerView.ViewHolder {
        ImageView colorBackground;
        private final ColorClickListener colorClickListener;
        View colorContainer;
        View colorSelectedRing;
        public ColorItem colorItem;
        TextView colorNumber;
        CircularProgressBar colorProgress;
        TextView colorBadge;

        public ColorViewHolder(@NonNull View view, ColorClickListener colorClickListener2) {
            super(view);
            this.colorBackground = view.findViewById(R.id.colorBackground);
            this.colorContainer = view.findViewById(R.id.colorContainer);
            this.colorSelectedRing = view.findViewById(R.id.colorSelectedRing);
            this.colorNumber = view.findViewById(R.id.colorNumber);
            this.colorProgress = view.findViewById(R.id.colorProgress);
            this.colorBadge = view.findViewById(R.id.colorBadge);
            this.colorClickListener = colorClickListener2;

            view.setOnClickListener(v -> {
                if (colorItem != null && !colorItem.isFinished()) {
                    colorClickListener.onColorClicked(colorItem);
                }
            });
        }

        public void playFinishAnimation(Runnable onEnd) {
            if (colorProgress != null) colorProgress.setVisibility(View.GONE);
            if (colorSelectedRing != null) colorSelectedRing.setVisibility(View.GONE);
            if (colorBadge != null) colorBadge.setVisibility(View.GONE);

            colorNumber.setVisibility(View.GONE);
            colorBackground.setImageResource(R.drawable.ic_check_mark);
            setBackgroundColor(colorItem.getColor());

            Animation animation = AnimationUtils.loadAnimation(
                    itemView.getContext(),
                    R.anim.zoomout
            );

            animation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    if (onEnd != null) onEnd.run();
                }
            });

            itemView.startAnimation(animation);
        }


        public void setColor(int position, int selectedColor, @NonNull ColorItem item) {
            this.colorItem = item;

            // Set color number text
            colorNumber.setText(String.valueOf(item.getNumber() + 1));

            // Set readable text color based on luminance
            colorNumber.setTextColor(
                    ColorUtils.calculateLuminance(item.getColor()) > 0.5
                            ? Color.parseColor("#722776")
                            : Color.WHITE
            );

            // Apply background color safely
            Drawable background = colorBackground.getBackground();
            if (background instanceof GradientDrawable) {
                ((GradientDrawable) background).setColor(item.getColor());
            }

            // Update UI state (selected / finished / default)
            updateSelectedColor(selectedColor);
        }


        public void updateProgress(int progress) {
            if (colorItem == null || colorItem.isFinished()) return;

            // Update circular progress ring
            if (colorProgress != null) {
                boolean enabled = GameSaver.isColorProgressEnabled(itemView.getContext());
                colorProgress.setVisibility(enabled ? View.VISIBLE : View.GONE);
                if (enabled) {
                    colorProgress.setProgress(progress);
                }
            }

            // Update badge remaining count
            if (colorBadge != null && colorBadge.getVisibility() == View.VISIBLE) {
                int remaining = colorItem.getTotalAreas() - colorItem.getCompletedAreas();
                colorBadge.setText(String.valueOf(Math.max(0, remaining)));
            }
        }

        private void setBackgroundColor(int color) {
            Drawable bg = colorBackground.getBackground();
            if (bg instanceof GradientDrawable) {
                ((GradientDrawable) bg).setColor(color);
            }
        }

        public void updateSelectedColor(int selectedColor) {
            if (colorItem.isFinished()) {
                colorNumber.setVisibility(View.GONE);
                colorBackground.setImageResource(R.drawable.ic_check_mark);
                colorProgress.setVisibility(View.GONE);
                colorSelectedRing.setVisibility(View.GONE);
                if (colorBadge != null) colorBadge.setVisibility(View.GONE);
                setBackgroundColor(colorItem.getColor());
                itemView.setScaleX(0.85f);
                itemView.setScaleY(0.85f);
                itemView.setElevation(0f);
                itemView.setAlpha(0.6f);
                return;
            }

            colorBackground.setImageDrawable(null);
            colorNumber.setVisibility(View.VISIBLE);
            itemView.setAlpha(1f);

            boolean isSelected = selectedColor == colorItem.getColor();
            boolean progressEnabled = GameSaver.isColorProgressEnabled(itemView.getContext());

            if (isSelected) {
                colorSelectedRing.setVisibility(View.GONE);
                itemView.animate()
                        .scaleX(1.12f).scaleY(1.12f)
                        .setDuration(200)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(2f))
                        .start();
                itemView.setElevation(12f);

                if (progressEnabled) {
                    colorProgress.setVisibility(View.VISIBLE);
                    colorProgress.setProgress(colorItem.getProgress());
                } else {
                    colorProgress.setVisibility(View.GONE);
                }

                // Show badge with remaining count
                if (colorBadge != null) {
                    int remaining = colorItem.getTotalAreas() - colorItem.getCompletedAreas();
                    colorBadge.setText(String.valueOf(remaining));
                    colorBadge.setVisibility(View.VISIBLE);
                }
            } else {
                colorSelectedRing.setVisibility(View.GONE);
                itemView.animate()
                        .scaleX(1.0f).scaleY(1.0f)
                        .setDuration(150)
                        .start();
                itemView.setElevation(2f);
                colorProgress.setVisibility(View.GONE);
                if (colorBadge != null) colorBadge.setVisibility(View.GONE);
            }

            setBackgroundColor(colorItem.getColor());
        }

    }

    public void removeColor(int color) {
        int pos = getPositionOfColor(color);
        if (pos != -1) {
            colors.remove(pos);
            notifyItemRemoved(pos);
        }
    }

    public ColorListAdapter(@NonNull Context context, @NonNull List<ColorItem> colors) {
        this.colors = colors;

        DisplayMetrics dm = new DisplayMetrics();
        WindowManager windowManager =
                (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        if (windowManager != null) {
            windowManager.getDefaultDisplay().getMetrics(dm);
        }

    }


    public void setColorClickListener(ColorClickListener colorClickListener2) {
        this.colorClickListener = colorClickListener2;
    }

    @Override
    @NonNull
    public ColorViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
        return new ColorViewHolder(LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.color_item, viewGroup, false), this.colorClickListener);
    }

    public void onBindViewHolder(@NonNull ColorViewHolder colorViewHolder, int position) {
        colorViewHolder.setColor(position, this.selectedColor, this.colors.get(position));
    }

    public void setSelectedColor(int selectedColor) {
        this.selectedColor = selectedColor;
    }

    @Override
    public int getItemCount() {
        return this.colors.size();
    }

    public int getNextUnfinishedColor() {
        for (ColorItem colorItem : this.colors) {
            if (!colorItem.isFinished()) {
                return colorItem.getColor();
            }
        }
        return -1; // Return -1 if all colors are finished
    }

    // Update the finishColor method - don't move item here, let animation handle it
    public void finishColor(int targetColor) {
        for (ColorItem colorItem : this.colors) {
            if (colorItem.getColor() == targetColor) {
                colorItem.setFinished(true);
                Log.d("ColorAdapter", "Marked color as finished: " + targetColor);
                break;
            }
        }
    }

    // Add this helper method to ColorAdapter class:
    public ColorItem getColorItemByColor(int color) {
        for (ColorItem item : this.colors) {
            if (item.getColor() == color) {
                return item;
            }
        }
        return null;
    }

    // Add this method to check if a specific color is finished
    public int getPositionOfColor(int color) {
        for (int i = 0; i < this.colors.size(); i++) {
            if (this.colors.get(i).getColor() == color) {
                return i;
            }
        }
        return -1;
    }

    public void setList(List<ColorItem> list) {
        this.colors = list;
        notifyDataSetChanged();
    }


    public List<ColorItem> checkAllColor(List<ColorItem> list) {
        ArrayList<ColorItem> arrayList = new ArrayList<>();
        for (ColorItem colorItem : list) {
            if (colorItem.isFinished()) {
                arrayList.add(colorItem);
            }
        }
        list.removeAll(arrayList);
        return list;
    }

    public boolean isGameFinished() {
        for (ColorItem colorItem : this.colors) {
            if (!colorItem.isFinished()) {
                return false;
            }
        }
        return true;
    }
}
