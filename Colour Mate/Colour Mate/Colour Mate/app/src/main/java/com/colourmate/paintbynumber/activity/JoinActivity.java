package com.colourmate.paintbynumber.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.Utils.ColorForgeApi;
import com.colourmate.paintbynumber.util.Multiplayer;

import org.json.JSONObject;

/**
 * Where a friend's invite lands: an invite link ({@code colourmate://j/CODE}, which the server's
 * /j/CODE page hands over), or the Home "Join a friend" button (asks for the code). Looks the
 * code up and opens the picture in the right mode. Has no screen of its own — only dialogs over
 * whatever was behind it.
 */
public class JoinActivity extends Activity {

    private static final String EXTRA_CODE = "code";

    /** Opens the "enter a code" dialog. */
    public static Intent ask(Context context) {
        return new Intent(context, JoinActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String code = Multiplayer.codeFromLink(getIntent().getData());
        if (code == null) code = getIntent().getStringExtra(EXTRA_CODE);
        if (code != null) {
            final String c = code;
            withName(this, () -> open(c), this::finish);
        } else {
            askCode();
        }
    }

    private void askCode() {
        EditText input = field(this, getString(R.string.mp_join_hint), "");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setFilters(new InputFilter[]{new InputFilter.AllCaps(), new InputFilter.LengthFilter(8)});
        new AlertDialog.Builder(this)
                .setTitle(R.string.mp_join)
                .setView(wrap(this, input))
                .setPositiveButton(R.string.mp_join, (d, w) -> {
                    String code = Multiplayer.normaliseCode(input.getText().toString());
                    if (code.length() != 6) {
                        Toast.makeText(this, R.string.mp_bad_code, Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }
                    withName(this, () -> open(code), this::finish);
                })
                .setNegativeButton(android.R.string.cancel, (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .show();
    }

    private void open(String code) {
        Multiplayer.lookup(this, code, info -> {
            if (isFinishing()) return;
            if (info == null || !info.has("fileName")) {
                Toast.makeText(this, R.string.mp_bad_code, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            startActivity(intentFor(this, code, info));
            finish();
        });
    }

    private static Intent intentFor(Context ctx, String code, JSONObject info) {
        String fileName = info.optString("fileName");
        String url = ColorForgeApi.revealBaseUrl(ctx, info.optString("category"), fileName);
        return RevealActivity.forMultiplayer(ctx, url, fileName, info.optString("kind"), code,
                info.optLong("ms"), info.optString("by", "your friend"));
    }

    // ─── The player's name, asked once ───

    /** Runs {@code then} once the player has a name friends will see; asks for one first time. */
    public static void withName(Activity activity, Runnable then) {
        withName(activity, then, null);
    }

    static void withName(Activity activity, Runnable then, Runnable cancelled) {
        if (!Multiplayer.name(activity).isEmpty()) {
            then.run();
            return;
        }
        EditText input = field(activity, activity.getString(R.string.mp_your_name), "");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(16)});
        new AlertDialog.Builder(activity)
                .setTitle(R.string.mp_your_name)
                .setView(wrap(activity, input))
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String name = input.getText().toString().trim();
                    Multiplayer.setName(activity, name.isEmpty() ? "Player" : name);
                    then.run();
                })
                .setNegativeButton(android.R.string.cancel, (d, w) -> { if (cancelled != null) cancelled.run(); })
                .setOnCancelListener(d -> { if (cancelled != null) cancelled.run(); })
                .show();
    }

    private static EditText field(Context c, String hint, String text) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setText(text);
        e.setSingleLine(true);
        return e;
    }

    private static FrameLayout wrap(Context c, EditText e) {
        FrameLayout box = new FrameLayout(c);
        int pad = (int) (22 * c.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(e);
        return box;
    }
}
