package com.example.velocitysuites;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

/**
 * Feeds a ViewPager2 with a room's photo gallery (4-5 images, ordered) -
 * used both for the compact hero pager inside RoomDetailsDialog and the
 * large pager in the full-screen RoomGalleryActivity, so both surfaces
 * stay in sync with whatever the current gallery data actually is
 * (no hard-coded image arrays anywhere).
 */
public class RoomGalleryPagerAdapter extends RecyclerView.Adapter<RoomGalleryPagerAdapter.PageViewHolder> {

    /** Fires on a tap of an individual page image - a ViewPager2's own click
     * listener never receives events because its internal RecyclerView
     * consumes them, so the listener is attached to each page's ImageView
     * here instead. */
    interface OnPageClickListener {
        void onPageClick(int position);
    }

    private final List<String> imageUrls;
    /** Index-aligned with imageUrls - which individual room each photo came from (e.g. "Room 302"); may be null/shorter than imageUrls, always bounds-checked. */
    private final List<String> imageLabels;
    private final int fallbackImageResId;
    private final OnPageClickListener onPageClickListener;

    public RoomGalleryPagerAdapter(List<String> imageUrls, int fallbackImageResId) {
        this(imageUrls, null, fallbackImageResId, null);
    }

    public RoomGalleryPagerAdapter(List<String> imageUrls, int fallbackImageResId, OnPageClickListener onPageClickListener) {
        this(imageUrls, null, fallbackImageResId, onPageClickListener);
    }

    public RoomGalleryPagerAdapter(List<String> imageUrls, List<String> imageLabels, int fallbackImageResId, OnPageClickListener onPageClickListener) {
        this.imageUrls = imageUrls;
        this.imageLabels = imageLabels;
        this.fallbackImageResId = fallbackImageResId;
        this.onPageClickListener = onPageClickListener;
    }

    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_room_gallery_page, parent, false);
        return new PageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        String url = imageUrls.get(position);
        Glide.with(holder.imageView)
                .load(url)
                .placeholder(fallbackImageResId)
                .error(fallbackImageResId)
                .into(holder.imageView);

        String label = (imageLabels != null && position < imageLabels.size()) ? imageLabels.get(position) : null;
        if (holder.labelView != null) {
            if (label != null && !label.isEmpty()) {
                holder.labelView.setText(label);
                holder.labelView.setVisibility(View.VISIBLE);
            } else {
                holder.labelView.setVisibility(View.GONE);
            }
        }

        if (onPageClickListener != null) {
            holder.imageView.setOnClickListener(v -> {
                int adapterPosition = holder.getBindingAdapterPosition();
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onPageClickListener.onPageClick(adapterPosition);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return imageUrls.size();
    }

    static class PageViewHolder extends RecyclerView.ViewHolder {
        final ImageView imageView;
        final TextView labelView;

        PageViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageRoomGalleryPage);
            labelView = itemView.findViewById(R.id.tvRoomGalleryPageLabel);
        }
    }
}
