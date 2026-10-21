package com.colourmate.paintbynumber.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.colourmate.paintbynumber.R;

public class BannerSlideAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_DAILY = 0;
    private static final int TYPE_OFFER = 1;

    public interface BannerListener {
        void onOfferClicked();

        void onBindDailyBanner(View itemView);

        void onBindOfferCard(View itemView);
    }

    private final BannerListener listener;

    public BannerSlideAdapter(BannerListener listener) {
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? TYPE_DAILY : TYPE_OFFER;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_DAILY) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_daily_banner, parent, false);
            return new DailyBannerViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_offer_card, parent, false);
            return new OfferViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof DailyBannerViewHolder) {
            listener.onBindDailyBanner(holder.itemView);
        } else if (holder instanceof OfferViewHolder) {
            listener.onBindOfferCard(holder.itemView);
            holder.itemView.setOnClickListener(v -> listener.onOfferClicked());
            View btn = holder.itemView.findViewById(R.id.tv_offer_btn);
            if (btn != null) {
                btn.setOnClickListener(v -> listener.onOfferClicked());
            }
        }
    }

    @Override
    public int getItemCount() {
        return 2;
    }

    static class DailyBannerViewHolder extends RecyclerView.ViewHolder {
        public DailyBannerViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    static class OfferViewHolder extends RecyclerView.ViewHolder {
        public OfferViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
