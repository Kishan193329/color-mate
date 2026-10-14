package com.colourmate.paintbynumber.fragments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.colourmate.paintbynumber.GlobalApplication;
import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.databinding.FragMyimageListBinding;
import com.colourmate.paintbynumber.databinding.FragBookitemBinding;
import com.colourmate.paintbynumber.util.UIHelpers;
import com.colourmate.paintbynumber.viewmodel.GameViewModel;
import com.colourmate.paintbynumber.views.RecolorDialog;

import java.io.File;
import java.util.ArrayList;

/**
 * Displays locally saved "Number Only" colored images
 * Allows publish & delete actions via popup menu
 */
@SuppressWarnings("ALL")
public class GalleryByNumberFragment extends Fragment {

    private FragMyimageListBinding binding;
    private RVAdapterImage adapter;
    private ArrayList<String> files;

    // ==================================
    // Broadcast receiver (reload images)
    // ==================================
    private final BroadcastReceiver reloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }
    };

    // ==================================
    // Lifecycle
    // ==================================
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragMyimageListBinding.inflate(inflater, container, false);

        adapter = new RVAdapterImage();
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                updateLayout();
            }
        });

        UIHelpers.setupGridRecyclerView(getContext(), binding.bookListInclude.bookList, adapter);

        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateLayout();
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(reloadReceiver, new IntentFilter("numberreload"));
    }

    @Override
    public void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(reloadReceiver);
    }

    // ==================================
    // UI helpers
    // ==================================
    private void updateLayout() {
        if (binding == null || adapter == null) return;
        binding.noneColored.getRoot().setVisibility(adapter.getItemCount() > 0 ? View.GONE : View.VISIBLE);
    }

    // ==================================
    // ViewHolder
    // ==================================
    class ImageHolder extends RecyclerView.ViewHolder {

        final FragBookitemBinding itemBinding;

        ImageHolder(FragBookitemBinding itemBinding) {
            super(itemBinding.getRoot());
            this.itemBinding = itemBinding;

            itemBinding.getRoot().setOnClickListener(this::onItemClick);
        }

        private void onItemClick(View view) {
            int position = getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) return;

            File file = new File(files.get(position));

            androidx.appcompat.widget.PopupMenu popupMenu = new androidx.appcompat.widget.PopupMenu(view.getContext(), view);

            popupMenu.getMenu().add(0, 1, 1, getString(R.string.dialog_draw_delete_ok));

            popupMenu.setOnMenuItemClickListener(item -> {
                handleMenuClick(item.getItemId(), file, position, view);
                return true;
            });

            popupMenu.show();
        }


        private void handleMenuClick(int action, File file, int position, View anchor) {

            // Delete
            if (action == 1) {
                RecolorDialog.create(anchor.getContext()).setText(R.string.dialog_draw_delete_confirm).setLeftBtn(R.string.dialog_no, null).setRightBtn(R.string.button_ok, R.color.hint_text_color, () -> {
                    if (file.exists()) {
                        file.delete();
                        files.remove(position);
                        adapter.notifyDataSetChanged();
                    }
                }).show();
            }
        }
    }

    // ==================================
    // Adapter
    // ==================================
    class RVAdapterImage extends RecyclerView.Adapter<ImageHolder> {

        @Override
        public ImageHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ImageHolder(FragBookitemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(ImageHolder holder, int position) {
            holder.itemBinding.ivBookItemBadge.setVisibility(View.GONE);
            holder.itemBinding.ivBookItemImage.setImageBitmap(null);

            Glide.with(requireActivity()).load(files.get(position)).into(holder.itemBinding.ivBookItemImage);
        }

        @Override
        public int getItemCount() {
            files = getNumberPixelImages();
            return files != null ? files.size() : 0;
        }
    }

    // ==================================
    // File handling
    // ==================================
    private ArrayList<String> getNumberPixelImages() {
        ArrayList<String> result = new ArrayList<>();
        File folder = new File(getOrCreateFolder(GameViewModel.COLOR_NUMBER_PATH));

        if (!folder.exists()) return result;

        for (File file : folder.listFiles()) {
            result.add(file.getPath());
        }
        return result;
    }

    private String getOrCreateFolder(String folderName) {
        String path = GlobalApplication.getAppContext().getFilesDir() + File.separator + folderName;

        File folder = new File(path);
        return folder.exists() || folder.mkdirs() ? path : null;
    }
}
