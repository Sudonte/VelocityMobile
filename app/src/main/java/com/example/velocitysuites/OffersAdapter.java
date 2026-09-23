package com.example.velocitysuites;

import android.text.TextUtils;
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
 * Renders the landing.xml "Ongoing Promotions & Special Offers" section as
 * one combined list of Promotion cards (image, bundle badge, full
 * description, amenities, room type, validity - all shown directly, no
 * separate details step) and Discount cards (simple badge + name +
 * description) - mirroring exactly how the web public Home page's
 * "Promotions & Discounts" section shows both models side by side in a
 * single grid (welcome.blade.php's #promotions section), rather than two
 * separate mobile-only lists.
 */
public class OffersAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_PROMOTION = 0;
    private static final int VIEW_TYPE_DISCOUNT = 1;

    private final List<Object> offers = new ArrayList<>();

    public OffersAdapter() {
    }

    /** Replaces the whole combined list - callers concatenate promotions + discounts before calling this. */
    public void updateList(List<Object> newOffers) {
        offers.clear();
        if (newOffers != null) offers.addAll(newOffers);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return offers.get(position) instanceof Discount ? VIEW_TYPE_DISCOUNT : VIEW_TYPE_PROMOTION;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_DISCOUNT) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_discount, parent, false);
            return new DiscountViewHolder(view);
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_promotion, parent, false);
        return new PromotionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object offer = offers.get(position);
        if (holder instanceof DiscountViewHolder) {
            bindDiscount((DiscountViewHolder) holder, (Discount) offer);
        } else if (holder instanceof PromotionViewHolder) {
            bindPromotion((PromotionViewHolder) holder, (Promotion) offer);
        }
    }

    private void bindDiscount(DiscountViewHolder holder, Discount discount) {
        holder.badge.setText(discount.getBadgeLabel());
        holder.name.setText(discount.getName());
        if (!TextUtils.isEmpty(discount.getDescription())) {
            holder.description.setVisibility(View.VISIBLE);
            holder.description.setText(discount.getDescription());
        } else {
            holder.description.setVisibility(View.GONE);
        }
    }

    private void bindPromotion(PromotionViewHolder holder, Promotion promotion) {
        if (promotion.getImageUrl() != null && !promotion.getImageUrl().isEmpty()) {
            holder.ivImage.setVisibility(View.VISIBLE);
            Glide.with(holder.itemView.getContext())
                    .load(promotion.getImageUrl())
                    .centerCrop()
                    .into(holder.ivImage);
        } else {
            holder.ivImage.setVisibility(View.GONE);
        }

        holder.tvTitle.setText(promotion.getName());

        if (!TextUtils.isEmpty(promotion.getDescription())) {
            holder.tvDescription.setVisibility(View.VISIBLE);
            holder.tvDescription.setText(promotion.getDescription());
        } else {
            holder.tvDescription.setVisibility(View.GONE);
        }

        List<String> amenities = promotion.getIncludedAmenities();
        if (amenities != null && !amenities.isEmpty()) {
            holder.tvAmenities.setVisibility(View.VISIBLE);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < amenities.size(); i++) {
                if (i > 0) sb.append("\n");
                sb.append("✓ ").append(amenities.get(i));
            }
            holder.tvAmenities.setText(sb.toString());
        } else {
            holder.tvAmenities.setVisibility(View.GONE);
        }

        if (promotion.getRoomTypeName() != null) {
            holder.tvRoomType.setVisibility(View.VISIBLE);
            holder.tvRoomType.setText(promotion.getRoomTypeName());
        } else {
            holder.tvRoomType.setVisibility(View.GONE);
        }

        holder.tvValidity.setText(holder.itemView.getContext().getString(R.string.promo_validity_format, promotion.getValidityRange()));
    }

    @Override
    public int getItemCount() {
        return offers.size();
    }

    static class PromotionViewHolder extends RecyclerView.ViewHolder {
        ImageView ivImage;
        TextView tvTitle, tvDescription, tvAmenities, tvRoomType, tvValidity;

        PromotionViewHolder(@NonNull View itemView) {
            super(itemView);
            ivImage = itemView.findViewById(R.id.promoImage);
            tvTitle = itemView.findViewById(R.id.promoTitle);
            tvDescription = itemView.findViewById(R.id.promoDescription);
            tvAmenities = itemView.findViewById(R.id.promoAmenitiesList);
            tvRoomType = itemView.findViewById(R.id.promoRoomType);
            tvValidity = itemView.findViewById(R.id.promoValidity);
        }
    }

    static class DiscountViewHolder extends RecyclerView.ViewHolder {
        TextView badge, name, description;

        DiscountViewHolder(@NonNull View itemView) {
            super(itemView);
            badge = itemView.findViewById(R.id.discountBadge);
            name = itemView.findViewById(R.id.discountName);
            description = itemView.findViewById(R.id.discountDescription);
        }
    }
}
