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

import com.colourmate.paintbynumber.adapter.ArtworksAdapter;
import com.colourmate.paintbynumber.databinding.FragMyimageListBinding;
import com.colourmate.paintbynumber.library.Library;
import com.colourmate.paintbynumber.util.UIHelpers;

/**
 * Displays user's colored pictures (My Color tab)
 * Opens editor on image click and refreshes on data change
 */

public class GalleryByColorFragment extends Fragment {

    private FragMyimageListBinding binding;
    private ArtworksAdapter adapter;

    // ==================================
    // Broadcast receiver (refresh list)
    // ==================================
    private final BroadcastReceiver itemsChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null
                    && "itemsChanged".equals(intent.getData() != null
                    ? intent.getData().getScheme()
                    : null)
                    && adapter != null) {
                adapter.refresh();
            }
        }
    };

    // ==================================
    // Lifecycle
    // ==================================
    @Override
    public View onCreateView(
            LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = FragMyimageListBinding.inflate(inflater, container, false);

        adapter = new ArtworksAdapter(this::onPictureClicked);

        adapter.registerAdapterDataObserver(
                new RecyclerView.AdapterDataObserver() {
                    @Override
                    public void onChanged() {
                        updateLayout();
                    }
                }
        );

        adapter.refreshAsync();

        UIHelpers.setupGridRecyclerView(
                getContext(),
                binding.bookListInclude.bookList,
                adapter
        );

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

        if (adapter != null) {
            adapter.refresh();
        }

        IntentFilter filter = new IntentFilter(Intent.ACTION_VIEW);
        filter.addDataScheme("itemsChanged");

        LocalBroadcastManager.getInstance(requireContext())
                .registerReceiver(itemsChangedReceiver, filter);
    }


    @Override
    public void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(requireContext())
                .unregisterReceiver(itemsChangedReceiver);
    }

    // ==================================
    // Click handling
    // ==================================
    private void onPictureClicked(Library.ColoredPicture picture) {
        Fragment parent = getParentFragment();
        if (parent instanceof SavedFragment) {
            ((SavedFragment) parent).startEditing(picture.getLibraryItem(), false);
        }
    }


    // ==================================
    // UI helpers
    // ==================================
    private void updateLayout() {
        if (binding == null || adapter == null) return;

        binding.noneColored.getRoot()
                .setVisibility(adapter.getItemCount() > 0
                        ? View.GONE
                        : View.VISIBLE);
    }
}
