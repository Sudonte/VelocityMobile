package com.example.velocitysuites;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Takes a guest from a notification (or any deep link that names a transaction id) to the EXACT
 * reservation or booking it refers to.
 * <p>
 * The hard part is that a reservation and a direct booking live in separate tables with independent id
 * sequences, so "transaction 588" can be two different records. {@link #pick} therefore never matches on the
 * bare id when it has anything better: the caller's exact knowledge (a known family), the receipt number a
 * payment notification carries, the notification's category (a Reservation notification can only be about a
 * reservation). Only when those leave two candidates does it fall back to the more recently active one - a
 * notification is fresh news, so it is far more likely to be about the newer transaction.
 * <p>
 * When the record is not in the loaded window it is fetched directly, trying the table the category makes most
 * likely first; a record that exists in none of them ends in a friendly "no longer available" message rather
 * than a crash or a silent nothing.
 */
final class TransactionNavigator {

    private TransactionNavigator() {
    }

    // ---- Pure selection ----

    /**
     * @param pool             the transactions to choose among (the loaded bookings cache, or the list rows' bookings)
     * @param referenceId      the id the notification/deep link carries
     * @param directBooking    true/false when the caller KNOWS which family it is (e.g. the dashboard holds the Booking),
     *                         null when unknown
     * @param notificationType a Notification.TYPE_* constant, or null
     * @param receiptNumber    the receipt the notification is about, or null
     * @return the transaction, or null when none carries that id
     */
    @Nullable
    static Booking pick(@Nullable List<Booking> pool, @Nullable String referenceId, @Nullable Boolean directBooking,
                        @Nullable String notificationType, @Nullable String receiptNumber) {
        if (pool == null || referenceId == null || referenceId.trim().isEmpty()) return null;
        String id = referenceId.trim();

        List<Booking> candidates = new ArrayList<>();
        for (Booking b : pool) {
            if (b != null && id.equals(b.getId())) candidates.add(b);
        }
        if (candidates.isEmpty()) return null;

        if (directBooking != null) {
            List<Booking> sameFamily = new ArrayList<>();
            for (Booking b : candidates) {
                if (b.isDirectBooking() == directBooking) sameFamily.add(b);
            }
            // The caller's exact knowledge wins - but if it is wrong (the record is not in that family) the
            // id still leads somewhere, so keep the other candidates rather than report "not found".
            if (!sameFamily.isEmpty()) candidates = sameFamily;
        }
        if (candidates.size() == 1) return candidates.get(0);

        // A receipt number belongs to exactly one transaction.
        if (receiptNumber != null && !receiptNumber.trim().isEmpty()) {
            List<Booking> withReceipt = new ArrayList<>();
            for (Booking b : candidates) {
                if (hasReceipt(b, receiptNumber.trim())) withReceipt.add(b);
            }
            if (withReceipt.size() == 1) return withReceipt.get(0);
            if (!withReceipt.isEmpty()) candidates = withReceipt;
        }

        // A Reservation notification is about a reservation-derived transaction, never a direct booking.
        if (Notification.TYPE_RESERVATION.equals(notificationType)) {
            List<Booking> reservations = new ArrayList<>();
            for (Booking b : candidates) {
                if (!b.isDirectBooking()) reservations.add(b);
            }
            if (!reservations.isEmpty()) candidates = reservations;
            if (candidates.size() == 1) return candidates.get(0);
        }

        // Still ambiguous: the more recently active one (ties: the reservation-derived one, the app's primary family).
        Booking best = candidates.get(0);
        for (Booking b : candidates) {
            long a = activity(b);
            long bestActivity = activity(best);
            boolean tieGoesToReservation = a == bestActivity && best.isDirectBooking() && !b.isDirectBooking();
            if (a > bestActivity || tieGoesToReservation) best = b;
        }
        return best;
    }

