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

        // One classification for the icon circle, the badge, and (via the shared
        // resolver) the detail screen's header - see PaymentTransactionStatus.
        PaymentTransactionStatus.Style style = PaymentTransactionStatus.styleFor(PaymentTransactionStatus.resolve(tx));
        holder.iconContainer.setCardBackgroundColor(ctx.getColor(style.iconBgColorRes));
        holder.ivIcon.setColorFilter(ctx.getColor(style.iconFgColorRes));
        holder.ivIcon.setImageResource(style.iconRes);

        // Color-coded status badge (Paid / Pending / Cancelled / Unpaid): a SOLID brand-red
        // pill for Paid, soft red for Pending, neutral gray for Cancelled/Unpaid - each with
        // its own leading icon, since the app is red-and-white only and status can't rely on
        // hue alone (see PaymentTransactionStatus#styleFor()).
        holder.tvStatusBadge.setText(style.badgeLabelRes);
        holder.tvStatusBadge.setBackgroundTintList(ctx.getColorStateList(style.badgeBgColorRes));
        holder.tvStatusBadge.setTextColor(ctx.getColor(style.badgeFgColorRes));
        applyBadgeIcon(holder.tvStatusBadge, style.iconRes, style.badgeFgColorRes);

        // Type + reference chip ("Booking #250" / "Reservation #100") - the reference is
        // always the Booking/Reservation's own (same formatting every other screen uses),
        // never a GCash-specific reference, which is absent for a Cash payment and would
        // leave this blank. Colored by NotificationCategoryPresenter, the same mapping the
        // "Filter by status" dropdown and notification cards use, so Booking/Reservation
        // reads as the same color/icon concept everywhere in the app.
        NotificationCategoryPresenter.Result typeCategory = NotificationCategoryPresenter.resolveForBooking(b.isHasBooking());
        holder.tvTypeChip.setText(ctx.getString(
                b.isHasBooking() ? R.string.direct_booking_ref_format : R.string.reservation_ref_format, b.getId()));
        holder.tvTypeChip.setBackgroundTintList(ctx.getColorStateList(typeCategory.bgColorRes));
        holder.tvTypeChip.setTextColor(ctx.getColor(typeCategory.fgColorRes));

        // The room selection ("Deluxe ×2 • Suite") - the same shared summary the Booking/
        // Reservation lists and their Details screen use, so it also names every room type of
        // a multi-room transaction instead of just the first.
        holder.tvTitle.setText(Booking.buildRoomSelectionSummaryText(b, null));

        holder.tvStay.setText(ctx.getString(R.string.ptx_stay_dates_format, b.getCheckInDate(), b.getCheckOutDate()));

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

        // "N Receipt(s) Available" - every already-issued receipt on this
        // booking (PR/FR/OR alike, independently counted - never collapsed to
        // "latest receipt only"), or hidden entirely when none exist yet.
        int receiptCount = b.getReceipts().size();
        if (receiptCount > 0) {
            holder.layoutReceiptsAvailable.setVisibility(View.VISIBLE);
            holder.tvReceiptsAvailable.setText(ctx.getResources().getQuantityString(
                    R.plurals.ptx_receipts_available, receiptCount, receiptCount));
        } else {
            holder.layoutReceiptsAvailable.setVisibility(View.GONE);
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

    /**
     * Leading 12dp icon inside a status pill, tinted to the pill's own text color - the same
     * technique as DashboardActivity#applyPillIcon(). A fresh, mutated drawable per call
     * (never the shared constant state), so tinting one badge can't recolor another.
     */
    private static void applyBadgeIcon(TextView pill, int iconRes, int fgColorRes) {
        Context ctx = pill.getContext();
        android.graphics.drawable.Drawable icon = androidx.core.content.ContextCompat.getDrawable(ctx, iconRes);
        if (icon == null) return;
        icon = icon.mutate();
        int size = (int) (12 * ctx.getResources().getDisplayMetrics().density);
        icon.setBounds(0, 0, size, size);
        icon.setTint(ctx.getColor(fgColorRes));
        pill.setCompoundDrawables(icon, null, null, null);
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

        /** Every field onBindViewHolder() above actually renders: the resolved status (Paid/Pending/Cancelled/Unpaid), type+reference, room selection, stay dates, payment date, amount, paid/remaining progress, and receipts-available count. */
        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            PaymentTransaction a = oldList.get(oldItemPosition);
            PaymentTransaction b = newList.get(newItemPosition);
            Booking ba = a.parentBooking;
            Booking bb = b.parentBooking;
            return PaymentTransactionStatus.resolve(a) == PaymentTransactionStatus.resolve(b)
                    && ba.isHasBooking() == bb.isHasBooking()
                    && Objects.equals(Booking.buildRoomSelectionSummaryText(ba, null), Booking.buildRoomSelectionSummaryText(bb, null))
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
        TextView tvTitle, tvTypeChip, tvStay, tvDate, tvAmount, tvPaymentProgress, tvReceiptsAvailable, tvStatusBadge;
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
            tvStay = itemView.findViewById(R.id.tvPtxStay);
            tvDate = itemView.findViewById(R.id.tvPtxDate);
            tvAmount = itemView.findViewById(R.id.tvPtxAmount);
            tvPaymentProgress = itemView.findViewById(R.id.tvPtxPaymentProgress);
            layoutReceiptsAvailable = itemView.findViewById(R.id.layoutPtxReceiptsAvailable);
            tvReceiptsAvailable = itemView.findViewById(R.id.tvPtxReceiptsAvailable);
        }
    }
}
