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

    /** True for a PARTIAL_RECEIPT/FULL_PAYMENT_RECEIPT (anchored on one specific payment) - false for an OFFICIAL_RECEIPT. */
    public boolean isAnchoredOnSinglePayment() {
        return anchorPayment != null;
    }

    /** The specific payment a PARTIAL_RECEIPT/FULL_PAYMENT_RECEIPT is anchored on - null for an OFFICIAL_RECEIPT. */
    public static class AnchorPayment implements Serializable {
        public final double amountPaid;
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
