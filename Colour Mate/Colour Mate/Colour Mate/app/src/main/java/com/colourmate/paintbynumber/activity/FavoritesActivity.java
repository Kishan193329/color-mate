package com.colourmate.paintbynumber.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.Utils.CompatUtils;
import com.colourmate.paintbynumber.Utils.Manager;
import com.colourmate.paintbynumber.ads.MediationManager;
import com.colourmate.paintbynumber.model.model.Level;
import com.colourmate.paintbynumber.util.DataManager;
import com.colourmate.paintbynumber.util.FavoritesManager;
import com.colourmate.paintbynumber.viewmodel.GameViewModel;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FavoritesActivity extends BaseActivity {

    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private FavoritesAdapter adapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left);
        setContentView(R.layout.activity_favorites);
        // Paper ground, transparent status bar, toolbar padded by the inset (design pass 2026-10-14).
        com.colourmate.paintbynumber.util.EdgeToEdge.apply(this, findViewById(R.id.toolbar), findViewById(R.id.ll_ads));

        MediationManager.getBannerAd(this);

        findViewById(R.id.RlBack).setOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.rv_favorites);
        tvEmpty = findViewById(R.id.tv_empty_favorites);

        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        // Safe here because the list is match_parent — its own size never depends on the content,
        // so RecyclerView can skip a full re-measure of the hierarchy on every adapter change.
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(4);
        adapter = new FavoritesAdapter(this);
        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadFavorites();
    }

    private void loadFavorites() {
        List<String> favList = FavoritesManager.getFavoriteList(this);

        if (favList.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            adapter.setItems(favList);
        }
    }

    // ─── Adapter ───

    private class FavoritesAdapter extends RecyclerView.Adapter<FavoritesAdapter.VH> {

        private final Context context;
        private List<String> items = new ArrayList<>();

        FavoritesAdapter(Context ctx) {
            this.context = ctx;
        }

        void setItems(List<String> list) {
            this.items = new ArrayList<>(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_favorite, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            String fileName = items.get(pos);
            h.bind(fileName);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final ImageView ivThumb;
            final ImageView ivFavIcon;
            final ProgressBar pbLoading;

            VH(View v) {
                super(v);
                ivThumb = v.findViewById(R.id.iv_fav_thumb);
                ivFavIcon = v.findViewById(R.id.iv_fav_icon);
                pbLoading = v.findViewById(R.id.pb_fav_loading);
            }

            void bind(String fileName) {
                pbLoading.setVisibility(View.VISIBLE);

                // A ColorForge favourite (date-prefixed fileName, §3.9) lives on OUR
                // server; the ionicsolutions host would 404 it. The reveal route ignores
                // the category segment — it resolves by fileName — so "c" is fine here,
                // which matters because favourites store only the name.
                boolean colorForge = isColorForgeName(fileName);
                String url = colorForge
                        ? com.colourmate.paintbynumber.Utils.ColorForgeApi.revealBaseUrl(
                                context, "c", fileName) + "border.png"
                        : Manager.BASE_URL + "thumb/" + extractCategory(fileName) +
                                "/t/" + fileName + ".png";
                ivThumb.setBackgroundColor(colorForge ? 0xFFFFFFFF : 0x00000000);
                // fit()+centerCrop = decode to the cell size, matching the layout's scaleType
                Picasso.get().load(url).fit().centerCrop().into(ivThumb, new Callback() {
                    @Override
                    public void onSuccess() {
                        pbLoading.setVisibility(View.GONE);
                    }

                    @Override
                    public void onError(Exception e) {
                        pbLoading.setVisibility(View.GONE);
                    }
                });

                ivFavIcon.setOnClickListener(v -> {
                    FavoritesManager.removeFavorite(context, fileName);
                    int position = getAdapterPosition();
                    if (position >= 0 && position < items.size()) {
                        items.remove(position);
                        notifyItemRemoved(position);
                        if (items.isEmpty()) {
                            tvEmpty.setVisibility(View.VISIBLE);
                            recyclerView.setVisibility(View.GONE);
                        }
                    }
                });

                itemView.setOnClickListener(v -> {
                    Level level = new Level();
                    try {
                        java.lang.reflect.Field fField = Level.class.getDeclaredField("fileName");
                        fField.setAccessible(true);
                        fField.set(level, fileName);
                        java.lang.reflect.Field cField = Level.class.getDeclaredField("category");
                        cField.setAccessible(true);
                        cField.set(level, extractCategory(fileName));
                    } catch (Exception ignored) {
                    }

                    if (DataManager.getInstance().isFinishedLevel(level)) {
                        File dir = new File(context.getFilesDir(), GameViewModel.COLOR_NUMBER_PATH);
                        String[] parts = fileName.split("/");
                        String name = parts[parts.length - 1];
                        File img = new File(dir, name + ".png");
                        if (img.exists()) {
                            MediationManager.showInterstitial((AppCompatActivity) context, () -> {
                                Intent intent = new Intent(context, ArtworkShareActivity.class);
                                intent.putExtra("path", img.getAbsolutePath());
                                startActivity(intent);
                            });
                        }
                    } else if (Manager.getInstance().hasInternetConnection(context)) {
                        MediationManager.showInterstitial((AppCompatActivity) context, () -> {
                            if (isColorForgeName(fileName)) {
                                // ColorForge pictures play the photo-reveal game; the flat
                                // engine would ask the ionicsolutions host and 404.
                                startActivity(com.colourmate.paintbynumber.activity.RevealActivity
                                        .fromUrl(context,
                                                com.colourmate.paintbynumber.Utils.ColorForgeApi
                                                        .revealBaseUrl(context, "c", fileName)));
                            } else {
                                startActivity(GameActivity.newIntent(context, level));
                            }
                        });
                    }
                });
            }

            /** Our fileNames are date-prefixed (YYYYMMDD_slug, §3.9); nobody else’s are. */
            private boolean isColorForgeName(String fileName) {
                return fileName != null && fileName.matches("^\\d{8}_.*");
            }

            private String extractCategory(String fileName) {
                if (fileName.contains("/")) {
                    return fileName.substring(0, fileName.indexOf("/"));
                }
                return "daily";
            }
        }
    }
}
