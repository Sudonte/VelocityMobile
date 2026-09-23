package com.example.velocitysuites;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * Compact, one-row-per-payment-event Transaction History list - separate
 * from TransactionAdapter/item_transaction_card.xml (which stays exactly as
 * it was, still shared by TransactionListActivity's booking-level lists) so
 * this payment-level flattening never touches that other, unrelated screen.
 */
public class PaymentTransactionAdapter extends RecyclerView.Adapter<PaymentTransactionAdapter.ViewHolder> {

    private final List<PaymentTransaction> transactions;
    private final OnTransactionClickListener listener;
    private String highlightedBookingId;

    public interface OnTransactionClickListener {
        void onDetailsClick(PaymentTransaction transaction);
    }

    public PaymentTransactionAdapter(List<PaymentTransaction> transactions, OnTransactionClickListener listener) {
        this.transactions = transactions;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_payment_transaction_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PaymentTransaction tx = transactions.get(position);
        Booking b = tx.parentBooking;
        Context ctx = holder.itemView.getContext();

        String status = tx.getStatus() != null ? tx.getStatus() : "";
        boolean cancelled = status.equalsIgnoreCase("Cancelled") || status.equalsIgnoreCase("Rejected") || status.equalsIgnoreCase("failed");
        boolean pending = status.equalsIgnoreCase("pending") || b.isPaymentPendingVerification();

        int bgColorRes, fgColorRes, iconRes;
        String title;
        if (cancelled) {
            title = ctx.getString(R.string.ptx_title_cancelled);
            bgColorRes = R.color.velocity_gray_soft;
            fgColorRes = R.color.velocity_inactive_gray;
            iconRes = R.drawable.ic_close;
        } else if (pending) {
            title = ctx.getString(R.string.ptx_title_pending);
            bgColorRes = R.color.velocity_blue_soft;
            fgColorRes = R.color.velocity_blue_primary;
            iconRes = R.drawable.ic_clock;
        } else {
            title = ctx.getString(R.string.ptx_title_successful);
            bgColorRes = R.color.velocity_green_soft;
            fgColorRes = R.color.velocity_green_dark;
            iconRes = R.drawable.ic_check_circle;
        }
        holder.tvTitle.setText(title);
        holder.iconContainer.setCardBackgroundColor(ctx.getColor(bgColorRes));
        holder.ivIcon.setColorFilter(ctx.getColor(fgColorRes));
        holder.ivIcon.setImageResource(iconRes);

        String typeLabel = b.isHasBooking()
                ? ctx.getString(R.string.ptx_type_booking_payment)
                : ctx.getString(R.string.ptx_type_reservation_payment);
        holder.tvSubtitle.setText(ctx.getString(R.string.ptx_subtitle_format, typeLabel, b.getRoomName()));

        String date = tx.getDate();
        holder.tvDate.setText(date != null && !date.isEmpty() ? date : ctx.getString(R.string.label_not_available));

        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
        holder.tvAmount.setText(currencyFormat.format(tx.getAmount()));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onDetailsClick(tx);
        });

        if (holder.itemView instanceof MaterialCardView) {
            MaterialCardView card = (MaterialCardView) holder.itemView;
            if (highlightedBookingId != null && highlightedBookingId.equals(b.getId())) {
                card.setStrokeColor(ctx.getColor(R.color.velocity_red_primary));
                card.setStrokeWidth((int) (2 * ctx.getResources().getDisplayMetrics().density));
            } else {
                card.setStrokeWidth(0);
            }
        }
    }

    public void updateList(List<PaymentTransaction> newList) {
        this.transactions.clear();
        this.transactions.addAll(newList);
        notifyDataSetChanged();
    }

    public void setHighlightedBookingId(String bookingId) {
        this.highlightedBookingId = bookingId;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return transactions.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvSubtitle, tvDate, tvAmount;
        MaterialCardView iconContainer;
        ImageView ivIcon;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            iconContainer = itemView.findViewById(R.id.iconContainerPtx);
            ivIcon = itemView.findViewById(R.id.ivPtxIcon);
            tvTitle = itemView.findViewById(R.id.tvPtxTitle);
            tvSubtitle = itemView.findViewById(R.id.tvPtxSubtitle);
            tvDate = itemView.findViewById(R.id.tvPtxDate);
            tvAmount = itemView.findViewById(R.id.tvPtxAmount);
        }
    }
}
