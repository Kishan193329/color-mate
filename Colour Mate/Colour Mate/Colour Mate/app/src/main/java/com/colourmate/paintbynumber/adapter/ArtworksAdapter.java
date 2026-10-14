package com.colourmate.paintbynumber.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.fragments.DrawingDialogFragment;
import com.colourmate.paintbynumber.library.Library;
import com.koushikdutta.ion.Ion;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ArtworksAdapter extends RecyclerView.Adapter<ArtworksAdapter.ViewHolder> {

    public static final String TAG = "ColoredPictureViewAdapter";

    private final OnPictureClicked clickListener;
    private List<Library.ColoredPicture> items = new ArrayList<>();
    private boolean selectMode = false;

    // -----------------------------------------------------
    // Listener
    // -----------------------------------------------------
    public interface OnPictureClicked {
        void onPictureClicked(Library.ColoredPicture picture);
    }

    // -----------------------------------------------------
    // Constructor
    // -----------------------------------------------------
    public ArtworksAdapter(OnPictureClicked listener) {
        this.clickListener = listener;

        Library.getInstance().ensureCacheLoaded();
        refresh();
    }


    public void refresh() {
        Library.getInstance().ensureCacheLoaded(); // ✅ important for first time

        List<Library.ColoredPicture> newList = Library.getInstance().getColoredPictures();
        this.items = (newList != null) ? newList : new ArrayList<>();

        notifyDataSetChanged();
    }

    public void refreshAsync() {
        Library.getInstance().ensureCacheLoadedAsync(this::refresh);
    }


    public void setSelectMode(boolean mode) {
        this.selectMode = mode;
        notifyDataSetChanged();
    }
    // -----------------------------------------------------
    // ViewHolder
    // -----------------------------------------------------
    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        final View root;
        final ImageView ivImage;
        final ImageView ivBadge;

        ViewHolder(View itemView) {
            super(itemView);

            root = itemView.findViewById(R.id.rl_book_item_container);
            ivImage = itemView.findViewById(R.id.iv_book_item_image);
            ivBadge = itemView.findViewById(R.id.iv_book_item_badge);

            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            int pos = getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;

            Library.ColoredPicture pic = items.get(pos);
            if (pic == null) return;

            if (selectMode) {
                onSelectItem(pos, pic);
            } else {
                onEditItem(v, pos, pic);
            }
        }
    }

    // -----------------------------------------------------
    // Adapter required overrides
    // -----------------------------------------------------
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.frag_bookitem, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder h, int position) {

        Library.ColoredPicture pic = items.get(position);

        h.ivBadge.setVisibility(View.GONE);
        h.ivImage.setImageDrawable(null);

        File thumbFile = pic.getThumbFile();

        // Load thumb (local or remote)
        if (thumbFile != null && thumbFile.exists()) {

            // Local cached file
            Ion.with(h.ivImage.getContext())
                    .load(thumbFile)
                    .noCache()
                    .withBitmap()
                    .intoImageView(h.ivImage);

        } else {

            // Remote URL
            Ion.with(h.ivImage.getContext())
                    .load(pic.getLibraryItem().thumbUrl)
                    .group("library")
                    .intoImageView(h.ivImage);
        }

        // Selection UI
        if (selectMode) {
            int bgColor = pic.mSelected ? 0xFF0099FF : Color.TRANSPARENT;
            h.root.setBackgroundColor(bgColor);
        }
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    // -----------------------------------------------------
    // Helper functions
    // -----------------------------------------------------
    public Library.ColoredPicture getSelectedItem() {
        for (Library.ColoredPicture p : items) {
            if (p.mSelected) return p;
        }
        return null;
    }

    // -----------------------------------------------------
    // Edit item (open drawing or callback)
    // -----------------------------------------------------
    private void onEditItem(View view, int pos, Library.ColoredPicture pic) {

        if (pic == null) return;

        // If file exists → open drawing
        if (pic.getThumbFile() != null && pic.getThumbFile().exists()) {
            DrawingDialogFragment.show(
                    (AppCompatActivity) view.getContext(),
                    pic,
                    false
            );
            return;
        }

        // Otherwise callback to parent
        if (clickListener != null) clickListener.onPictureClicked(pic);
    }

    // -----------------------------------------------------
    // Select item mode (single select)
    // -----------------------------------------------------
    private void onSelectItem(int pos, Library.ColoredPicture pic) {
        if (items == null) return;

        // Clear all selections
        for (int i = 0; i < items.size(); i++) {
            Library.ColoredPicture p = items.get(i);
            if (p != null && p.mSelected) {
                p.mSelected = false;
                notifyItemChanged(i);
            }
        }

        // Select clicked item
        pic.mSelected = true;
        notifyItemChanged(pos);

        if (clickListener != null) clickListener.onPictureClicked(pic);
    }

}

