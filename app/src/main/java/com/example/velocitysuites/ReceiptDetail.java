package com.example.velocitysuites;

import androidx.annotation.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The app-level model for GET guest/receipts/{receiptNumber} (Api\ReceiptController::show(),
 * ReceiptService::buildReceiptPayload() on the backend) - built by
 * ApiMapper#toReceiptDetail(ReceiptDetailDto) from the raw DTO, following
 * this app's existing convention of never handing a raw network.dto object
 * to an Activity/Fragment (see Booking).
 * <p>
 * IMPORTANT (see PAYMENT_RECEIPT_HISTORY_BACKEND_SPEC.md §3/§4/§5): for a
 * PARTIAL_RECEIPT/FULL_PAYMENT_RECEIPT (anchorPayment non-null), summary/
 * transactions are a FROZEN POINT-IN-TIME SNAPSHOT of what was true the
 * moment that specific payment was made - display exactly as received,
 * never recompute/overwrite using a separately-cached Booking's current
 * live totals. For an OFFICIAL_RECEIPT (anchorPayment null), summary/
 * transactions are the live final checkout totals and complete history.
 */
public class ReceiptDetail implements Serializable {
    private final String receiptType;
    private final String receiptNumber;
    private final String bookingId;
    @Nullable private final String reservationId;
    @Nullable private final String guestAccountName;
    @Nullable private final String representativeName;
    @Nullable private final String roomType;
    private final List<BookingRoom> roomLines;
    @Nullable private final String checkIn;
    @Nullable private final String checkOut;
    private final int numberOfNights;
    private final List<String> assignedRoomNumbers;
    private final int adults;
    private final int children;
    @Nullable private final Booking.PaymentSummary paymentSummary;
    private final List<Booking.PaymentTransactionRecord> paymentTransactions;
    @Nullable private final AnchorPayment anchorPayment;
    @Nullable private final String issuedAt;

    public ReceiptDetail(String receiptType, String receiptNumber, String bookingId, @Nullable String reservationId,
                          @Nullable String guestAccountName, @Nullable String representativeName, @Nullable String roomType,
                          @Nullable List<BookingRoom> roomLines, @Nullable String checkIn, @Nullable String checkOut,
                          int numberOfNights, @Nullable List<String> assignedRoomNumbers, int adults, int children,
                          @Nullable Booking.PaymentSummary paymentSummary,
                          @Nullable List<Booking.PaymentTransactionRecord> paymentTransactions,
                          @Nullable AnchorPayment anchorPayment, @Nullable String issuedAt) {
        this.receiptType = receiptType;
        this.receiptNumber = receiptNumber;
        this.bookingId = bookingId;
        this.reservationId = reservationId;
        this.guestAccountName = guestAccountName;
        this.representativeName = representativeName;
        this.roomType = roomType;
        this.roomLines = roomLines != null ? roomLines : Collections.emptyList();
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.numberOfNights = numberOfNights;
        this.assignedRoomNumbers = assignedRoomNumbers != null ? assignedRoomNumbers : new ArrayList<>();
        this.adults = adults;
        this.children = children;
        this.paymentSummary = paymentSummary;
        this.paymentTransactions = paymentTransactions != null ? paymentTransactions : new ArrayList<>();
        this.anchorPayment = anchorPayment;
        this.issuedAt = issuedAt;
    }

