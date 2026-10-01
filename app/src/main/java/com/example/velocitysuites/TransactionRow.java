package com.example.velocitysuites;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * One Transaction History card = one Booking/Reservation - an immutable SNAPSHOT of everything the card
 * shows, sorts and filters by, computed once when the list is (re)built.
 * <p>
 * Why a snapshot and not the Booking itself: RoomRepository replaces and mutates Booking objects in place,
 * and a DiffUtil that compared a live Booking with itself would never see a status change (the trap that
 * bit the notification list - see NotificationAdapter.Row). Everything DiffUtil compares, everything the
 * card binds and everything a filter/search/sort reads lives here as plain values; the Booking is kept only
 * to navigate to the details screen. Doing the parsing and text building once per rebuild, instead of inside
 * comparators and per-keystroke filters, is also what keeps a few hundred rows cheap on the UI thread.
 * <p>
 * Null-safe by construction: any Booking field may be missing and the row still builds (a missing room type,
 * dates or amounts become empty/0 here and "—" on screen).
 */
public final class TransactionRow {

    enum Kind { BOOKING, RESERVATION }

    /** The record to open - navigation only, never read for display or diffing. */
    final Booking booking;
    final String id;
    /** True for a "New Booking" created directly (its own table and id sequence), false for a reservation-derived transaction. */
    final boolean direct;
    final Kind kind;
    final TransactionStatusHelper.Summary summary;
    /** "Deluxe", "Deluxe ×2 • Suite" - or "" when the record names no room type at all. */
    final String roomText;
    final String checkIn;
    final String checkOut;
    /** Most recent activity on the transaction (creation or latest payment) - what the list is sorted by, newest first. */
    final long sortMillis;
    /** The check-in as a calendar date (for the date filter), or null when it can't be read. */
    @Nullable
    final LocalDate checkInDate;
    final int receiptCount;
    /** Distinct room type names as written (for the Room Type picker) and lower-cased (for matching the filter). */
    final List<String> roomTypes;
    final List<String> roomTypesLower;
    /** The booking/reservation lifecycle status ("Pending", "Confirmed", "Checked-In", ...), for the Booking Status filter. */
    final String lifecycleStatus;
    /** The whole transaction was cancelled or rejected (a closed record) - distinct from a payment being rejected. */
    final boolean closed;
    /** Some payment was made or attempted against it. */
    final boolean hasAnyPayment;
    /** Lower-cased text the search box matches against (reference, room, dates, status words, payment/receipt references, amounts). */
    final String searchText;

    private TransactionRow(Booking booking, String id, boolean direct, Kind kind, TransactionStatusHelper.Summary summary,
                           String roomText, String checkIn, String checkOut, long sortMillis, @Nullable LocalDate checkInDate,
                           int receiptCount, List<String> roomTypes, List<String> roomTypesLower, String lifecycleStatus,
                           boolean closed, boolean hasAnyPayment, String searchText) {
        this.booking = booking;
        this.id = id;
        this.direct = direct;
        this.kind = kind;
        this.summary = summary;
        this.roomText = roomText;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.sortMillis = sortMillis;
        this.checkInDate = checkInDate;
        this.receiptCount = receiptCount;
        this.roomTypes = roomTypes;
        this.roomTypesLower = roomTypesLower;
        this.lifecycleStatus = lifecycleStatus;
        this.closed = closed;
        this.hasAnyPayment = hasAnyPayment;
        this.searchText = searchText;
    }

    // ---- Building ----

    public static TransactionRow from(@NonNull Booking b) {
        String id = nz(b.getId());
        Kind kind = b.isHasBooking() ? Kind.BOOKING : Kind.RESERVATION;
        TransactionStatusHelper.Summary summary = TransactionStatusHelper.summarize(b);
        String roomText = roomTextOf(b);
        String checkIn = nz(b.getCheckInDate());
        String checkOut = nz(b.getCheckOutDate());
        String lifecycle = nz(b.getStatus());
        boolean closed = "cancelled".equalsIgnoreCase(lifecycle) || "canceled".equalsIgnoreCase(lifecycle)
                || "rejected".equalsIgnoreCase(lifecycle);

        List<String> types = new ArrayList<>();
        List<String> typesLower = new ArrayList<>();
        for (String type : b.getAllRoomTypeNames()) {
            if (type == null || type.trim().isEmpty()) continue;
            String trimmed = type.trim();
            if (!typesLower.contains(trimmed.toLowerCase(Locale.US))) {
                types.add(trimmed);
                typesLower.add(trimmed.toLowerCase(Locale.US));
            }
        }

        List<Booking.PaymentRecord> payments = b.getPaymentHistory();
        boolean anyPayment = (payments != null && !payments.isEmpty())
                || (b.getTransactionRef() != null && !b.getTransactionRef().trim().isEmpty())
                || MoneyFormat.isPositive(b.getAmountPaid());

        return new TransactionRow(b, id, b.isDirectBooking(), kind, summary, roomText, checkIn, checkOut,
                sortMillisOf(b), PaymentDates.parseDate(checkIn),
                b.getReceipts() != null ? b.getReceipts().size() : 0,
                Collections.unmodifiableList(types), Collections.unmodifiableList(typesLower), lifecycle, closed, anyPayment,
                searchTextOf(b, id, kind, summary, roomText, checkIn, checkOut, lifecycle));
    }

