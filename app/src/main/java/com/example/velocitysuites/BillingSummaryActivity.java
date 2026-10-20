package com.example.velocitysuites;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.velocitysuites.network.dto.RequestableAmenityDto;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.Locale;

/**
 * Billing Summary: the checkout step between "Pay Now" on an existing
 * Reservation List card and the Payment screen. Shows the full reservation
 * details and gates Confirm Booking behind having viewed (and accepted) the
 * Terms & Cancellation Policy before handing off to PaymentActivity, which
 * performs the actual payment and (server-side) reservation-to-booking
 * conversion.
 */
public class BillingSummaryActivity extends SafeActivity {

    public static final String EXTRA_RESERVATION_ID = "RESERVATION_ID";

    private RoomRepository repository;
    private String reservationId;
    private Booking booking;

    private TextView tvBillingRef, tvBillingStatus, tvBillingHeaderGrandTotal, tvBillingRoomType, tvBillingDates,
            tvBillingNights, tvBillingRate, tvBillingDiscount, tvBillingPrimaryGuest, tvBillingRepresentativeName, tvBillingGuestCount, tvBillingIdType,
            tvBillingTotalAmenities, tvBillingBalanceDue, tvBillingAlreadyPaid, tvBillingRemaining,
            tvBillingPayNowOption, tvBillingPayNowAmount,
            tvBillingPaymentMethod, tvBillingPaymentType, tvBillingPaymentStatus,
            tvAmenitiesSectionTotal, tvNoAmenitiesMessage,
            tvBillingRoomQuantity, tvBillingSingleRoomRate, tvBillingStayRoomAmount,
            tvBillingPayNowNote, tvBillingConfirmHint;
    private View tvViewBillingTerms;
    private View layoutBillingAdditionalGuestsSection;
    private LinearLayout layoutBillingAdditionalGuests;
    private View cardBillingAmenities;
    private LinearLayout layoutBillingAmenitiesRows;
    private View layoutBillingDiscount;
    /** Legacy single-room-type block vs. the itemized per-room-type rows below it - exactly
     *  one of the two is visible at a time, chosen by whether booking.getRooms() has real
     *  itemized data (see updateBillingBreakdownAmounts()). */
    private View layoutBillingSingleRoomInfo;
    private LinearLayout layoutBillingRoomsRows;
    /** "Room Rate Per Night" only makes sense for a single room type/rate - hidden whenever
     *  the itemized rows above already show each room type's own rate individually. */
    private View cardBillingPaymentInfo, layoutBillingPaymentMethod, layoutBillingPaymentType, layoutBillingPaymentStatus;
    private MaterialCheckBox cbAcceptBillingTerms;
    private MaterialButton btnConfirmBilling;
    /** Sum of (price × quantity) over this reservation's paid add-on amenities - the single
     *  computed value shared by both the Add-on Amenities section's own total row and the
     *  Billing Breakdown's "Total Amenities Amount" row, so the two can never disagree. */
    private double totalAmenitiesAmount = 0;
    /** Set once in populateSummary(); reused by updateBillingBreakdownAmounts() (called again
     *  later from populateAmenities()) so nights-based math never needs a second computation. */
    private long nights = 0;
    /** What bindPayNow() last computed - the Payment Information card reads "already paid" from it. */
    private BillingAmounts amounts;
    /** The guest has opened and read the Terms and Policy (this visit). */
    private boolean termsRead;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.billingsummary);

        repository = RoomRepository.getInstance(this);
        reservationId = getIntent().getStringExtra(EXTRA_RESERVATION_ID);

        initViews();
        setupTermsGate();

        repository.refreshBookings(new RoomRepository.RepositoryCallback<List<Booking>>() {
            @Override
            public void onSuccess(List<Booking> result) {
                if (isFinishing() || isDestroyed()) return;
                loadReservation();
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                loadReservation();
            }
        });

        if (reservationId != null) {
            repository.fetchRequestableAmenities(reservationId, new RoomRepository.RepositoryCallback<List<RequestableAmenityDto>>() {
                @Override
                public void onSuccess(List<RequestableAmenityDto> items) {
                    if (isFinishing() || isDestroyed()) return;
                    populateAmenities(items);
                }

                @Override
                public void onError(String message) {
                    if (isFinishing() || isDestroyed()) return;
                    // No amenities line rather than a stale/blank one - same
                    // "hide rather than fabricate" convention as the discount line.
                    populateAmenities(null);
                }
            });
        }
    }

    // ---- Keeping the figures current ----
    // This is the screen a guest reads just before paying, so while it is open the amounts and status are checked
    // again every 30s (and when the guest returns to it) instead of staying as they were when it opened.
    private final VisiblePoller visiblePoller = new VisiblePoller(this::refreshQuietly);
    /** What populateSummary() last drew, so a check that finds the same booking redraws nothing. */
    private final ChangeGate billingGate = new ChangeGate();
    /** onCreate() already loaded, so the very first onResume() must not check again. */
    private boolean firstResume = true;

    @Override
    protected void onResume() {
        super.onResume();
        if (firstResume) {
            firstResume = false;
        } else {
            refreshQuietly();
        }
        visiblePoller.start();
    }

    @Override
    protected void onPause() {
        visiblePoller.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        visiblePoller.stop();
        super.onDestroy();
    }

    /**
     * A light, silent check for changes to this reservation (a receptionist verifying a payment, a discount applied,
     * the status moving on). Redraws only if THIS booking really changed; if it has vanished from the list the
     * screen is left as it is (opening it already handles that case) and a failed check says nothing.
     */
    private void refreshQuietly() {
        if (reservationId == null || booking == null) return;
        repository.pollBookingsSplit((merged, reservationsError, directError) -> {
            if (isFinishing() || isDestroyed()) return;
            Booking fresh = null;
            for (Booking b : repository.getBookings()) {
                if (b.getId().equals(reservationId)) {
                    fresh = b;
                    break;
                }
            }
            if (fresh == null || !billingGate.accept(fresh)) return;
            booking = fresh;
            populateSummary();
        });
    }

    private void initViews() {
        findViewById(R.id.btnBillingBack).setOnClickListener(v -> finish());

        tvBillingRef = findViewById(R.id.tvBillingRef);
        tvBillingStatus = findViewById(R.id.tvBillingStatus);
        tvBillingHeaderGrandTotal = findViewById(R.id.tvBillingHeaderGrandTotal);
        tvBillingPayNowNote = findViewById(R.id.tvBillingPayNowNote);
        tvBillingConfirmHint = findViewById(R.id.tvBillingConfirmHint);
        tvBillingRoomType = findViewById(R.id.tvBillingRoomType);
        tvBillingDates = findViewById(R.id.tvBillingDates);
        tvBillingNights = findViewById(R.id.tvBillingNights);
        tvBillingRate = findViewById(R.id.tvBillingRate);
        layoutBillingDiscount = findViewById(R.id.layoutBillingDiscount);
        tvBillingDiscount = findViewById(R.id.tvBillingDiscount);
        cardBillingAmenities = findViewById(R.id.cardBillingAmenities);
        layoutBillingAmenitiesRows = findViewById(R.id.layoutBillingAmenitiesRows);
        tvBillingPrimaryGuest = findViewById(R.id.tvBillingPrimaryGuest);
        tvBillingRepresentativeName = findViewById(R.id.tvBillingRepresentativeName);
        tvBillingGuestCount = findViewById(R.id.tvBillingGuestCount);
        tvBillingIdType = findViewById(R.id.tvBillingIdType);
        tvBillingTotalAmenities = findViewById(R.id.tvBillingTotalAmenities);
        tvBillingBalanceDue = findViewById(R.id.tvBillingBalanceDue);
        tvBillingAlreadyPaid = findViewById(R.id.tvBillingAlreadyPaid);
        tvBillingRemaining = findViewById(R.id.tvBillingRemaining);
        tvBillingPayNowOption = findViewById(R.id.tvBillingPayNowOption);
        tvBillingPayNowAmount = findViewById(R.id.tvBillingPayNowAmount);
        tvAmenitiesSectionTotal = findViewById(R.id.tvAmenitiesSectionTotal);
        tvNoAmenitiesMessage = findViewById(R.id.tvNoAmenitiesMessage);
        tvBillingRoomQuantity = findViewById(R.id.tvBillingRoomQuantity);
        tvBillingSingleRoomRate = findViewById(R.id.tvBillingSingleRoomRate);
        tvBillingStayRoomAmount = findViewById(R.id.tvBillingStayRoomAmount);
        layoutBillingSingleRoomInfo = findViewById(R.id.layoutBillingSingleRoomInfo);
        layoutBillingRoomsRows = findViewById(R.id.layoutBillingRoomsRows);

        cardBillingPaymentInfo = findViewById(R.id.cardBillingPaymentInfo);
        layoutBillingPaymentMethod = findViewById(R.id.layoutBillingPaymentMethod);
        tvBillingPaymentMethod = findViewById(R.id.tvBillingPaymentMethod);
        layoutBillingPaymentType = findViewById(R.id.layoutBillingPaymentType);
        tvBillingPaymentType = findViewById(R.id.tvBillingPaymentType);
        layoutBillingPaymentStatus = findViewById(R.id.layoutBillingPaymentStatus);
        tvBillingPaymentStatus = findViewById(R.id.tvBillingPaymentStatus);

        layoutBillingAdditionalGuestsSection = findViewById(R.id.layoutBillingAdditionalGuestsSection);
        layoutBillingAdditionalGuests = findViewById(R.id.layoutBillingAdditionalGuests);

        tvViewBillingTerms = findViewById(R.id.tvViewBillingTerms);
        cbAcceptBillingTerms = findViewById(R.id.cbAcceptBillingTerms);
        btnConfirmBilling = findViewById(R.id.btnConfirmBilling);
    }

    /**
     * The acceptance checkbox stays disabled until the guest has actually
     * opened the Terms & Cancellation Policy dialog at least once; Confirm
     * Booking then stays disabled until the checkbox itself is checked.
     */
    private void setupTermsGate() {
        tvViewBillingTerms.setOnClickListener(v -> showTermsDialog());
        cbAcceptBillingTerms.setOnCheckedChangeListener((buttonView, isChecked) -> updateConfirmState());
        btnConfirmBilling.setOnClickListener(v -> confirmAndProceedToPayment());
        updateConfirmState();
    }

    /** Confirm Booking needs both: the Terms and Policy opened and read, and the box ticked. */
    static boolean canConfirm(boolean termsRead, boolean checked) {
        return termsRead && checked;
    }

    /** The short reason Confirm Booking is disabled, or 0 when it is enabled. */
    static int confirmHintRes(boolean termsRead, boolean checked) {
        if (canConfirm(termsRead, checked)) return 0;
        return termsRead ? R.string.billing_confirm_hint_tick : R.string.billing_confirm_hint_read;
    }

    private void updateConfirmState() {
        boolean checked = cbAcceptBillingTerms.isChecked();
        btnConfirmBilling.setEnabled(canConfirm(termsRead, checked));
        int hint = confirmHintRes(termsRead, checked);
        tvBillingConfirmHint.setVisibility(hint == 0 ? View.GONE : View.VISIBLE);
        if (hint != 0) tvBillingConfirmHint.setText(hint);
    }

    /** Read-only Terms and Policy viewer - the same shared one every consent screen uses. Reading it to the end unlocks the box. */
    private void showTermsDialog() {
        TermsPolicyDialog.show(this, null, () -> {
            termsRead = true;
            cbAcceptBillingTerms.setEnabled(true);
            updateConfirmState();
        });
    }

    private void loadReservation() {
        booking = null;
        if (reservationId != null) {
            for (Booking b : repository.getBookings()) {
                if (b.getId().equals(reservationId)) {
                    booking = b;
                    break;
                }
            }
        }

        if (booking == null) {
            // Most likely already paid, modified, or cancelled elsewhere since
            // the guest tapped Pay Now.
            Toast.makeText(this, R.string.error_no_active_booking, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        billingGate.accept(booking); // what is about to be drawn - see refreshQuietly()
        populateSummary();
    }

    private void populateSummary() {
        tvBillingRef.setText(getString(booking.isHasBooking() ? R.string.booking_ref_label : R.string.reservation_ref_label, booking.getDisplayId()));
        tvBillingStatus.setText(hasText(booking.getStatus()) ? BookingStatusPresenter.statusWord(this, booking) : getString(R.string.status_pending));
        logRawReservationForDebug();
        setRow(tvBillingRoomType, roomHeadline());
        setRow(tvBillingDates, hasText(booking.getCheckInDate()) && hasText(booking.getCheckOutDate())
                ? getString(R.string.date_range_format, booking.getCheckInDate(), booking.getCheckOutDate()) : null);
        nights = nightsBetween(booking.getCheckInDate(), booking.getCheckOutDate());
        tvBillingNights.setText(getResources().getQuantityString(R.plurals.receipt_nights_count, (int) nights, (int) nights));
        if (layoutBillingDiscount != null && tvBillingDiscount != null) {
            // Hidden rather than shown as ₱0.00 - only a genuine discount
            // (active promotion or Senior/PWD statutory 20%, see backend
            // Reservation::discount_preview) is worth surfacing here.
            boolean hasDiscount = booking.getDiscountAmount() > 0.009;
            layoutBillingDiscount.setVisibility(hasDiscount ? View.VISIBLE : View.GONE);
            if (hasDiscount) {
                tvBillingDiscount.setText(MoneyFormat.format(-booking.getDiscountAmount()));
            }
        }

        setRow(tvBillingPrimaryGuest, guestAccountName());
        // Transaction-specific: the name captured on THIS booking at creation time, never the signed-in account's own
        // name above - a guest can book a stay for someone else.
        setRow(tvBillingRepresentativeName, RepresentativeName.representativeOf(this, booking));
        tvBillingGuestCount.setText(getResources().getQuantityString(R.plurals.guests_count, booking.getGuests(), booking.getGuests()));

        String idType = booking.getIdCardType();
        setRow(tvBillingIdType, idType == null || idType.equalsIgnoreCase("None") ? null : idType);

        List<Booking.AdditionalGuest> additionalGuests = booking.getAdditionalGuests();
        boolean hasAdditionalGuests = additionalGuests != null && !additionalGuests.isEmpty();
        layoutBillingAdditionalGuestsSection.setVisibility(hasAdditionalGuests ? View.VISIBLE : View.GONE);
        layoutBillingAdditionalGuests.removeAllViews();
        if (hasAdditionalGuests) {
            for (Booking.AdditionalGuest guest : additionalGuests) {
                TextView row = new TextView(this);
                row.setText(hasText(guest.relationship)
                        ? getString(R.string.details_guest_entry, guest.name, guest.relationship) : String.valueOf(guest.name));
                row.setTextColor(getResources().getColor(R.color.velocity_text_primary, getTheme()));
                row.setTextSize(13f);
                row.setPadding(0, 0, 0, (int) (getResources().getDisplayMetrics().density * 4));
                layoutBillingAdditionalGuests.addView(row);
            }
        }

        // Booking Amount/Total Amenities Amount/Grand Total all derive from this one method
        // (see its own doc comment) - called again once populateAmenities() knows the real
        // amenities figure, so Booking Amount starts as the full total and is corrected down
        // the moment amenities load, rather than ever showing two different numbers for the
        // same figure in the Add-on Amenities section vs. this Billing Breakdown card.
        updateBillingBreakdownAmounts();

        // Payment Information: the method on record, else GCash - the payment screen that Confirm Booking opens is the
        // GCash one. The type shows only once something was paid; the status is always the shared transaction status.
        boolean hasPaymentType = amounts != null && amounts.alreadyPaid > 0.009;
        String method = booking.getPaymentMethod();
        tvBillingPaymentMethod.setText("cash".equalsIgnoreCase(method) ? getString(R.string.payment_method_cash)
                : getString(R.string.payment_method_gcash)); // no method on record: the GCash screen is the one that opens
        layoutBillingPaymentType.setVisibility(hasPaymentType ? View.VISIBLE : View.GONE);
        if (hasPaymentType) {
            tvBillingPaymentType.setText(PaymentStateUtil.isFullyPaid(booking)
                    ? R.string.review_payment_type_full : R.string.review_payment_type_partial);
        }
        tvBillingPaymentStatus.setText(BookingStatusPresenter.paymentStatusPillText(this, booking));
        cardBillingPaymentInfo.setVisibility(View.VISIBLE);
    }

    /**
     * The room (type) name for this reservation, taken from the first source that has one: the itemized room lines
     * (room_lines[].room_type), then the reservation's room type (room_type.name), then its room name. A generic
     * "Room" is the mapper's own stand-in for "the API sent no name", so it does not count. Null when the API sent
     * none - the caller then hides the row instead of printing a placeholder.
     */
    @androidx.annotation.Nullable
    private String roomHeadline() {
        return roomNameOf(booking);
    }

    @androidx.annotation.Nullable
    static String roomNameOf(Booking b) {
        List<String> names = b.getAllRoomTypeNames();
        if (!names.isEmpty()) return android.text.TextUtils.join(" + ", names);
        if (hasText(b.getRoomType())) return b.getRoomType().trim();
        String name = b.getRoomName();
        return hasText(name) && !"Room".equalsIgnoreCase(name.trim()) ? name.trim() : null;
    }

    /**
     * Single authoritative source for Room Total / Total Amenities Amount / Grand Total.
     * Called once right after the reservation loads (totalAmenitiesAmount still 0, so Room
     * Total briefly shows the full total) and again once populateAmenities() knows the real
     * figure - the Add-on Amenities section and this Billing Breakdown card always read from
     * this same computed value, so they can never disagree.
     */
    private void updateBillingBreakdownAmounts() {
        if (booking == null) return;

        List<BookingRoom> roomLines = booking.getRooms();
        boolean hasItemizedRooms = roomLines != null && !roomLines.isEmpty();

        double roomTotal;
        if (hasItemizedRooms) {
            // Real, saved per-room-type records (Reservation/Booking room lines) - each
            // selected room type keeps its own rate, never averaged/collapsed into another
            // type's price. This is the authoritative figure; it is NEVER reconstructed from
            // the reservation's overall grand total.
            double sum = 0;
            for (BookingRoom line : roomLines) sum += line.getSubtotal();
            roomTotal = sum;
        } else {
            // Legacy fallback for a reservation that only ever had one room type (no itemized
            // lines saved). booking.getTotalAmount()'s meaning depends on whether it already
            // bundles amenities (see Booking#isTotalIncludesAmenities()/ApiMapper) - true for
            // both a converted Booking's real Billing total AND an unconverted Reservation's
            // total_amount_due (which already sums room + amenities server-side). Subtracting
            // totalAmenitiesAmount back out only when that flag is true avoids double-counting
            // amenities that were never added on top of it in the first place - previously this
            // only checked isHasBooking(), so a still-pending Reservation's already
            // amenities-inclusive total_amount_due had amenities added a SECOND time (the
            // confirmed ₱8,650 + ₱150 = ₱8,800 bug).
            roomTotal = booking.isTotalIncludesAmenities()
                    ? Math.max(0, booking.getTotalAmount() - totalAmenitiesAmount)
                    : booking.getTotalAmount();
        }
        // The server's own total when it already bundles the amenities (what the payment screen charges against);
        // otherwise the sum of what this screen lists.
        double grandTotal = booking.isTotalIncludesAmenities() && booking.getTotalAmount() > 0.009
                ? booking.getTotalAmount() : roomTotal + totalAmenitiesAmount;

        if (layoutBillingSingleRoomInfo != null) {
            layoutBillingSingleRoomInfo.setVisibility(hasItemizedRooms ? View.GONE : View.VISIBLE);
        }
        if (hasItemizedRooms) {
            if (layoutBillingRoomsRows != null) {
                layoutBillingRoomsRows.setVisibility(View.VISIBLE);
                layoutBillingRoomsRows.removeAllViews();
                for (BookingRoom line : roomLines) {
                    layoutBillingRoomsRows.addView(buildRoomLineRow(line, roomLines.size() > 1));
                }
            }
        } else if (layoutBillingRoomsRows != null) {
            layoutBillingRoomsRows.setVisibility(View.GONE);
            layoutBillingRoomsRows.removeAllViews();

            // Single room type only - same "combined nightly rate ÷ quantity" breakdown as
            // before, now fed by the corrected roomTotal above.
            int roomQuantity = Math.max(1, booking.getRoomsRequested());
            double totalRoomRatePerNight = nights > 0 ? roomTotal / nights : roomTotal;
            double perRoomRate = totalRoomRatePerNight / roomQuantity;

            if (tvBillingRoomQuantity != null) tvBillingRoomQuantity.setText(getString(R.string.rooms_quantity_format, roomQuantity));
            if (tvBillingSingleRoomRate != null) tvBillingSingleRoomRate.setText(getString(R.string.price_per_night_format, perRoomRate));
        }

        if (tvBillingStayRoomAmount != null) tvBillingStayRoomAmount.setText(MoneyFormat.format(roomTotal));
        if (tvBillingRate != null) tvBillingRate.setText(MoneyFormat.format(roomTotal));

        if (tvAmenitiesSectionTotal != null) tvAmenitiesSectionTotal.setText(MoneyFormat.format(totalAmenitiesAmount)); // never blank while the add-ons load
        if (tvBillingTotalAmenities != null) tvBillingTotalAmenities.setText(MoneyFormat.format(totalAmenitiesAmount));
        if (tvBillingBalanceDue != null) tvBillingBalanceDue.setText(MoneyFormat.format(grandTotal));
        if (tvBillingHeaderGrandTotal != null) tvBillingHeaderGrandTotal.setText(MoneyFormat.format(grandTotal));
        bindPayNow(grandTotal);

    }

    /**
     * Amount Already Paid, Remaining Balance and the highlighted Total Amount to Pay: the Grand Total before any
     * payment (the guest picks Partial 20-50% or Full on the next screen), the Remaining Balance after one.
     */
    private void bindPayNow(double grandTotal) {
        amounts = BillingAmounts.compute(grandTotal, booking.getEffectiveTotalAmountPaid(), submittedAmountAwaitingVerification());
        tvBillingAlreadyPaid.setText(MoneyFormat.format(amounts.alreadyPaid));
        tvBillingRemaining.setText(MoneyFormat.format(amounts.remaining));
        tvBillingPayNowAmount.setText(MoneyFormat.format(amounts.amountToPay));
        tvBillingPayNowAmount.setGravity(amounts.paymentMade ? android.view.Gravity.END : android.view.Gravity.START);
        // Before a payment the block is just the amount (the Grand Total is already in the header and the breakdown).
        tvBillingPayNowOption.setText(R.string.billing_remaining_balance_label);
        tvBillingPayNowOption.setVisibility(amounts.paymentMade ? View.VISIBLE : View.GONE);
        tvBillingAlreadyPaid.setTextColor(getColor(amounts.paymentMade ? R.color.velocity_green_dark : R.color.velocity_text_primary));
        tvBillingPayNowNote.setVisibility(amounts.paymentMade ? View.GONE : View.VISIBLE);
    }

    /** A GCash payment already sent but not yet verified is not in the verified total; it still counts as paid here. */
    @androidx.annotation.Nullable
    private Double submittedAmountAwaitingVerification() {
        return booking.isPaymentPendingVerification() ? booking.getRequiredPaymentAmount() : null;
    }

    /** Shows the value in its row, or hides the whole row when the API sent nothing - never a placeholder. */
    private static void setRow(TextView value, @androidx.annotation.Nullable String text) {
        boolean has = hasText(text);
        if (has) value.setText(text);
        ((View) value.getParent()).setVisibility(has ? View.VISIBLE : View.GONE);
    }

    private static boolean hasText(@androidx.annotation.Nullable String text) {
        return text != null && !text.trim().isEmpty() && !"null".equalsIgnoreCase(text.trim());
    }

    /** Debug builds only: what the API gave us for the room name and the money, to see which field is missing. */
    private void logRawReservationForDebug() {
        if (!BuildConfig.DEBUG) return;
        android.util.Log.d("BillingSummary", "id=" + booking.getId() + " displayId=" + booking.getDisplayId()
                + " roomName=" + booking.getRoomName() + " roomType=" + booking.getRoomType()
                + " roomTypeId=" + booking.getRoomTypeId() + " roomLines=" + booking.getRooms().size()
                + " roomLineTypes=" + booking.getAllRoomTypeNames() + " resolvedRoomName=" + roomNameOf(booking)
                + " total=" + booking.getTotalAmount() + " totalIncludesAmenities=" + booking.isTotalIncludesAmenities()
                + " paid=" + booking.getAmountPaid() + " effectivePaid=" + booking.getEffectiveTotalAmountPaid()
                + " method=" + booking.getPaymentMethod() + " pendingVerification=" + booking.isPaymentPendingVerification()
                + " pct=" + booking.getSelectedPaymentPercentage() + " required=" + booking.getRequiredPaymentAmount());
    }


    /**
     * One itemized room-type block: price per night, quantity, and the subtotal only when more than one room of this
     * type was booked (with one room it would repeat Room Total). The type name leads only when several types are listed
     * (a single type is already the "Room Type" row). Same label/value style as every other row.
     */
    private View buildRoomLineRow(BookingRoom room, boolean showName) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        if (showName && hasText(room.getRoomTypeName())) {
            container.addView(buildAmenityRow(getString(R.string.room_type_summary_label), room.getRoomTypeName()));
        }
        // Nights is not repeated per room: every room shares the one stay period shown once in Stay Information.
        container.addView(buildAmenityRow(getString(R.string.price_per_room_night_label),
                MoneyFormat.format(room.getPricePerNight())));
        container.addView(buildAmenityRow(getString(R.string.selected_room_quantity_label),
                String.valueOf(room.getQuantity())));
        if (room.getQuantity() > 1) {
            container.addView(buildAmenityRow(getString(R.string.room_line_subtotal_label),
                    MoneyFormat.format(room.getSubtotal())));
        }
        return container;
    }

    /**
     * Paid/additional amenities originally selected at creation time (see
     * RequestableAmenityDto's own doc comment - this endpoint never returns complimentary/free
     * amenities), reusing the same requestable-amenities endpoint the old request screen used to
     * fetches from - no new backend endpoint needed. Never hides the card entirely: an empty
     * result shows a "no paid amenities" message plus a ₱0.00 total instead of a blank/missing
     * section.
     */
    private void populateAmenities(List<RequestableAmenityDto> items) {
        if (cardBillingAmenities == null || layoutBillingAmenitiesRows == null) return;
        layoutBillingAmenitiesRows.removeAllViews();

        double total = 0;
        if (items != null) {
            for (RequestableAmenityDto item : items) {
                // Defensive against malformed server data (negative price/quantity) - a zeroed
                // or negative quantity contributes nothing rather than subtracting from the total.
                double price = Math.max(0, item.price);
                int quantity = Math.max(0, item.original_quantity);
                if (quantity <= 0) continue;
                double subtotal = price * quantity;
                total += subtotal;
                layoutBillingAmenitiesRows.addView(buildAmenityRow(
                        getString(R.string.summary_amenity_row_qty_format, item.amenity_name, quantity),
                        MoneyFormat.format(subtotal)));
            }
        }
        totalAmenitiesAmount = total;

        if (tvNoAmenitiesMessage != null) {
            tvNoAmenitiesMessage.setVisibility(layoutBillingAmenitiesRows.getChildCount() == 0 ? View.VISIBLE : View.GONE);
        }
        if (tvAmenitiesSectionTotal != null) {
            tvAmenitiesSectionTotal.setText(MoneyFormat.format(totalAmenitiesAmount));
        }
        updateBillingBreakdownAmounts();
    }

    /** A label/value line in the same style as the layout's rows: label left, value right, 10dp between lines. */
    private View buildAmenityRow(String label, String amount) {
        float density = getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        row.setPadding(0, 0, 0, (int) (10 * density));

        android.graphics.Typeface medium = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL);
        TextView tvLabel = new TextView(this);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        tvLabel.setText(label);
        tvLabel.setTextSize(13f);
        tvLabel.setTypeface(medium);
        tvLabel.setTextColor(getColor(R.color.velocity_text_secondary));

        TextView tvAmount = new TextView(this);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        valueParams.setMarginStart((int) (12 * density));
        tvAmount.setLayoutParams(valueParams);
        tvAmount.setGravity(android.view.Gravity.END);
        tvAmount.setText(amount);
        tvAmount.setTextSize(13f);
        tvAmount.setTypeface(medium);
        tvAmount.setTextColor(getColor(R.color.velocity_text_primary));

        row.addView(tvLabel);
        row.addView(tvAmount);
        return row;
    }

    /** Reservations don't carry the primary guest's name back from the API - fall back to the signed-in account's name, same as the booking form itself does. */
    @androidx.annotation.Nullable
    private String guestAccountName() {
        SharedPreferences prefs = getSharedPreferences("VelocityPrefs", MODE_PRIVATE);
        String first = prefs.getString("userFirstName", "").trim();
        String middle = prefs.getString("userMiddleName", "").trim();
        String last = prefs.getString("userLastName", "").trim();
        StringBuilder name = new StringBuilder();
        if (!first.isEmpty()) name.append(first);
        if (!middle.isEmpty()) name.append(" ").append(middle);
        if (!last.isEmpty()) name.append(" ").append(last);
        return name.length() > 0 ? name.toString().trim() : null;
    }

    private long nightsBetween(String checkIn, String checkOut) {
        return StayDateCalculator.nightsBetween(checkIn, checkOut);
    }

    private void confirmAndProceedToPayment() {
        if (booking == null) return;
        // Guards against a fast double-tap launching PaymentActivity twice
        // before this Activity's finish() actually takes effect - startActivity()
        // is synchronous but finish() only marks the Activity for destruction,
        // it doesn't block further click events on the same frame.
        btnConfirmBilling.setEnabled(false);
        Intent intent = new Intent(this, PaymentActivity.class);
        intent.putExtra("BOOKING_ID", booking.getId());
        intent.putExtra(PaymentActivity.EXTRA_FROM_RESERVATION_PAYMENT, true);
        startActivity(intent);
        finish();
    }
}
