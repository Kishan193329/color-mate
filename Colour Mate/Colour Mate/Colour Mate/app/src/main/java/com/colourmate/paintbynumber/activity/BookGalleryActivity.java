package com.colourmate.paintbynumber.activity;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.Utils.ColorForgeApi;
import com.colourmate.paintbynumber.util.EdgeToEdge;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Our Colour Book shelf (backend /book/feed.json): category chips over a grid of pages. */
public class BookGalleryActivity extends AppCompatActivity {

    private final List<JSONObject> all = new ArrayList<>();
    private final List<JSONObject> shown = new ArrayList<>();
    private String category = null;          // null = all
    private Adapter adapter;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_gallery);
        EdgeToEdge.apply(this, findViewById(R.id.ll_book_header), findViewById(R.id.rv_book));
        findViewById(R.id.fl_gallery_back).setOnClickListener(v -> finish());
        RecyclerView rv = findViewById(R.id.rv_book);
        rv.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new Adapter();
        rv.setAdapter(adapter);
        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null) adapter.notifyDataSetChanged();     // progress chips
    }

    /** The feed, or null — on a background thread. Also used by the By Images banner. */
    public static JSONObject fetchFeedPublic(Context c) {
        return fetchFeed(c);
    }

    static JSONObject fetchFeed(Context c) {
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(
                    ColorForgeApi.baseUrl(c) + "book/feed.json").openConnection();
            con.setConnectTimeout(15000);
            con.setReadTimeout(20000);
            try (InputStream in = con.getInputStream()) {
                java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
                byte[] b = new byte[8192];
                for (int n; (n = in.read(b)) > 0; ) buf.write(b, 0, n);
                return new JSONObject(buf.toString(StandardCharsets.UTF_8.name()));
            } finally {
                con.disconnect();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void load() {
        new Thread(() -> {
            JSONObject feed = fetchFeed(this);
            main.post(() -> {
                if (isFinishing()) return;
                findViewById(R.id.pb_book_gallery).setVisibility(View.GONE);
                if (feed == null) {
                    message(getString(R.string.mp_no_server));
                    return;
                }
                JSONArray items = feed.optJSONArray("items");
                for (int i = 0; items != null && i < items.length(); i++) all.add(items.optJSONObject(i));
                buildChips(feed.optJSONArray("categories"));
                filter();
            });
        }, "book-feed").start();
    }

    private void message(String text) {
        TextView m = findViewById(R.id.tv_book_message);
        m.setText(text);
        m.setVisibility(View.VISIBLE);
    }

    private void buildChips(JSONArray cats) {
        LinearLayout row = findViewById(R.id.ll_book_cats);
        row.removeAllViews();
        List<String> names = new ArrayList<>();
        names.add(null);
        for (int i = 0; cats != null && i < cats.length(); i++) names.add(cats.optString(i));
        float d = getResources().getDisplayMetrics().density;
        for (String name : names) {
            TextView chip = (TextView) LayoutInflater.from(this).inflate(R.layout.item_book_chip, row, false);
            chip.setText(name == null ? getString(R.string.book_all) : cap(name));
            chip.setSelected(name == null ? category == null : name.equals(category));
            chip.setOnClickListener(v -> {
                category = name;
                for (int i = 0; i < row.getChildCount(); i++) row.getChildAt(i).setSelected(row.getChildAt(i) == v);
                filter();
            });
            ((LinearLayout.LayoutParams) chip.getLayoutParams()).setMarginEnd((int) (8 * d));
            row.addView(chip);
        }
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void filter() {
        shown.clear();
        for (JSONObject o : all) if (category == null || category.equals(o.optString("category"))) shown.add(o);
        adapter.notifyDataSetChanged();
        if (shown.isEmpty()) message(getString(R.string.book_empty));
        else findViewById(R.id.tv_book_message).setVisibility(View.GONE);
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
        class Holder extends RecyclerView.ViewHolder {
            final ImageView thumb;
            final TextView pct;

            Holder(View v) {
                super(v);
                thumb = v.findViewById(R.id.iv_book_thumb);
                pct = v.findViewById(R.id.tv_book_pct);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_book, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            JSONObject o = shown.get(position);
            String base = ColorForgeApi.baseUrl(BookGalleryActivity.this).replaceAll("/$", "");
            Glide.with(h.thumb).load(base + o.optString("thumb")).into(h.thumb);
            int pct = getSharedPreferences(BookActivity.PREFS, MODE_PRIVATE).getInt(o.optString("id") + "#pct", 0);
            h.pct.setVisibility(pct > 0 ? View.VISIBLE : View.GONE);
            h.pct.setText(pct >= 100 ? "✓" : pct + "%");
            h.itemView.setOnClickListener(v -> startActivity(BookActivity.open(BookGalleryActivity.this,
                    o.optString("id"), base + o.optString("art"))));
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }
    }
}
