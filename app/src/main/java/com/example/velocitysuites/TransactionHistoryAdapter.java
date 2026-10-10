package com.example.velocitysuites;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/**
 * The Transaction History list: one card per reservation/booking (see item_transaction_history_card.xml for
 * the layout), bound from immutable {@link TransactionRow} snapshots. Replaces PaymentTransactionAdapter,
 * which listed one row per payment event and showed the amount PAID (so a just-submitted P1,800.00 payment
 * read "P0.00 / Not yet paid").
 * <p>
 * Every status, amount and text comes from {@link TransactionStatusHelper}/{@link TransactionText} - nothing
 * is decided here. Changes are dispatched through DiffUtil keyed by the transaction's identity, so a poll that
 * changed nothing touches no view, and one status flip rebinds exactly one card.
 */
public class TransactionHistoryAdapter extends RecyclerView.Adapter<TransactionHistoryAdapter.ViewHolder> {

    /** Tapped a card - opens that transaction's details. */
    public interface OnTransactionClickListener {
        void onTransactionClick(TransactionRow row);
    }

    private final OnTransactionClickListener listener;
    /** The adapter's own copy, replaced wholesale in submit() - never mutated in place, so the next diff always has the previous state to compare against. */
    private List<TransactionRow> rows = new ArrayList<>();
    /** The card to flash (a deep-linked transaction), by stable id; none = Long.MIN_VALUE. */
    private long highlightedStableId = Long.MIN_VALUE;

    public TransactionHistoryAdapter(OnTransactionClickListener listener) {
        setHasStableIds(true);
        this.listener = listener;
    }

    // ---- Data ----

