package com.example.velocitysuites;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.ViewHolder> {

    // Built once instead of on every bind - NumberFormat instance construction
    // does a locale data lookup and isn't free; RecyclerView binding always runs
    // on the main thread so a single shared (non-thread-safe) instance is safe.
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));

    private final List<Booking> bookings;
    private final OnTransactionClickListener listener;
    private final OnDeleteClickListener deleteListener;
    private String highlightedBookingId;

    public interface OnTransactionClickListener {
        void onDetailsClick(Booking booking);
    }

    public interface OnDeleteClickListener {
        void onDeleteClick(Booking booking);
    }

    public TransactionAdapter(List<Booking> bookings, OnTransactionClickListener listener) {
        this(bookings, listener, null);
    }

    public TransactionAdapter(List<Booking> bookings, OnTransactionClickListener listener, OnDeleteClickListener deleteListener) {
        this.bookings = bookings;
        this.listener = listener;
        this.deleteListener = deleteListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Booking booking = bookings.get(position);
        boolean noShow = booking.isNoShow();
        holder.tvBookingId.setText(holder.itemView.getContext().getString(R.string.booking_id_format, booking.getId()));
        holder.tvStatusBadge.setText(noShow ? holder.itemView.getContext().getString(R.string.status_no_show) : booking.getStatus());
        holder.tvRoomName.setText(booking.getRoomName());
        
        String dateInfo = booking.getCheckInDate() + " – " + booking.getCheckOutDate();
        holder.tvStayDates.setText(dateInfo);

        holder.tvTotalAmount.setText(CURRENCY_FORMAT.format(booking.getTotalAmount()));

        android.content.Context cardCtx = holder.itemView.getContext();
        String naLabel = cardCtx.getString(R.string.label_not_available);
        holder.tvCardPaymentMethod.setText(cardCtx.getString(R.string.payment_method_label,
                booking.getPaymentMethod() != null ? booking.getPaymentMethod() : naLabel));
        holder.tvCardPaymentDate.setText(cardCtx.getString(R.string.payment_date_label,
                booking.getPaymentDate() != null ? booking.getPaymentDate() : naLabel));
        holder.tvCardTransactionRef.setText(cardCtx.getString(R.string.transaction_ref_label,
                booking.getTransactionRef() != null ? booking.getTransactionRef() : naLabel));

        if (holder.tvPaymentStatusPill2 != null) {
            android.content.Context ctx = holder.itemView.getContext();
            PaymentStatusResolver.Result result = PaymentStatusResolver.resolve(ctx, booking);
            holder.tvPaymentStatusPill2.setText(result.label);
            holder.tvPaymentStatusPill2.setBackgroundTintList(ctx.getColorStateList(result.bgColorRes));
            holder.tvPaymentStatusPill2.setTextColor(ctx.getColor(result.fgColorRes));

            if (holder.iconContainerTx != null && holder.ivTxStatusIcon != null) {
                holder.iconContainerTx.setCardBackgroundColor(ctx.getColor(result.bgColorRes));
                holder.ivTxStatusIcon.setColorFilter(ctx.getColor(result.fgColorRes));
            }
        }

        if (holder.layoutExpandableDetails != null) {
            holder.layoutExpandableDetails.setVisibility(View.GONE);
            holder.btnExpandTransaction.setImageResource(R.drawable.ic_expand_more);
            if (holder.tvExpandRoomType != null) {
                holder.tvExpandRoomType.setText(holder.itemView.getContext().getString(R.string.booking_detail_room_type,
                        android.text.TextUtils.join(", ", booking.getAllRoomTypeNames())));
            }
            if (holder.tvExpandGuests != null) {
                holder.tvExpandGuests.setText(holder.itemView.getContext().getString(R.string.guests_label, booking.getGuests()));
            }
            if (holder.tvExpandTransactionRef != null) {
                String ref = booking.getTransactionRef() != null ? booking.getTransactionRef() : holder.itemView.getContext().getString(R.string.label_not_available);
                holder.tvExpandTransactionRef.setText(holder.itemView.getContext().getString(R.string.transaction_ref_label, ref));
            }
            holder.btnExpandTransaction.setOnClickListener(v -> {
                boolean expanded = holder.layoutExpandableDetails.getVisibility() == View.VISIBLE;
                holder.layoutExpandableDetails.setVisibility(expanded ? View.GONE : View.VISIBLE);
                holder.btnExpandTransaction.setImageResource(expanded ? R.drawable.ic_expand_more : R.drawable.ic_expand_less);
            });
        }

        holder.btnViewDetails.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDetailsClick(booking);
            }
        });

        if (deleteListener != null) {
            holder.btnDeleteRecord.setVisibility(View.VISIBLE);
            holder.btnDeleteRecord.setOnClickListener(v -> deleteListener.onDeleteClick(booking));
        } else {
            holder.btnDeleteRecord.setVisibility(View.GONE);
        }

        // Set status badge color based on status - kept in sync with the same
        // Confirmed/Checked-In/Checked-Out/Verified = success convention
        // DashboardActivity and UpcomingTransactionsActivity use.
        if ("Confirmed".equalsIgnoreCase(booking.getStatus()) || "Checked-In".equalsIgnoreCase(booking.getStatus())
                || "Checked-Out".equalsIgnoreCase(booking.getStatus()) || "Verified".equalsIgnoreCase(booking.getStatus())) {
            holder.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_success);
        } else if ("Cancelled".equalsIgnoreCase(booking.getStatus()) || "Rejected".equalsIgnoreCase(booking.getStatus())) {
            holder.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_error);
        } else {
            holder.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_warning);
        }

        // Deep-link highlight for a Booking ID passed from the dashboard - outlines the exact
        // card so it's unambiguous which transaction the guest tapped, without hiding the rest.
        if (holder.itemView instanceof MaterialCardView) {
            MaterialCardView card = (MaterialCardView) holder.itemView;
            android.content.Context ctx = holder.itemView.getContext();
            if (highlightedBookingId != null && highlightedBookingId.equals(booking.getId())) {
                card.setStrokeColor(ctx.getColor(R.color.velocity_red_primary));
                card.setStrokeWidth((int) (2 * ctx.getResources().getDisplayMetrics().density));
            } else {
                card.setStrokeWidth(0);
            }
        }
    }

    public void updateList(List<Booking> newList) {
        this.bookings.clear();
        this.bookings.addAll(newList);
        notifyDataSetChanged();
    }

    public void setHighlightedBookingId(String bookingId) {
        this.highlightedBookingId = bookingId;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return bookings.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvBookingId, tvStatusBadge, tvRoomName, tvStayDates, tvTotalAmount;
        TextView tvPaymentStatusPill2, tvExpandRoomType, tvExpandGuests, tvExpandTransactionRef;
        TextView tvCardPaymentMethod, tvCardPaymentDate, tvCardTransactionRef;
        View layoutExpandableDetails;
        ImageButton btnExpandTransaction;
        MaterialButton btnViewDetails;
        ImageButton btnDeleteRecord;
        MaterialCardView iconContainerTx;
        android.widget.ImageView ivTxStatusIcon;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            iconContainerTx = itemView.findViewById(R.id.iconContainerTx);
            ivTxStatusIcon = itemView.findViewById(R.id.ivTxStatusIcon);
            tvBookingId = itemView.findViewById(R.id.tvBookingId);
            tvStatusBadge = itemView.findViewById(R.id.tvStatusBadge);
            tvRoomName = itemView.findViewById(R.id.tvRoomName);
            tvStayDates = itemView.findViewById(R.id.tvStayDates);
            tvTotalAmount = itemView.findViewById(R.id.tvTotalAmount);
            tvPaymentStatusPill2 = itemView.findViewById(R.id.tvPaymentStatusPillTx);
            tvCardPaymentMethod = itemView.findViewById(R.id.tvCardPaymentMethod);
            tvCardPaymentDate = itemView.findViewById(R.id.tvCardPaymentDate);
            tvCardTransactionRef = itemView.findViewById(R.id.tvCardTransactionRef);
            layoutExpandableDetails = itemView.findViewById(R.id.layoutExpandableDetails);
            btnExpandTransaction = itemView.findViewById(R.id.btnExpandTransaction);
            tvExpandRoomType = itemView.findViewById(R.id.tvExpandRoomType);
            tvExpandGuests = itemView.findViewById(R.id.tvExpandGuests);
            tvExpandTransactionRef = itemView.findViewById(R.id.tvExpandTransactionRef);
            btnViewDetails = itemView.findViewById(R.id.btnViewDetails);
            btnDeleteRecord = itemView.findViewById(R.id.btnDeleteRecord);
        }
    }
}
