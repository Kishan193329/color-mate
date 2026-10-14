package com.colourmate.paintbynumber.adapter;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.model.reveal.RevealLevel;

/**
 * The photo-reveal palette, built on the By Number screen's own {@code color_item.xml} so both games
 * share one visual language: a circular swatch with the colour number, a ring when selected, and a
 * badge with how many beads of that colour are left.
 */
public class RevealPaletteAdapter extends RecyclerView.Adapter<RevealPaletteAdapter.SwatchHolder> {

    public interface OnNumberSelected {
        void onNumberSelected(int colourNumber);
    }

    private static final int SWATCH_PX = 96;

    /**
     * Only the numbers that actually have regions. The palette in regions.json is sparse — it can
     * run 1..100 with gaps — and a chip for an empty number looked tappable but could never reveal
     * anything, which read as "the reveal is broken".
     */
    private int[] numbers;

    private final RevealLevel level;
    private final OnNumberSelected listener;
    private int selected = 1;

    public RevealPaletteAdapter(RevealLevel level, OnNumberSelected listener) {
        this.level = level;
        this.listener = listener;

        rebuild();
        setHasStableIds(true);
    }

    /**
     * The numbers still worth showing: those that have regions AND are not finished.
     *
     * <p>A colour the player has completed is dead weight in the strip — it cannot be tapped to any
     * effect and it pushes the remaining ones off screen. Dropping it also makes the strip a
     * progress bar in its own right: it empties as the picture fills.
     *
     * <p>Called again from {@link #refreshCounts()} whenever a region is revealed.
     */
    private void rebuild() {
        int count = 0;
        int[] present = new int[level.numberCount() + 1];
        for (int n = 1; n <= level.numberCount(); n++) {
            if (level.totalOf(n) > 0 && level.revealedOf(n) < level.totalOf(n)) present[count++] = n;
        }
        numbers = java.util.Arrays.copyOf(present, count);
    }

    /**
     * A region was revealed: update the badges, and drop the colour from the strip if that was its
     * last region. Returns true when the list itself changed, so the caller can keep the selection
     * and the scroll position sensible.
     */
    public boolean refreshCounts() {
        int before = numbers.length;
        rebuild();
        notifyDataSetChanged();
        return numbers.length != before;
    }

    public void setSelected(int colourNumber) {
        int previous = positionOf(selected);
        selected = colourNumber;
        if (previous >= 0) notifyItemChanged(previous);
        int now = positionOf(selected);
        if (now >= 0) notifyItemChanged(now);
    }

    /** Position of a number in the strip, or -1 if it has no regions. */
    public int positionOf(int number) {
        for (int i = 0; i < numbers.length; i++) {
            if (numbers[i] == number) return i;
        }
        return -1;
    }

    @Override
    public long getItemId(int position) {
        return numbers[position];
    }

    @NonNull
    @Override
    public SwatchHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new SwatchHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.color_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull SwatchHolder holder, int position) {
        int number = numbers[position];
        int colour = level.palette[number];
        int remaining = level.totalOf(number) - level.revealedOf(number);
        boolean finished = remaining <= 0;
        boolean isSelected = number == selected;

        holder.number.setText(String.valueOf(number));
        // Same readability rule the game uses.
        holder.number.setTextColor(ColorUtils.calculateLuminance(colour) > 0.5
                ? Color.parseColor("#722776") : Color.WHITE);

        // Show a real crop of the photo, like the web build's swatches; fall back to the flat
        // colour if the crop could not be made.
        android.graphics.Bitmap swatch = level.swatch(number, SWATCH_PX);
        Drawable background = holder.background.getBackground();
        if (background instanceof GradientDrawable) {
            ((GradientDrawable) background.mutate()).setColor(colour);
        }
        if (swatch != null) {
            holder.background.setImageDrawable(circular(holder, swatch));
        } else {
            holder.background.setImageDrawable(null);
        }

        holder.ring.setVisibility(isSelected ? View.VISIBLE : View.GONE);

        holder.badge.setVisibility(finished ? View.GONE : View.VISIBLE);
        holder.badge.setText(remaining > 99 ? "99+" : String.valueOf(remaining));

        // Finished colours fade rather than vanish, so the strip never reflows under a finger.
        holder.itemView.setAlpha(finished ? 0.35f : 1f);

        holder.itemView.setOnClickListener(v -> {
            setSelected(number);
            if (listener != null) listener.onNumberSelected(number);
        });
    }

    @Override
    public int getItemCount() {
        return numbers.length;
    }

    private static androidx.core.graphics.drawable.RoundedBitmapDrawable circular(
            SwatchHolder holder, android.graphics.Bitmap bitmap) {
        androidx.core.graphics.drawable.RoundedBitmapDrawable drawable =
                androidx.core.graphics.drawable.RoundedBitmapDrawableFactory.create(
                        holder.itemView.getResources(), bitmap);
        drawable.setCircular(true);
        return drawable;
    }

    static class SwatchHolder extends RecyclerView.ViewHolder {
        final ImageView background;
        final TextView number;
        final TextView badge;
        final View ring;

        SwatchHolder(@NonNull View itemView) {
            super(itemView);
            background = itemView.findViewById(R.id.colorBackground);
            number = itemView.findViewById(R.id.colorNumber);
            badge = itemView.findViewById(R.id.colorBadge);
            ring = itemView.findViewById(R.id.colorSelectedRing);
        }
    }
}