    /** Diffs against what is shown and dispatches only the real changes (none at all for an identical list). */
    public void submit(List<TransactionRow> newRows) {
        List<TransactionRow> next = new ArrayList<>(newRows);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new RowDiff(rows, next));
        rows = next;
        diff.dispatchUpdatesTo(this);
    }

    TransactionRow getRow(int position) {
        return rows.get(position);
    }

    /** Position of the row with this stable id, or -1. */
    int positionOfStableId(long stableId) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).stableId() == stableId) return i;
        }
        return -1;
    }

    /** Flashes the given transaction's card (and stops flashing the previous one). */
    void setHighlightedStableId(long stableId) {
        if (highlightedStableId == stableId) return;
        int before = positionOfStableId(highlightedStableId);
        highlightedStableId = stableId;
        int after = positionOfStableId(stableId);
        if (before >= 0) notifyItemChanged(before);
        if (after >= 0) notifyItemChanged(after);
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @Override
    public long getItemId(int position) {
        return rows.get(position).stableId();
    }

    // ---- Views ----

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction_history_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TransactionRow row = rows.get(position);
        Context ctx = holder.itemView.getContext();
        TransactionStatusHelper.Summary summary = row.summary;

        // Status badge - the same label/colors/icon every screen uses for this status.
        TransactionStatusHelper.Style style = TransactionStatusHelper.styleFor(summary.status);
        StatusBadges.bind(holder.tvStatusBadge, summary.status);

        String reference = TransactionText.reference(ctx, row);
        holder.tvRef.setText(reference);
        String room = TransactionText.roomOrDash(ctx, row.roomText);
        holder.tvRoom.setText(room);
        String total = MoneyFormat.format(summary.grandTotal);
        holder.tvTotal.setText(total);
        holder.tvStay.setText(TransactionText.stay(ctx, row.checkIn, row.checkOut));
        String payment = TransactionText.payment(ctx, summary);
        holder.tvPayment.setText(payment);

        holder.tvRemoved.setVisibility(row.removedByGuest ? View.VISIBLE : View.GONE);

        if (row.receiptCount > 0) {
            holder.tvReceipts.setVisibility(View.VISIBLE);
            holder.tvReceipts.setText(ctx.getResources().getQuantityString(
                    R.plurals.ptx_receipts_available, row.receiptCount, row.receiptCount));
            TextIcons.setRelative(holder.tvReceipts, R.drawable.ic_transaction_history, 0, 1.1f);
        } else {
            holder.tvReceipts.setVisibility(View.GONE);
        }

        // One spoken summary for TalkBack instead of a dozen fragments.
        holder.card.setContentDescription(ctx.getString(R.string.txn_cd_open_format, reference,
                ctx.getString(style.labelRes) + ", " + room + ", " + total + ", " + payment.replace('\n', '.')));
        holder.card.setOnClickListener(v -> {
            if (listener != null) listener.onTransactionClick(row);
        });

        bindHighlight(holder, row.stableId() == highlightedStableId);
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        holder.cancelHighlight();
    }

    /** A deep-linked card flashes a 2dp red border for ~1.5s and fades to the normal hairline; every other card shows the normal hairline. */
    private static void bindHighlight(ViewHolder holder, boolean highlighted) {
        holder.cancelHighlight();
        MaterialCardView card = holder.card;
        Context ctx = card.getContext();
        float density = ctx.getResources().getDisplayMetrics().density;
        int normalColor = ctx.getColor(R.color.velocity_red_alpha_20);
        int normalWidth = Math.round(density);
        card.setStrokeColor(normalColor);
        card.setStrokeWidth(normalWidth);
        if (!highlighted) return;

        int highlightColor = ctx.getColor(R.color.velocity_red_primary);
        int highlightWidth = Math.round(2 * density);
        card.setStrokeColor(highlightColor);
        card.setStrokeWidth(highlightWidth);

        android.animation.ArgbEvaluator evaluator = new android.animation.ArgbEvaluator();
        ValueAnimator fade = ValueAnimator.ofFloat(0f, 1f);
        fade.setStartDelay(600);
        fade.setDuration(900);
        fade.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            card.setStrokeColor((int) evaluator.evaluate(t, highlightColor, normalColor));
            card.setStrokeWidth(Math.round(highlightWidth + (normalWidth - highlightWidth) * t));
        });
        holder.highlightAnimator = fade;
        fade.start();
    }

    // ---- Diff ----

    private static final class RowDiff extends DiffUtil.Callback {
        private final List<TransactionRow> oldRows;
        private final List<TransactionRow> newRows;

        RowDiff(List<TransactionRow> oldRows, List<TransactionRow> newRows) {
            this.oldRows = oldRows;
            this.newRows = newRows;
        }

        @Override
        public int getOldListSize() {
            return oldRows.size();
        }

        @Override
        public int getNewListSize() {
            return newRows.size();
        }

        @Override
        public boolean areItemsTheSame(int oldPosition, int newPosition) {
            return oldRows.get(oldPosition).sameTransactionAs(newRows.get(newPosition));
        }

        @Override
        public boolean areContentsTheSame(int oldPosition, int newPosition) {
            return oldRows.get(oldPosition).sameContentAs(newRows.get(newPosition));
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final TextView tvStatusBadge, tvRef, tvRoom, tvTotal, tvStay, tvPayment, tvReceipts, tvRemoved;
        ValueAnimator highlightAnimator;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.cardTransaction);
            tvStatusBadge = itemView.findViewById(R.id.tvTxnStatusBadge);
            tvRef = itemView.findViewById(R.id.tvTxnRef);
            tvRoom = itemView.findViewById(R.id.tvTxnRoom);
            tvTotal = itemView.findViewById(R.id.tvTxnTotal);
            tvStay = itemView.findViewById(R.id.tvTxnStay);
            tvPayment = itemView.findViewById(R.id.tvTxnPayment);
            tvReceipts = itemView.findViewById(R.id.tvTxnReceipts);
            tvRemoved = itemView.findViewById(R.id.tvTxnRemoved);
        }

        void cancelHighlight() {
            if (highlightAnimator != null) {
                highlightAnimator.cancel();
                highlightAnimator = null;
            }
        }
    }
}
