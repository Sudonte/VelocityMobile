package com.example.velocitysuites;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import androidx.core.widget.TextViewCompat;
import com.example.velocitysuites.network.ApiClient;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;

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

    private SwipeRefreshLayout swipeRefresh;
    private ActivityResultLauncher<Intent> editLauncher;
    private final ClickGuard clickGuard = new ClickGuard();

    /**
     * While this screen is open a guest may be waiting for the receptionist to verify a payment or an ID, so the
     * transaction is re-read every 30s (and when the guest returns to the screen) - the same beat Transaction
     * Details uses - and the page is rebuilt only if something about it really changed.
     */
    private final VisiblePoller visiblePoller = new VisiblePoller(() -> {
        if (booking == null) return;
        refreshFromServer(false);
        // Nothing else polls notifications while this screen is open, so check here: a receipt / verification
        // notification then reaches arrivalListener below and refreshes the page at once.
        RoomRepository.getInstance(this).pollNotifications(null);
    });

    /**
     * A booking / payment / receipt notification arrived (the receptionist just verified, checked in or out):
     * refresh now instead of waiting for the next 30-second beat. Only ones about THIS transaction, or carrying
     * a receipt, count - another booking's news must not redraw this page.
     */
    private final RoomRepository.NotificationArrivalListener arrivalListener = arrived -> {
        if (isFinishing() || isDestroyed() || booking == null) return;
        for (Notification n : arrived) {
            boolean aboutThis = booking.getId() != null && booking.getId().equals(n.getReferenceId());
            boolean transactional = NotificationPrimaryActionResolver.isTransactionCategory(n.getType());
            if (transactional && (aboutThis || n.hasStructuredReceipt())) {
                refreshFromServer(false);
                return;
            }
        }
    };
    private final ChangeGate detailsGate = new ChangeGate();
    /** onCreate() already refreshed once, so the very first onResume() must not refresh again. */
    private boolean firstResume = true;

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

        swipeRefresh = findViewById(R.id.swipeRefreshDetails);
        swipeRefresh.setColorSchemeResources(R.color.velocity_red_primary);
        FlatUi.removeSpinnerShadow(swipeRefresh);
        swipeRefresh.setOnRefreshListener(() -> refreshFromServer(true));

        editLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                // The edit was saved: reload so the details, totals and timeline show it right away.
                refreshFromServer(true);
            }
        });
        MaterialButton btnEdit = findViewById(R.id.btnEditReservation);
        btnEdit.setOnClickListener(v -> {
            if (clickGuard.tryAcquire()) editLauncher.launch(BookingWizardActivity.newEditIntent(this, booking));
        });

        correctTotalAmountThenRender();
        detailsGate.accept(booking); // what is being drawn from the intent's copy
        // "On the next open": always pick up what the receptionist verified since this record was cached.
        refreshFromServer(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (firstResume) {
            firstResume = false;
        } else if (booking != null) {
            refreshFromServer(false); // back on the screen: catch up silently
        }
        visiblePoller.start();
    }

    @Override
    protected void onStart() {
        super.onStart();
        RoomRepository.getInstance(this).addNotificationArrivalListener(arrivalListener);
    }

    @Override
    protected void onStop() {
        RoomRepository.getInstance(this).removeNotificationArrivalListener(arrivalListener);
        super.onStop();
    }

    @Override
    protected void onPause() {
        visiblePoller.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        visiblePoller.stop();
        if (ioExecutor != null) ioExecutor.shutdownNow();
        // The downloaded ID photo is private: do not leave a copy in the cache.
        if (idCardFile != null) idCardFile.delete();
        super.onDestroy();
    }

    /**
     * Re-fetches this one transaction (pull-to-refresh, after an edit, and silently once on open)
     * so server-side changes - a receptionist verifying a payment / ID / check-in, a saved edit -
     * appear without leaving the screen. A failure keeps what is already shown.
     */
    private void refreshFromServer(boolean userInitiated) {
        if (booking.isHistoricalReservation()) {
            swipeRefresh.setRefreshing(false);
            return;
        }
        if (userInitiated) swipeRefresh.setRefreshing(true);
        final String id = booking.getId();
        RoomRepository.getInstance(this).fetchTransactionById(id, !booking.isDirectBooking(), new RoomRepository.RepositoryCallback<Booking>() {
            @Override
            public void onSuccess(Booking fresh) {
                if (isFinishing() || isDestroyed()) return;
                swipeRefresh.setRefreshing(false);
                // A silent check that finds the same transaction must not rebuild every section (pull-to-refresh and
                // a saved edit always redraw, as before).
                boolean changed = detailsGate.accept(fresh);
                if (!changed && !userInitiated) return;
                booking = fresh;
                correctTotalAmountThenRender();
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                swipeRefresh.setRefreshing(false);
                if (userInitiated) {
                    Toast.makeText(BookingDetailsActivity.this, R.string.details_refresh_failed, Toast.LENGTH_LONG).show();
                }
            }
        });
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
                        if (isFinishing() || isDestroyed()) return;
                        renderAllSections();
                    }

                    @Override
                    public void onError(String message) {
                        if (isFinishing() || isDestroyed()) return;
                        // correctPendingReservationTotal() itself never calls onError - present
                        // for RepositoryCallback's contract only.
                        renderAllSections();
                    }
                });
    }

    private void renderAllSections() {
        // Re-rendering (after a refresh/edit) must replace, not append to, what was drawn before.
        int[] sectionIds = {R.id.sectionBalanceContent, R.id.sectionBookingInfoContent, R.id.sectionGuestInfoContent, R.id.sectionRoomInfoContent,
                R.id.sectionPaymentInfoContent, R.id.sectionPaymentSummaryContent, R.id.sectionTimelineContent,
                R.id.sectionPaymentHistoryContent, R.id.sectionDiscountIdContent};
        for (int id : sectionIds) {
            ((LinearLayout) findViewById(id)).removeAllViews();
        }
        findViewById(R.id.btnEditReservation).setVisibility(
                ReservationEditPolicy.canEdit(booking, LocalTransactionState.hasModifiedOnce(this, booking.getId()))
                        ? View.VISIBLE : View.GONE);
        bindRequestAmenities();
        // Resolved before bindHeader() now (previously ran after it) so the
        // header's own itemized room-selection line can use the same
        // groupMembers fallback buildRoomInfoSection() below already relies on.
        resolveGroupMembers();
        bindHeader();

        // Card/section-based layout - every section renders and stays visible
        // together (see activity_booking_details.xml), replacing the old
        // 4-tab toggle switcher that hid all but one section at a time.
        buildBalanceSummary(findViewById(R.id.sectionBalanceContent));
        buildBookingInfoSection(findViewById(R.id.sectionBookingInfoContent));
        buildGuestInfoSection(findViewById(R.id.sectionGuestInfoContent));
        buildRoomInfoSection(findViewById(R.id.sectionRoomInfoContent));
        buildPaymentInfoSection(findViewById(R.id.sectionPaymentInfoContent));
        buildPaymentHistoryCard(findViewById(R.id.sectionPaymentHistoryContent));
        buildPaymentSummarySection(findViewById(R.id.sectionPaymentSummaryContent));
        buildDiscountIdSection(findViewById(R.id.cardDiscountId), findViewById(R.id.sectionDiscountIdContent));
        buildTimelineSection(findViewById(R.id.sectionTimelineContent));
    }

    // ---- Balance summary + progress tracker ----

    private int dpPx(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /**
     * The first card the guest sees: a plain-words payment status, what is still owed, what was paid and the grand
     * total (all the server's figures - nothing is added up here), then a four-step tracker
     * Booked -> Verified -> Checked in -> Checked out, each with its receipt once issued or a friendly placeholder.
     */
    private void buildBalanceSummary(LinearLayout container) {
        TransactionStatusHelper.Summary summary = TransactionStatusHelper.summarize(booking);
        double grand = TransactionStatusHelper.grandTotalOf(booking);
        double paid = TransactionStatusHelper.verifiedPaidOf(booking);
        double balance = Math.max(0, grand - paid);
        boolean cancelled = "Cancelled".equalsIgnoreCase(booking.getStatus()) || "Rejected".equalsIgnoreCase(booking.getStatus());

        TextView title = new TextView(this);
        title.setText(R.string.balance_summary_title);
        title.setAllCaps(true);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setTextColor(getColor(R.color.velocity_red_primary));
        title.setTextSize(12);
        container.addView(title);

        PaymentStatusResolver.Result status = PaymentStatusResolver.resolve(this, booking);
        TextView badge = new TextView(this);
        badge.setText(status.label);
        badge.setBackgroundResource(R.drawable.shape_intro_pill);
        badge.setBackgroundTintList(getColorStateList(status.bgColorRes));
        badge.setTextColor(getColor(status.fgColorRes));
        badge.setTypeface(badge.getTypeface(), android.graphics.Typeface.BOLD);
        badge.setTextSize(12);
        badge.setPadding(dpPx(10), dpPx(4), dpPx(10), dpPx(4));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeParams.topMargin = dpPx(8);
        container.addView(badge, badgeParams);

        // The sentence that answers "do I owe anything?"
        TextView plain = new TextView(this);
        plain.setText(cancelled ? getString(R.string.balance_plain_cancelled)
                : balance > 0.004 ? getString(R.string.balance_plain_owe, MoneyFormat.format(balance))
                : getString(R.string.balance_plain_paid));
        plain.setTextColor(getColor(R.color.velocity_text_primary));
        plain.setTypeface(plain.getTypeface(), android.graphics.Typeface.BOLD);
        plain.setTextSize(15);
        LinearLayout.LayoutParams plainParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plainParams.topMargin = dpPx(10);
        container.addView(plain, plainParams);

        addInfoRow(container, getString(R.string.receipt_hl_remaining), MoneyFormat.format(balance));
        addInfoRow(container, getString(R.string.receipt_hl_amount_to_pay), MoneyFormat.format(balance));
        addInfoRow(container, getString(R.string.receipt_hl_total_paid), MoneyFormat.format(paid));
        addInfoRow(container, getString(R.string.receipt_hl_grand_total), MoneyFormat.format(grand));

        if (cancelled) return;
        addSectionDivider(container, getString(R.string.tracker_title));

        String st = booking.getStatus() == null ? "" : booking.getStatus();
        boolean checkedOut = "Checked-Out".equalsIgnoreCase(st) || "Completed".equalsIgnoreCase(st);
        boolean checkedIn = checkedOut || "Checked-In".equalsIgnoreCase(st);
        boolean verified = booking.isStaffVerified() || checkedIn;
        java.util.Map<Integer, Booking.ReceiptSummary> byStage = new java.util.HashMap<>();
        for (Booking.ReceiptSummary r : booking.getReceipts()) {
            int stage = ReceiptTypeMapper.stageOf(r.receiptType);
            if (stage > 0 && !byStage.containsKey(stage)) byStage.put(stage, r);
        }

        addTrackerStep(container, getString(R.string.tracker_booked), true, null, getString(R.string.tracker_booked_note));
        addTrackerStep(container, getString(R.string.tracker_verified), verified, byStage.get(1), getString(R.string.tracker_receipt1_placeholder));
        addTrackerStep(container, getString(R.string.tracker_checked_in), checkedIn, byStage.get(2), getString(R.string.tracker_receipt2_placeholder));
        addTrackerStep(container, getString(R.string.tracker_checked_out), checkedOut, byStage.get(3), getString(R.string.tracker_receipt3_placeholder));
    }

    /** One tracker row: a filled or empty dot, the step, and - when it has one - its receipt (tap to open) or why it is not there yet. */
    private void addTrackerStep(LinearLayout container, String label, boolean reached, @Nullable Booking.ReceiptSummary receipt, String placeholder) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dpPx(44));

        View dot = new View(this);
        dot.setBackgroundResource(reached ? R.drawable.shape_status_dot_green : R.drawable.shape_timeline_dot);
        dot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(dot, new LinearLayout.LayoutParams(dpPx(12), dpPx(12)));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.setMarginStart(dpPx(12));
        row.addView(text, textParams);

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(13);
        name.setTypeface(name.getTypeface(), reached ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        name.setTextColor(getColor(reached ? R.color.velocity_text_primary : R.color.velocity_text_secondary));
        text.addView(name);

        TextView note = new TextView(this);
        note.setTextSize(12);
        if (receipt != null) {
            note.setText(getString(R.string.tracker_receipt_ready_format, receipt.receiptNumber));
            note.setTextColor(getColor(R.color.velocity_red_primary));
            note.setTypeface(note.getTypeface(), android.graphics.Typeface.BOLD);
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(v -> {
                if (clickGuard.tryAcquire()) startActivity(PaymentReceiptActivity.newIntentForReceipt(this, booking, receipt.receiptNumber));
            });
        } else {
            note.setText(placeholder);
            note.setTextColor(getColor(R.color.velocity_text_secondary));
        }
        text.addView(note);
        container.addView(row);
    }

    /** Request Additional Amenities: offered for a stay that exists (not a cancelled one or a finished one); the server's answer enables it or explains why not. */
    private void bindRequestAmenities() {
        View button = findViewById(R.id.btnRequestAmenitiesDetails);
        TextView reason = findViewById(R.id.tvRequestAmenitiesDetailsReason);
        String status = booking.getStatus();
        boolean offered = booking.isHasBooking() && !"Cancelled".equalsIgnoreCase(status) && !"Rejected".equalsIgnoreCase(status)
                && !"Checked-Out".equalsIgnoreCase(status);
        if (!offered) {
            button.setVisibility(View.GONE);
            reason.setVisibility(View.GONE);
            return;
        }
        AdditionalAmenitiesEntry.bindWithEligibility(this, button, reason, booking, () -> {
            if (clickGuard.tryAcquire()) startActivity(AdditionalAmenitiesEntry.intent(this, booking));
        });
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
                ? getString(R.string.direct_booking_ref_format, booking.getDisplayId())
                : getString(R.string.reservation_ref_format, booking.getDisplayId()));

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

    private String actualOrExpected(String actualDisplay, java.time.LocalTime posted) {
        if (actualDisplay != null && !actualDisplay.isEmpty()) return actualDisplay;
        return getString(R.string.details_expected_time_format, StayCalendarModel.formatTime(posted));
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
        // The moment the receptionist actually checked the guest in / out, to the second; until then the hotel's posted time.
        addInfoRow(container, getString(R.string.details_label_checked_in_at), actualOrExpected(booking.getCheckedInAtDisplay(),
                StayCalendarModel.CHECK_IN_TIME));
        addInfoRow(container, getString(R.string.details_label_checked_out_at), actualOrExpected(booking.getCheckedOutAtDisplay(),
                StayCalendarModel.CHECK_OUT_TIME));
        StayInfo stay = booking.getStay();
        if (stay != null) {
            // In house or finished: the dates and nights the front desk actually billed - scheduled, actual, nights
            // (booked vs actual when they differ), the extra nights' charge and the updated total.
            for (StayInfo.Row row : stay.rows(this)) {
                if (row.label.equals(getString(R.string.stay_scheduled_check_in))
                        || row.label.equals(getString(R.string.stay_scheduled_check_out))) continue; // already shown just above
                addInfoRow(container, row.label, row.value);
            }
        } else {
            addInfoRow(container, getString(R.string.receipt_number_of_nights_label), computeNights(booking.getCheckInDate(), booking.getCheckOutDate()));
        }
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
                        getString(sibling.isHasBooking() ? R.string.direct_booking_ref_format : R.string.reservation_ref_format, sibling.getDisplayId()));
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
        addIconRow(container, R.drawable.ic_person, getString(R.string.guest_label_name), guestAccountName);
        if (representativeName != null && !representativeName.trim().equalsIgnoreCase(guestAccountName == null ? "" : guestAccountName.trim())) {
            addIconRow(container, R.drawable.ic_people, getString(R.string.details_label_representative_name), representativeName);
        }
        addIconRow(container, R.drawable.ic_email, getString(R.string.guest_label_email), prefs.getString("userEmail", null));
        addIconRow(container, R.drawable.ic_phone, getString(R.string.guest_label_phone), prefs.getString("userMobile", null));
        addIconRow(container, R.drawable.ic_location, getString(R.string.guest_label_address), prefs.getString("userAddress", null));

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
                // Each room line's own nights when the front desk itemized the stay (an extended room costs more nights).
                long lineNights = booking.getStay() != null && room.getNights() > 0 ? room.getNights() : stayNights;
                double subtotal = room.getPricePerNight() * room.getQuantity() * lineNights;
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

    // ---- Discount ID section ----

    /**
     * The guest's discount ID check (Senior / PWD / ...), decided by the front desk separately from the transaction
     * itself. Shown only when a discount was asked for (status pending / approved / rejected).
     */
    private void buildDiscountIdSection(android.view.View card, LinearLayout container) {
        String status = booking.getDiscountVerificationStatus();
        boolean approved = "approved".equalsIgnoreCase(status);
        boolean rejected = "rejected".equalsIgnoreCase(status);
        boolean pending = "pending".equalsIgnoreCase(status);
        if (!approved && !rejected && !pending) {
            // not_requested / no discount: the whole section is hidden
            card.setVisibility(android.view.View.GONE);
            return;
        }
        card.setVisibility(android.view.View.VISIBLE);

        // The verification status leads the section - read fresh from the server on open, resume and pull-to-refresh.
        int chipText = approved ? R.string.discount_chip_approved : rejected ? R.string.discount_chip_rejected : R.string.discount_chip_pending;
        int chipBg = approved ? R.color.velocity_green_soft : rejected ? R.color.velocity_red_subtle : R.color.velocity_orange_soft;
        int chipFg = approved ? R.color.velocity_green_dark : rejected ? R.color.velocity_red_dark : R.color.velocity_orange_primary;
        int chipIcon = approved ? R.drawable.ic_check_circle : rejected ? R.drawable.ic_close : R.drawable.ic_clock;
        container.addView(buildStatusChip(chipText, chipBg, chipFg, chipIcon));

        if (approved) {
            addHelperText(container, getString(R.string.discount_help_approved));
        } else if (pending) {
            addHelperText(container, getString(R.string.discount_help_pending));
        } else {
            String reason = booking.getDiscountRejectionReason();
            addHelperText(container, TextUtils.isEmpty(reason) || reason.trim().isEmpty()
                    ? getString(R.string.discount_help_rejected_no_reason)
                    : getString(R.string.discount_help_rejected, reason.trim()));
        }

        String type = booking.getIdCardType();
        if (type != null && !type.trim().isEmpty() && !"none".equalsIgnoreCase(type.trim())) {
            addInfoRow(container, getString(R.string.discount_label_type), type.trim());
        }
        if (booking.isHasIdCard() && !booking.isDirectBooking()) {
            addIdPhotoRow(container);
        }
        if (approved && booking.getDiscountAmount() > 0.009) {
            addInfoRow(container, getString(R.string.discount_label_amount), formatPrice(booking.getDiscountAmount()));
        } else if (pending) {
            addInfoRow(container, getString(R.string.discount_label_amount), getString(R.string.discount_amount_pending));
        }
    }

    private String discountDisplayName(@Nullable String idType) {
        String t = idType == null ? "" : idType.toLowerCase(Locale.ROOT);
        if (t.contains("senior")) return getString(R.string.discount_name_senior);
        if (t.contains("pwd")) return getString(R.string.discount_name_pwd);
        return idType != null && !idType.trim().isEmpty() && !"none".equalsIgnoreCase(idType.trim()) ? idType.trim() : getString(R.string.discount_name_generic);
    }

    private TextView buildStatusChip(int textRes, int bgColor, int fgColor, int iconRes) {
        TextView chip = new TextView(this);
        chip.setText(textRes);
        chip.setTextSize(13);
        chip.setTypeface(null, android.graphics.Typeface.BOLD);
        chip.setTextColor(getColor(fgColor));
        chip.setBackgroundResource(R.drawable.shape_flat_badge);
        chip.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(bgColor)));
        float d = getResources().getDisplayMetrics().density;
        chip.setPadding((int) (10 * d), (int) (6 * d), (int) (10 * d), (int) (6 * d));
        chip.setCompoundDrawablePadding((int) (8 * d));
        chip.setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0);
        TextViewCompat.setCompoundDrawableTintList(chip, android.content.res.ColorStateList.valueOf(getColor(fgColor)));
        chip.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (8 * d);
        chip.setLayoutParams(lp);
        return chip;
    }

    private void addHelperText(LinearLayout container, String text) {
        TextView helper = new TextView(this);
        helper.setText(text);
        helper.setTextSize(12);
        helper.setLineSpacing(0, 1.1f);
        helper.setTextColor(getColor(R.color.velocity_text_secondary));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (12 * getResources().getDisplayMetrics().density);
        helper.setLayoutParams(lp);
        container.addView(helper);
    }

    // ---- the uploaded ID photo: a small thumbnail, full screen on tap ----

    private java.util.concurrent.ExecutorService ioExecutor;
    private java.io.File idCardFile;

    private void addIdPhotoRow(LinearLayout container) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = (int) (8 * d);
        row.setLayoutParams(rowLp);

        TextView label = new TextView(this);
        label.setText(R.string.discount_label_id_details);
        label.setTextSize(12);
        label.setTextColor(getColor(R.color.velocity_text_secondary));
        label.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        ImageView thumb = new ImageView(this);
        thumb.setLayoutParams(new LinearLayout.LayoutParams((int) (96 * d), (int) (64 * d)));
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setBackgroundColor(getColor(R.color.velocity_red_soft));
        thumb.setImageResource(R.drawable.ic_person);
        thumb.setPadding((int) (20 * d), (int) (12 * d), (int) (20 * d), (int) (12 * d));
        thumb.setContentDescription(getString(R.string.discount_id_photo_cd));
        row.addView(label);
        row.addView(thumb);
        container.addView(row);
        loadIdPhoto(thumb);
    }

    /** Downloads the ID off the main thread, decodes a thumbnail-sized bitmap, and keeps a private copy for the full-screen viewer. */
    private void loadIdPhoto(ImageView thumb) {
        if (ioExecutor == null) ioExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
        final String id = booking.getId();
        ApiClient.getService(this).getIdCard(id).enqueue(new retrofit2.Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(retrofit2.Call<okhttp3.ResponseBody> call, retrofit2.Response<okhttp3.ResponseBody> response) {
                if (isFinishing() || isDestroyed() || !response.isSuccessful() || response.body() == null || ioExecutor == null) return;
                final okhttp3.ResponseBody body = response.body();
                try {
                    ioExecutor.execute(() -> {
                        try {
                            byte[] bytes = body.bytes();
                            android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
                            bounds.inJustDecodeBounds = true;
                            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                            android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
                            opts.inSampleSize = Math.max(1, Math.max(bounds.outWidth, bounds.outHeight) / 320);
                            android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
                            java.io.File file = new java.io.File(getCacheDir(), "id_card_" + id + ".jpg");
                            try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
                                out.write(bytes);
                            }
                            runOnUiThread(() -> {
                                if (isFinishing() || isDestroyed() || bmp == null) return;
                                idCardFile = file;
                                thumb.setPadding(0, 0, 0, 0);
                                thumb.setImageBitmap(bmp);
                                thumb.setOnClickListener(v -> ReceiptImageViewerActivity.start(BookingDetailsActivity.this, android.net.Uri.fromFile(file).toString()));
                            });
                        } catch (java.io.IOException | RuntimeException e) {
                            // Keep the placeholder; the details themselves are unaffected.
                        }
                    });
                } catch (java.util.concurrent.RejectedExecutionException ignored) {
                    // screen is closing
                }
            }

            @Override
            public void onFailure(retrofit2.Call<okhttp3.ResponseBody> call, Throwable t) {
                // Placeholder stays.
            }
        });
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
        if (PaymentEligibility.needsFrontDeskPayment(booking)) {
            TextView note = (TextView) LayoutInflater.from(this).inflate(R.layout.view_front_desk_note, container, false);
            note.setText(FrontDeskNote.messageFor(this, booking));
            container.addView(note);
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
            receiptView.setContentDescription(getString(R.string.receipt_image_content_description));
            receiptView.setOnClickListener(v -> ReceiptImageViewerActivity.start(this, booking.getReceiptUrl()));
            container.addView(receiptView);
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

    // ---- Timeline tab ----

    private static class TimelineStep {
        final String label;
        final boolean reached;
        /** Manila-formatted "MMM d, yyyy • h:mm a" from the backend, or null when reached but the backend hasn't sent a timestamp for this step yet (older cached data). */
        final String timestamp;
        /** "Verified" / "Pending" / "Rejected" for a server-built step; null for the legacy locally-derived steps. */
        final String statusText;
        /** True for a step the server built (even a "Recorded" one with no status text). */
        final boolean fromServer;
        TimelineStep(String label, boolean reached, String timestamp) {
            this.label = label;
            this.reached = reached;
            this.timestamp = timestamp;
            this.statusText = null;
            this.fromServer = false;
        }

        TimelineStep(String label, boolean reached, String timestamp, String statusText) {
            this.label = label;
            this.reached = reached;
            this.timestamp = timestamp;
            this.statusText = statusText;
            this.fromServer = true;
        }
    }

    /**
     * The timeline exactly as the server built it (Api: `timeline`): every entry's own label,
     * status (Verified / Pending / Rejected) and recorded date + time - including "Reservation
     * modified by guest" and, once a receptionist verifies something, that entry as "Verified".
     * Re-fetched whenever the details screen opens or is pull-to-refreshed.
     */
    private List<TimelineStep> serverTimelineSteps() {
        List<TimelineStep> steps = new ArrayList<>();
        for (Booking.TimelineEntry entry : booking.getTimeline()) {
            boolean reached = !"Pending".equalsIgnoreCase(entry.status);
            steps.add(new TimelineStep(entry.label, reached, entry.atDisplay, statusLabel(entry.status)));
        }
        return steps;
    }

    private String statusLabel(String serverStatus) {
        if ("Verified".equalsIgnoreCase(serverStatus)) return getString(R.string.timeline_status_verified);
        if ("Pending".equalsIgnoreCase(serverStatus)) return getString(R.string.timeline_status_pending);
        if ("Rejected".equalsIgnoreCase(serverStatus)) return getString(R.string.timeline_status_rejected);
        return null; // "Recorded": an event with nothing to verify - just show when it happened
    }

    private void buildTimelineSection(LinearLayout container) {
        if (!booking.getTimeline().isEmpty()) {
            List<TimelineStep> serverSteps = serverTimelineSteps();
            for (int i = 0; i < serverSteps.size(); i++) {
                container.addView(buildTimelineRow(serverSteps.get(i), i == serverSteps.size() - 1));
            }
            return;
        }
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
        if (step.fromServer) {
            // Server-built step: "<status> • <date and time>", or just the status while it is still Pending.
            TextView timeLabel = new TextView(this);
            String when = !TextUtils.isEmpty(step.timestamp) ? step.timestamp : null;
            String text;
            if (step.statusText != null && when != null) text = step.statusText + " • " + when;
            else if (step.statusText != null) text = step.statusText;
            else text = when != null ? when : getString(R.string.timeline_pending);
            timeLabel.setText(text);
            timeLabel.setTextSize(11);
            timeLabel.setTextColor(getResources().getColor(step.reached ? R.color.velocity_text_secondary : R.color.velocity_inactive_gray));
            textColumn.addView(timeLabel);
        } else if (step.reached) {
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
    // ---- Payment History card: rows newest first, then the totals ----

    private static final class PayRow {
        double amount;
        String methodType;
        String dateTime;
        long sortKey;
        int statusRes;
        int statusBg;
        int statusFg;
        @Nullable String reference;
        @Nullable String receiptNumber;
    }

    private void buildPaymentHistoryCard(LinearLayout container) {
        List<PayRow> rows = new ArrayList<>();
        List<Booking.PaymentTransactionRecord> transactions = booking.getPaymentTransactions();
        if (!transactions.isEmpty()) {
            for (Booking.PaymentTransactionRecord tx : transactions) rows.add(rowFrom(tx));
        } else if (booking.getPaymentHistory() != null) {
            int order = 0;
            for (Booking.PaymentRecord record : booking.getPaymentHistory()) {
                PayRow row = rowFrom(record);
                row.sortKey = row.sortKey != 0 ? row.sortKey : order;
                order++;
                rows.add(row);
            }
        }
        java.util.Collections.sort(rows, (a, b) -> Long.compare(b.sortKey, a.sortKey));

        if (rows.isEmpty()) {
            TextView none = new TextView(this);
            none.setText(R.string.ph_no_payments);
            none.setTextSize(13);
            none.setTextColor(getColor(R.color.velocity_text_secondary));
            none.setPadding(0, 0, 0, (int) (8 * getResources().getDisplayMetrics().density));
            container.addView(none);
        }
        for (PayRow row : rows) {
            container.addView(buildPayRowView(container, row));
        }
        addPaymentTotals(container);
    }

    private PayRow rowFrom(Booking.PaymentTransactionRecord tx) {
        PayRow row = new PayRow();
        row.amount = tx.amountPaid;
        boolean gcash = "gcash".equalsIgnoreCase(tx.paymentMethod);
        String method = getString(gcash ? R.string.payment_method_gcash : R.string.payment_method_cash);
        String stage = tx.paymentStage == null ? "" : tx.paymentStage.toLowerCase(Locale.ROOT);
        String type;
        if (stage.contains("deposit")) {
            type = getString(R.string.ph_type_deposit);
        } else if ("checkout_payment".equalsIgnoreCase(tx.transactionType)) {
            type = getString(R.string.ph_type_balance);
        } else if (stage.contains("final")) {
            boolean firstPayment = Math.abs(tx.totalPaidAfterTransaction - tx.amountPaid) < 0.01;
            type = getString(firstPayment ? R.string.ph_type_full : R.string.ph_type_balance);
        } else {
            type = getString(R.string.ph_type_payment);
        }
        row.methodType = getString(R.string.ph_method_type_format, method, type);
        row.dateTime = tx.paymentDate != null ? TimeUtils.formatDateTime(tx.paymentDate) : "";
        java.time.Instant at = TimeUtils.parseInstant(tx.paymentDate);
        row.sortKey = at != null ? at.toEpochMilli() : 0;
        String ref = tx.gcashReferenceNumber != null ? tx.gcashReferenceNumber : tx.referenceNumber;
        row.reference = gcash && !TextUtils.isEmpty(ref) ? GcashReferenceFormatter.format(ref) : (TextUtils.isEmpty(ref) ? null : ref);
        row.receiptNumber = tx.receiptNumber;
        String verification = tx.verificationStatus != null ? tx.verificationStatus.toLowerCase(Locale.ROOT) : "";
        String paymentStatus = tx.paymentStatus != null ? tx.paymentStatus.toLowerCase(Locale.ROOT) : "";
        if ("verified".equals(verification) || (verification.isEmpty() && "completed".equals(paymentStatus))) {
            setPayStatus(row, R.string.ph_status_verified, R.color.velocity_green_soft, R.color.velocity_green_dark);
        } else if ("rejected".equals(verification) || "rejected".equals(paymentStatus) || "failed".equals(paymentStatus)) {
            setPayStatus(row, R.string.ph_status_rejected, R.color.velocity_red_subtle, R.color.velocity_red_dark);
        } else {
            setPayStatus(row, R.string.ph_status_pending, R.color.velocity_orange_soft, R.color.velocity_orange_primary);
        }
        return row;
    }

    private PayRow rowFrom(Booking.PaymentRecord record) {
        PayRow row = new PayRow();
        row.amount = MoneyFormat.parse(record.amount);
        boolean gcash = record.method != null && record.method.toLowerCase(Locale.ROOT).contains("gcash");
        row.methodType = getString(R.string.ph_method_type_format,
                getString(gcash ? R.string.payment_method_gcash : R.string.payment_method_cash), getString(R.string.ph_type_payment));
        row.dateTime = record.date != null ? TimeUtils.formatDateTime(record.date) : "";
        java.time.Instant at = TimeUtils.parseInstant(record.date);
        row.sortKey = at != null ? at.toEpochMilli() : 0;
        row.reference = TextUtils.isEmpty(record.referenceNumber) ? null : (gcash ? GcashReferenceFormatter.format(record.referenceNumber) : record.referenceNumber);
        String st = record.status == null ? "" : record.status.toLowerCase(Locale.ROOT);
        if (st.contains("reject") || st.contains("fail")) {
            setPayStatus(row, R.string.ph_status_rejected, R.color.velocity_red_subtle, R.color.velocity_red_dark);
        } else if (st.contains("verif") || st.contains("complet") || st.contains("paid")) {
            setPayStatus(row, R.string.ph_status_verified, R.color.velocity_green_soft, R.color.velocity_green_dark);
        } else {
            setPayStatus(row, R.string.ph_status_pending, R.color.velocity_orange_soft, R.color.velocity_orange_primary);
        }
        return row;
    }

    private static void setPayStatus(PayRow row, int label, int bg, int fg) {
        row.statusRes = label;
        row.statusBg = bg;
        row.statusFg = fg;
    }

    private View buildPayRowView(LinearLayout parent, PayRow row) {
        View view = LayoutInflater.from(this).inflate(R.layout.item_payment_history_row, parent, false);
        ((TextView) view.findViewById(R.id.tvPhAmount)).setText(formatPrice(row.amount));
        TextView status = view.findViewById(R.id.tvPhStatus);
        status.setText(row.statusRes);
        status.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(row.statusBg)));
        status.setTextColor(getColor(row.statusFg));
        ((TextView) view.findViewById(R.id.tvPhMethodType)).setText(row.methodType);
        TextView date = view.findViewById(R.id.tvPhDate);
        date.setText(row.dateTime);
        date.setVisibility(TextUtils.isEmpty(row.dateTime) ? View.GONE : View.VISIBLE);
        TextView ref = view.findViewById(R.id.tvPhReference);
        if (TextUtils.isEmpty(row.reference)) {
            ref.setVisibility(View.GONE);
        } else {
            ref.setText(getString(R.string.ph_reference_format, row.reference));
        }
        View hint = view.findViewById(R.id.tvPhReceiptHint);
        if (!TextUtils.isEmpty(row.receiptNumber)) {
            hint.setVisibility(View.VISIBLE);
            view.setClickable(true);
            view.setOnClickListener(v -> {
                if (clickGuard.tryAcquire()) {
                    startActivity(PaymentReceiptActivity.newIntentForReceipt(this, booking, row.receiptNumber));
                }
            });
        }
        return view;
    }

    /** Total, Discount (only once approved), Paid and Remaining Balance - same figures and same Remaining rule as the billing breakdown. */
    private void addPaymentTotals(LinearLayout container) {
        double totalAmount;
        double amountPaid;
        double remainingBalance;
        if (groupMembers != null) {
            BookingGroupAggregator.Totals totals = BookingGroupAggregator.sum(groupMembers);
            totalAmount = totals.totalAmount;
            amountPaid = totals.amountPaid;
            remainingBalance = totalAmount - amountPaid;
        } else {
            totalAmount = booking.getTotalAmount();
            amountPaid = booking.getEffectiveTotalAmountPaid();
            remainingBalance = booking.getEffectiveRemainingBalance();
        }
        View rule = new View(this);
        LinearLayout.LayoutParams ruleLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) getResources().getDisplayMetrics().density);
        ruleLp.topMargin = (int) (4 * getResources().getDisplayMetrics().density);
        ruleLp.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
        rule.setLayoutParams(ruleLp);
        rule.setBackgroundColor(getColor(R.color.velocity_red_subtle));
        container.addView(rule);

        addInfoRow(container, getString(R.string.ph_total_label), formatPrice(totalAmount));
        if ("approved".equalsIgnoreCase(booking.getDiscountVerificationStatus()) && booking.getDiscountAmount() > 0.009) {
            addInfoRow(container, getString(R.string.ph_discount_label), "-" + formatPrice(booking.getDiscountAmount()));
        }
        addInfoRow(container, getString(R.string.ph_amount_paid_label), formatPrice(amountPaid));
        if (remainingBalance > 0.009 && (amountPaid > 0.009 || booking.isHasBooking())
                && !"Checked-Out".equalsIgnoreCase(booking.getStatus()) && !"Completed".equalsIgnoreCase(booking.getStatus())) {
            addInfoRow(container, getString(R.string.ph_remaining_label), formatPrice(remainingBalance));
        }
    }

    /** One label/value row with a small leading icon; skipped when there is no real value (never shows "null"). */
    private void addIconRow(LinearLayout container, int iconRes, String label, @Nullable String value) {
        if (TextUtils.isEmpty(value) || "null".equalsIgnoreCase(value.trim())) return;
        float d = getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = (int) (10 * d);
        row.setLayoutParams(rowLp);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(getColor(R.color.velocity_red_primary));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams((int) (18 * d), (int) (18 * d));
        iconLp.setMarginEnd((int) (8 * d));
        row.addView(icon, iconLp);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextSize(12);
        tvLabel.setTextColor(getColor(R.color.velocity_text_secondary));
        row.addView(tvLabel, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f));

        TextView tvValue = new TextView(this);
        tvValue.setText(value.trim());
        tvValue.setTextSize(13);
        tvValue.setTypeface(null, android.graphics.Typeface.BOLD);
        tvValue.setTextColor(getColor(R.color.velocity_text_primary));
        tvValue.setGravity(Gravity.END);
        row.addView(tvValue, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f));
        container.addView(row);
    }

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
        // A long room name, email or address wraps in its own column instead of being squeezed or cut off.
        tvValue.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f));

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
