package com.colourmate.paintbynumber.activity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.ads.MediationManager;
import com.colourmate.paintbynumber.model.book.BookView;
import com.colourmate.paintbynumber.util.EdgeToEdge;
import com.colourmate.paintbynumber.viewmodel.GameViewModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * One page of our Colour Book (backend book_tab.py): free colouring — choose any colour from the
 * palette, tap a shape. The page is cached on disk; progress is saved per page id.
 */
public class BookActivity extends AppCompatActivity implements BookView.Listener {

    private static final String TAG = "BookActivity";
    private static final String EXTRA_ID = "book_id";
    private static final String EXTRA_ART = "book_art_url";
    static final String PREFS = "book_progress";
    /** Pages are shown at most this big — enough for a phone, and it bounds the shape map's memory. */
    private static final int MAX_SIDE = 1600;

    /** A calm, broad palette: the player picks anything, so it should cover the wheel. */
    private static final int[] PALETTE = {
            0xFFFF6B6B, 0xFFFF8E53, 0xFFFFB347, 0xFFFFD93D, 0xFFF9F871, 0xFFC5E86C, 0xFF6BCB77,
            0xFF2EC4B6, 0xFF4ECDC4, 0xFF56CCF2, 0xFF4D96FF, 0xFF3A86FF, 0xFF6C63FF, 0xFF9B5DE5,
            0xFFC77DFF, 0xFFF15BB5, 0xFFFF85A1, 0xFFFFC8DD, 0xFFFFE5B4, 0xFFD4A373, 0xFFA0522D,
            0xFF6D4C41, 0xFF8D99AE, 0xFF495057, 0xFF212529, 0xFFFFFFFF};

    private BookView board;
    private TextView progress;
    private String pageId;
    private boolean ready;
    private View selectedSwatch;
    private final Handler main = new Handler(Looper.getMainLooper());

    public static Intent open(Context context, String id, String artUrl) {
        return new Intent(context, BookActivity.class).putExtra(EXTRA_ID, id).putExtra(EXTRA_ART, artUrl);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book);
        EdgeToEdge.apply(this, findViewById(R.id.fl_book_back), findViewById(R.id.fl_book_palette));
        pageId = getIntent().getStringExtra(EXTRA_ID);
        board = findViewById(R.id.book_view);
        progress = findViewById(R.id.tv_book_progress);
        board.setListener(this);
        findViewById(R.id.fl_book_back).setOnClickListener(v -> leave());
        findViewById(R.id.btn_book_undo).setOnClickListener(v -> { if (board.undo()) onFilled(); });
        findViewById(R.id.btn_book_done).setOnClickListener(v -> finishPage());
        buildPalette();
        load(getIntent().getStringExtra(EXTRA_ART));
    }

    private void buildPalette() {
        LinearLayout row = findViewById(R.id.ll_book_palette);
        float d = getResources().getDisplayMetrics().density;
        int size = (int) (42 * d), gap = (int) (6 * d);
        for (int colour : PALETTE) {
            View swatch = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(gap, 0, gap, 0);
            swatch.setLayoutParams(lp);
            swatch.setBackground(circle(colour, false));
            swatch.setOnClickListener(v -> select(v, colour));
            row.addView(swatch);
            if (selectedSwatch == null) select(swatch, colour);
        }
    }

    private GradientDrawable circle(int colour, boolean selected) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(colour);
        float d = getResources().getDisplayMetrics().density;
        g.setStroke((int) ((selected ? 3 : 1) * d), selected ? 0xFF7A5CFF : 0x33201636);
        return g;
    }

    private void select(View swatch, int colour) {
        if (selectedSwatch != null) {
            selectedSwatch.setBackground(circle((Integer) selectedSwatch.getTag(), false));
            selectedSwatch.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
        }
        swatch.setTag(colour);
        swatch.setBackground(circle(colour, true));
        swatch.animate().scaleX(1.15f).scaleY(1.15f).setDuration(120).start();
        selectedSwatch = swatch;
        board.setBrush(colour);
    }

    private void load(String url) {
        new Thread(() -> {
            BookView.Prepared prepared = null;
            try {
                File file = cached(url);
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getPath(), o);
                int sample = 1;
                while (Math.max(o.outWidth, o.outHeight) / sample > MAX_SIDE) sample *= 2;
                o = new BitmapFactory.Options();
                o.inSampleSize = sample;
                o.inPreferredConfig = Bitmap.Config.ARGB_8888;
                Bitmap page = BitmapFactory.decodeFile(file.getPath(), o);
                if (page != null) prepared = BookView.prepare(page.copy(Bitmap.Config.ARGB_8888, false));
            } catch (Exception e) {
                Log.e(TAG, "could not open page", e);
            }
            final BookView.Prepared p = prepared;
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                findViewById(R.id.pb_book_loading).setVisibility(View.GONE);
                if (p == null) {
                    Toast.makeText(this, "Could not open this page", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                board.show(p, prefs().getString(pageId, null));
                ready = true;
                onFilled();
            });
        }, "book-load").start();
    }

    /** The page is downloaded once and kept; a page never changes after it is published. */
    private File cached(String url) throws Exception {
        File dir = new File(getCacheDir(), "book");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File file = new File(dir, pageId + ".png");
        if (file.length() > 0) return file;
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(20000);
        con.setReadTimeout(30000);
        con.setInstanceFollowRedirects(true);
        File tmp = new File(dir, pageId + ".part");
        try (InputStream in = con.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[16384];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        } finally {
            con.disconnect();
        }
        if (!tmp.renameTo(file)) throw new IllegalStateException("cache write failed");
        return file;
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    @Override
    public void onFilled() {
        progress.setText(board.paintedPercent() + "%");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (ready) {
            prefs().edit().putString(pageId, board.encode())
                    .putInt(pageId + "#pct", board.paintedPercent()).apply();
        }
    }

    /** Done: the page joins My Works (the same folder the other games use), then we leave. */
    private void finishPage() {
        if (!ready) return;
        Bitmap art = board.render();
        new Thread(() -> {
            try {
                File dir = new File(getFilesDir(), GameViewModel.COLOR_NUMBER_PATH);
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
                try (FileOutputStream out = new FileOutputStream(new File(dir, pageId + ".png"))) {
                    art.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
            } catch (Exception e) {
                Log.w(TAG, "could not save the page", e);
            }
        }, "book-save").start();
        Toast.makeText(this, "Saved to My Works 🎨", Toast.LENGTH_SHORT).show();
        leave();
    }

    private boolean leaving;

    private void leave() {
        if (leaving) return;
        leaving = true;
        MediationManager.showInterstitial(this, this::finish);
    }

    @Override
    public void onBackPressed() {
        leave();
    }
}
