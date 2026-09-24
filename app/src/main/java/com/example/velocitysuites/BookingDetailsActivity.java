package com.example.velocitysuites;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Full-detail screen for a single Booking/Reservation, replacing
 * BookingAndReservationActivity.showBookingDetails()'s dialog. Reached by
 * tapping any card in the compact list - the whole Booking is already in
 * memory (RoomRepository's cache), so it's passed as a plain Serializable
 * Intent extra rather than re-fetched.
 */
public class BookingDetailsActivity extends AppCompatActivity {

    public static final String EXTRA_BOOKING = "EXTRA_BOOKING";

    private Booking booking;
    /**
     * Every sibling Booking/Reservation created together with this one as a
     * multi-room-type transaction (see BookingGroupState's own doc), this
     * record included - null if this transaction isn't grouped. Resolved
     * once in renderAllSections() and reused by buildRoomInfoSection() (the
     * itemized room breakdown) and buildPaymentSummarySection() (the summed
     * Room/Grand Total across the whole group, not just this one member's
     * own room-only amount).
     */
    @Nullable
    private List<Booking> groupMembers;
    /** Sum of every selected room's quantity (never the number of distinct room TYPES) -
     *  computed once in buildRoomInfoSection() and shown only there ("No. of Rooms",
     *  directly above Room Total) - never repeated in Payment Summary. */
    private int roomsCount = 0;
    /** Total cost of every selected room across every room type in this single transaction -
     *  computed once in buildRoomInfoSection() (its own Room Total row) and reused as-is by
     *  buildPaymentSummarySection()'s Room Total row, so the two can never disagree. */
    private double roomTotalAmount = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_details);

        booking = (Booking) getIntent().getSerializableExtra(EXTRA_BOOKING);
        if (booking == null) {
            finish();
            return;
        }

        ImageButton btnBack = findViewById(R.id.btnBookingDetailsBack);
        btnBack.setOnClickListener(v -> finish());

        correctTotalAmountThenRender();
    }

    /**
     * booking.getTotalAmount() undercounts paid add-on amenities for a reservation that hasn't
     * converted into a Booking yet - see ApiMapper#toBooking(ReservationDto): with no Billing
     * row yet, it falls back to `nightlyRate * nights * roomsRequested` (room cost only), since
     * the reservation API response carries no amenities/total field of its own. Corrects
     * getTotalAmount() in place - before anything on this screen reads it or the
     * getRemainingBalance() derived from it - using the same requestable-amenities endpoint
     * BillingSummaryActivity already relies on for the identical correction. A no-op (renders
     * immediately) once the reservation has converted, since the real Billing total already
     * includes amenities by then.
     */
    private void correctTotalAmountThenRender() {
        // Single source of truth for this correction - see
        // RoomRepository#correctPendingReservationTotal() (a no-op that calls
        // back immediately once booking.isHasBooking(), so no separate check
        // is needed here).
        RoomRepository.getInstance(this).correctPendingReservationTotal(booking,
                new RoomRepository.RepositoryCallback<Booking>() {
                    @Override
                    public void onSuccess(Booking corrected) {
                        renderAllSections();
                    }

                    @Override
                    public void onError(String message) {
                        // correctPendingReservationTotal() itself never calls onError - present
                        // for RepositoryCallback's contract only.
                        renderAllSections();
                    }
                });
    }

    private void renderAllSections() {
        // Resolved before bindHeader() now (previously ran after it) so the
        // header's own itemized room-selection line can use the same
        // groupMembers fallback buildRoomInfoSection() below already relies on.
        resolveGroupMembers();
        bindHeader();

        // Card/section-based layout - every section renders and stays visible
        // together (see activity_booking_details.xml), replacing the old
        // 4-tab toggle switcher that hid all but one section at a time.
        buildBookingInfoSection(findViewById(R.id.sectionBookingInfoContent));
        buildGuestInfoSection(findViewById(R.id.sectionGuestInfoContent));
        buildRoomInfoSection(findViewById(R.id.sectionRoomInfoContent));
        buildPaymentInfoSection(findViewById(R.id.sectionPaymentInfoContent));
        buildPaymentSummarySection(findViewById(R.id.sectionPaymentSummaryContent));
        buildTimelineSection(findViewById(R.id.sectionTimelineContent));
    }

    private void bindHeader() {
        // A Reservation (isHasBooking()==false, including a frozen historical
        // record of one that's since converted - see Booking's own doc on
        // historicalReservation) must never be labelled "Booking Details" -
        // this screen is shared by both transaction types, so the title and
        // reference prefix both need to reflect which one is actually open.
        TextView tvScreenTitle = findViewById(R.id.tvDetailsScreenTitle);
        if (tvScreenTitle != null) {
            tvScreenTitle.setText(booking.isHasBooking()
                    ? R.string.booking_details_screen_title
                    : R.string.reservation_details_screen_title);
        }

        // No header image bound here by design - see activity_booking_details.xml's
        // header card doc; a room photo only ever appears per-room-type inside the
        // Room Information section (buildRoomInfoSection()/addRoomDetailBlock()).

        // Primary highlighted identifier - the Booking/Reservation ID itself,
        // never the room name (previously shown here, with the ID repeated a
        // second time right below in tvDetailsRef, now removed entirely - an
        // ID must never be shown twice in one header).
        TextView tvHeadline = findViewById(R.id.tvDetailsRoomHeadline);
        tvHeadline.setText(booking.isHasBooking()
                ? getString(R.string.direct_booking_ref_format, booking.getId())
                : getString(R.string.reservation_ref_format, booking.getId()));

        TextView tvStatus = findViewById(R.id.tvDetailsStatus);
        tvStatus.setText(BookingStatusPresenter.computeStatusLabel(this, booking));
        BookingStatusPresenter.styleStatusBadge(this, tvStatus, booking);

        TextView tvDates = findViewById(R.id.tvDetailsDates);
        tvDates.setText(getString(R.string.date_range_format, booking.getCheckInDate(), booking.getCheckOutDate()));

        // Every selected room type and its quantity, directly below the stay
        // dates and directly above the status pill - same three-tier source
        // buildRoomInfoSection() below already uses (itemized backend line
        // items, then legacy client-side-grouped siblings, then a single
        // legacy room x quantity), via the same shared formatter the
        // Booking/Reservation list cards use, so this can never disagree
        // with either of those.
        TextView tvRoomGuestCounts = findViewById(R.id.tvDetailsRoomGuestCounts);
        tvRoomGuestCounts.setText(buildHeaderRoomSelectionSummary());
    }

    private String buildHeaderRoomSelectionSummary() {
        return Booking.buildRoomSelectionSummaryText(booking, groupMembers);
    }

    // ---- Booking Information section ----

    private void buildBookingInfoSection(LinearLayout container) {
        addInfoRow(container, getString(booking.isHasBooking() ? R.string.receipt_label_booking_id : R.string.receipt_label_reservation_id),
                booking.getId());
        // Status sits directly below the identifier, ahead of the date rows.
        addInfoRow(container, getString(R.string.details_label_status), BookingStatusPresenter.computeStatusLabel(this, booking));
        addInfoRow(container, getString(R.string.timeline_created), booking.getCreatedAtDisplay());
        // Confirmed/converted date (Booking#getBookingDate(), the backend's
        // confirmed_at) - distinct from Created Date above, which is the
        // original reservation-creation instant. Empty/hidden for a still-
        // pending Reservation that hasn't converted yet (see ApiMapper).
        addInfoRow(container, getString(R.string.details_label_booking_date), booking.getBookingDate());
        addInfoRow(container, getString(R.string.details_label_check_in), booking.getCheckInDate());
        addInfoRow(container, getString(R.string.details_label_check_out), booking.getCheckOutDate());
        addInfoRow(container, getString(R.string.receipt_number_of_nights_label), computeNights(booking.getCheckInDate(), booking.getCheckOutDate()));
        // Cross-reference for a historical (converted) reservation record -
        // see Booking.convertedBookingId's own doc for why this is the one
        // new identifier this feature surfaces, rather than restating the
        // shared reservation/booking ref number everywhere else.
        if (booking.getConvertedBookingId() != null) {
            addInfoRow(container, getString(R.string.details_label_converted_booking),
                    getString(R.string.direct_booking_ref_format, booking.getConvertedBookingId()));
        }

        // Multi-room "group booking" siblings (BookingGroupState) - the guest
        // booked several room types together in one wizard run; each landed
        // as its own separate Booking/Reservation row, linked only by this
        // local group tag (see resolveGroupMembers()). This is just a light
        // cross-reference to each sibling's own id/status here - the actual
        // room-type/quantity/price breakdown across the whole group is in
        // Room Information, and the summed Room/Grand Total is in Payment
        // Summary, so neither is duplicated in this section.
        String groupRef = BookingGroupState.getGroupRef(this, booking.getId());
        if (groupRef != null && groupMembers != null) {
            addSectionDivider(container, getString(R.string.details_group_reference_label, groupRef));
            for (Booking sibling : groupMembers) {
                if (sibling.getId().equals(booking.getId())) continue;
                addInfoRow(container, sibling.getRoomName(),
                        getString(sibling.isHasBooking() ? R.string.direct_booking_ref_format : R.string.reservation_ref_format, sibling.getId()));
            }
        }
    }

    /** Populates {@link #groupMembers} (this record included) if it belongs to a BookingGroupState group, else leaves it null. */
    private void resolveGroupMembers() {
        groupMembers = BookingGroupState.resolveGroupMembers(this, RoomRepository.getInstance(this), booking.getId());
    }

    // ---- Guest Information section ----

    /**
     * Guest Account Name (the authenticated account holder, same SharedPreferences
     * source PaymentReceiptActivity already reads for its own identically-labeled
     * row) and Representative Name (Booking#getRepresentativeName(), the stay guest
     * entered for THIS specific transaction - may be a different person than the
     * account holder, and must never be confused with it). Email Address and Mobile
     * Number are intentionally not shown here - this section only ever needs the two
     * name fields. Uses a label-above-value layout (not the label-left/value-right
     * row the other sections use) so a long representative name always has the full
     * card width to wrap into, never squeezed into a narrow trailing column.
     */
    private void buildGuestInfoSection(LinearLayout container) {
        android.content.SharedPreferences prefs = getSharedPreferences("VelocityPrefs", MODE_PRIVATE);
        String guestAccountName = prefs.getString("userName", null);
        String representativeName = booking.getRepresentativeName();
        addInfoRowVertical(container, getString(R.string.receipt_guest_account_name_label), guestAccountName);
        addInfoRowVertical(container, getString(R.string.details_label_representative_name), representativeName);

        // Guest counts live here (not Room Information) - they describe WHO is
        // staying, not what was booked. Moved from buildRoomInfoSection().
        // Deliberately NOT summed across groupMembers: every sibling in a
        // BookingGroupState group was created from the SAME
        // pendingWizardState.adults/children (see
        // PaymentActivity#submitPendingBookingGroups()/submitPendingReservationGroups(),
        // which pass the one whole-party adults/children value to every
        // per-room-type create call) - each sibling's own getAdults()/
        // getChildren()/getGuests() already holds the full party count, not
        // a per-room-type split, so summing across N siblings would inflate
        // the true count by a factor of N. This one anchor record's own
        // value is already correct, exactly like getGuests() below already
        // relied on unconditionally.
        addInfoRow(container, getString(R.string.details_label_adults), booking.getAdults() > 0 ? String.valueOf(booking.getAdults()) : null);
        addInfoRow(container, getString(R.string.details_label_children), booking.getChildren() > 0 ? String.valueOf(booking.getChildren()) : null);
        addInfoRow(container, getString(R.string.details_label_total_guests), String.valueOf(booking.getGuests()));
    }

    // ---- Room Information section ----

    private void buildRoomInfoSection(LinearLayout container) {
        // Itemized multi-room-type breakdown (Booking#getRooms()) only ever
        // populates once the backend ships the contract in
        // MULTI_ROOM_TRANSACTION_BACKEND_SPEC.md - empty on every
        // transaction today, so this falls back first to the legacy grouped-
        // siblings reconstruction, then to a single-room block - see each
        // branch below. All three render the identical Room Name/Quantity/
        // Price Per Room/Night/Subtotal breakdown (Number of Nights is a
        // whole-stay fact, shown once at reservation level, never repeated
        // per room type), then No. of Rooms and Room Total at the bottom of
        // this section. Both roomsCount and roomTotalAmount are cached as
        // instance fields so buildPaymentSummarySection()'s own Room Total
        // row (part of the running Amenities Total -> Room Total -> Grand
        // Total breakdown there) reuses this exact same computed value
        // instead of recomputing it independently - the two can never
        // disagree.
        int stayNights = nightsOrOne(booking.getCheckInDate(), booking.getCheckOutDate());
        List<BookingRoom> rooms = booking.getRooms();
        roomsCount = 0;
        roomTotalAmount = 0;
        if (!rooms.isEmpty()) {
            // Itemized backend line items - one entry per distinct room TYPE
            // already (see BookingRoomDto's own doc), so no de-duplication is
            // needed here. The image is resolved from the already-cached room
            // catalog by type id (see RoomRepository#findRoomTypeImageUrl()'s
            // own doc), and assigned room numbers now come directly from each
            // line's own BookingRoom#getAssignedRoomNumbers() (confirmed live
            // 2026-09-18 - see BookingRoomDto#assigned_room_numbers's own doc)
            // rather than guessing from the single legacy roomNumber field.
            for (BookingRoom room : rooms) {
                double subtotal = room.getPricePerNight() * room.getQuantity() * stayNights;
                String imageUrl = RoomRepository.getInstance(this).findRoomTypeImageUrl(room.getRoomTypeId());
                String assignedRoomsText = Booking.resolveAssignedRoomsText(this, room.getAssignedRoomNumbers());
                addRoomDetailBlock(container, room.getRoomTypeName(), room.getQuantity(), room.getPricePerNight(), subtotal, imageUrl, assignedRoomsText);
                roomsCount += Math.max(1, room.getQuantity());
                roomTotalAmount += subtotal;
            }
        } else if (groupMembers != null) {
            // Today's actual multi-room-type case: each sibling record IS one
            // distinct room type (see resolveGroupMembers()) - one block per
            // sibling, its own quantity and own subtotal, instead of just this
            // one member's single Room Type row. Each sibling is its own full
            // Booking, so its own getRoomImageUrl()/getRoomNumber() are already
            // correctly scoped to that specific room type - no catalog lookup
            // needed, unlike the itemized branch above.
            for (Booking member : groupMembers) {
                int quantity = Math.max(1, member.getRoomsRequested());
                // Room-only subtotal: a converted member's real Billing split
                // (getRoomCharge()) when one exists, else its own total minus
                // any itemized amenity charge already counted separately below
                // - never double-counts amenities.
                double subtotal = member.getRoomCharge() > 0.009
                        ? member.getRoomCharge() : Math.max(0, member.getTotalAmount() - member.getAmenityCharge());
                double pricePerNight = stayNights > 0 ? subtotal / (quantity * stayNights) : subtotal;
                addRoomDetailBlock(container, member.getRoomType(), quantity, pricePerNight, subtotal,
                        member.getRoomImageUrl(), resolveAssignedRoomsText(member.getRoomNumber()));
                roomsCount += quantity;
                roomTotalAmount += subtotal;
            }
        } else {
            int quantity = Math.max(1, booking.getRoomsRequested());
            double subtotal = booking.getRoomCharge() > 0.009
                    ? booking.getRoomCharge() : Math.max(0, booking.getTotalAmount() - booking.getAmenityCharge());
            double pricePerNight = stayNights > 0 ? subtotal / (quantity * stayNights) : subtotal;
            addRoomDetailBlock(container, booking.getRoomType(), quantity, pricePerNight, subtotal,
                    booking.getRoomImageUrl(), resolveAssignedRoomsText(booking.getRoomNumber()));
            roomsCount = quantity;
            roomTotalAmount = subtotal;
        }
        addInfoRow(container, getString(R.string.details_label_room_number), booking.getRoomNumber());

        List<Booking.AdditionalGuest> additionalGuests = booking.getAdditionalGuests();
        if (additionalGuests != null && !additionalGuests.isEmpty()) {
            addSectionDivider(container, getString(R.string.details_label_additional_guests));
            for (Booking.AdditionalGuest g : additionalGuests) {
                addInfoRow(container, g.name, g.relationship != null ? g.relationship : getString(R.string.label_not_available));
            }
        }

        // Total Room Types, then No. of Rooms, directly above Room Total, at
        // the very bottom of this section - the sole location for all three
        // (see buildPaymentSummarySection(), which reuses roomTotalAmount
        // rather than showing its own "No. of Rooms").
        int roomTypeCount = !rooms.isEmpty() ? rooms.size() : (groupMembers != null ? groupMembers.size() : 1);
        addInfoRow(container, getString(R.string.details_label_room_types), String.valueOf(roomTypeCount));
        addInfoRow(container, getString(R.string.details_label_rooms_requested), String.valueOf(Math.max(0, roomsCount)));
        addTotalRow(container, getString(R.string.room_total_label), formatPrice(roomTotalAmount));
    }

    // ---- Payment Information section ----

    private void buildPaymentInfoSection(LinearLayout container) {
        boolean showPaymentMethod = booking.getPaymentMethod() != null && !booking.getPaymentMethod().trim().isEmpty();
        if (showPaymentMethod) {
            addInfoRow(container, getString(R.string.details_label_payment_method),
                    "cash".equalsIgnoreCase(booking.getPaymentMethod()) ? getString(R.string.payment_method_cash) : getString(R.string.payment_method_gcash));
        }
        boolean isGcash = "gcash".equalsIgnoreCase(booking.getPaymentMethod());
        if (isGcash) {
            // GCash Mobile #/GCash Reference # - the exact values the guest entered in
            // payment.xml Steps 2-3 for this booking's latest payment. Formatted via the
            // same shared GcashReferenceFormatter the receipt uses, so the two screens
            // never disagree on how a value is displayed. Replaces the old generic
            // "Transaction Ref." label, which is kept below for Cash only.
            addInfoRow(container, getString(R.string.receipt_gcash_mobile_label),
                    GcashReferenceFormatter.formatMobileNumber(booking.getGcashNumber()));
            addInfoRow(container, getString(R.string.receipt_gcash_reference_number_label),
                    GcashReferenceFormatter.formatOrFallback(booking.getTransactionRef(),
                            getString(R.string.receipt_gcash_value_missing),
                            getString(R.string.receipt_gcash_reference_legacy_incomplete)));
        } else {
            addInfoRow(container, getString(R.string.details_label_transaction_ref), booking.getTransactionRef());
        }
        // Percentage is the deposit/full-payment percentage chosen once at
        // reservation-creation time (Booking#getSelectedPaymentPercentage()'s own
        // doc) - shown regardless of fullyPaid, so a Full Payment (100%)
        // transaction still shows "Payment Percentage: 100%" instead of the row
        // being hidden entirely (the old `!fullyPaid` gate here hid it for every
        // Full Payment, since that always leaves remainingBalance at 0 - the exact
        // bug PaymentReceiptActivity#populateReceipt() already fixed; kept
        // consistent with it here). Backend-authoritative payment_summary totals
        // when attached, else the legacy fields - must never disagree with the
        // Payment Status row right below (BookingStatusPresenter.paymentStatusPillText(),
        // same authoritative-first rule) - PAYMENT_RECEIPT_HISTORY_BACKEND_SPEC.md
        // Phase 6 §1.
        boolean fullyPaid = booking.getEffectiveRemainingBalance() <= 0.009;
        if (booking.getEffectiveTotalAmountPaid() > 0.009 || booking.isHasBooking()) {
            addInfoRow(container, getString(R.string.details_label_payment_type),
                    getString(fullyPaid ? R.string.review_payment_type_full : R.string.review_payment_type_partial));
        }
        if (booking.getSelectedPaymentPercentage() != null) {
            addInfoRow(container, getString(R.string.receipt_payment_percentage_label),
                    formatPercentage(booking.getSelectedPaymentPercentage()));
        }
        addInfoRow(container, getString(R.string.payment_status), BookingStatusPresenter.paymentStatusPillText(this, booking));
        addInfoRow(container, getString(R.string.details_label_payment_date), booking.getPaymentDateOnly());
        addInfoRow(container, getString(R.string.details_label_payment_time), booking.getPaymentTimeOnly());
        if (booking.getPaymentVerificationStatus() != null) {
            addInfoRow(container, getString(R.string.details_label_verification_status), booking.getPaymentVerificationStatus());
        }
        boolean showRejectionReason = ("Cancelled".equalsIgnoreCase(booking.getStatus()) || "Rejected".equalsIgnoreCase(booking.getStatus()))
                && !TextUtils.isEmpty(booking.getTransactionRejectionReason());
        if (showRejectionReason) {
            addInfoRow(container, getString(R.string.details_label_rejection_reason), booking.getTransactionRejectionReason());
        }
    }

    // ---- Payment Summary section ----

    private void buildPaymentSummarySection(LinearLayout container) {
        // Room Total itself is NOT recomputed here - buildRoomInfoSection() (which
        // always runs first, see renderAllSections()) already computed the
        // canonical roomTotalAmount, with the correct per-branch fallbacks
        // (itemized room lines / grouped siblings / single legacy room), and
        // this section just reuses it below so the two can never disagree.
        // Grouped multi-room-type transaction (see resolveGroupMembers()): every
        // amount below must be the sum across every sibling, not just this one
        // member's own room-only amount - "All Selected Rooms + All Selected Paid
        // Amenities = Grand Total". amountPaid is always the CUMULATIVE amount
        // across every verified payment (Booking#getAmountPaid(), itself already a
        // sum over booking.getPaymentHistory()/payments - never just the latest
        // one), so remainingBalance below is never wrong after a second or later
        // partial payment. See BookingGroupAggregator's own doc for the
        // deliberately-unchanged status-handling caveat (a Cancelled/Rejected
        // sibling is still summed in, matching this method's pre-existing behavior).
        double amenityCharge;
        double additionalGuestFee;
        double totalAmount;
        double amountPaid;
        double remainingBalance;
        if (groupMembers != null) {
            BookingGroupAggregator.Totals totals = BookingGroupAggregator.sum(groupMembers);
            amenityCharge = totals.amenityCharge;
            additionalGuestFee = totals.additionalGuestFee;
            totalAmount = totals.totalAmount;
            amountPaid = totals.amountPaid;
            remainingBalance = totalAmount - amountPaid;
        } else {
            amenityCharge = booking.getAmenityCharge();
            additionalGuestFee = booking.getAdditionalGuestFee();
            totalAmount = booking.getTotalAmount();
            // Backend-authoritative payment_summary.total_amount_paid/remaining_balance
            // when attached, else the legacy client-side fields - see
            // Booking#getEffectiveTotalAmountPaid()'s own doc
            // (PAYMENT_RECEIPT_HISTORY_BACKEND_SPEC.md Phase 5 §11).
            amountPaid = booking.getEffectiveTotalAmountPaid();
            remainingBalance = booking.getEffectiveRemainingBalance();
        }

        // ---- 1. Selected Amenities: every selected amenity, then Amenities
        // Total at the bottom of THIS section (never outside it). amenitiesTotalValue
        // is cached so section 3 below (Grand Total Amount) can show the exact
        // same figure again rather than recalculating it.
        // Itemized amenity breakdown (Booking#getAmenities()) only ever populates
        // once the backend ships the multi-room contract - falls back to the
        // single amenityCharge dollar total computed either from this one
        // record's own Billing row or summed across the legacy group.
        double amenitiesTotalValue = amenityCharge;
        if (!booking.getAmenities().isEmpty()) {
            addSectionDivider(container, getString(R.string.details_label_amenities_section));
            double itemizedAmenityTotal = 0;
            for (BookingAmenity amenity : booking.getAmenities()) {
                double unitPrice = amenity.getQuantity() > 0 ? amenity.getSubtotal() / amenity.getQuantity() : amenity.getSubtotal();
                addAmenityDetailBlock(container, amenity.getAmenityName(), amenity.getQuantity(), unitPrice, amenity.getSubtotal());
                itemizedAmenityTotal += amenity.getSubtotal();
            }
            addTotalRow(container, getString(R.string.details_amenities_total_label), formatPrice(itemizedAmenityTotal));
            amenitiesTotalValue = itemizedAmenityTotal;
        } else if (amenityCharge > 0.009) {
            addSectionDivider(container, getString(R.string.details_label_amenities_section));
            addTotalRow(container, getString(R.string.details_amenities_total_label), formatPrice(amenityCharge));
        }

        // ---- 2. Payment Balance: purely payment PROGRESS (what's been paid
        // so far vs. what's left) - never mixed with the totals below it.
        // Required Payment is deliberately not shown anywhere in this screen.
        addSectionDivider(container, getString(R.string.payment_balance_section_title));
        if (amountPaid > 0.009) {
            addInfoRow(container, getString(R.string.details_label_amount_paid), formatPrice(amountPaid));
        }
        // A plain Reservation with no payment made yet and no Booking row
        // behind it has no real payment obligation established - showing
        // "Remaining Balance: <full total>" there would be booking-only
        // payment info leaking into a transaction where no payment is
        // actually involved yet. Once either is true (some amount was
        // already paid, or a real Booking exists), the balance is meaningful.
        if (remainingBalance > 0.009 && (amountPaid > 0.009 || booking.isHasBooking())) {
            addInfoRow(container, getString(R.string.details_label_remaining_balance), formatPrice(remainingBalance));
        }

        // ---- 3. Grand Total Amount: Amenities Total (same value as section 1
        // above, never recalculated), Room Total (same value already shown at
        // the bottom of Room Information), then Grand Total LAST, most
        // strongly highlighted.
        addSectionDivider(container, getString(R.string.grand_total_amount_section_title));
        addTotalRow(container, getString(R.string.details_amenities_total_label), formatPrice(amenitiesTotalValue));
        addTotalRow(container, getString(R.string.room_total_label), formatPrice(roomTotalAmount));
        if (additionalGuestFee > 0.009) {
            addInfoRow(container, getString(R.string.details_label_additional_guest_fee), formatPrice(additionalGuestFee));
        }
        if (booking.getDiscountAmount() > 0.009) {
            addInfoRow(container, getString(R.string.details_label_discount), formatPrice(booking.getDiscountAmount()));
        }
        addGrandTotalRow(container, getString(R.string.details_grand_total_label), formatPrice(totalAmount));

        buildReceiptActionCard(container);

        if (booking.getReceiptUrl() != null && !booking.getReceiptUrl().isEmpty()) {
            addSectionDivider(container, getString(R.string.details_label_receipt_image));
            ImageView receiptView = new ImageView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (int) (180 * getResources().getDisplayMetrics().density));
            params.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
            receiptView.setLayoutParams(params);
            receiptView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            receiptView.setAdjustViewBounds(true);
            Glide.with(this).load(booking.getReceiptUrl()).into(receiptView);
            container.addView(receiptView);
        }

        buildPaymentHistorySection(container);
    }

    /**
     * Payment Transaction History - prefers the backend-authoritative
     * booking.getPaymentTransactions() (richer: includes payment_stage,
     * verifier, receipt cross-reference) when the backend has attached one;
     * falls back to the legacy flat paymentHistory only for an older/
     * not-yet-migrated response. Never both at once - showing the same
     * payments twice would double the guest's apparent transaction count.
     */
    private void buildPaymentHistorySection(LinearLayout container) {
        List<Booking.PaymentTransactionRecord> transactions = booking.getPaymentTransactions();
        if (!transactions.isEmpty()) {
            addSectionDivider(container, getString(R.string.details_label_payment_history));
            for (int i = 0; i < transactions.size(); i++) {
                container.addView(ReceiptCardHelper.buildTransactionRow(this, container, transactions.get(i), i == transactions.size() - 1));
            }
            return;
        }

        List<Booking.PaymentRecord> history = booking.getPaymentHistory();
        if (history != null && !history.isEmpty()) {
            addSectionDivider(container, getString(R.string.details_label_payment_history));
            NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
            for (Booking.PaymentRecord record : history) {
                container.addView(buildPaymentHistoryRow(container, record, currencyFormat));
            }
        }
    }

    /** Same calendar-day computation PaymentReceiptActivity uses for its identical row - kept as a local copy per this codebase's existing per-screen-helper convention. */
    @Nullable
    private String computeNights(@Nullable String checkIn, @Nullable String checkOut) {
        Long nights = StayDateCalculator.nightsBetweenOrNull(checkIn, checkOut);
        return nights != null ? String.valueOf(nights) : null;
    }

    /**
     * "Receipts" (one card per booking.getReceipts() entry, each opening its
     * own PaymentReceiptActivity by exact receipt_number) when the backend
     * has already issued at least one; otherwise falls back to the legacy
     * single-receipt card gated on booking.isStaffVerified() (the same
     * server-authoritative signal PaymentStatusResolver already treats as
     * "verified" everywhere else) plus an actual payment existing - a plain
     * Reservation with no payment involved shows no card at all. A guest-
     * uploaded GCash screenshot (booking.getReceiptUrl(), shown separately
     * below as "Receipt image") is never treated as satisfying either path -
     * only staff verification/an actually-issued receipt does. See
     * ReceiptCardHelper for the shared binding logic (previously inlined
     * here and duplicated byte-for-byte in TransactionDetailsActivity).
     */
    private void buildReceiptActionCard(LinearLayout container) {
        if (!booking.getReceipts().isEmpty()) {
            addSectionDivider(container, getString(R.string.details_label_receipts_section));
            ReceiptCardHelper.buildReceiptsSection(this, container, booking);
            return;
        }

        if (!ReceiptCardHelper.hasLegacyReceiptCandidate(booking)) return;

        View card = LayoutInflater.from(this).inflate(R.layout.item_payment_receipt_action, container, false);
        ReceiptCardHelper.bindLegacyReceiptActionCard(this, card, booking);
        container.addView(card);
    }

    private View buildPaymentHistoryRow(ViewGroup parent, Booking.PaymentRecord record, NumberFormat currencyFormat) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_payment_history_entry, parent, false);
        TextView tvMethod = row.findViewById(R.id.tvHistoryMethod);
        TextView tvDate = row.findViewById(R.id.tvHistoryDate);
        TextView tvAmount = row.findViewById(R.id.tvHistoryAmount);
        TextView tvStatus = row.findViewById(R.id.tvHistoryStatus);

        tvMethod.setText(record.method != null ? record.method : getString(R.string.label_not_available));
        tvDate.setText(record.date != null ? record.date : getString(R.string.label_not_available));
        try {
            tvAmount.setText(currencyFormat.format(Double.parseDouble(record.amount)));
        } catch (NumberFormatException | NullPointerException e) {
            tvAmount.setText(record.amount != null ? record.amount : "");
        }
        tvStatus.setText(record.status != null ? record.status : "");
        return row;
    }

    // ---- Timeline tab ----

    private static class TimelineStep {
        final String label;
        final boolean reached;
        /** Manila-formatted "MMM d, yyyy • h:mm a" from the backend, or null when reached but the backend hasn't sent a timestamp for this step yet (older cached data). */
        final String timestamp;
        TimelineStep(String label, boolean reached, String timestamp) {
            this.label = label;
            this.reached = reached;
            this.timestamp = timestamp;
        }
    }

    private void buildTimelineSection(LinearLayout container) {
        List<TimelineStep> steps = new ArrayList<>();
        steps.add(new TimelineStep(
                getString(booking.isDirectBooking() ? R.string.timeline_booking_created : R.string.timeline_created),
                true, booking.getCreatedAtDisplay()));

        boolean isGcash = "gcash".equalsIgnoreCase(booking.getPaymentMethod());
        if (isGcash) {
            boolean submitted = booking.getTransactionRef() != null;
            steps.add(new TimelineStep(getString(R.string.timeline_payment_submitted), submitted, submitted ? booking.getPaymentDate() : null));
            steps.add(new TimelineStep(getString(R.string.timeline_payment_verified), booking.isPaymentVerified(), booking.getPaymentVerifiedAtDisplay()));
        }

        // A direct "New Booking" was never a Reservation, so it can never have
        // been "Converted to Booking" - only a transaction that actually
        // started as a Reservation (isDirectBooking()==false) gets that step.
        // Previously this was one hardcoded "Confirmed / Converted to Booking"
        // step shown for every booking, which was factually wrong for every
        // direct booking (see timeline_confirmed's old value, strings.xml).
        boolean confirmed = booking.isHasBooking();
        if (booking.isDirectBooking()) {
            steps.add(new TimelineStep(getString(R.string.timeline_booking_confirmed), confirmed, confirmed ? booking.getBookingDate() : null));
        } else {
            steps.add(new TimelineStep(getString(R.string.timeline_reservation_confirmed), confirmed, confirmed ? booking.getBookingDate() : null));
            steps.add(new TimelineStep(getString(R.string.timeline_converted_to_booking), confirmed, confirmed ? booking.getBookingDate() : null));
        }

        boolean checkedIn = "Checked-In".equalsIgnoreCase(booking.getStatus()) || "Checked-Out".equalsIgnoreCase(booking.getStatus());
        if (checkedIn) {
            steps.add(new TimelineStep(getString(R.string.timeline_checked_in), true, booking.getCheckedInAtDisplay()));
        }
        if ("Checked-Out".equalsIgnoreCase(booking.getStatus())) {
            steps.add(new TimelineStep(getString(R.string.timeline_checked_out), true, booking.getCheckedOutAtDisplay()));
            steps.add(new TimelineStep(getString(R.string.timeline_completed), true, booking.getCompletedAtDisplay()));
        }
        if ("Cancelled".equalsIgnoreCase(booking.getStatus()) || "Rejected".equalsIgnoreCase(booking.getStatus())) {
            steps.add(new TimelineStep(booking.getStatus(), true, booking.getCancellationDate()));
        }

        for (int i = 0; i < steps.size(); i++) {
            container.addView(buildTimelineRow(steps.get(i), i == steps.size() - 1));
        }
    }

    private View buildTimelineRow(TimelineStep step, boolean isLast) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = isLast ? 0 : (int) (14 * getResources().getDisplayMetrics().density);
        row.setLayoutParams(rowParams);

        ImageView dot = new ImageView(this);
        int dotSize = (int) (18 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dotSize, dotSize);
        dotParams.rightMargin = (int) (12 * getResources().getDisplayMetrics().density);
        dot.setLayoutParams(dotParams);
        Drawable icon = ContextCompat.getDrawable(this, step.reached ? R.drawable.ic_check_circle : R.drawable.ic_clock);
        dot.setImageDrawable(icon);
        dot.setColorFilter(getResources().getColor(step.reached ? R.color.velocity_green_primary : R.color.velocity_inactive_gray));

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);

        TextView label = new TextView(this);
        label.setText(step.label);
        label.setTextSize(13);
        label.setTextColor(getResources().getColor(step.reached ? R.color.velocity_text_primary : R.color.velocity_inactive_gray));
        label.setTypeface(null, step.reached ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        textColumn.addView(label);

        // Only a reached step can meaningfully show "when" - an unreached
        // step (e.g. Checked Out on a still-Checked-In stay) has no
        // timestamp to show and would otherwise print a misleading "N/A".
        if (step.reached) {
            TextView timeLabel = new TextView(this);
            timeLabel.setText(!TextUtils.isEmpty(step.timestamp) ? step.timestamp : getString(R.string.timeline_pending));
            timeLabel.setTextSize(11);
            timeLabel.setTextColor(getResources().getColor(R.color.velocity_text_secondary));
            textColumn.addView(timeLabel);
        }

        row.addView(dot);
        row.addView(textColumn);
        return row;
    }

    // ---- Shared row helper ----

    /** One "Label ............ Value" row, matching BookingAndReservationActivity's existing buildDetailsInfoRow() convention. Skips entirely if value is null/blank, per "only display fields that exist." */
    private void addInfoRow(LinearLayout container, String label, @Nullable String value) {
        if (TextUtils.isEmpty(value)) return;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
        row.setLayoutParams(rowParams);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextSize(12);
        tvLabel.setTextColor(getResources().getColor(R.color.velocity_text_secondary));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvLabel.setLayoutParams(labelParams);

        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setTextSize(13);
        tvValue.setTypeface(null, android.graphics.Typeface.BOLD);
        tvValue.setTextColor(getResources().getColor(R.color.velocity_text_primary));
        tvValue.setGravity(Gravity.END);

        row.addView(tvLabel);
        row.addView(tvValue);
        container.addView(row);
    }

    /**
     * "Label" on its own line, bold value on the line below, both at full container
     * width - for fields whose value can be long enough that a label-left/value-right
     * row (addInfoRow() above) would squeeze it into an unreadably narrow column (e.g.
     * a long Representative Name). Skips entirely if value is null/blank, same as
     * addInfoRow().
     */
    private void addInfoRowVertical(LinearLayout container, String label, @Nullable String value) {
        if (TextUtils.isEmpty(value)) return;

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        columnParams.bottomMargin = (int) (12 * getResources().getDisplayMetrics().density);
        column.setLayoutParams(columnParams);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextSize(12);
        tvLabel.setTextColor(getResources().getColor(R.color.velocity_text_secondary));

        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setTextSize(15);
        tvValue.setTypeface(null, android.graphics.Typeface.BOLD);
        tvValue.setTextColor(getResources().getColor(R.color.velocity_text_primary));
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        valueParams.topMargin = (int) (2 * getResources().getDisplayMetrics().density);
        tvValue.setLayoutParams(valueParams);

        column.addView(tvLabel);
        column.addView(tvValue);
        container.addView(column);
    }

    private void addSectionDivider(LinearLayout container, String title) {
        View divider = new View(this);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (1 * getResources().getDisplayMetrics().density));
        dividerParams.topMargin = (int) (4 * getResources().getDisplayMetrics().density);
        dividerParams.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
        divider.setLayoutParams(dividerParams);
        divider.setBackgroundColor(getResources().getColor(R.color.velocity_red_subtle));
        container.addView(divider);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(12);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(getResources().getColor(R.color.velocity_red_primary));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
        tvTitle.setLayoutParams(titleParams);
        container.addView(tvTitle);
    }

    /** Bold room/amenity name introducing one itemized block - lighter-weight than addSectionDivider() (no red rule line), since one repeats per room/amenity rather than once per whole section. */
    private void addSubHeading(LinearLayout container, String text) {
        TextView tvHeading = new TextView(this);
        tvHeading.setText(text);
        tvHeading.setTextSize(14);
        tvHeading.setTypeface(null, android.graphics.Typeface.BOLD);
        tvHeading.setTextColor(getResources().getColor(R.color.velocity_text_primary));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = (int) (10 * getResources().getDisplayMetrics().density);
        params.bottomMargin = (int) (2 * getResources().getDisplayMetrics().density);
        tvHeading.setLayoutParams(params);
        container.addView(tvHeading);
    }

    /**
     * One itemized room-TYPE block - the room's own main image (item_selected_room_type_card.xml,
     * falling back to RoomVisuals' generic placeholder when no image_url is available, same
     * fallback mechanism bindHeader()'s single header image already uses - never a broken-image
     * icon, crash, or blank space), name, quantity, and assigned-room-number status, followed by
     * this method's pre-existing Quantity/Price Per Room/Night/Subtotal rows (unchanged - the
     * image card replaces addSubHeading()'s plain-text room name, never duplicates it).
     */
    private void addRoomDetailBlock(LinearLayout container, String roomName, int quantity, double pricePerNight,
                                     double subtotal, @Nullable String imageUrl, String assignedRoomsText) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_selected_room_type_card, container, false);
        ImageView ivRoomImage = card.findViewById(R.id.ivSelectedRoomTypeImage);
        int fallback = RoomVisuals.getRoomImage(roomName);
        if (imageUrl != null && !imageUrl.isEmpty()) {
            Glide.with(this).load(imageUrl).placeholder(fallback).error(fallback).into(ivRoomImage);
        } else {
            ivRoomImage.setImageResource(fallback);
        }
        ((TextView) card.findViewById(R.id.tvSelectedRoomTypeName)).setText(roomName);
        ((TextView) card.findViewById(R.id.tvSelectedRoomTypeQuantity))
                .setText(getResources().getQuantityString(R.plurals.rooms_selected_count, Math.max(1, quantity), Math.max(1, quantity)));
        ((TextView) card.findViewById(R.id.tvSelectedRoomTypeAssigned)).setText(assignedRoomsText);
        container.addView(card);

        addInfoRow(container, getString(R.string.quantity_label), String.valueOf(quantity));
        addInfoRow(container, getString(R.string.details_price_per_night_label), formatPrice(pricePerNight));
        addInfoRow(container, getString(R.string.summary_subtotal_label), formatPrice(subtotal));
    }

    private String resolveAssignedRoomsText(@Nullable String rawRoomNumber) {
        return Booking.resolveAssignedRoomsText(this, rawRoomNumber);
    }

    /** One itemized amenity block - name, then Quantity/Price/Subtotal rows, per the required Additional Amenities breakdown. */
    private void addAmenityDetailBlock(LinearLayout container, String amenityName, int quantity, double unitPrice, double subtotal) {
        addSubHeading(container, amenityName);
        addInfoRow(container, getString(R.string.quantity_label), String.valueOf(quantity));
        addInfoRow(container, getString(R.string.details_amenity_price_label), formatPrice(unitPrice));
        addInfoRow(container, getString(R.string.summary_subtotal_label), formatPrice(subtotal));
    }

    /** Emphasized "Room Total"/"Amenities Total"/"Grand Total" line - bold label, larger bold red-accent value, so a running total visually stands apart from the itemized rows above it. */
    private void addTotalRow(LinearLayout container, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = (int) (6 * getResources().getDisplayMetrics().density);
        rowParams.bottomMargin = (int) (10 * getResources().getDisplayMetrics().density);
        row.setLayoutParams(rowParams);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextSize(13);
        tvLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        tvLabel.setTextColor(getResources().getColor(R.color.velocity_text_primary));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvLabel.setLayoutParams(labelParams);

        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setTextSize(16);
        tvValue.setTypeface(null, android.graphics.Typeface.BOLD);
        tvValue.setTextColor(getResources().getColor(R.color.velocity_red_primary));
        tvValue.setGravity(Gravity.END);

        row.addView(tvLabel);
        row.addView(tvValue);
        container.addView(row);
    }

    /**
     * Grand Total specifically - visually stronger than addTotalRow()'s Amenities
     * Total/Room Total rows (per "Highlight Grand Total more strongly than the
     * other totals"): a soft-red highlighted panel (same @drawable/bg_receipt_total_panel
     * PaymentReceiptActivity already uses for its own total-paid panel, kept
     * consistent here) with a larger, bolder value.
     */
    private void addGrandTotalRow(LinearLayout container, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_receipt_total_panel);
        int hPad = (int) (14 * getResources().getDisplayMetrics().density);
        int vPad = (int) (12 * getResources().getDisplayMetrics().density);
        row.setPadding(hPad, vPad, hPad, vPad);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
        row.setLayoutParams(rowParams);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextSize(14);
        tvLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        tvLabel.setTextColor(getResources().getColor(R.color.velocity_red_dark));
        tvLabel.setAllCaps(true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvLabel.setLayoutParams(labelParams);

        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setTextSize(20);
        tvValue.setTypeface(null, android.graphics.Typeface.BOLD);
        tvValue.setTextColor(getResources().getColor(R.color.velocity_red_primary));
        tvValue.setGravity(Gravity.END);

        row.addView(tvLabel);
        row.addView(tvValue);
        container.addView(row);
    }

    /** Whole-stay night count used to derive a per-room price when only a subtotal is known (legacy grouped/single-room fallbacks) - defaults to 1 on an unparseable/missing date, same failure behavior as computeNights() above. */
    private int nightsOrOne(@Nullable String checkIn, @Nullable String checkOut) {
        return (int) StayDateCalculator.nightsBetween(checkIn, checkOut);
    }

    private String formatPrice(double amount) {
        return String.format(Locale.US, getString(R.string.price_format), amount);
    }

    private String formatPercentage(Double percentage) {
        return PaymentPercentageUtil.formatApiPercentageForDisplay(percentage);
    }

    public static Intent newIntent(android.content.Context context, Booking booking) {
        Intent intent = new Intent(context, BookingDetailsActivity.class);
        intent.putExtra(EXTRA_BOOKING, booking);
        return intent;
    }
}
