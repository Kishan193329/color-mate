package com.colourmate.paintbynumber.util;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.colourmate.paintbynumber.R;

import java.util.ArrayList;

/**
 * Utility class for selecting effects with a RecyclerView adapter.
 */
public class EffectSelector {

    public interface EffectClickListener {
        void onEffectClicked(int position, EffectItem effectItem);
    }

    /**
     * RecyclerView adapter for effect items.
     */
    public static class EffectAdapter extends RecyclerView.Adapter<EffectAdapter.ViewHolder> {

        private final ArrayList<EffectItem> effectList;
        private final EffectClickListener clickListener;
        private final int layoutResource;

        /**
         * ViewHolder for an effect item.
         */
        public class ViewHolder extends RecyclerView.ViewHolder {

            public final View container;

            public ViewHolder(View itemView) {
                super(itemView);
                container = itemView.findViewById(R.id.fl_effect_item_container);
                View.OnClickListener itemClickListener = v -> {
                    int position = getAdapterPosition();
                    if (position >= 0 && clickListener != null) {
                        clickListener.onEffectClicked(position, getItem(position));
                    }
                };
                container.setOnClickListener(itemClickListener);
            }
        }

        public EffectAdapter(ArrayList<EffectItem> effectList, int layoutResource, EffectClickListener listener) {
            this.effectList = effectList;
            this.layoutResource = layoutResource;
            this.clickListener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(layoutResource, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            EffectItem item = effectList.get(position);

            ImageView imageView = holder.container.findViewById(R.id.iv_item_image);
            View selectionIndicator = holder.container.findViewById(R.id.v_effect_sel);
            TextView textView = holder.container.findViewById(R.id.tv_effect_item_text);

            // Set text
            textView.setText(item.mText);

            // Set background image or gradient
            if (imageView != null) {
                imageView.setImageResource(item.mBackground);
            } else {
                GradientDrawable gradient = new GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{
                                UIHelpers.lighterColor(item.mBackground, 15) | ViewCompat.MEASURED_STATE_MASK,
                                UIHelpers.darkerColor(item.mBackground, 15) | ViewCompat.MEASURED_STATE_MASK
                        });
                holder.container.setBackground(gradient);
            }

            // Selection indicator
            selectionIndicator.setVisibility(item.mSelected ? View.VISIBLE : View.GONE);

        }

        @Override
        public int getItemCount() {
            return effectList.size();
        }

        /**
         * Returns true if the item at position is selected.
         */
        public boolean isSelected(int position) {
            return position >= 0 && position < effectList.size() && effectList.get(position).mSelected;
        }

        /**
         * Selects a single item and deselects all others.
         */
        public void selectItem(int position) {
            if (position >= 0 && position < effectList.size()) {
                for (int i = 0; i < effectList.size(); i++) {
                    EffectItem item = effectList.get(i);
                    if (item.mSelected) {
                        item.mSelected = false;
                        notifyItemChanged(i);
                    }
                }
                effectList.get(position).mSelected = true;
                notifyItemChanged(position);
            }
        }

        /**
         * Returns the currently selected item, or null if none.
         */
        public EffectItem getSelectedItem() {
            for (EffectItem item : effectList) {
                if (item.mSelected) return item;
            }
            return null;
        }

        /**
         * Returns the index of the currently selected item, or -1 if none.
         */
        public int getSelectedItemIndex() {
            return effectList.indexOf(getSelectedItem());
        }

        /**
         * Returns the EffectItem at the given position.
         */
        public EffectItem getItem(int position) {
            return effectList.get(position);
        }
    }

    /**
     * Model class representing an effect item.
     */
    public static class EffectItem {
        public String mText;
        public int mBackground;
        public boolean mSelected = false;
        public Object mUserData;

        public EffectItem(String text, int background, Object userData) {
            this.mText = text;
            this.mBackground = background;
            this.mUserData = userData;
        }
    }
}
