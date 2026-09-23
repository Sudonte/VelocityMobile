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
public class BillingSummaryActivity extends AppCompatActivity {

    public static final String EXTRA_RESERVATION_ID = "RESERVATION_ID";

    private RoomRepository repository;
    private String reservationId;
    private Booking booking;

    private TextView tvBillingRef, tvBillingStatus, tvBillingRoomHeadline, tvBillingRoomType, tvBillingDates,
            tvBillingNights, tvBillingRate, tvBillingRoomRate, tvBillingDiscount, tvBillingPrimaryGuest, tvBillingRepresentativeName, tvBillingGuestCount, tvBillingIdType,
            tvBillingTotalAmenities, tvBillingBalanceDue, tvBillingSelectedPayment,
            tvBillingPaymentMethod, tvBillingPaymentType, tvBillingPaymentStatus,
            tvAmenitiesSectionTotal, tvNoAmenitiesMessage,
            tvBillingBreakdownRoomRate, tvBillingBreakdownNights,
            tvBillingRoomQuantity, tvBillingSingleRoomRate, tvBillingRoomSubtotalPerNight, tvBillingStayRoomAmount;
    private View layoutBillingSelectedPayment;
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
    private View layoutBillingRoomRatePerNightRow, layoutBillingBreakdownRoomRateRow;
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
                loadReservation();
            }

            @Override
            public void onError(String message) {
                loadReservation();
            }
        });

        if (reservationId != null) {
            repository.fetchRequestableAmenities(reservationId, new RoomRepository.RepositoryCallback<List<RequestableAmenityDto>>() {
                @Override
                public void onSuccess(List<RequestableAmenityDto> items) {
                    populateAmenities(items);
                }

                @Override
                public void onError(String message) {
                    // No amenities line rather than a stale/blank one - same
                    // "hide rather than fabricate" convention as the discount line.
                    populateAmenities(null);
                }
            });
        }
    }

    private void initViews() {
        findViewById(R.id.btnBillingBack).setOnClickListener(v -> finish());

        tvBillingRef = findViewById(R.id.tvBillingRef);
        tvBillingStatus = findViewById(R.id.tvBillingStatus);
        tvBillingRoomHeadline = findViewById(R.id.tvBillingRoomHeadline);
        tvBillingRoomType = findViewById(R.id.tvBillingRoomType);
        tvBillingDates = findViewById(R.id.tvBillingDates);
        tvBillingNights = findViewById(R.id.tvBillingNights);
        tvBillingRate = findViewById(R.id.tvBillingRate);
        tvBillingRoomRate = findViewById(R.id.tvBillingRoomRate);
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
        layoutBillingSelectedPayment = findViewById(R.id.layoutBillingSelectedPayment);
        tvBillingSelectedPayment = findViewById(R.id.tvBillingSelectedPayment);
        tvAmenitiesSectionTotal = findViewById(R.id.tvAmenitiesSectionTotal);
        tvNoAmenitiesMessage = findViewById(R.id.tvNoAmenitiesMessage);
        tvBillingBreakdownRoomRate = findViewById(R.id.tvBillingBreakdownRoomRate);
        tvBillingBreakdownNights = findViewById(R.id.tvBillingBreakdownNights);
        tvBillingRoomQuantity = findViewById(R.id.tvBillingRoomQuantity);
        tvBillingSingleRoomRate = findViewById(R.id.tvBillingSingleRoomRate);
        tvBillingRoomSubtotalPerNight = findViewById(R.id.tvBillingRoomSubtotalPerNight);
        tvBillingStayRoomAmount = findViewById(R.id.tvBillingStayRoomAmount);
        layoutBillingSingleRoomInfo = findViewById(R.id.layoutBillingSingleRoomInfo);
        layoutBillingRoomsRows = findViewById(R.id.layoutBillingRoomsRows);
        layoutBillingRoomRatePerNightRow = findViewById(R.id.layoutBillingRoomRatePerNightRow);
        layoutBillingBreakdownRoomRateRow = findViewById(R.id.layoutBillingBreakdownRoomRateRow);

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
        cbAcceptBillingTerms.setOnCheckedChangeListener((buttonView, isChecked) -> btnConfirmBilling.setEnabled(isChecked));
        btnConfirmBilling.setOnClickListener(v -> confirmAndProceedToPayment());
    }

    /** Read-only Terms & Cancellation Policy viewer - same dialog/pattern BookingAndReservationActivity uses. */
    private void showTermsDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_terms_agreement, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();
        View closeButton = dialogView.findViewById(R.id.termsCloseButton);
        closeButton.setOnClickListener(v -> dialog.dismiss());
        // Any way the guest leaves the dialog (close button, back press, or
        // tapping outside) counts as having viewed it.
        dialog.setOnDismissListener(d -> cbAcceptBillingTerms.setEnabled(true));
        dialog.show();
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

        populateSummary();
    }

    private void populateSummary() {
        tvBillingRef.setText(getString(R.string.booking_ref_label, booking.getId()));
        tvBillingStatus.setText(booking.getStatus().toUpperCase(Locale.US));
        tvBillingRoomHeadline.setText(roomHeadline());
        tvBillingRoomType.setText(android.text.TextUtils.join(", ", booking.getAllRoomTypeNames()));
        tvBillingDates.setText(getString(R.string.date_range_format, booking.getCheckInDate(), booking.getCheckOutDate()));
        nights = nightsBetween(booking.getCheckInDate(), booking.getCheckOutDate());
        tvBillingNights.setText(getString(R.string.nights_count_format, (int) nights));
        if (layoutBillingDiscount != null && tvBillingDiscount != null) {
            // Hidden rather than shown as ₱0.00 - only a genuine discount
            // (active promotion or Senior/PWD statutory 20%, see backend
            // Reservation::discount_preview) is worth surfacing here.
            boolean hasDiscount = booking.getDiscountAmount() > 0.009;
            layoutBillingDiscount.setVisibility(hasDiscount ? View.VISIBLE : View.GONE);
            if (hasDiscount) {
                tvBillingDiscount.setText(String.format(Locale.US, "-₱%,.2f", booking.getDiscountAmount()));
            }
        }

        tvBillingPrimaryGuest.setText(guestAccountName());
        if (tvBillingRepresentativeName != null) {
            // Transaction-specific: the name captured on THIS booking at creation time
            // (booking.guestFirstName/MiddleName/LastName), never the authenticated
            // account's own name above - a guest can book a stay for someone else.
            String representativeName = booking.getRepresentativeName();
            tvBillingRepresentativeName.setText(representativeName == null || representativeName.trim().isEmpty()
                    ? getString(R.string.not_provided_label) : representativeName);
        }
        tvBillingGuestCount.setText(getString(R.string.guests_count_format, booking.getGuests()));

        String idType = booking.getIdCardType();
        tvBillingIdType.setText(idType == null || idType.equalsIgnoreCase("None")
                ? getString(R.string.no_id_uploaded) : idType);

        List<Booking.AdditionalGuest> additionalGuests = booking.getAdditionalGuests();
        boolean hasAdditionalGuests = additionalGuests != null && !additionalGuests.isEmpty();
        layoutBillingAdditionalGuestsSection.setVisibility(hasAdditionalGuests ? View.VISIBLE : View.GONE);
        layoutBillingAdditionalGuests.removeAllViews();
        if (hasAdditionalGuests) {
            for (Booking.AdditionalGuest guest : additionalGuests) {
                TextView row = new TextView(this);
                row.setText(getString(R.string.details_guest_entry, guest.name, guest.relationship));
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

        double alreadyPaid = booking.getAmountPaid();

        // GCash only - the percentage/amount chosen once at reservation
        // creation (see PaymentActivity#selectedGcashPercentageForRequest()),
        // shown here so the guest can review it before Confirm Booking hands
        // off to PaymentActivity, which reuses this exact stored figure.
        Double selectedPercentage = booking.getSelectedPaymentPercentage();
        Double requiredAmount = booking.getRequiredPaymentAmount();
        boolean hasSelectedPayment = selectedPercentage != null && requiredAmount != null;
        if (layoutBillingSelectedPayment != null) {
            layoutBillingSelectedPayment.setVisibility(hasSelectedPayment ? View.VISIBLE : View.GONE);
        }
        if (hasSelectedPayment && tvBillingSelectedPayment != null) {
            String percentText = selectedPercentage == Math.floor(selectedPercentage)
                    ? String.valueOf(selectedPercentage.intValue())
                    : String.valueOf(selectedPercentage);
            tvBillingSelectedPayment.setText(getString(R.string.selected_payment_value_format, percentText,
                    String.format(Locale.US, "%,.2f", requiredAmount)));
        }

        // Payment Information card: hidden entirely (rather than shown with fabricated/
        // placeholder values) until this specific reservation actually carries a payment
        // method/attempt - a fresh, never-paid reservation has none of these yet.
        boolean hasPaymentMethod = booking.getPaymentMethod() != null && !booking.getPaymentMethod().trim().isEmpty();
        boolean hasPaymentType = alreadyPaid > 0.009;
        boolean hasPaymentStatus = booking.isHasBooking();
        if (layoutBillingPaymentMethod != null) {
            layoutBillingPaymentMethod.setVisibility(hasPaymentMethod ? View.VISIBLE : View.GONE);
            if (hasPaymentMethod && tvBillingPaymentMethod != null) {
                tvBillingPaymentMethod.setText("cash".equalsIgnoreCase(booking.getPaymentMethod())
                        ? getString(R.string.payment_method_cash) : getString(R.string.payment_method_gcash));
            }
        }
        if (layoutBillingPaymentType != null) {
            layoutBillingPaymentType.setVisibility(hasPaymentType ? View.VISIBLE : View.GONE);
            if (hasPaymentType && tvBillingPaymentType != null) {
                tvBillingPaymentType.setText(PaymentStateUtil.isFullyPaid(booking)
                        ? R.string.review_payment_type_full : R.string.review_payment_type_partial);
            }
        }
        if (layoutBillingPaymentStatus != null) {
            layoutBillingPaymentStatus.setVisibility(hasPaymentStatus ? View.VISIBLE : View.GONE);
            if (hasPaymentStatus && tvBillingPaymentStatus != null) {
                tvBillingPaymentStatus.setText(BookingStatusPresenter.paymentStatusPillText(this, booking));
            }
        }
        if (cardBillingPaymentInfo != null) {
            cardBillingPaymentInfo.setVisibility(hasPaymentMethod || hasPaymentType || hasPaymentStatus ? View.VISIBLE : View.GONE);
        }
    }

    /** First selected room type's name, joined with the others when more than one was
     *  selected in this single reservation transaction (e.g. "Bryan Dela Cruz + Deluxe") -
     *  never a fake averaged/combined room name. */
    private String roomHeadline() {
        List<BookingRoom> roomLines = booking.getRooms();
        if (roomLines == null || roomLines.isEmpty()) {
            return booking.getRoomName();
        }
        StringBuilder sb = new StringBuilder();
        for (BookingRoom line : roomLines) {
            if (sb.length() > 0) sb.append(" + ");
            sb.append(line.getRoomTypeName());
        }
        return sb.toString();
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
        double grandTotal = roomTotal + totalAmenitiesAmount;

        if (layoutBillingSingleRoomInfo != null) {
            layoutBillingSingleRoomInfo.setVisibility(hasItemizedRooms ? View.GONE : View.VISIBLE);
        }
        if (layoutBillingRoomRatePerNightRow != null) {
            layoutBillingRoomRatePerNightRow.setVisibility(hasItemizedRooms ? View.GONE : View.VISIBLE);
        }
        if (layoutBillingBreakdownRoomRateRow != null) {
            layoutBillingBreakdownRoomRateRow.setVisibility(hasItemizedRooms ? View.GONE : View.VISIBLE);
        }

        if (hasItemizedRooms) {
            if (layoutBillingRoomsRows != null) {
                layoutBillingRoomsRows.setVisibility(View.VISIBLE);
                layoutBillingRoomsRows.removeAllViews();
                for (BookingRoom line : roomLines) {
                    layoutBillingRoomsRows.addView(buildRoomLineRow(line));
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
            double roomSubtotalPerNight = perRoomRate * roomQuantity;

            if (tvBillingRoomQuantity != null) tvBillingRoomQuantity.setText(getString(R.string.rooms_quantity_format, roomQuantity));
            if (tvBillingSingleRoomRate != null) tvBillingSingleRoomRate.setText(getString(R.string.price_per_night_format, perRoomRate));
            if (tvBillingRoomSubtotalPerNight != null) tvBillingRoomSubtotalPerNight.setText(String.format(Locale.US, "₱%,.2f", roomSubtotalPerNight));
            if (tvBillingRoomRate != null) tvBillingRoomRate.setText(String.format(Locale.US, "₱%,.2f", totalRoomRatePerNight));
            if (tvBillingBreakdownRoomRate != null) tvBillingBreakdownRoomRate.setText(String.format(Locale.US, "₱%,.2f", totalRoomRatePerNight));
        }

        if (tvBillingBreakdownNights != null) tvBillingBreakdownNights.setText(String.valueOf(nights));
        if (tvBillingStayRoomAmount != null) tvBillingStayRoomAmount.setText(String.format(Locale.US, "₱%,.2f", roomTotal));
        if (tvBillingRate != null) tvBillingRate.setText(String.format(Locale.US, "₱%,.2f", roomTotal));

        if (tvBillingTotalAmenities != null) tvBillingTotalAmenities.setText(String.format(Locale.US, "₱%,.2f", totalAmenitiesAmount));
        if (tvBillingBalanceDue != null) tvBillingBalanceDue.setText(String.format(Locale.US, "₱%,.2f", grandTotal));

        if (tvBillingRoomHeadline != null) tvBillingRoomHeadline.setText(roomHeadline());
    }

    /** One itemized room-type row: name header + price/night, quantity, nights, subtotal -
     *  mirrors buildAmenityRow()'s label/value line style. */
    private View buildRoomLineRow(BookingRoom room) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        int vPad = (int) (getResources().getDisplayMetrics().density * 6);
        container.setPadding(0, vPad, 0, vPad);

        TextView tvName = new TextView(this);
        tvName.setText(room.getRoomTypeName());
        tvName.setTextSize(14f);
        tvName.setTypeface(tvName.getTypeface(), android.graphics.Typeface.BOLD);
        tvName.setTextColor(getResources().getColor(R.color.velocity_text_primary, getTheme()));
        container.addView(tvName);

        // Nights is deliberately NOT repeated per room - every room in this reservation
        // shares the same single stay period, already shown once at reservation level
        // (Stay Dates/Nights rows above Room Total). Still used internally for
        // room.getSubtotal() (rate x quantity x nights) - only the redundant display row
        // is removed.
        container.addView(buildAmenityRow(getString(R.string.price_per_room_night_label),
                String.format(Locale.US, "₱%,.2f", room.getPricePerNight())));
        container.addView(buildAmenityRow(getString(R.string.selected_room_quantity_label),
                String.valueOf(room.getQuantity())));
        container.addView(buildAmenityRow(getString(R.string.room_line_subtotal_label),
                String.format(Locale.US, "₱%,.2f", room.getSubtotal())));

        return container;
    }

    /**
     * Paid/additional amenities originally selected at creation time (see
     * RequestableAmenityDto's own doc comment - this endpoint never returns complimentary/free
     * amenities), reusing the same requestable-amenities endpoint RequestAmenityActivity already
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
                        String.format(Locale.US, "₱%,.2f", subtotal)));
            }
        }
        totalAmenitiesAmount = total;

        if (tvNoAmenitiesMessage != null) {
            tvNoAmenitiesMessage.setVisibility(layoutBillingAmenitiesRows.getChildCount() == 0 ? View.VISIBLE : View.GONE);
        }
        if (tvAmenitiesSectionTotal != null) {
            tvAmenitiesSectionTotal.setText(String.format(Locale.US, "₱%,.2f", totalAmenitiesAmount));
        }
        updateBillingBreakdownAmounts();
    }

    private View buildAmenityRow(String label, String amount) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        row.setPadding(0, (int) (getResources().getDisplayMetrics().density * 3), 0, (int) (getResources().getDisplayMetrics().density * 3));

        TextView tvLabel = new TextView(this);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tvLabel.setText(label);
        tvLabel.setTextSize(14f);
        tvLabel.setTextColor(getResources().getColor(R.color.velocity_text_secondary, getTheme()));

        TextView tvAmount = new TextView(this);
        tvAmount.setText(amount);
        tvAmount.setTextSize(14f);
        tvAmount.setTextColor(getResources().getColor(R.color.velocity_text_primary, getTheme()));

        row.addView(tvLabel);
        row.addView(tvAmount);
        return row;
    }

    /** Reservations don't carry the primary guest's name back from the API - fall back to the signed-in account's name, same as the booking form itself does. */
    private String guestAccountName() {
        SharedPreferences prefs = getSharedPreferences("VelocityPrefs", MODE_PRIVATE);
        String first = prefs.getString("userFirstName", "").trim();
        String middle = prefs.getString("userMiddleName", "").trim();
        String last = prefs.getString("userLastName", "").trim();
        StringBuilder name = new StringBuilder();
        if (!first.isEmpty()) name.append(first);
        if (!middle.isEmpty()) name.append(" ").append(middle);
        if (!last.isEmpty()) name.append(" ").append(last);
        return name.length() > 0 ? name.toString().trim() : getString(R.string.not_specified);
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