    private static boolean hasReceipt(Booking b, String receiptNumber) {
        if (b.findReceipt(receiptNumber) != null) return true;
        for (Booking.PaymentTransactionRecord tx : b.getPaymentTransactions()) {
            if (receiptNumber.equals(tx.receiptNumber)) return true;
        }
        return false;
    }

    private static long activity(Booking b) {
        long latest = Math.max(0, b.getCreatedAtMillis());
        for (Booking.PaymentRecord p : b.getPaymentHistory()) {
            if (p != null) latest = Math.max(latest, PaymentDates.parseMillis(p.date));
        }
        return latest;
    }

    /** The tables to ask, most likely first, for a transaction that is not in the loaded window. */
    static List<RoomRepository.TransactionFamily> lookupOrder(@Nullable String notificationType) {
        if (Notification.TYPE_RESERVATION.equals(notificationType)) {
            return Arrays.asList(RoomRepository.TransactionFamily.RESERVATION, RoomRepository.TransactionFamily.DIRECT_BOOKING);
        }
        if (Notification.TYPE_BOOKING.equals(notificationType) || Notification.TYPE_CHECK_IN.equals(notificationType)) {
            return Arrays.asList(RoomRepository.TransactionFamily.DIRECT_BOOKING, RoomRepository.TransactionFamily.RESERVATION);
        }
        return Arrays.asList(RoomRepository.TransactionFamily.RESERVATION, RoomRepository.TransactionFamily.DIRECT_BOOKING);
    }

    // ---- Opening ----

    /** What the caller shows while/after the lookup - kept as callbacks so the same flow serves the list card and the detail screen. */
    interface Listener {
        /** The record is being fetched (it was not already loaded) - show progress / disable the trigger. */
        void onLookupStarted();

        /** The lookup finished one way or the other - hide progress / re-enable the trigger. */
        void onLookupFinished();

        /** No such transaction any more - show {@link #showNotFoundDialog}. */
        void onNotFound();

        /** Couldn't ask (offline, server error) - tell the guest, they can tap again. */
        void onError(String message);
    }

    /** Opens the transaction a notification points at: straight away when it is loaded, else after a direct fetch. */
    static void openFromNotification(@NonNull Activity activity, @NonNull Notification notification, @NonNull Listener listener) {
        RoomRepository repository = RoomRepository.getInstance(activity);
        String referenceId = notification.getReferenceId();
        Booking hit = pick(repository.getAllBookings(), referenceId, null, notification.getType(), notification.getReceiptNumber());
        if (hit != null) {
            activity.startActivity(detailsIntent(activity, hit));
            return;
        }
        if (referenceId == null || referenceId.trim().isEmpty()) {
            listener.onNotFound();
            return;
        }

        listener.onLookupStarted();
        repository.lookupTransaction(referenceId, lookupOrder(notification.getType()), new RoomRepository.TransactionLookupCallback() {
            @Override
            public void onFound(Booking booking) {
                listener.onLookupFinished();
                if (activity.isFinishing() || activity.isDestroyed()) return;
                activity.startActivity(detailsIntent(activity, booking));
            }

            @Override
            public void onNotFound() {
                listener.onLookupFinished();
                if (activity.isFinishing() || activity.isDestroyed()) return;
                listener.onNotFound();
            }

            @Override
            public void onError(String message) {
                listener.onLookupFinished();
                if (activity.isFinishing() || activity.isDestroyed()) return;
                listener.onError(message);
            }
        });
    }

    static Intent detailsIntent(Context context, Booking booking) {
        return TransactionDetailsActivity.newIntent(context, new PaymentTransaction(booking, null));
    }

    /** The friendly message for a transaction that no longer exists. */
    static void showNotFoundDialog(@NonNull Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.deep_link_transaction_not_found_title)
                .setMessage(R.string.deep_link_transaction_not_found)
                .setPositiveButton(R.string.confirm_dialog_positive, null)
                .show();
    }
}
