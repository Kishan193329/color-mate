package com.colourmate.paintbynumber.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.colourmate.paintbynumber.adapter.LibraryItemAdapter;
import com.colourmate.paintbynumber.databinding.FragBookItemListBinding;

/**
 * Fragment responsible for displaying a list of library books
 * Data is loaded via MainActivity based on provided tag
 */
public class ColorListFragment extends Fragment {

    public static final String TAG = "LibraryItemFragment";
    public static final String ARG_ALL_BOOKS = "ALL_BOOKS";

    private FragBookItemListBinding binding;
    private LibraryItemAdapter currentAdapter;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragBookItemListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupRecyclerView();
        loadData();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (binding != null && binding.bookList != null) {
            binding.bookList.setAdapter(null);
            // ⚡ FIX: Remove scroll listeners to prevent leaks
            binding.bookList.clearOnScrollListeners();
        }
        currentAdapter = null;
        binding = null;
    }

    private void setupRecyclerView() {
        if (binding == null) return;

        RecyclerView recyclerView = binding.bookList;

//        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(4); // ⚡ FIX: 10 is too high, wastes memory

        // ⚡ FIX 11: REMOVE deprecated DrawingCache - does nothing on API 28+
        // and actually HURTS performance on older devices by caching bitmaps
        // recyclerView.setDrawingCacheEnabled(true);  ← REMOVED
        // recyclerView.setDrawingCacheQuality(...)    ← REMOVED

        recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        // ⚡ FIX 12: Actually pause/resume image loading via adapter
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (currentAdapter == null) return;

                if (newState == RecyclerView.SCROLL_STATE_SETTLING) {
                    // Only pause during FLING (fast scroll)
                    // DRAGGING = user is slowly scrolling, don't pause
                    currentAdapter.setScrolling(true);
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    currentAdapter.setScrolling(false);
                }
            }
        });
    }

    private void loadData() {
        if (binding == null || getArguments() == null) return;

        String tag = getArguments().getString(ARG_ALL_BOOKS, null);
        if (tag == null) return;

        Fragment parent = getParentFragment();
        if (parent instanceof ColorFragment) {
            ((ColorFragment) parent).setRecyclerViewByTag(
                    binding.bookList, tag, true);

            // ⚡ FIX: Keep reference to adapter for scroll control
            RecyclerView.Adapter<?> adapter = binding.bookList.getAdapter();
            if (adapter instanceof LibraryItemAdapter) {
                currentAdapter = (LibraryItemAdapter) adapter;
            }
        }
    }
}