    /** Builds a row per transaction, most recent activity first (ties: the newer - higher - id first). */
    public static List<TransactionRow> fromAll(@Nullable List<Booking> bookings) {
        List<TransactionRow> rows = new ArrayList<>();
        if (bookings != null) {
            for (Booking b : bookings) {
                if (b == null) continue;
                try {
                    rows.add(from(b));
                } catch (RuntimeException e) {
                    // One malformed record must never blank the whole list - skip it, say so in the debug log.
                    try {
                        com.example.velocitysuites.network.DiagnosticLog.e("TransactionRow.from", "id=" + b.getId(), e);
                    } catch (RuntimeException ignored) {
                        // Logging is best-effort (and unavailable in a plain JVM test) - never the thing that fails.
                    }
                }
            }
        }
        Collections.sort(rows, NEWEST_FIRST);
        return rows;
    }

    static final java.util.Comparator<TransactionRow> NEWEST_FIRST = (a, b) -> {
        int byTime = Long.compare(b.sortMillis, a.sortMillis);
        if (byTime != 0) return byTime;
        int byId = Long.compare(numericId(b.id), numericId(a.id));
        if (byId != 0) return byId;
        return Boolean.compare(a.direct, b.direct);
    };

    // ---- Identity / equality for the list ----

    /** Stable across refreshes (RecyclerView stable ids): the id within its own family - a reservation and a direct booking can share an id. */
    long stableId() {
        return (direct ? (1L << 62) : 0L) | (numericId(id) & 0x3FFFFFFFFFFFFFFFL);
    }

    boolean sameTransactionAs(TransactionRow other) {
        return direct == other.direct && id.equals(other.id);
    }

    /** Every value the card renders - what DiffUtil compares to decide whether a row needs rebinding. */
    boolean sameContentAs(TransactionRow o) {
        return kind == o.kind
                && summary.status == o.summary.status
                && summary.hasPendingPayment == o.summary.hasPendingPayment
                && Double.compare(summary.grandTotal, o.summary.grandTotal) == 0
                && Double.compare(summary.verifiedPaid, o.summary.verifiedPaid) == 0
                && Double.compare(summary.pendingSubmitted, o.summary.pendingSubmitted) == 0
                && Double.compare(summary.balance, o.summary.balance) == 0
                && Objects.equals(roomText, o.roomText)
                && Objects.equals(checkIn, o.checkIn)
                && Objects.equals(checkOut, o.checkOut)
                && receiptCount == o.receiptCount;
    }

    // ---- Derivations ----

    /** "Deluxe" / "Deluxe ×2 • Suite" for a transaction, or "" when it names no room type - null-safe. */
    static String roomTextOf(Booking b) {
        List<BookingRoom> rooms = b.getRooms();
        if (rooms != null && !rooms.isEmpty()) {
            LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
            for (BookingRoom room : rooms) {
                if (room == null) continue;
                String name = room.getRoomTypeName();
                if (name == null || name.trim().isEmpty()) continue;
                counts.merge(name.trim(), Math.max(1, room.getQuantity()), Integer::sum);
            }
            if (!counts.isEmpty()) return Booking.formatRoomSelectionSummary(counts);
        }
        String base = nz(b.getRoomType()).trim();
        if (base.isEmpty()) base = nz(b.getRoomName()).trim();
        if (base.isEmpty()) return "";
        int quantity = Math.max(1, b.getRoomsRequested());
        return quantity > 1 ? base + " ×" + quantity : base;
    }

    private static long sortMillisOf(Booking b) {
        long latest = Math.max(0, b.getCreatedAtMillis());
        List<Booking.PaymentRecord> payments = b.getPaymentHistory();
        if (payments != null) {
            for (Booking.PaymentRecord payment : payments) {
                if (payment != null) latest = Math.max(latest, PaymentDates.parseMillis(payment.date));
            }
        }
        return latest;
    }

    private static String searchTextOf(Booking b, String id, Kind kind, TransactionStatusHelper.Summary summary,
                                       String roomText, String checkIn, String checkOut, String lifecycle) {
        StringBuilder sb = new StringBuilder(160);
        add(sb, id);
        add(sb, "#" + id);
        add(sb, kind == Kind.BOOKING ? "booking" : "reservation");
        add(sb, roomText);
        add(sb, b.getRoomName());
        for (String type : b.getAllRoomTypeNames()) add(sb, type);
        add(sb, checkIn);
        add(sb, checkOut);
        add(sb, lifecycle);
        add(sb, statusWords(summary.status));
        add(sb, b.getTransactionRef());
        List<Booking.PaymentRecord> payments = b.getPaymentHistory();
        if (payments != null) {
            for (Booking.PaymentRecord payment : payments) {
                if (payment != null) add(sb, payment.referenceNumber);
            }
        }
        if (b.getReceipts() != null) {
            for (Booking.ReceiptSummary receipt : b.getReceipts()) add(sb, receipt.receiptNumber);
        }
        // Amounts, formatted and bare, so "1800" and "1,800" both find a P1,800.00 transaction.
        add(sb, MoneyFormat.format(summary.grandTotal));
        add(sb, String.valueOf((long) summary.grandTotal));
        return sb.toString().toLowerCase(Locale.US);
    }

    private static String statusWords(TransactionStatusHelper.Status status) {
        switch (status) {
            case PAID: return "paid";
            case PARTIALLY_PAID: return "partially paid partial";
            case CANCELLED: return "cancelled canceled";
            case REJECTED: return "rejected";
            case PENDING:
            default: return "pending";
        }
    }

    private static void add(StringBuilder sb, @Nullable String part) {
        if (part == null) return;
        String trimmed = part.trim();
        if (trimmed.isEmpty()) return;
        sb.append(trimmed).append(' ');
    }

    private static String nz(@Nullable String s) {
        return s == null ? "" : s;
    }

    private static long numericId(String id) {
        try {
            return Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            return id.hashCode();
        }
    }
}
