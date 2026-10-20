package com.example.velocitysuites;

import android.content.Context;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.velocitysuites.network.ApiMapper;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The timeline of ONE transaction, newest first, from creation to check-out. Pure data: it is built from every dated fact the
 * app can get - the transaction's own timestamps ({@link Booking#getEventTime}), its payment records, its amenity requests
 * (GET guest/bookings/{id}/additional-amenities) and the guest's notifications about it (the front desk's actions create those).
 *
 * <p>The same event reported by two sources is shown once: events that can happen only once (created, verified, checked in, ...)
 * are taken from the transaction itself and a notification for the same kind is dropped; a payment notification is dropped when a
 * payment record of the same kind exists within {@link #PAYMENT_WINDOW_MS}; amenity notifications are used only while the
 * amenity request list is not available. (The server has no audit-log endpoint, so this is the most complete trail the app can build.)
 */
final class TransactionTimeline {

    enum Kind {
        CREATED, EDITED, VERIFIED, CONVERTED, PAYMENT_SUBMITTED, PAYMENT_VERIFIED, PAYMENT_REJECTED, CHECKED_IN,
        AMENITY_REQUESTED, AMENITY_APPROVED, AMENITY_REJECTED, CHECKED_OUT, SETTLED, CANCELLED, REJECTED
    }

    static final class Entry {
        /** Stable id of the event, to tell new entries from ones already shown. */
        final String key;
        final Kind kind;
        final long millis;
        final String title;
        @Nullable final String detail;
        final String when;
        @DrawableRes final int icon;
        /** True for the newest entry: the current step. */
        boolean current;

        Entry(String key, Kind kind, long millis, String title, @Nullable String detail, String when, @DrawableRes int icon) {
            this.key = key;
            this.kind = kind;
            this.millis = millis;
            this.title = title;
            this.detail = detail;
            this.when = when;
            this.icon = icon;
        }
    }

    static final long PAYMENT_WINDOW_MS = 10 * 60 * 1000L;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a", Locale.ENGLISH);
    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");

    private TransactionTimeline() {
    }

    static String format(long millis) {
        return WHEN.format(Instant.ofEpochMilli(millis).atZone(MANILA));
    }

    @NonNull
    static List<Entry> of(@NonNull Context c, @NonNull Booking b) {
        return build(c, b, AmenityRoomSources.api(b), notificationsOf(c, b));
    }

    private static List<Notification> notificationsOf(Context c, Booking b) {
        List<Notification> out = new ArrayList<>();
        try {
            for (Notification n : RoomRepository.getInstance(c).getNotifications()) {
                if (n != null && n.getReferenceId() != null && n.getReferenceId().equals(b.getId())
                        && NotificationPrimaryActionResolver.isTransactionCategory(n.getType())) {
                    out.add(n);
                }
            }
        } catch (RuntimeException ignored) {
            // notifications are an extra source; the timeline stands without them
        }
        return out;
    }

    @NonNull
    static List<Entry> build(@NonNull Context c, @NonNull Booking b, @Nullable List<AmenityRecord> amenityRequests,
                             @NonNull List<Notification> notifications) {
        List<Entry> out = new ArrayList<>();
        boolean booking = b.isHasBooking();
        String ref = TransactionText.reference(c, booking, b.getDisplayId());

        // ---- payments (the server's own records)
        for (Booking.PaymentTransactionRecord p : b.getPaymentTransactions()) addPayment(c, out, p);

        // ---- amenity requests
        if (amenityRequests != null) {
            for (AmenityRecord r : amenityRequests) addAmenity(c, out, r);
        }

        // ---- the server's own timeline (the main source: it is what the front desk's actions wrote)
        for (Booking.TimelineEntry t : b.getTimeline()) {
            long at = ms(t.atRaw);
            Kind kind = kindOfServerStep(t.key, t.label);
            if (at <= 0 || kind == null || "Pending".equalsIgnoreCase(t.status)) continue;
            if ((kind == Kind.PAYMENT_VERIFIED || kind == Kind.PAYMENT_REJECTED || kind == Kind.PAYMENT_SUBMITTED) && hasNear(out, kind, at)) continue;
            add(out, "srv|" + t.key, kind, at, t.label, null, iconFor(kind));
        }

        // ---- the transaction's own timestamps
        long created = ms(b.getEventTime("created"));
        if (created == 0) created = b.getCreatedAtMillis();
        add(out, "created", Kind.CREATED, created, c.getString(booking && b.isDirectBooking() ? R.string.tl_booking_created : R.string.tl_reservation_created),
                datesLine(c, b), R.drawable.ic_booking);
        add(out, "edited", Kind.EDITED, ms(b.getEventTime("edited")), c.getString(R.string.tl_reservation_modified), null, R.drawable.ic_edit);
        add(out, "verified", Kind.VERIFIED, ms(b.getEventTime("verified")),
                c.getString(booking ? R.string.tl_booking_verified : R.string.tl_reservation_verified), null, R.drawable.ic_check_circle);
        if (booking && !b.isDirectBooking()) {
            add(out, "converted", Kind.CONVERTED, ms(b.getEventTime("confirmed")), c.getString(R.string.tl_converted_to_booking),
                    c.getString(R.string.tl_converted_detail, ref), R.drawable.ic_check_circle);
        }
        add(out, "checked_in", Kind.CHECKED_IN, ms(b.getEventTime("checked_in")), c.getString(R.string.tl_checked_in), roomsLine(c, b), R.drawable.ic_booking);
        add(out, "checked_out", Kind.CHECKED_OUT, ms(b.getEventTime("checked_out")), c.getString(R.string.tl_checked_out), null, R.drawable.ic_booking);
        long completed = ms(b.getEventTime("completed"));
        if (completed > 0 && TransactionStatusHelper.summarize(b).balance <= MoneyFormat.EPSILON) {
            add(out, "settled", Kind.SETTLED, completed, c.getString(R.string.tl_final_balance_settled), null, R.drawable.ic_check_circle);
        }
        String status = b.getStatus() == null ? "" : b.getStatus();
        long cancelled = ms(b.getEventTime("cancelled"));
        if ("Rejected".equalsIgnoreCase(status)) {
            add(out, "rejected", Kind.REJECTED, cancelled, c.getString(R.string.tl_rejected), clean(b.getTransactionRejectionReason()), R.drawable.ic_close);
        } else if ("Cancelled".equalsIgnoreCase(status)) {
            add(out, "cancelled", Kind.CANCELLED, cancelled,
                    c.getString(booking ? R.string.tl_booking_cancelled : R.string.tl_reservation_cancelled), clean(b.getTransactionRejectionReason()), R.drawable.ic_close);
        }

        // ---- notifications: only what the sources above do not already say
        for (Notification n : notifications) addFromNotification(c, out, n, amenityRequests != null);

        Collections.sort(out, (x, y) -> Long.compare(y.millis, x.millis));
        if (!out.isEmpty()) out.get(0).current = true;
        return out;
    }

    // ---- sources

    private static void addPayment(Context c, List<Entry> out, Booking.PaymentTransactionRecord p) {
        String detail = MoneyFormat.format(p.amountPaid);
        String method = DisplayLabels.paymentMethod(c, p.paymentMethod);
        if (!method.isEmpty()) detail += " · " + method;
        long submitted = ms(p.paymentDate);
        String verification = (p.verificationStatus == null ? "" : p.verificationStatus).toLowerCase(Locale.ROOT);
        String payStatus = (p.paymentStatus == null ? "" : p.paymentStatus).toLowerCase(Locale.ROOT);
        boolean rejected = verification.contains("reject") || payStatus.contains("reject") || payStatus.equals("failed");
        boolean verified = !rejected && (p.verifiedAt != null || verification.equals("verified") || payStatus.equals("completed"));
        long decided = ms(p.verifiedAt);
        if (decided == 0) decided = submitted;
        add(out, "pay_sub|" + p.id, Kind.PAYMENT_SUBMITTED, submitted, c.getString(R.string.tl_payment_submitted), detail, R.drawable.ic_payment);
        if (rejected) {
            add(out, "pay_rej|" + p.id, Kind.PAYMENT_REJECTED, decided, c.getString(R.string.tl_payment_rejected),
                    p.rejectionReason != null && !p.rejectionReason.trim().isEmpty() ? detail + " · " + p.rejectionReason.trim() : detail, R.drawable.ic_close);
        } else if (verified) {
            add(out, "pay_ver|" + p.id, Kind.PAYMENT_VERIFIED, decided, c.getString(R.string.tl_payment_verified), detail, R.drawable.ic_check_circle);
        }
    }

    private static void addAmenity(Context c, List<Entry> out, AmenityRecord r) {
        String detail = r.name + " × " + r.quantity;
        if (r.room != null) detail += " · " + c.getString(R.string.room_card_title_format, r.room);
        if (r.subtotal > 0) detail += " · " + MoneyFormat.format(r.subtotal);
        String id = r.id != 0 ? String.valueOf(r.id) : r.nameKey() + "|" + r.quantity + "|" + r.room;
        add(out, "am_req|" + id, Kind.AMENITY_REQUESTED, r.requestedAtMillis, c.getString(R.string.tl_amenity_requested), detail, R.drawable.ic_amenity_concierge);
        if (r.status == AmenityRecord.Status.APPROVED) {
            add(out, "am_dec|" + id, Kind.AMENITY_APPROVED, r.decidedAtMillis, c.getString(R.string.tl_amenity_approved), detail, R.drawable.ic_check_circle);
        } else if (r.status == AmenityRecord.Status.REJECTED) {
            add(out, "am_dec|" + id, Kind.AMENITY_REJECTED, r.decidedAtMillis, c.getString(R.string.tl_amenity_rejected), detail, R.drawable.ic_close);
        }
    }

    private static void addFromNotification(Context c, List<Entry> out, Notification n, boolean haveAmenityList) {
        long when = n.getCreatedAtMillis();
        if (when <= 0) return;
        String text = ((n.getTitle() == null ? "" : n.getTitle()) + " " + (n.getMessage() == null ? "" : n.getMessage())).toLowerCase(Locale.ROOT);
        if (text.contains("amenit")) {
            if (haveAmenityList) return; // the request list already says it
            AmenityRecord r = NotificationText.amenityRecordIn(n.getMessage());
            if (r == null || r.status == AmenityRecord.Status.PENDING) return;
            addAmenity(c, out, new AmenityRecord(0, r.name, r.quantity, r.subtotal, r.room, null, r.status, 0, when));
            return;
        }
        Kind kind = kindOf(text);
        if (kind == null) return;
        if (!once(kind) && hasNear(out, kind, when)) return; // the same payment event, already shown by a payment record
        int title = kind == Kind.PAYMENT_VERIFIED ? R.string.tl_payment_verified : kind == Kind.PAYMENT_REJECTED ? R.string.tl_payment_rejected
                : kind == Kind.VERIFIED ? R.string.tl_booking_verified : kind == Kind.CHECKED_IN ? R.string.tl_checked_in
                : kind == Kind.CHECKED_OUT ? R.string.tl_checked_out : kind == Kind.CANCELLED ? R.string.tl_booking_cancelled
                : kind == Kind.REJECTED ? R.string.tl_rejected : R.string.tl_converted_to_booking;
        add(out, "n|" + n.getId(), kind, when, c.getString(title), null, kind == Kind.CANCELLED || kind == Kind.REJECTED || kind == Kind.PAYMENT_REJECTED
                ? R.drawable.ic_close : R.drawable.ic_check_circle);
    }

    @Nullable
    private static Kind kindOf(String t) {
        if (t.contains("payment") && (t.contains("verified") || t.contains("approved") || t.contains("confirmed"))) return Kind.PAYMENT_VERIFIED;
        if (t.contains("payment") && (t.contains("reject") || t.contains("declin") || t.contains("failed"))) return Kind.PAYMENT_REJECTED;
        if (t.contains("converted")) return Kind.CONVERTED;
        if (t.contains("checked in") || t.contains("checked-in") || t.contains("check-in complete")) return Kind.CHECKED_IN;
        if (t.contains("checked out") || t.contains("checked-out")) return Kind.CHECKED_OUT;
        if (t.contains("cancel")) return Kind.CANCELLED;
        if (t.contains("reject") || t.contains("declin")) return Kind.REJECTED;
        if (t.contains("verified") || t.contains("confirmed")) return Kind.VERIFIED;
        return null;
    }

    // ---- helpers

    /** Things that can only happen once: a second report of the same kind (another source) is the same event. */
    private static boolean once(Kind k) {
        return k != Kind.PAYMENT_SUBMITTED && k != Kind.PAYMENT_VERIFIED && k != Kind.PAYMENT_REJECTED
                && k != Kind.AMENITY_REQUESTED && k != Kind.AMENITY_APPROVED && k != Kind.AMENITY_REJECTED;
    }

    private static boolean hasNear(List<Entry> out, Kind kind, long millis) {
        for (Entry e : out) if (e.kind == kind && Math.abs(e.millis - millis) <= PAYMENT_WINDOW_MS) return true;
        return false;
    }

    @Nullable
    private static Kind kindOfServerStep(@Nullable String key, @Nullable String label) {
        String t = ((key == null ? "" : key) + " " + (label == null ? "" : label)).toLowerCase(Locale.ROOT).replace('_', ' ');
        if (t.contains("checked in") || t.contains("check in")) return Kind.CHECKED_IN;
        if (t.contains("checked out") || t.contains("check out")) return Kind.CHECKED_OUT;
        if (t.contains("complet")) return Kind.SETTLED;
        if (t.contains("cancel")) return Kind.CANCELLED;
        if (t.contains("payment") && t.contains("reject")) return Kind.PAYMENT_REJECTED;
        if (t.contains("payment") && t.contains("verif")) return Kind.PAYMENT_VERIFIED;
        if (t.contains("payment")) return Kind.PAYMENT_SUBMITTED;
        if (t.contains("reject")) return Kind.REJECTED;
        if (t.contains("convert")) return Kind.CONVERTED;
        if (t.contains("modif") || t.contains("edit")) return Kind.EDITED;
        if (t.contains("verif") || t.contains("confirm")) return Kind.VERIFIED;
        if (t.contains("creat") || t.contains("made") || t.contains("placed")) return Kind.CREATED;
        return null;
    }

    @DrawableRes
    private static int iconFor(Kind k) {
        switch (k) {
            case CANCELLED:
            case REJECTED:
            case PAYMENT_REJECTED:
            case AMENITY_REJECTED:
                return R.drawable.ic_close;
            case EDITED:
                return R.drawable.ic_edit;
            case PAYMENT_SUBMITTED:
                return R.drawable.ic_payment;
            case CHECKED_IN:
            case CHECKED_OUT:
            case CREATED:
                return R.drawable.ic_booking;
            default:
                return R.drawable.ic_check_circle;
        }
    }

    private static void add(List<Entry> out, String key, Kind kind, long millis, String title, @Nullable String detail, @DrawableRes int icon) {
        if (millis <= 0) return; // no date, no entry: the timeline never shows a made-up time
        for (Entry e : out) {
            if (e.key.equals(key)) return;
            if (once(kind) && e.kind == kind) return;
        }
        out.add(new Entry(key, kind, millis, title, detail, format(millis), icon));
    }

    private static long ms(@Nullable String raw) {
        return raw == null || raw.trim().isEmpty() ? 0 : ApiMapper.epochMillis(raw);
    }

    @Nullable
    private static String clean(@Nullable String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    @Nullable
    private static String datesLine(Context c, Booking b) {
        if (clean(b.getCheckInDate()) == null || clean(b.getCheckOutDate()) == null) return null;
        return c.getString(R.string.date_range_format, b.getCheckInDate(), b.getCheckOutDate());
    }

    /** "Room 1-001, 602" once rooms are assigned. */
    @Nullable
    private static String roomsLine(Context c, Booking b) {
        List<String> numbers = new ArrayList<>();
        for (TransactionRooms.RoomItem r : TransactionRooms.of(b, Collections.emptyList(), Collections.emptyList(), Collections.emptyList()).rooms()) {
            if (r != null && r.number != null) numbers.add(r.number);
        }
        if (numbers.isEmpty()) return null;
        return c.getResources().getQuantityString(R.plurals.tl_rooms_assigned, numbers.size(), String.join(", ", numbers));
    }
}