    public String getReceiptType() { return receiptType; }
    public String getReceiptNumber() { return receiptNumber; }
    public String getBookingId() { return bookingId; }
    @Nullable public String getReservationId() { return reservationId; }
    @Nullable public String getGuestAccountName() { return guestAccountName; }
    @Nullable public String getRepresentativeName() { return representativeName; }
    @Nullable public String getRoomType() { return roomType; }
    /** The room lines exactly as the front desk billed them (each room's own nights) once the stay is itemized, else the booked lines. */
    public List<BookingRoom> getRoomLines() {
        return stay != null && !stay.rooms.isEmpty() ? stay.asBookingRooms() : roomLines;
    }
    @Nullable public String getCheckIn() { return checkIn; }
    @Nullable public String getCheckOut() { return checkOut; }
    public int getNumberOfNights() { return stay != null ? stay.actualNights : numberOfNights; }
    @Nullable public Highlight getHighlight() { return highlight; }
    public void setHighlight(@Nullable Highlight highlight) { this.highlight = highlight; }
    private Highlight highlight;
    @Nullable public CheckinSettlement getCheckinSettlement() { return checkinSettlement; }
    public void setCheckinSettlement(@Nullable CheckinSettlement checkinSettlement) { this.checkinSettlement = checkinSettlement; }
    private CheckinSettlement checkinSettlement;
    /** Every stay of the transaction as the receipt froze it; a one-stay receipt has one entry (or none from an older server). */
    public List<Booking.StayRecord> getStays() { return stays; }
    public void setStays(@Nullable List<Booking.StayRecord> stays) { this.stays = stays != null ? stays : new ArrayList<>(); }
    private List<Booking.StayRecord> stays = new ArrayList<>();
    @Nullable public StayInfo getStay() { return stay; }
    public void setStay(@Nullable StayInfo stay) { this.stay = stay; }
    private StayInfo stay;
    public List<String> getAssignedRoomNumbers() { return assignedRoomNumbers; }
    public int getAdults() { return adults; }
    public int getChildren() { return children; }
    @Nullable public Booking.PaymentSummary getPaymentSummary() { return paymentSummary; }
    public List<Booking.PaymentTransactionRecord> getPaymentTransactions() { return paymentTransactions; }
    @Nullable public AnchorPayment getAnchorPayment() { return anchorPayment; }
    @Nullable public String getIssuedAt() { return issuedAt; }

    /** The server's raw issue timestamp - the date/time source of last resort when a receipt row has no payment of its own. */
    @Nullable private String issuedAtRaw;
    @Nullable public String getIssuedAtRaw() { return issuedAtRaw; }
    public void setIssuedAtRaw(@Nullable String issuedAtRaw) { this.issuedAtRaw = issuedAtRaw; }

    /** The highlighted bottom block of a receipt, exactly as the server froze it (nulls = not part of that receipt). */
    public static class Highlight implements Serializable {
        public static final String BOOKING_VERIFICATION = "booking_verification";
        public static final String CHECKIN_PAYMENT = "checkin_payment";
        public static final String CHECKOUT_FINAL = "checkout_final";

        public final String kind;
        @Nullable public final String paymentType;
        @Nullable public final String paymentMethod;
        @Nullable public final String amountToPayNote;
        public final java.util.Map<String, Double> amounts = new java.util.LinkedHashMap<>();

        /** True when the final receipt carries its own lines (amenities by room, desk charges, payments); older ones do not. */
        public boolean finalLines;
        public final java.util.List<AmenityGroup> amenityGroups = new java.util.ArrayList<>();
        public final java.util.List<ChargeLine> otherCharges = new java.util.ArrayList<>();
        public final java.util.List<PaymentLine> payments = new java.util.ArrayList<>();
        @Nullable public String noRefundNote;

        public static class AmenityItem implements Serializable {
            public final String name;
            public final int quantity;
            public final double unitPrice;
            public final double subtotal;

            public AmenityItem(String name, int quantity, double unitPrice, double subtotal) {
                this.name = name != null ? name : "";
                this.quantity = Math.max(1, quantity);
                this.unitPrice = unitPrice;
                this.subtotal = subtotal;
            }
        }

        /** The approved amenities of one room (null room: for the whole stay). */
        public static class AmenityGroup implements Serializable {
            @Nullable public final String room;
            /** "Deluxe (room 2)": the booked room, when there is no room number to name it by. */
            @Nullable public String label;
            public final java.util.List<AmenityItem> items = new java.util.ArrayList<>();
            public double subtotal;

            public AmenityGroup(@Nullable String room) {
                this.room = room;
            }
        }

        public static class ChargeLine implements Serializable {
            public final String name;
            public final double amount;

            public ChargeLine(String name, double amount) {
                this.name = name != null ? name : "";
                this.amount = amount;
            }
        }

