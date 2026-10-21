package com.colourmate.paintbynumber.fragments;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;

import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.activity.MainActivity;
import com.colourmate.paintbynumber.databinding.FragDrawbookDialogBinding;
import com.colourmate.paintbynumber.library.Library;
import com.colourmate.paintbynumber.views.RecolorDialog;
import com.koushikdutta.ion.Ion;
import com.koushikdutta.ion.builder.Builders;

/**
 * Dialog fragment for displaying a colored picture and providing options to publish, create new, or delete it.
 */
@SuppressWarnings("ALL")
public class DrawingDialogFragment extends DialogFragment {
    private static final String TAG = "DrawingDialogFragment";
    private static final String ARG_ITEM_KEY = "item";
    private static final String ARG_SHOW_PUBLISH_ONLY = "showPublishOnly";
    private static final int MAIN_PAGE = 0;
    private static final int PUBLISH_PAGE = 1;
    private FragDrawbookDialogBinding binding;

    Handler mHandler = new Handler(Looper.getMainLooper()) {
        /**
         * Handles messages sent to the handler.
         *
         * @param message The message to handle.
         */
        public void handleMessage(Message message) {
            Toast.makeText(getActivity(), "Failed to upload the picture.", Toast.LENGTH_SHORT).show();
        }
    };
    private Library.ColoredPicture mItem;
    private boolean mShowPublishOnly;

    /**
     * Creates and shows a new instance of the DrawingDialogFragment.
     *
     * @param activity      The activity to show the dialog in.
     * @param picture       The colored picture to display.
     * @param isPublishOnly Whether to show only the publish page.
     */
    public static void show(AppCompatActivity activity, Library.ColoredPicture picture, boolean isPublishOnly) {
        DrawingDialogFragment drawingDialogFragment = new DrawingDialogFragment();
        Bundle bundle = new Bundle();
        bundle.putString(ARG_ITEM_KEY, picture.getBaseName());
        bundle.putBoolean(ARG_SHOW_PUBLISH_ONLY, isPublishOnly);
        drawingDialogFragment.setArguments(bundle);
        drawingDialogFragment.show(activity.getSupportFragmentManager(), TAG);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_TITLE, R.style.draw_d);
        Bundle arguments = getArguments();
        if (arguments == null || !arguments.containsKey(ARG_ITEM_KEY)) {
            dismiss();
            return;
        }
        this.mItem = Library.getInstance().getColoredItemByBaseName(arguments.getString(ARG_ITEM_KEY));
        this.mShowPublishOnly = arguments.getBoolean(ARG_SHOW_PUBLISH_ONLY);
    }

    @Override
    public void onResume() {
        super.onResume();
        int dimensionPixelSize = getResources().getDimensionPixelSize(R.dimen.sub_dialog_width);
        if (dimensionPixelSize > getResources().getDisplayMetrics().widthPixels - 100) {
            dimensionPixelSize = -1;
        }
        getDialog().getWindow().setLayout(dimensionPixelSize, -2);
        getDialog().getWindow().getAttributes().dimAmount = 0.85f;
        getDialog().getWindow().setSoftInputMode(16);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new Dialog(getActivity(), getTheme()) {
            @Override
            public void onBackPressed() {
                if (getView() == null) {
                    super.onBackPressed();
                } else if (mShowPublishOnly) {
                    super.onBackPressed();
                } else {
                    super.onBackPressed();
                }
            }
        };
    }

    @Override
    public void onDismiss(DialogInterface dialogInterface) {
//        Manager.PublishItemTask publishItemTask = this.mPublishTask;
//        if (publishItemTask != null) {
//            publishItemTask.cancel();
//            this.mPublishTask = null;
//        }
        super.onDismiss(dialogInterface);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragDrawbookDialogBinding.inflate(inflater, container, false);
        getDialog().setTitle("");
        getDialog().setCanceledOnTouchOutside(true);
        if (this.mItem != null) {
            ImageView imageView = binding.ivDlgArt;
            ((Builders.Any.B) Ion.with(imageView.getContext()).load(this.mItem.getThumbFile()).noCache()).withBitmap().intoImageView(imageView);
            binding.btnDlgClose.setOnClickListener(v -> dismiss());
            binding.btnDlgContinue.setOnClickListener(v -> {
                if (mItem.mScanned) {
                    createNew();
                    return;
                }
                mItem.getLibraryItem().mColored = mItem;
                if (getActivity() instanceof MainActivity) {
                    if (((MainActivity) getActivity()).startEditing(mItem.getLibraryItem(), false)) {
                        dismiss();
                    }
                }
            });

            View createNewButton = binding.ibDlgCreateNewBtn;
            createNewButton.setVisibility(this.mItem.mScanned ? View.GONE : View.VISIBLE);
            createNewButton.setOnClickListener(v -> createNew());
            binding.ibDlgDeleteBtn.setOnClickListener(v -> delete());
            getDialog().getWindow().getDecorView().setSystemUiVisibility(4);
        }
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    /**
     * Navigates to the specified page.
     *
     * @param page The page to navigate to.
     */
    private void gotoPage(int page) {
        if (binding != null) {
            View mainPage = binding.getRoot();
            if (page == MAIN_PAGE) {
                mainPage.setVisibility(View.VISIBLE);
                return;
            }

            mainPage.setVisibility(View.GONE);
        }
    }

    /**
     * Deletes the colored picture.
     */
    private void delete() {
        RecolorDialog.create(getContext()).setText(R.string.dialog_draw_delete_confirm).setLeftBtn(R.string.dialog_no, (RecolorDialog.OnItemClickListener) null).setRightBtn(R.string.dialog_draw_delete_ok, R.color.hint_text_color, new RecolorDialog.OnItemClickListener() {
            @Override
            public void onClick() {
                Library.getInstance().deleteColoredPicture(mItem);
                dismiss();
            }
        }).show();
    }

    /**
     * Starts editing a new picture.
     */
    private void createNew() {
        if (getActivity() instanceof MainActivity) {

            if (((MainActivity) getActivity()).startEditing(this.mItem.getLibraryItem(), true)) {
                dismiss();
            }
        }
    }
}
