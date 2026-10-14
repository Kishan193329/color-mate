package com.colourmate.paintbynumber.fragments;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.Utils.Manager;
import com.colourmate.paintbynumber.activity.DrawingActivity;
import com.colourmate.paintbynumber.ads.MediationManager;
import com.colourmate.paintbynumber.library.Library;
import com.colourmate.paintbynumber.util.Refreshable;
import com.colourmate.paintbynumber.views.BeautifulProgressDialog;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.sumoing.recolor.library.LibraryItem;

import java.util.concurrent.atomic.AtomicBoolean;

public class SavedFragment extends Fragment {

    private static final String TAG = "SavedFragment";
    private BeautifulProgressDialog progressDialog;

    // ✅ Track if refresh needed
    private boolean needsRefresh = false;
    private boolean isInitialized = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_saved, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TabLayout tabLayout = view.findViewById(R.id.book_myworks_tabs);
        ViewPager2 viewPager = view.findViewById(R.id.vp_book_myworks);

        viewPager.setOffscreenPageLimit(3);
        viewPager.setAdapter(new MyWorksPagerAdapter(this));

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText(R.string.myworks_number_pics_tab);
                    break;
                case 1:
                    tab.setText(R.string.myworks_all_pics_tab);
                    break;
                case 2:
                    tab.setText(R.string.mywork_number_pixel);
                    break;
            }
        }).attach();

        isInitialized = true;
    }

    // ==================== ✅ REFRESH CONTROL ====================

    /**
     * Called from MainActivity when user switches to Saved tab.
     * Only refreshes if data has changed.
     */
    public void refreshIfNeeded() {
        if (!needsRefresh || !isInitialized || !isAdded() || getView() == null) {
            return;
        }

        Log.d(TAG, "Refreshing saved items...");

        ViewPager2 viewPager = getView().findViewById(R.id.vp_book_myworks);
        if (viewPager != null && viewPager.getAdapter() != null) {
            // ✅ Notify child fragments to refresh their data
            FragmentStateAdapter adapter = (FragmentStateAdapter) viewPager.getAdapter();

            // Refresh all child fragments
            getChildFragmentManager().getFragments().forEach(fragment -> {
                if (fragment instanceof Refreshable) {
                    ((Refreshable) fragment).onRefresh();
                }
            });
        }

        needsRefresh = false;
    }

    /**
     * Call this from anywhere when saved data changes
     * (e.g., after completing a drawing, deleting, etc.)
     */
    public void markNeedsRefresh() {
        needsRefresh = true;
    }

    // ✅ Auto-refresh when fragment becomes visible via show/hide
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            if (getView() != null) {
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

            if (needsRefresh) {
                refreshIfNeeded();
            }
        }
    }

    // ==================== EXISTING CODE (unchanged) ====================

    public void startEditing(LibraryItem item, boolean makeCopy) {
        if (item == null || !isAdded())
            return;

        Library.getInstance().isNewCopy = makeCopy;

        if (item.isFreeContent()) {
            startDownload(item);
            return;
        }

        if (Manager.getInstance().hasInternetConnection(requireContext())) {
            startDownload(item);
        } else {
            Toast.makeText(requireContext(),
                    R.string.internet_conn_failed, Toast.LENGTH_SHORT).show();
        }
    }

    public void startDownload(LibraryItem item) {
        if (!isAdded())
            return;
        Log.d(TAG, "start download " + item.getName());

        if (progressDialog != null
                || (Library.mCurrentDrawing != null
                        && Library.mCurrentDrawing.isDownloading())) {
            return;
        }

        Library.mCurrentDrawing = new Library.Drawing();
        Library.mCurrentDrawing.mItem = item;

        final AtomicBoolean downloadCompleted = new AtomicBoolean(false);

        progressDialog = new BeautifulProgressDialog(
                (Activity) requireContext(),
                BeautifulProgressDialog.withLottie, "Please wait");
        progressDialog.setCancelable(true);

        progressDialog.setOnDismissListener(dialog -> {
            if (!downloadCompleted.get() && Library.mCurrentDrawing != null) {
                Library.mCurrentDrawing.cancelDownload(requireContext());
            }
            progressDialog = null;
        });

        progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel",
                (dialog, which) -> {
                    if (Library.mCurrentDrawing != null) {
                        Library.mCurrentDrawing.cancelDownload(requireContext());
                    }
                    dialog.cancel();
                    progressDialog = null;
                });

        progressDialog.show();

        Library.mCurrentDrawing.download(requireContext(),
                new Library.Drawing.OnDownloadCompletedListener() {
                    @Override
                    public void onCompleted() {
                        downloadCompleted.set(true);
                        if (!isAdded())
                            return;
                        requireActivity().runOnUiThread(() -> {
                            dismissProgress();
                            if (isAdded()) {
                                MediationManager.showInterstitial(
                                        (AppCompatActivity) requireActivity(), () -> {
                                            Intent intent = new Intent(requireContext(),
                                                    DrawingActivity.class);
                                            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                            startActivity(intent);
                                        });
                            }
                        });
                    }

                    @Override
                    public void onFailed(String error) {
                        downloadCompleted.set(false);
                        if (!isAdded())
                            return;
                        requireActivity().runOnUiThread(() -> {
                            dismissProgress();
                            if (isAdded()) {
                                Toast.makeText(requireContext(),
                                        "Download failed: " + error,
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
    }

    private void dismissProgress() {
        if (progressDialog != null && progressDialog.isShowing()) {
            try {
                progressDialog.dismiss();
            } catch (Exception ignored) {
            }
            progressDialog = null;
        }
    }

    private static class MyWorksPagerAdapter extends FragmentStateAdapter {
        public MyWorksPagerAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return new GalleryByNumberFragment();
                case 1:
                    return new GalleryByColorFragment();
                case 2:
                    return new GalleryByPixelFragment();
                default:
                    return new Fragment();
            }
        }

        @Override
        public int getItemCount() {
            return 3;
        }
    }
}
