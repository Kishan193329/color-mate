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
import com.colourmate.paintbynumber.model.diamond.DiamondLevel;

/**
 * The diamond palette, built on the By Number screen's own {@code color_item.xml} so both games
 * share one visual language: a circular swatch with the colour number, a ring when selected, and a
 * badge with how many beads of that colour are left.
 */
public class DiamondPaletteAdapter extends RecyclerView.Adapter<DiamondPaletteAdapter.SwatchHolder> {

    public interface OnColourSelected {
        void onColourSelected(int colourNumber);
    }

    private final DiamondLevel level;
    private final OnColourSelected listener;
    private int selected = 1;

    public DiamondPaletteAdapter(DiamondLevel level, OnColourSelected listener) {
        this.level = level;
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setSelected(int colourNumber) {
        int previous = selected;
        selected = colourNumber;
        notifyItemChanged(previous - 1);
        notifyItemChanged(selected - 1);
    }

    @Override
    public long getItemId(int position) {
        return position + 1;
    }

    @NonNull
    @Override
    public SwatchHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new SwatchHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.color_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull SwatchHolder holder, int position) {
        int number = position + 1;
        int colour = level.palette[number];
        int remaining = level.totalOf(number) - level.placedOf(number);
        boolean finished = remaining <= 0;
        boolean isSelected = number == selected;

        holder.number.setText(String.valueOf(number));
        // Same readability rule the game uses.
        holder.number.setTextColor(ColorUtils.calculateLuminance(colour) > 0.5
                ? Color.parseColor("#722776") : Color.WHITE);

        Drawable background = holder.background.getBackground();
        if (background instanceof GradientDrawable) {
            ((GradientDrawable) background.mutate()).setColor(colour);
        }

        holder.ring.setVisibility(isSelected ? View.VISIBLE : View.GONE);

        holder.badge.setVisibility(finished ? View.GONE : View.VISIBLE);
        holder.badge.setText(remaining > 99 ? "99+" : String.valueOf(remaining));

        // Finished colours fade rather than vanish, so the strip never reflows under a finger.
        holder.itemView.setAlpha(finished ? 0.35f : 1f);

        holder.itemView.setOnClickListener(v -> {
            setSelected(number);
            if (listener != null) listener.onColourSelected(number);
        });
    }

    @Override
    public int getItemCount() {
        return level == null ? 0 : level.colourCount();
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
