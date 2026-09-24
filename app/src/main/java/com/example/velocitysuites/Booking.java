package com.example.velocitysuites;

import androidx.annotation.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Booking implements Serializable {
    private String id;
    private String roomId;
    private String roomName;
    private String roomType;
    private String checkInDate;
    private String checkOutDate;
    private int guests;
    private double totalAmount;
    /**
     * True when totalAmount above was already computed server-side as the
     * full grand total (rooms + paid amenities) - either from Billing (post-
     * conversion) or from Reservation::total_amount_due (pre-conversion, see
     * ApiMapper#toBooking(ReservationDto)). RoomRepository#correctPendingReservationTotal()
     * must skip its amenities top-up whenever this is true, since totalAmount
     * already includes them - otherwise amenities get added a second time,
     * inflating the total shown/used on the Pay Now screen above what the
     * backend actually validates payment percentages against (see
     * PaymentController::store()'s tier match, which always uses the real,
     * non-inflated total).
     */
    private boolean totalIncludesAmenities;
    /** From Reservation::discount_preview (backend) - live quote pre-conversion, locked Billing figure after. 0 when no discount applies or the preview wasn't available. */
    private double discountAmount;
    private String status; // Pending, Confirmed, Checked-In, Checked-Out, Cancelled
    private double amountPaid;
    private String bookingDate;
    private String transactionRef;
    private String paymentDate;
    private String paymentMethod;
    private String cancellationDate;
    private String cancellationReason;
    private String actualCheckIn;
    private String actualCheckOut;
    private String idCardType; // e.g., "None", "Senior Citizen", "PWD"
    private String idCardUri; // Path to uploaded image
    private String paymentReceiptUri; // Path to GCash receipt
    private boolean serviceFeeApplied = false;
    private List<AdditionalGuest> additionalGuests;
    // Reservation vs Booking split: a server-side Booking row only exists
    // once a payment has been made. false = plain Reservation (no payment).
    private boolean hasBooking = false;
    /**
     * Origin discriminator, separate from hasBooking above (which is a
     * payment-state flag): true only for a "New Booking" transaction created
     * directly via Api\BookingController - reservation_id is null server-side
     * for the entire lifetime of this record, no Reservation was ever
     * created. hasBooking is always true whenever this is true (a direct
     * booking is created already paid/confirmed), but the reverse isn't -
     * a converted Reservation also has hasBooking=true yet isDirectBooking=false.
     */
    private boolean directBooking = false;
    private String billingStatus; // pending, partial, paid (null when no booking)
    private boolean paymentPendingVerification = false;
    /** Receptionist-level verification (separate from payment verification
     *  above) - true once staff click Verify Booking/Verify Reservation on
     *  their dashboard. Drives the Active vs Complete list split. */
    private boolean staffVerified = false;
    /**
     * True only for a client-synthesized "frozen" view of a Reservation that
     * has since converted into a Booking - see ApiMapper#toHistoricalReservation()
     * and RoomRepository#getCompletedHistoricalReservations(). Exists purely
     * so the original reservation can keep showing under the Reservation
     * tab's Completed Reservation List (view/record only) even after the
     * real converted Booking already shows under the Booking tab - never
     * true for a real, independently-actionable transaction. Must never
     * offer Pay Now/Cancel/Modify/Delete/Book Again - view-only.
     */
    private boolean historicalReservation = false;
    /**
     * Set only on a historical reservation entry (see historicalReservation) -
     * the real backend bookings.id it converted into, for a "Converted
     * Booking: BOOK-X" cross-reference display. Deliberately the only new
     * identifier surfaced by this whole feature: the reservation's own id
     * (getId()) is already shown everywhere else per this app's existing
     * single-ID convention, so a matching "original reservation id" field
     * on the real Booking side would just restate getId() - not added.
     */
    private String convertedBookingId;
    /**
     * Legacy field, defensive/for completeness only - kept in case an older
     * server build still returns a soft-hide marker on some rows. The
     * guest-facing "delete" feature (see RoomRepository#deleteReservationPermanently()/
     * #deleteBookingPermanently()) is now a real, permanent row deletion
     * rather than a hide, so a row with this set should no longer occur in
     * practice; a truly deleted transaction never reaches the app's cache
     * at all, since it no longer exists server-side.
     */
    private String hiddenAt;
    private List<PaymentRecord> paymentHistory = new ArrayList<>();
    /** Numeric id of the most recent payment row, needed to call cancel/void on it. Null if no payment attempt exists yet. */
    private String latestPaymentId;
    /** Registered GCash mobile number submitted with the most recent payment attempt, if any. */
    private String gcashNumber;
    /** Absolute URL to the most recent payment's uploaded GCash receipt image, if any. */
    private String receiptUrl;
    /** Absolute URL to the room type's main image, for the compact list card's thumbnail - from RoomTypeDto.image_url, same field every room-browsing screen already loads via Glide. Null for older cached data with no room type resolved. */
    private String roomImageUrl;
    /** Server-authoritative tri-state for the most recent payment: "pending_verification" | "verified" | "rejected" | null. */
    private String paymentVerificationStatus;
    /** Receptionist's reason for rejecting the most recent payment, if rejected. */
    private String rejectionReason;
    /**
     * Reservation/Booking-level rejection reason - a receptionist rejecting
     * the whole transaction (Receptionist\ReservationController::reject()
     * pre-conversion, Receptionist\BookingController::reject() post-
     * conversion) or the system auto-rejecting an unpaid reservation past
     * its 48-hour deadline (ReservationWorkflowService::expireUnpaid()).
     * Deliberately separate from rejectionReason above, which is about a
     * single payment attempt, not the transaction itself.
     */
    private String transactionRejectionReason;
    /**
     * ISO-8601 payment deadline for a still-unpaid, still-pending
     * reservation (Reservation::payment_deadline - backend), or null when
     * the 2-day/48-hour rule doesn't apply (already paid/converted, or
     * check-in is less than 2 days out).
     */
    private String paymentDeadline;
    /**
     * The percentage (20/30/40/50/100) and peso amount actually submitted
     * with a payment attempt against this reservation (Reservation::selected_payment_percentage/
     * required_payment_amount - backend, computed and persisted by
     * Api\PaymentController::store() from the validated amount_paid, never
     * trusted from the client). Null until the guest actually submits a
     * payment through payment.xml's Review Billing (Cash or GCash) - the
     * 20/30/40/50%/Full choice is never asked at reservation-creation time
     * itself. Once a submission is pending verification,
     * PaymentActivity#renderStoredPaymentPercentageIfPresent() locks the
     * selector onto these values instead of showing it interactively.
     */
    private Double selectedPaymentPercentage;
    private Double requiredPaymentAmount;
    /**
     * True once this reservation's one-time Cash -> GCash payment-method switch has
     * been used (server-side reservations.payment_method_locked_at, not a local/
     * per-device flag - see Api\ReservationController::switchToGcash()). Once true,
     * the switch can never happen again for this reservation.
     */
    private boolean paymentMethodLocked = false;
    /**
     * True once this reservation's one-time Modify (dates/guest counts/room
     * selection) has been used (server-side reservations.edited_at, not a
     * local/per-device flag - see Api\ReservationController::update()). Once
     * true, Modify can never be used again for this reservation, even after
     * a reinstall or on a different device - mirrors isPaymentMethodLocked()
     * above exactly.
     */
    private boolean editedOnce = false;
    /**
     * Fields needed to re-seed BookingWizardActivity when a Reservation is
     * edited via Modify - only meaningful for a plain Reservation
     * (isHasBooking() false), which is the only thing Modify ever applies to.
     */
    private String roomTypeId;
    private int roomsRequested;
    private int adults;
    private int children;
    private String guestFirstName;
    private String guestMiddleName;
    private String guestLastName;
    /**
     * Manila-formatted ("MMM d, yyyy • h:mm a") display timestamps, built by
     * ApiMapper from the backend's UTC created_at/checked_in_at/checked_out_at/
     * completed_at columns via TimeUtils - null whenever the underlying
     * action hasn't happened yet (or the field predates a given backend
     * response), in which case the Timeline UI shows "Not yet available"
     * rather than a stale/fabricated value. Distinct from bookingDate
     * (confirmed_at) and cancellationDate (cancelled_at), which already
     * existed before this timestamp-accuracy pass.
     */
    private String createdAtDisplay;
    /**
     * Epoch millis parsed from the backend's created_at, for reliable
     * newest-first sorting (see BookingAndReservationActivity) without
     * re-parsing the human-readable createdAtDisplay string. 0 when the
     * backend hasn't sent created_at (older cached response) - callers
     * should fall back to comparing getId() (auto-increment PK, so still
     * monotonic with creation order) rather than treating 0 as a real date.
     */
    private long createdAtMillis;
    private String checkedInAtDisplay;
    private String checkedOutAtDisplay;
    private String completedAtDisplay;
    /** When staff verified the most recent payment - separate from paymentDate (when it was submitted). */
    private String paymentVerifiedAtDisplay;
    /** Human-readable room number (e.g. "201") assigned at check-in against the converted Booking - see ApiMapper#toBooking(). Null until a room is actually assigned. */
    private String roomNumber;
    /** Room-only charge from the backend's Billing breakdown (Billing::room_charge) - 0 when no Billing row exists yet (a still-pending Reservation) or the value is missing. */
    private double roomCharge;
    /** Add-on amenity charge from the backend's Billing breakdown (Billing::amenity_charge) - 0 when none were added or no Billing row exists yet. */
    private double amenityCharge;
    /** Additional-guest fee from the backend's Billing breakdown (Billing::additional_guest_fee) - 0 when not applicable or no Billing row exists yet. */
    private double additionalGuestFee;
    /** Calendar-date-only rendering of the most recent payment's timestamp (paymentDate holds date+time combined) - see Booking#getPaymentDate(). */
    private String paymentDateOnly;
    /** Time-of-day-only rendering of the most recent payment's timestamp - see paymentDateOnly. */
    private String paymentTimeOnly;
    /**
     * Itemized room-type breakdown for a multi-room-type transaction (e.g.
     * "2 Deluxe + 1 Executive" is two entries) - see
     * MULTI_ROOM_TRANSACTION_BACKEND_SPEC.md. Empty on every transaction
     * today since the backend doesn't return this yet (it only ever creates
     * one room type per transaction) - callers must fall back to the
     * existing single roomType/roomName fields when this is empty, never
     * assume it's populated.
     */
    private List<BookingRoom> rooms = new ArrayList<>();
    /**
     * Itemized paid-amenity breakdown for this transaction - see
     * MULTI_ROOM_TRANSACTION_BACKEND_SPEC.md. Empty on every transaction
     * today since the backend doesn't return this yet - only
     * getAmenityCharge() (a single dollar total, no item breakdown) is ever
     * populated for now. Callers must fall back to that when this is empty,
     * never assume it's populated.
     */
    private List<BookingAmenity> amenities = new ArrayList<>();
    /**
     * The authoritative Grand Total/Total Amount Paid/Remaining Balance/
     * Payment Status/Official-Receipt-availability block, straight off the
     * backend (ReceiptService::paymentSummary(), see PAYMENT_RECEIPT_HISTORY_BACKEND_SPEC.md) -
     * null whenever the backend response this Booking was built from didn't
     * attach one yet (an older cached response, or a response from before
     * this feature deployed). Screens must prefer this over amountPaid/
     * getRemainingBalance()/PaymentStatusResolver's client-side
     * reconstruction whenever it's non-null - see this class's own
     * getEffective*() helpers below.
     */
    private PaymentSummary paymentSummary;
    /**
     * The complete, chronological Payment Transaction History straight off
     * the backend (ReceiptService::paymentTransactions()) - distinct from,
     * and richer than, the legacy paymentHistory (List&lt;PaymentRecord&gt;)
     * above (which is still populated the same old way, for any screen not
     * yet migrated to this). Empty (never null) when the backend response
     * didn't attach one.
     */
    private List<PaymentTransactionRecord> paymentTransactions = new ArrayList<>();
    /**
     * Every receipt already issued for this booking (Partial/Full-Payment/
     * Official) straight off the backend (ReceiptService::receiptsList()).
     * Empty (never null) when the backend response didn't attach one - see
     * this class's own hasOfficialReceipt()/findReceipt() helpers below.
     */
    private List<ReceiptSummary> receipts = new ArrayList<>();

    /**
     * Mirrors ReceiptService::paymentSummary()'s shape (backend) field-for-
     * field - see PaymentSummaryDto's own doc for why these are primitive
     * doubles/an Integer, not formatted Strings.
     */
    public static class PaymentSummary implements Serializable {
        public final double grandTotal;
        public final double totalAmountPaid;
        public final double remainingBalance;
        public final String paymentStatus;
        public final Integer paymentPercentage;
        public final boolean officialReceiptAvailable;

        public PaymentSummary(double grandTotal, double totalAmountPaid, double remainingBalance,
                               String paymentStatus, Integer paymentPercentage, boolean officialReceiptAvailable) {
            this.grandTotal = grandTotal;
            this.totalAmountPaid = totalAmountPaid;
            this.remainingBalance = remainingBalance;
            this.paymentStatus = paymentStatus;
            this.paymentPercentage = paymentPercentage;
            this.officialReceiptAvailable = officialReceiptAvailable;
        }
    }

    /**
     * Mirrors ReceiptService::paymentTransactions()'s shape (backend)
     * field-for-field - see PaymentTransactionDto's own doc for which
     * fields are legitimately null (e.g. every GCash-only field is null for
     * a Cash checkout row).
     */
    public static class PaymentTransactionRecord implements Serializable {
        public final long id;
        public final String paymentMethod;
        public final String paymentStage;
        public final String transactionType;
        public final double amountPaid;
        public final String paymentStatus;
        @Nullable public final String verificationStatus;
        @Nullable public final String gcashNumber;
        @Nullable public final String gcashReferenceNumber;
        @Nullable public final String referenceNumber;
        @Nullable public final Integer paymentPercentage;
        @Nullable public final String verifiedBy;
        @Nullable public final String verifiedAt;
        @Nullable public final String rejectionReason;
        @Nullable public final String paymentDate;
        public final double totalPaidAfterTransaction;
        public final double remainingBalanceAfterTransaction;
        @Nullable public final String receiptType;
        @Nullable public final String receiptNumber;

        public PaymentTransactionRecord(long id, String paymentMethod, String paymentStage, String transactionType,
                                         double amountPaid, String paymentStatus, @Nullable String verificationStatus,
                                         @Nullable String gcashNumber, @Nullable String gcashReferenceNumber,
                                         @Nullable String referenceNumber, @Nullable Integer paymentPercentage,
                                         @Nullable String verifiedBy, @Nullable String verifiedAt,
                                         @Nullable String rejectionReason, @Nullable String paymentDate,
                                         double totalPaidAfterTransaction, double remainingBalanceAfterTransaction,
                                         @Nullable String receiptType, @Nullable String receiptNumber) {
            this.id = id;
            this.paymentMethod = paymentMethod;
            this.paymentStage = paymentStage;
            this.transactionType = transactionType;
            this.amountPaid = amountPaid;
            this.paymentStatus = paymentStatus;
            this.verificationStatus = verificationStatus;
            this.gcashNumber = gcashNumber;
            this.gcashReferenceNumber = gcashReferenceNumber;
            this.referenceNumber = referenceNumber;
            this.paymentPercentage = paymentPercentage;
            this.verifiedBy = verifiedBy;
            this.verifiedAt = verifiedAt;
            this.rejectionReason = rejectionReason;
            this.paymentDate = paymentDate;
            this.totalPaidAfterTransaction = totalPaidAfterTransaction;
            this.remainingBalanceAfterTransaction = remainingBalanceAfterTransaction;
            this.receiptType = receiptType;
            this.receiptNumber = receiptNumber;
        }
    }

    /** Mirrors ReceiptService::receiptsList()'s shape (backend) field-for-field. */
    public static class ReceiptSummary implements Serializable {
        public final String receiptNumber;
        public final String receiptType;
        public final String status;
        public final double amount;
        @Nullable public final Integer paymentPercentage;
        @Nullable public final String issuedAt;

        public ReceiptSummary(String receiptNumber, String receiptType, String status, double amount,
                               @Nullable Integer paymentPercentage, @Nullable String issuedAt) {
            this.receiptNumber = receiptNumber;
            this.receiptType = receiptType;
            this.status = status;
            this.amount = amount;
            this.paymentPercentage = paymentPercentage;
            this.issuedAt = issuedAt;
        }
    }

    public static class PaymentRecord implements Serializable {
        public String amount;
        public String method;
        public String referenceNumber;
        public String date;
        public String status;
        /** GCash mobile number entered for this specific payment - null for a Cash payment. */
        public String gcashNumber;

        public PaymentRecord(String amount, String method, String referenceNumber, String date, String status) {
            this(amount, method, referenceNumber, date, status, null);
        }

        public PaymentRecord(String amount, String method, String referenceNumber, String date, String status, String gcashNumber) {
            this.amount = amount;
            this.method = method;
            this.referenceNumber = referenceNumber;
            this.date = date;
            this.status = status;
            this.gcashNumber = gcashNumber;
        }
    }

    public static class AdditionalGuest implements Serializable {
        public String name;
        public int age;
        public String gender;
        public String relationship;

        public AdditionalGuest(String name, int age, String gender, String relationship) {
            this.name = name;
            this.age = age;
            this.gender = gender;
            this.relationship = relationship;
        }
    }

    public Booking(String id, String roomId, String roomName, String roomType, String checkInDate, String checkOutDate, 
                   int guests, double totalAmount, String status, String bookingDate) {
        this.id = id;
        this.roomId = roomId;
        this.roomName = roomName;
        this.roomType = roomType;
        this.checkInDate = checkInDate;
        this.checkOutDate = checkOutDate;
        this.guests = guests;
        this.totalAmount = totalAmount;
        this.status = status;
        this.bookingDate = bookingDate;
        this.amountPaid = status.equals("Checked-Out") || status.equals("Checked-In") || status.equals("Confirmed") ? totalAmount : 0;
        this.additionalGuests = new ArrayList<>();
        this.idCardType = "None";
    }

    // Getters
    public String getId() { return id; }
    public String getRoomId() { return roomId; }
    public String getRoomName() { return roomName; }
    public String getRoomType() { return roomType; }
    public String getCheckInDate() { return checkInDate; }
    public String getCheckOutDate() { return checkOutDate; }
    public int getGuests() { return guests; }
    public double getTotalAmount() { return totalAmount; }
    public boolean isTotalIncludesAmenities() { return totalIncludesAmenities; }
    public void setTotalIncludesAmenities(boolean totalIncludesAmenities) { this.totalIncludesAmenities = totalIncludesAmenities; }
    public double getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(double discountAmount) { this.discountAmount = discountAmount; }
    public String getStatus() { return status; }
    public double getAmountPaid() { return amountPaid; }
    public double getRemainingBalance() { return totalAmount - amountPaid; }
    public String getBookingDate() { return bookingDate; }
    public String getTransactionRef() { return transactionRef; }
    public String getPaymentDate() { return paymentDate; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getCancellationDate() { return cancellationDate; }
    public String getCancellationReason() { return cancellationReason; }
    public String getActualCheckIn() { return actualCheckIn; }
    public String getActualCheckOut() { return actualCheckOut; }
    public String getIdCardType() { return idCardType; }
    public String getIdCardUri() { return idCardUri; }
    public String getPaymentReceiptUri() { return paymentReceiptUri; }
    public boolean isServiceFeeApplied() { return serviceFeeApplied; }
    public List<AdditionalGuest> getAdditionalGuests() { return additionalGuests; }
    public boolean isHasBooking() { return hasBooking; }
    public boolean isDirectBooking() { return directBooking; }
    public String getBillingStatus() { return billingStatus; }
    public boolean isPaymentPendingVerification() { return paymentPendingVerification; }
    public boolean isStaffVerified() { return staffVerified; }
    public boolean isHistoricalReservation() { return historicalReservation; }
    public String getConvertedBookingId() { return convertedBookingId; }

    /**
     * Which id (and implicitly, which endpoint) Delete Permanently must use.
     * Confirmed directly against the live backend (2026-09-18, via SSH):
     * Api\BookingController is exclusively a direct-booking controller - its
     * own guest-facing cancel() hard-rejects any booking with a non-null
     * reservation_id ("The guest mobile app's 'New Booking' path - a
     * genuinely independent transaction, never derived from or routed
     * through a Reservation"). So DELETE guest/bookings/{id} only ever
     * exists for isDirectBooking()==true. Every reservation-derived
     * transaction - converted or not, Cancelled or Completed - is deleted
     * through DELETE guest/reservations/{id} using getId() (the reservation's
     * own id); the reservation endpoint itself is responsible for inspecting
     * the nested Booking's status when one exists, since "operational status
     * lives on Booking instead" once converted (see ApiMapper's own doc).
     * Returns null to mean "use deleteReservationPermanently(getId()) instead".
     */
    public String getBookingEndpointDeleteId() {
        return isDirectBooking() ? getId() : null;
    }
    public String getHiddenAt() { return hiddenAt; }
    public List<PaymentRecord> getPaymentHistory() { return paymentHistory; }
    public String getLatestPaymentId() { return latestPaymentId; }
    public String getGcashNumber() { return gcashNumber; }
    public String getReceiptUrl() { return receiptUrl; }
    public String getRoomImageUrl() { return roomImageUrl; }
    public String getPaymentVerificationStatus() { return paymentVerificationStatus; }
    public String getRejectionReason() { return rejectionReason; }
    public boolean isPaymentRejected() { return "rejected".equalsIgnoreCase(paymentVerificationStatus); }
    public String getTransactionRejectionReason() { return transactionRejectionReason; }

    /**
     * True when this transaction was automatically cancelled by the backend
     * as a No-Show (see ReservationWorkflowService::processNoShow()'s
     * "NO_SHOW:" marker prefix on rejection_reason) - server-authoritative,
     * replacing the old on-device-only LocalTransactionState simulation.
     */
    public boolean isNoShow() {
        return transactionRejectionReason != null && transactionRejectionReason.trim().startsWith("NO_SHOW:");
    }

    /** The guest-facing No-Show reason with the internal "NO_SHOW:" marker prefix stripped, or null if this isn't a No-Show. */
    public String getNoShowReason() {
        if (!isNoShow()) return null;
        return transactionRejectionReason.trim().substring("NO_SHOW:".length()).trim();
    }
    @Nullable public PaymentSummary getPaymentSummary() { return paymentSummary; }
    public void setPaymentSummary(@Nullable PaymentSummary paymentSummary) { this.paymentSummary = paymentSummary; }
    public List<PaymentTransactionRecord> getPaymentTransactions() { return paymentTransactions; }
    public void setPaymentTransactions(@Nullable List<PaymentTransactionRecord> paymentTransactions) {
        this.paymentTransactions = paymentTransactions != null ? paymentTransactions : new ArrayList<>();
    }
    public List<ReceiptSummary> getReceipts() { return receipts; }
    public void setReceipts(@Nullable List<ReceiptSummary> receipts) {
        this.receipts = receipts != null ? receipts : new ArrayList<>();
    }

    /** True once the backend has attached its own authoritative payment_summary to this Booking - see PaymentSummary's own doc for why every caller should check this before falling back to client-side reconstruction. */
    public boolean hasAuthoritativePaymentSummary() {
        return paymentSummary != null;
    }

    /**
     * Total Amount Paid - the backend's own paymentSummary.totalAmountPaid
     * when available, falling back to the legacy client-side amountPaid
     * field only for an older/not-yet-migrated response. Callers should
     * prefer this over getAmountPaid() directly wherever a mix of old and
     * new responses might occur - see PAYMENT_RECEIPT_HISTORY_BACKEND_SPEC.md
     * §11 ("Android must not independently decide... Total Amount Paid...
     * when those values are provided by the backend").
     */
    public double getEffectiveTotalAmountPaid() {
        return paymentSummary != null ? paymentSummary.totalAmountPaid : amountPaid;
    }

    /** Remaining Balance - see getEffectiveTotalAmountPaid()'s identical fallback rule. */
    public double getEffectiveRemainingBalance() {
        return paymentSummary != null ? paymentSummary.remainingBalance : getRemainingBalance();
    }

    /**
     * Whether the Official Payment Receipt is available - the backend's own
     * official_receipt_available flag when present, otherwise falls back to
     * this app's existing isStaffVerified()-based gate (the pre-existing,
     * less precise signal every current screen already uses). Never
     * inferred from remaining balance/payment percentage - see
     * PAYMENT_RECEIPT_HISTORY_BACKEND_SPEC.md Scenario C.
     */
    public boolean isOfficialReceiptAvailable() {
        return paymentSummary != null ? paymentSummary.officialReceiptAvailable : isStaffVerified();
    }

    /** Finds an already-issued receipt of this booking by its receipt_number, or null - never generates one client-side. */
    @Nullable
    public ReceiptSummary findReceipt(String receiptNumber) {
        if (receiptNumber == null) return null;
        for (ReceiptSummary r : receipts) {
            if (receiptNumber.equals(r.receiptNumber)) return r;
        }
        return null;
    }

    public String getPaymentDeadline() { return paymentDeadline; }
    public Double getSelectedPaymentPercentage() { return selectedPaymentPercentage; }
    public Double getRequiredPaymentAmount() { return requiredPaymentAmount; }
    public boolean isPaymentMethodLocked() { return paymentMethodLocked; }
    public boolean isEditedOnce() { return editedOnce; }
    public String getRoomTypeId() { return roomTypeId; }
    public int getRoomsRequested() { return roomsRequested; }
    public int getAdults() { return adults; }
    public int getChildren() { return children; }
    public String getGuestFirstName() { return guestFirstName; }
    public String getGuestMiddleName() { return guestMiddleName; }
    public String getGuestLastName() { return guestLastName; }
    public boolean isPaymentVerified() { return "verified".equalsIgnoreCase(paymentVerificationStatus); }
    public String getCreatedAtDisplay() { return createdAtDisplay; }
    public long getCreatedAtMillis() { return createdAtMillis; }
    public String getCheckedInAtDisplay() { return checkedInAtDisplay; }
    public String getCheckedOutAtDisplay() { return checkedOutAtDisplay; }
    public String getCompletedAtDisplay() { return completedAtDisplay; }
    public String getPaymentVerifiedAtDisplay() { return paymentVerifiedAtDisplay; }
    public String getRoomNumber() { return roomNumber; }
    public double getRoomCharge() { return roomCharge; }
    public double getAmenityCharge() { return amenityCharge; }
    public double getAdditionalGuestFee() { return additionalGuestFee; }
    public String getPaymentDateOnly() { return paymentDateOnly; }
    public String getPaymentTimeOnly() { return paymentTimeOnly; }
    public List<BookingRoom> getRooms() { return rooms; }
    public List<BookingAmenity> getAmenities() { return amenities; }

    /**
     * Every selected room type and its quantity, formatted as one compact
     * string - "Deluxe ×2" for a single type, "Bryan Dela Cruz ×1 • Deluxe
     * ×1" for multiple - the single source of truth for this summary,
     * shared by the Booking/Reservation list cards
     * (BookingAndReservationActivity#buildTrueMultiRoomSummaryText()/
     * buildGroupSummaryText()) and Booking/Reservation Details' header
     * (BookingDetailsActivity#bindHeader()) so the two screens can never
     * drift onto different wording for the same transaction. Callers
     * build the room-type-name -> quantity map themselves since they read
     * it from different sources (this object's own getRooms() for a true
     * multi-room-line-item transaction, vs several sibling Booking
     * objects' getRoomName() for the legacy client-side-grouped case).
     */
    public static String formatRoomSelectionSummary(java.util.LinkedHashMap<String, Integer> countsByRoomTypeName) {
        if (countsByRoomTypeName.isEmpty()) return "";
        if (countsByRoomTypeName.size() == 1) {
            java.util.Map.Entry<String, Integer> only = countsByRoomTypeName.entrySet().iterator().next();
            return only.getValue() > 1 ? only.getKey() + " ×" + only.getValue() : only.getKey();
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (java.util.Map.Entry<String, Integer> entry : countsByRoomTypeName.entrySet()) {
            if (!first) sb.append(" • ");
            first = false;
            sb.append(entry.getValue() > 1 ? entry.getKey() + " ×" + entry.getValue() : entry.getKey());
        }
        return sb.toString();
    }

    /**
     * The full three-tier room-selection summary text for one transaction -
     * itemized getRooms() line items first, then a legacy grouped-sibling
     * transaction (BookingGroupState#resolveGroupMembers()), then the single
     * legacy room x quantity fallback. Extracted from
     * BookingDetailsActivity#buildHeaderRoomSelectionSummary() so
     * TransactionListActivity's read-only detail dialog shows the exact same
     * text for the exact same transaction rather than its own narrower
     * getAllRoomTypeNames()-based line, which never considered grouped
     * siblings at all.
     */
    public static String buildRoomSelectionSummaryText(Booking booking, @androidx.annotation.Nullable List<Booking> groupMembers) {
        if (!booking.getRooms().isEmpty()) {
            java.util.LinkedHashMap<String, Integer> counts = new java.util.LinkedHashMap<>();
            for (BookingRoom room : booking.getRooms()) {
                counts.merge(room.getRoomTypeName(), room.getQuantity(), Integer::sum);
            }
            return formatRoomSelectionSummary(counts);
        }
        if (groupMembers != null && !groupMembers.isEmpty()) {
            java.util.LinkedHashMap<String, Integer> counts = new java.util.LinkedHashMap<>();
            for (Booking member : groupMembers) {
                counts.merge(member.getRoomName(), 1, Integer::sum);
            }
            return formatRoomSelectionSummary(counts);
        }
        int quantity = Math.max(1, booking.getRoomsRequested());
        return quantity > 1 ? booking.getRoomName() + " ×" + quantity : booking.getRoomName();
    }

    /**
     * "Assigned Room(s): 201, 202" once a receptionist has assigned a physical
     * room, "Room Assignment: Pending" beforehand - a single shared resolver
     * (BookingDetailsActivity's per-room-type cards, TransactionListActivity's
     * read-only detail dialog) so every room-image call site treats null/
     * empty/the legacy "Pending Assignment" placeholder string identically
     * rather than each guessing its own null-handling.
     */
    public static String resolveAssignedRoomsText(android.content.Context context, String rawRoomNumber) {
        if (rawRoomNumber == null || rawRoomNumber.trim().isEmpty() || "Pending Assignment".equalsIgnoreCase(rawRoomNumber.trim())) {
            return context.getString(R.string.room_assignment_pending_label);
        }
        return context.getString(R.string.assigned_rooms_format, rawRoomNumber.trim());
    }

    /**
     * Overload for a genuinely itemized room-type line (BookingRoom#getAssignedRoomNumbers()) -
     * real per-type physical room numbers, populated server-side once BookingRoomDto's
     * assigned_room_numbers field is live (see that field's own doc). "Pending" when
     * empty (before check-in, or a not-yet-converted Reservation).
     */
    public static String resolveAssignedRoomsText(android.content.Context context, List<String> assignedRoomNumbers) {
        if (assignedRoomNumbers == null || assignedRoomNumbers.isEmpty()) {
            return context.getString(R.string.room_assignment_pending_label);
        }
        return context.getString(R.string.assigned_rooms_format, android.text.TextUtils.join(", ", assignedRoomNumbers));
    }

    /** Derived from getRooms() rather than a separately-transmitted count, so it can never disagree with the actual line items - see MULTI_ROOM_TRANSACTION_BACKEND_SPEC.md. 0 when getRooms() is empty (today, always). */
    public int getTotalRoomCount() {
        int total = 0;
        for (BookingRoom r : rooms) total += r.getQuantity();
        return total;
    }

    /** Distinct room types in this transaction - see getTotalRoomCount()'s own doc. */
    public int getTotalRoomTypeCount() {
        return rooms.size();
    }

    /**
     * Every distinct room type name in this transaction - getRooms()'s own
     * itemized breakdown when present, falling back to the single legacy
     * getRoomType() otherwise. The single source every room-type search/
     * filter/matching call site should use instead of getRoomType() alone,
     * which only ever reflects the FIRST selected type for a multi-room-type
     * transaction (see MULTI_ROOM_TRANSACTION_BACKEND_SPEC.md) - searching
     * or filtering by a second/third room type (e.g. "Suite" in a
     * "Deluxe + Suite" booking) would otherwise silently never match.
     */
    public List<String> getAllRoomTypeNames() {
        if (!rooms.isEmpty()) {
            List<String> names = new java.util.ArrayList<>();
            for (BookingRoom r : rooms) {
                if (r.getRoomTypeName() != null && !names.contains(r.getRoomTypeName())) {
                    names.add(r.getRoomTypeName());
                }
            }
            return names;
        }
        return roomType != null && !roomType.isEmpty()
                ? java.util.Collections.singletonList(roomType)
                : java.util.Collections.emptyList();
    }

    /** True if any of this transaction's room types (see getAllRoomTypeNames()) matches, case-insensitively. */
    public boolean hasRoomType(String name) {
        if (name == null) return false;
        for (String rt : getAllRoomTypeNames()) {
            if (rt.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    /** True if any of this transaction's room types (see getAllRoomTypeNames()) contains the query, case-insensitively - for keyword search. */
    public boolean anyRoomTypeContains(String lowercaseQuery) {
        for (String rt : getAllRoomTypeNames()) {
            if (rt.toLowerCase(java.util.Locale.US).contains(lowercaseQuery)) return true;
        }
        return false;
    }

    /** Derived from getAmenities() - see getTotalRoomCount()'s own doc, same rule. */
    public int getTotalAmenityItemCount() {
        int total = 0;
        for (BookingAmenity a : amenities) total += a.getQuantity();
        return total;
    }

    /** Distinct amenity types in this transaction - see getTotalAmenityItemCount()'s own doc. */
    public int getTotalAmenityTypeCount() {
        return amenities.size();
    }

    /**
     * The Representative Name captured at reservation/booking time, composed
     * the same way the backend's Reservation::getStayGuestFullNameAttribute()/
     * Booking::getStayGuestFullNameAttribute() does - null when neither name
     * part is set (a legacy or system-created record).
     */
    public String getRepresentativeName() {
        if ((guestFirstName == null || guestFirstName.trim().isEmpty()) && (guestLastName == null || guestLastName.trim().isEmpty())) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (guestFirstName != null) sb.append(guestFirstName.trim()).append(' ');
        if (guestMiddleName != null && !guestMiddleName.trim().isEmpty()) sb.append(guestMiddleName.trim()).append(' ');
        if (guestLastName != null) sb.append(guestLastName.trim());
        String result = sb.toString().trim().replaceAll("\\s+", " ");
        return result.isEmpty() ? null : result;
    }

    // Setters
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public void setRoomName(String roomName) { this.roomName = roomName; }
    public void setRoomType(String roomType) { this.roomType = roomType; }
    public void setGuests(int guests) { this.guests = guests; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }
    public void setStatus(String status) { this.status = status; }
    public void setAmountPaid(double amountPaid) {
        if (amountPaid < 0) {
            this.amountPaid = 0;
        } else {
            this.amountPaid = amountPaid;
        }
    }
    public void setCheckInDate(String checkInDate) { this.checkInDate = checkInDate; }
    public void setCheckOutDate(String checkOutDate) { this.checkOutDate = checkOutDate; }
    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }
    public void setPaymentDate(String paymentDate) { this.paymentDate = paymentDate; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public void setCancellationDate(String cancellationDate) { this.cancellationDate = cancellationDate; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }
    public void setActualCheckIn(String actualCheckIn) { this.actualCheckIn = actualCheckIn; }
    public void setActualCheckOut(String actualCheckOut) { this.actualCheckOut = actualCheckOut; }
    public void setIdCardType(String idCardType) { this.idCardType = idCardType; }
    public void setIdCardUri(String idCardUri) { this.idCardUri = idCardUri; }
    public void setPaymentReceiptUri(String paymentReceiptUri) { this.paymentReceiptUri = paymentReceiptUri; }
    public void setServiceFeeApplied(boolean serviceFeeApplied) { this.serviceFeeApplied = serviceFeeApplied; }
    public void setAdditionalGuests(List<AdditionalGuest> additionalGuests) { this.additionalGuests = additionalGuests; }
    public void setHasBooking(boolean hasBooking) { this.hasBooking = hasBooking; }
    public void setDirectBooking(boolean directBooking) { this.directBooking = directBooking; }
    public void setBillingStatus(String billingStatus) { this.billingStatus = billingStatus; }
    public void setPaymentPendingVerification(boolean paymentPendingVerification) { this.paymentPendingVerification = paymentPendingVerification; }
    public void setStaffVerified(boolean staffVerified) { this.staffVerified = staffVerified; }
    public void setHistoricalReservation(boolean historicalReservation) { this.historicalReservation = historicalReservation; }
    public void setConvertedBookingId(String convertedBookingId) { this.convertedBookingId = convertedBookingId; }
    public void setHiddenAt(String hiddenAt) { this.hiddenAt = hiddenAt; }
    public void setPaymentHistory(List<PaymentRecord> paymentHistory) { this.paymentHistory = paymentHistory != null ? paymentHistory : new ArrayList<>(); }
    public void setLatestPaymentId(String latestPaymentId) { this.latestPaymentId = latestPaymentId; }
    public void setGcashNumber(String gcashNumber) { this.gcashNumber = gcashNumber; }
    public void setReceiptUrl(String receiptUrl) { this.receiptUrl = receiptUrl; }
    public void setRoomImageUrl(String roomImageUrl) { this.roomImageUrl = roomImageUrl; }
    public void setPaymentVerificationStatus(String paymentVerificationStatus) { this.paymentVerificationStatus = paymentVerificationStatus; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public void setTransactionRejectionReason(String transactionRejectionReason) { this.transactionRejectionReason = transactionRejectionReason; }
    public void setPaymentDeadline(String paymentDeadline) { this.paymentDeadline = paymentDeadline; }
    public void setSelectedPaymentPercentage(Double selectedPaymentPercentage) { this.selectedPaymentPercentage = selectedPaymentPercentage; }
    public void setRequiredPaymentAmount(Double requiredPaymentAmount) { this.requiredPaymentAmount = requiredPaymentAmount; }
    public void setPaymentMethodLocked(boolean paymentMethodLocked) { this.paymentMethodLocked = paymentMethodLocked; }
    public void setEditedOnce(boolean editedOnce) { this.editedOnce = editedOnce; }
    public void setRoomTypeId(String roomTypeId) { this.roomTypeId = roomTypeId; }
    public void setRoomsRequested(int roomsRequested) { this.roomsRequested = roomsRequested; }
    public void setAdults(int adults) { this.adults = adults; }
    public void setChildren(int children) { this.children = children; }
    public void setGuestFirstName(String guestFirstName) { this.guestFirstName = guestFirstName; }
    public void setGuestMiddleName(String guestMiddleName) { this.guestMiddleName = guestMiddleName; }
    public void setGuestLastName(String guestLastName) { this.guestLastName = guestLastName; }
    public void setCreatedAtDisplay(String createdAtDisplay) { this.createdAtDisplay = createdAtDisplay; }
    public void setCreatedAtMillis(long createdAtMillis) { this.createdAtMillis = createdAtMillis; }
    public void setCheckedInAtDisplay(String checkedInAtDisplay) { this.checkedInAtDisplay = checkedInAtDisplay; }
    public void setCheckedOutAtDisplay(String checkedOutAtDisplay) { this.checkedOutAtDisplay = checkedOutAtDisplay; }
    public void setCompletedAtDisplay(String completedAtDisplay) { this.completedAtDisplay = completedAtDisplay; }
    public void setPaymentVerifiedAtDisplay(String paymentVerifiedAtDisplay) { this.paymentVerifiedAtDisplay = paymentVerifiedAtDisplay; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }
    public void setRoomCharge(double roomCharge) { this.roomCharge = roomCharge; }
    public void setAmenityCharge(double amenityCharge) { this.amenityCharge = amenityCharge; }
    public void setAdditionalGuestFee(double additionalGuestFee) { this.additionalGuestFee = additionalGuestFee; }
    public void setPaymentDateOnly(String paymentDateOnly) { this.paymentDateOnly = paymentDateOnly; }
    public void setPaymentTimeOnly(String paymentTimeOnly) { this.paymentTimeOnly = paymentTimeOnly; }
    public void setRooms(List<BookingRoom> rooms) { this.rooms = rooms != null ? rooms : new ArrayList<>(); }
    public void setAmenities(List<BookingAmenity> amenities) { this.amenities = amenities != null ? amenities : new ArrayList<>(); }
}


