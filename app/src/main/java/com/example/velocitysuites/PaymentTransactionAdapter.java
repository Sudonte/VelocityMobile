package com.example.velocitysuites;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Compact, one-row-per-payment-event Transaction History list - separate
 * from TransactionAdapter/item_transaction_card.xml (which stays exactly as
 * it was, still shared by TransactionListActivity's booking-level lists) so
 * this payment-level flattening never touches that other, unrelated screen.
 */
public class PaymentTransactionAdapter extends RecyclerView.Adapter<PaymentTransactionAdapter.ViewHolder> {

    // Built once instead of on every bind - see TransactionAdapter's identical field for why.
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));

    private final List<PaymentTransaction> transactions;
    private final OnTransactionClickListener listener;
    private String highlightedBookingId;

    public interface OnTransactionClickListener {
        void onDetailsClick(PaymentTransaction transaction);
    }

    public PaymentTransactionAdapter(List<PaymentTransaction> transactions, OnTransactionClickListener listener) {
        setHasStableIds(true);
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

        // Color-coded status badge - same cancelled/pending/successful classification
        // already computed above for the title/icon, just also shown as a distinct pill
        // (task requirement: a color-coded status badge on each Transaction History card).
        if (holder.tvStatusBadge != null) {
            holder.tvStatusBadge.setText(title);
            holder.tvStatusBadge.setBackgroundTintList(ctx.getColorStateList(bgColorRes));
            holder.tvStatusBadge.setTextColor(ctx.getColor(fgColorRes));
        }

        // Type chip - same NotificationCategoryPresenter mapping the "Filter by
        // status" dropdown and notification cards use, so Booking/Reservation
        // reads as the same color/icon concept everywhere in the app.
        if (holder.tvTypeChip != null) {
            NotificationCategoryPresenter.Result typeCategory = NotificationCategoryPresenter.resolveForBooking(b.isHasBooking());
            holder.tvTypeChip.setText(b.isHasBooking() ? R.string.quick_action_booking : R.string.quick_action_reservation);
            holder.tvTypeChip.setBackgroundTintList(ctx.getColorStateList(typeCategory.bgColorRes));
            holder.tvTypeChip.setTextColor(ctx.getColor(typeCategory.fgColorRes));
        }
        // Room name only - the type chip above already conveys Booking-vs-Reservation.
        holder.tvSubtitle.setText(b.getRoomName());

        // Task requirement: the card itself (not just the expanded detail
        // screen) must show a reference number and check-in/check-out dates.
        // Always the Booking/Reservation's own reference (Booking #.../
        // Reservation #...), never the payment-specific GCash reference -
        // that's absent for a Cash payment, and this line must never be
        // blank for a Cash transaction just because of that.
        if (holder.tvRefAndStay != null) {
            String ref = ctx.getString(b.isHasBooking() ? R.string.direct_booking_ref_format : R.string.reservation_ref_format, b.getId());
            holder.tvRefAndStay.setText(ctx.getString(R.string.ptx_ref_and_stay_format, ref, b.getCheckInDate(), b.getCheckOutDate()));
        }

        String date = tx.getDate();
        // A synthetic summary row for a Cash Pay-Later Reservation nobody has
        // paid against yet (see PaymentTransaction#getDate()'s own fallback to
        // parentBooking.getPaymentDate(), which is genuinely null in that
        // case) has no payment date to show - "Not yet paid" states that
        // honestly instead of the generic, alarming "N/A" this used to show.
        holder.tvDate.setText(date != null && !date.isEmpty() ? date : ctx.getString(R.string.transaction_date_not_yet_paid));

        holder.tvAmount.setText(CURRENCY_FORMAT.format(tx.getAmount()));

        // Booking-level Paid/Remaining - authoritative payment_summary when the
        // backend has attached one, else the legacy fields (Booking#getEffectiveTotalAmountPaid()'s
        // own fallback rule) - never this one transaction's own amount above.
        // Task requirement: only shown when PARTIALLY paid (something paid, something
        // still owed) - a fully-paid or still-unpaid transaction has nothing "in
        // progress" to report, and showing "Paid PHP0.00 - Remaining PHPX" for a
        // still-pending reservation read as confusing rather than informative.
        if (holder.tvPaymentProgress != null) {
            double effectivePaid = b.getEffectiveTotalAmountPaid();
            double effectiveRemaining = b.getEffectiveRemainingBalance();
            boolean isPartiallyPaid = effectivePaid > 0.009 && effectiveRemaining > 0.009;
            if (isPartiallyPaid) {
                holder.tvPaymentProgress.setVisibility(View.VISIBLE);
                holder.tvPaymentProgress.setText(ctx.getString(R.string.ptx_payment_progress_format,
                        CURRENCY_FORMAT.format(effectivePaid), CURRENCY_FORMAT.format(effectiveRemaining)));
            } else {
                holder.tvPaymentProgress.setVisibility(View.GONE);
            }
        }

        // "N Receipt(s) Available" - every already-issued receipt on this
        // booking (PR/FR/OR alike, independently counted - never collapsed to
        // "latest receipt only"), or hidden entirely when none exist yet.
        if (holder.layoutReceiptsAvailable != null && holder.tvReceiptsAvailable != null) {
            int receiptCount = b.getReceipts().size();
            if (receiptCount > 0) {
                holder.layoutReceiptsAvailable.setVisibility(View.VISIBLE);
                holder.tvReceiptsAvailable.setText(ctx.getResources().getQuantityString(
                        R.plurals.ptx_receipts_available, receiptCount, receiptCount));
            } else {
                holder.layoutReceiptsAvailable.setVisibility(View.GONE);
            }
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onDetailsClick(tx);
        });

        if (holder.itemView instanceof MaterialCardView) {
            MaterialCardView card = (MaterialCardView) holder.itemView;
            // Deep-link highlight - a brief, smooth flash-then-fade (matching
            // NotificationAdapter's identical treatment) rather than a static
            // border that stayed until something else changed.
            if (highlightedBookingId != null && highlightedBookingId.equals(b.getId())) {
                startHighlightFade(card, ctx);
            } else {
                card.setStrokeWidth(0);
            }
        }
    }

    /** Bright red stroke -> no stroke, over ~1.5s - see NotificationAdapter#startHighlightFade()'s identical doc for the full reasoning (short hold, then a real fade, never an instant snap). */
    private void startHighlightFade(MaterialCardView card, Context ctx) {
        float density = ctx.getResources().getDisplayMetrics().density;
        int highlightColor = ctx.getColor(R.color.velocity_red_primary);
        int highlightWidth = (int) (2 * density);

        card.setStrokeColor(highlightColor);
        card.setStrokeWidth(highlightWidth);

        android.animation.ValueAnimator widthFade = android.animation.ValueAnimator.ofInt(highlightWidth, 0);
        widthFade.setStartDelay(600);
        widthFade.setDuration(900);
        widthFade.addUpdateListener(a -> card.setStrokeWidth((int) a.getAnimatedValue()));
        widthFade.start();
    }

    /**
     * Diffs the new list against what's currently shown (by the same
     * identity isSameTransaction()/getItemId() use, see that method's own
     * doc) and dispatches only the precise resulting changes - a poll that
     * found nothing new or changed produces a DiffResult with zero
     * operations, so this never calls any notify*() method at all in that
     * case; an update to one existing row (e.g. a status change) rebinds
     * only that row instead of the whole visible list.
     */
    public void updateList(List<PaymentTransaction> newList) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new PaymentTransactionDiffCallback(this.transactions, newList));
        this.transactions.clear();
        this.transactions.addAll(newList);
        diffResult.dispatchUpdatesTo(this);
    }

    /** Same identity two payment-transaction rows share when they're "the same row, possibly with updated data" - parentBooking id plus this specific payment's own referenceNumber/date/amount, since Booking.PaymentRecord itself has no numeric id. Shared by getItemId() and PaymentTransactionDiffCallback so both use the exact same notion of identity. */
    private static boolean isSameTransaction(PaymentTransaction a, PaymentTransaction b) {
        if (!Objects.equals(a.parentBooking.getId(), b.parentBooking.getId())) return false;
        if (a.record == null && b.record == null) return true;
        if (a.record == null || b.record == null) return false;
        return Objects.equals(a.record.referenceNumber, b.record.referenceNumber)
                && Objects.equals(a.record.date, b.record.date)
                && Objects.equals(a.record.amount, b.record.amount);
    }

    private static class PaymentTransactionDiffCallback extends DiffUtil.Callback {
        private final List<PaymentTransaction> oldList;
        private final List<PaymentTransaction> newList;

        PaymentTransactionDiffCallback(List<PaymentTransaction> oldList, List<PaymentTransaction> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return isSameTransaction(oldList.get(oldItemPosition), newList.get(newItemPosition));
        }

        /** Every field onBindViewHolder() above actually renders: status classification (+ pending-verification flag), type+room, reference+stay dates, payment date, amount, paid/remaining progress, and receipts-available count. */
        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            PaymentTransaction a = oldList.get(oldItemPosition);
            PaymentTransaction b = newList.get(newItemPosition);
            Booking ba = a.parentBooking;
            Booking bb = b.parentBooking;
            return Objects.equals(a.getStatus(), b.getStatus())
                    && ba.isPaymentPendingVerification() == bb.isPaymentPendingVerification()
                    && ba.isHasBooking() == bb.isHasBooking()
                    && Objects.equals(ba.getRoomName(), bb.getRoomName())
                    && Objects.equals(ba.getCheckInDate(), bb.getCheckInDate())
                    && Objects.equals(ba.getCheckOutDate(), bb.getCheckOutDate())
                    && Objects.equals(a.getDate(), b.getDate())
                    && a.getAmount() == b.getAmount()
                    && ba.getEffectiveTotalAmountPaid() == bb.getEffectiveTotalAmountPaid()
                    && ba.getEffectiveRemainingBalance() == bb.getEffectiveRemainingBalance()
                    && ba.getReceipts().size() == bb.getReceipts().size();
        }
    }

    /**
     * A stable per-row identity (see isSameTransaction()'s own doc for the
     * exact fields) - lets RecyclerView's default item animator and
     * saved-state logic track a row correctly across a refresh/load-more
     * instead of treating every position as a brand-new view every time
     * this adapter updates.
     */
    @Override
    public long getItemId(int position) {
        PaymentTransaction tx = transactions.get(position);
        int recordKey = tx.record != null
                ? Objects.hash(tx.record.referenceNumber, tx.record.date, tx.record.amount)
                : 0;
        return Objects.hash(tx.parentBooking.getId(), recordKey);
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
        TextView tvTitle, tvTypeChip, tvSubtitle, tvRefAndStay, tvDate, tvAmount, tvPaymentProgress, tvReceiptsAvailable, tvStatusBadge;
        MaterialCardView iconContainer;
        ImageView ivIcon;
        View layoutReceiptsAvailable;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            iconContainer = itemView.findViewById(R.id.iconContainerPtx);
            ivIcon = itemView.findViewById(R.id.ivPtxIcon);
            tvTitle = itemView.findViewById(R.id.tvPtxTitle);
            tvTypeChip = itemView.findViewById(R.id.tvPtxTypeChip);
            tvStatusBadge = itemView.findViewById(R.id.tvPtxStatusBadge);
            tvSubtitle = itemView.findViewById(R.id.tvPtxSubtitle);
            tvRefAndStay = itemView.findViewById(R.id.tvPtxRefAndStay);
            tvDate = itemView.findViewById(R.id.tvPtxDate);
            tvAmount = itemView.findViewById(R.id.tvPtxAmount);
            tvPaymentProgress = itemView.findViewById(R.id.tvPtxPaymentProgress);
            layoutReceiptsAvailable = itemView.findViewById(R.id.layoutPtxReceiptsAvailable);
            tvReceiptsAvailable = itemView.findViewById(R.id.tvPtxReceiptsAvailable);
        }
    }
}