        public static class PaymentLine implements Serializable {
            public final String label;
            public final double amount;
            @Nullable public final String method;
            @Nullable public final String date;

            public PaymentLine(String label, double amount, @Nullable String method, @Nullable String date) {
                this.label = label != null ? label : "";
                this.amount = amount;
                this.method = method;
                this.date = date;
            }
        }

        public Highlight(String kind, @Nullable String paymentType, @Nullable String paymentMethod, @Nullable String amountToPayNote) {
            this.kind = kind;
            this.paymentType = paymentType;
            this.paymentMethod = paymentMethod;
            this.amountToPayNote = amountToPayNote;
        }

        /** The amount for a key, or 0 when the receipt did not carry it. */
        public double amount(String key) {
            Double v = amounts.get(key);
            return v == null ? 0 : v;
        }

        public boolean has(String key) {
            return amounts.get(key) != null;
        }
    }

    /** Receipt 2: what was settled in the Check-In module - frozen by the server at that moment. */
    public static class CheckinSettlement implements Serializable {
        public static final String PARTIAL_TO_FULL = "partial_to_full";
        public static final String FULL_PAYMENT = "full_payment";
        public static final String FULL_CONFIRMATION = "full_confirmation";

        public final String settlementType;
        public final double amountSettled;
        @Nullable public final String paymentMethod;
        public final double changeReturned;
        public final double totalAmount;
        public final double totalPaid;
        public final double balance;
        /** not_requested | approved | rejected */
        public final String discountStatus;
        @Nullable public final String discountName;
        public final double discountAmount;
        @Nullable public final String discountRejectionReason;
        @Nullable public final String verifiedBy;

        public CheckinSettlement(String settlementType, double amountSettled, @Nullable String paymentMethod, double changeReturned,
                                 double totalAmount, double totalPaid, double balance, String discountStatus,
                                 @Nullable String discountName, double discountAmount, @Nullable String discountRejectionReason,
                                 @Nullable String verifiedBy) {
            this.settlementType = settlementType;
            this.amountSettled = amountSettled;
            this.paymentMethod = paymentMethod;
            this.changeReturned = changeReturned;
            this.totalAmount = totalAmount;
            this.totalPaid = totalPaid;
            this.balance = balance;
            this.discountStatus = discountStatus != null ? discountStatus : "not_requested";
            this.discountName = discountName;
            this.discountAmount = discountAmount;
            this.discountRejectionReason = discountRejectionReason;
            this.verifiedBy = verifiedBy;
        }
    }

    /** True for a PARTIAL_RECEIPT/FULL_PAYMENT_RECEIPT (anchored on one specific payment) - false for an OFFICIAL_RECEIPT. */
    public boolean isAnchoredOnSinglePayment() {
        return anchorPayment != null;
    }

    /** The specific payment a PARTIAL_RECEIPT/FULL_PAYMENT_RECEIPT is anchored on - null for an OFFICIAL_RECEIPT. */
    public static class AnchorPayment implements Serializable {
        public final double amountPaid;
        /** Cash only (null for GCash): the cash handed over and the change returned. */
        @Nullable public Double cashReceived;
        @Nullable public Double changeGiven;
        public final String paymentMethod;
        @Nullable public final Integer paymentPercentage;
        @Nullable public final String gcashNumber;
        @Nullable public final String gcashReferenceNumber;
        @Nullable public final String verifiedAt;
        @Nullable public final String verifiedBy;

        public AnchorPayment(double amountPaid, String paymentMethod, @Nullable Integer paymentPercentage,
                              @Nullable String gcashNumber, @Nullable String gcashReferenceNumber,
                              @Nullable String verifiedAt, @Nullable String verifiedBy) {
            this.amountPaid = amountPaid;
            this.paymentMethod = paymentMethod;
            this.paymentPercentage = paymentPercentage;
            this.gcashNumber = gcashNumber;
            this.gcashReferenceNumber = gcashReferenceNumber;
            this.verifiedAt = verifiedAt;
            this.verifiedBy = verifiedBy;
        }
    }
}
