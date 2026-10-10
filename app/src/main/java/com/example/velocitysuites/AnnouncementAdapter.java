package com.example.velocitysuites;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.ArrayList;
import java.util.List;

/**
 * Guest-facing announcement card - deliberately shows only the image, title,
 * and a View Details trigger. Publication date, full content, and any
 * audience/role information are guest-inappropriate on the summary card and
 * only ever surface inside the details dialog (never target audience/roles -
 * that stays internal admin/staff information, per LandingActivity's
 * showAnnouncementDetail()).
 */
public class AnnouncementAdapter extends RecyclerView.Adapter<AnnouncementAdapter.ViewHolder> {

    private final List<Announcement> announcements = new ArrayList<>();
    private final OnAnnouncementClickListener listener;

    public interface OnAnnouncementClickListener {
        void onAnnouncementClick(Announcement announcement);
    }

    public AnnouncementAdapter(OnAnnouncementClickListener listener) {
        this.listener = listener;
    }

    public void updateList(List<Announcement> newAnnouncements) {
        announcements.clear();
        if (newAnnouncements != null) announcements.addAll(newAnnouncements);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_announcement, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Announcement announcement = announcements.get(position);
        holder.tvTitle.setText(announcement.getTitle());

        String imageUrl = announcement.getFirstImageUrl();
        if (imageUrl != null && !imageUrl.isEmpty()) {
            holder.ivImage.setVisibility(View.VISIBLE);
            // placeholder+error required here (unlike a one-shot dialog ImageView) -
            // this ImageView is recycled across rows, so a broken/slow URL without
            // an explicit fallback can leave a previous row's announcement image
            // showing behind the new row's content.
            Glide.with(holder.itemView.getContext())
                    .load(imageUrl)
                    .centerCrop()
                    .placeholder(R.drawable.bg_landing_banner_wellness)
                    .error(R.drawable.bg_landing_banner_wellness)
                    .into(holder.ivImage);
        } else {
            holder.ivImage.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onAnnouncementClick(announcement);
        });
    }

    @Override
    public int getItemCount() {
        return announcements.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivImage;
        TextView tvTitle;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivImage = itemView.findViewById(R.id.announcementImage);
            tvTitle = itemView.findViewById(R.id.announcementTitle);
        }
    }
}
