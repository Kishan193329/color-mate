package com.colourmate.paintbynumber.views;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.colourmate.paintbynumber.R;

/**
 * A custom, reusable dialog with two buttons and a text message.
 * It is configured using a builder pattern.
 */
public class RecolorDialog extends Dialog {

    private String dialogMessage;
    private int dialogWidth;

    private String leftButtonText;
    private OnItemClickListener leftButtonClickListener;

    private String rightButtonText;
    @ColorInt
    private int rightButtonColor = ViewCompat.MEASURED_STATE_MASK; // Default to black
    private OnItemClickListener rightButtonClickListener;

    /**
     * Listener for button click events in the dialog.
     */
    public interface OnItemClickListener {
        void onClick();
    }

    /**
     * Creates a new instance of RecolorDialog with a default width.
     *
     * @param context The context.
     * @return A new RecolorDialog instance.
     */
    public static RecolorDialog create(Context context) {
        RecolorDialog dialog = new RecolorDialog(context, R.style.default_d);
        dialog.setDialogWidth((int) context.getResources().getDimension(R.dimen.max_query_dlg_width));
        return dialog;
    }

    public RecolorDialog(Context context) {
        super(context);
    }

    public RecolorDialog(Context context, int themeResId) {
        super(context, themeResId);
    }

    /**
     * Sets the main message text of the dialog.
     *
     * @param message The text to display.
     */
    public RecolorDialog setText(String message) {
        this.dialogMessage = message;
        return this;
    }

    /**
     * Sets the main message text of the dialog from a string resource.
     *
     * @param messageResId The resource ID of the string to display.
     */
    public RecolorDialog setText(@StringRes int messageResId) {
        this.dialogMessage = getContext().getResources().getString(messageResId);
        return this;
    }

    /**
     * Sets the properties for the left button.
     *
     * @param text     The text for the button.
     * @param listener The listener for click events.
     */
    public RecolorDialog setLeftBtn(String text, OnItemClickListener listener) {
        this.leftButtonText = text;
        this.leftButtonClickListener = listener;
        return this;
    }

    /**
     * Sets the properties for the left button from string resource.
     *
     * @param textResId The resource ID of the text for the button.
     * @param listener  The listener for click events.
     */
    public RecolorDialog setLeftBtn(@StringRes int textResId, OnItemClickListener listener) {
        return setLeftBtn(getContext().getResources().getString(textResId), listener);
    }

    /**
     * Sets the properties for the right button.
     *
     * @param text     The text for the button.
     * @param listener The listener for click events.
     */
    public RecolorDialog setRightBtn(String text, OnItemClickListener listener) {
        this.rightButtonText = text;
        this.rightButtonClickListener = listener;
        return this;
    }

    /**
     * Sets the properties for the right button from string resource.
     *
     * @param textResId The resource ID of the text for the button.
     * @param listener  The listener for click events.
     */
    public RecolorDialog setRightBtn(@StringRes int textResId, OnItemClickListener listener) {
        return setRightBtn(getContext().getResources().getString(textResId), listener);
    }

    /**
     * Sets the properties for the right button with a custom text color.
     *
     * @param textResId  The resource ID of the text for the button.
     * @param colorResId The resource ID of the color for the button text.
     * @param listener   The listener for click events.
     */
    public RecolorDialog setRightBtn(@StringRes int textResId, @ColorRes int colorResId, OnItemClickListener listener) {
        this.rightButtonColor = ContextCompat.getColor(getContext(), colorResId);
        return setRightBtn(getContext().getResources().getString(textResId), listener);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupWindow();
        setContentView(R.layout.book_two_btn_dialog);
        setupViews();
    }

    private void setupWindow() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = getWindow();
        if (window != null) {
            int width = (dialogWidth == 0) ? WindowManager.LayoutParams.WRAP_CONTENT : dialogWidth;
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
            window.getAttributes().dimAmount = 0.85f;
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
        setCanceledOnTouchOutside(true);
    }

    private void setupViews() {
        if (this.dialogMessage == null) {
            dismiss(); // If there is no text, don't show the dialog.
            return;
        }

        TextView dialogTextView = findViewById(R.id.dialog_text);
        dialogTextView.setText(this.dialogMessage);

        LinearLayout leftButton = findViewById(R.id.btn_left_btn);
        leftButton.setOnClickListener(view -> {
            if (this.leftButtonClickListener != null) {
                this.leftButtonClickListener.onClick();
            }
            dismiss();
        });

        LinearLayout rightButton = findViewById(R.id.iv_right_btn);
        rightButton.setOnClickListener(view -> {
            if (this.rightButtonClickListener != null) {
                this.rightButtonClickListener.onClick();
            }
            dismiss();
        });
    }

    @Override
    public void show() {
        // This flag allows the dialog to be shown without taking immediate focus,
        // which can be useful to avoid interrupting animations or other UI updates.
        if (getWindow() != null) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        }
        super.show();
        if (getWindow() != null) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        }
    }

    /**
     * Sets a specific width for the dialog.
     *
     * @param width The width in pixels.
     */
    public void setDialogWidth(int width) {
        this.dialogWidth = width;
    }
}
