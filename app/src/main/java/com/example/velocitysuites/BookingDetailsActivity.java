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
public class BookingDetailsActivity extends SafeActivity {

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
     * transaction is re-read every 15s (and when the guest returns to the screen) - the same beat Transaction
     * Details uses - and the page is rebuilt only if something about it really changed.
     */
    private final VisiblePoller visiblePoller = new VisiblePoller(() -> {
        if (booking == null) return;
        if (!DetailsLive.isActive(booking)) { // checked out, cancelled, rejected or failed: nothing left to wait for
            BookingDetailsActivity.this.visiblePoller.stop();
            return;
        }
        refreshFromServer(false);
        AmenityRoomSources.fetch(this, booking);
        // Nothing else polls notifications while this screen is open, so check here: a receipt / verification
        // notification then reaches arrivalListener below and refreshes the page at once.
        RoomRepository.getInstance(this).pollNotifications(null);
    }, DetailsLive.POLL_INTERVAL_MS);
    private DetailsLive live;

    /**
     * A booking / payment / receipt notification arrived (the receptionist just verified, checked in or out):
     * refresh now instead of waiting for the next beat. Only ones about THIS transaction, or carrying
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
        live = DetailsLive.attach(this, DetailsLive.contentOf(swipeRefresh), () -> refreshFromServer(true));
        AmenityRoomSources.listen(booking, amenityRequestsListener);

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
        if (DetailsLive.isActive(booking)) visiblePoller.start();
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
        if (booking != null) AmenityRoomSources.unlisten(booking, amenityRequestsListener);
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
                live.markUpdated(System.currentTimeMillis());
                // A silent check that finds the same transaction must not rebuild every section (pull-to-refresh and
                // a saved edit always redraw, as before).
                boolean changed = detailsGate.accept(fresh);
                if (!changed && !userInitiated) return;
                booking = fresh;
                if (!DetailsLive.isActive(booking)) visiblePoller.stop();
                correctTotalAmountThenRender();
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                swipeRefresh.setRefreshing(false);
                live.markFailed();
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
        androidx.core.widget.NestedScrollView scroller = DetailsLive.scrollerOf(swipeRefresh);
        final int keepScroll = scroller.getScrollY();
        renderSections();
        if (keepScroll > 0) scroller.post(() -> scroller.scrollTo(0, keepScroll)); // a quiet refresh never jumps the page
    }

    private void renderSections() {
        // Re-rendering (after a refresh/edit) must replace, not append to, what was drawn before.
        int[] sectionIds = {R.id.sectionBalanceContent, R.id.sectionBookingInfoContent, R.id.sectionGuestInfoContent, R.id.sectionRoomInfoContent,
                R.id.sectionPaymentInfoContent, R.id.sectionPaymentSummaryContent, R.id.sectionTimelineContent,
                R.id.sectionPaymentHistoryContent, R.id.sectionDiscountIdContent, R.id.sectionReceiptsContent};
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

        // One structure for every details screen and receipt (see PayingForBreakdown): header, your stay (date ranges with rooms and
        // amenities), guests, bill, payments, receipts, timeline. Each value appears once; the old balance card, booking-information
        // card and payment-information rows repeated the dates, the status, the totals and the balance, so they are gone.
        hideCardOf(R.id.sectionBookingInfoContent);
        findViewById(R.id.cardBalanceSummary).setVisibility(View.GONE);
        hideCardOf(R.id.sectionPaymentInfoContent);
        PayingForBreakdown breakdown = PayingForBreakdown.forDetails(this, booking);
        breakdown.renderRanges(this, findViewById(R.id.sectionRoomInfoContent));
        buildGuestInfoSection(findViewById(R.id.sectionGuestInfoContent));
        breakdown.renderBill(this, findViewById(R.id.sectionPaymentSummaryContent));
        addReceiptImage(findViewById(R.id.sectionPaymentSummaryContent));
        TransactionSections.payments(this, findViewById(R.id.sectionPaymentHistoryContent), booking.getPaymentTransactions(), booking.getPaymentHistory());
        buildReceiptsCard();
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
    private boolean offersAmenityRequests() {
        String status = booking.getStatus();
        return booking.isHasBooking() && !"Cancelled".equalsIgnoreCase(status) && !"Rejected".equalsIgnoreCase(status)
                && !"Checked-Out".equalsIgnoreCase(status);
    }

    /** Request Additional Amenities: offered for a stay that exists (not a cancelled one or a finished one); the server's answer enables it or explains why not. */
    private void bindRequestAmenities() {
        View button = findViewById(R.id.btnRequestAmenitiesDetails);
        TextView reason = findViewById(R.id.tvRequestAmenitiesDetailsReason);
        boolean offered = offersAmenityRequests();
        if (!offered) {
            button.setVisibility(View.GONE);
            reason.setVisibility(View.GONE);
            return;
        }
        AdditionalAmenitiesEntry.bindWithEligibility(this, button, reason, booking, () -> {
            if (clickGuard.tryAcquire()) GroupStays.requestAmenities(this, booking); // several checked-in stays: pick the stay first
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

        // created date and the kind of transaction (the dates of the stay are in "Your Stay")
        TextView tvDates = findViewById(R.id.tvDetailsDates);
        String created = booking.getCreatedAtDisplay();
        tvDates.setText(created == null || created.trim().isEmpty() ? "" : getString(R.string.details_created_format, created));
        tvDates.setVisibility(created == null || created.trim().isEmpty() ? View.GONE : View.VISIBLE);
        TextView tvType = findViewById(R.id.tvDetailsRoomGuestCounts);
        tvType.setText(booking.isHasBooking() ? R.string.details_type_booking : R.string.details_type_reservation);
    }

    // ---- Booking Information section ----

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
        TransactionSections.guests(this, container, booking);
    }

    private void hideCardOf(int contentId) {
        View content = findViewById(contentId);
        if (content != null && content.getParent() != null && ((View) content.getParent()).getParent() instanceof View) {
            ((View) ((View) content.getParent()).getParent()).setVisibility(View.GONE);
        }
    }

    /** The picture of the receipt the guest uploaded, when there is one (tap to open it). */
    private void addReceiptImage(LinearLayout container) {
        if (booking.getReceiptUrl() == null || booking.getReceiptUrl().isEmpty()) return;
        ImageView receiptView = new ImageView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (180 * getResources().getDisplayMetrics().density));
        params.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
        receiptView.setLayoutParams(params);
        receiptView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        receiptView.setAdjustViewBounds(true);
        Glide.with(this).load(booking.getReceiptUrl()).into(receiptView);
        receiptView.setContentDescription(getString(R.string.receipt_image_content_description));
        receiptView.setOnClickListener(v -> ReceiptImageViewerActivity.start(this, booking.getReceiptUrl()));
        container.addView(receiptView);
    }

    // ---- Room Information section ----

    /**
     * The stays of the transaction, each with its rooms priced on ITS OWN nights (a room is paid for the nights of the stay it belongs to,
     * never the span of the whole transaction), then that stay's amenities by room, then its room charges; and last the room charges of
     * all the stays with the number of room types and rooms.
     */
    /** A small secondary line under a row (a room's assignment, a minor's age): text only, no value column. */
    private void addNote(LinearLayout container, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTextColor(getColor(R.color.velocity_text_secondary));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dpPx(6);
        tv.setLayoutParams(lp);
        container.addView(tv);
    }

    private void addSubheading(LinearLayout container, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        tv.setTextColor(getColor(R.color.velocity_text_primary));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dpPx(6);
        lp.bottomMargin = dpPx(4);
        tv.setLayoutParams(lp);
        container.addView(tv);
    }

    // ---- Payment Information section ----

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
        int chipFg = approved ? R.color.velocity_green_dark : rejected ? R.color.velocity_red_text : R.color.velocity_orange_primary;
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

    /**
     * The money of a stay that is in house or done, in three groups: Check-in Billing (room, discount taken once, the
     * check-in grand total and what was paid), Amenities & Charges (every approved amenity and desk charge - the
     * remaining balance, payable at check-out - with the requests that are NOT counted listed apart), and the Payment
     * Summary that closes it. Every figure is the server's (StayBill); nothing is recomputed on the phone.
     */
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
    /** Receipts get their own card, between Payment Information and the Timeline; hidden when there is nothing to show. */
    private void buildReceiptsCard() {
        LinearLayout content = findViewById(R.id.sectionReceiptsContent);
        buildReceiptActionCard(content);
        findViewById(R.id.cardReceiptsSection).setVisibility(content.getChildCount() == 0 ? View.GONE : View.VISIBLE);
    }

    private void buildReceiptActionCard(LinearLayout container) {
        if (!booking.getReceipts().isEmpty()) {
            ReceiptCardHelper.buildReceiptsSection(this, container, booking);
            return;
        }

        if (!ReceiptCardHelper.hasLegacyReceiptCandidate(booking)) return;

        View card = LayoutInflater.from(this).inflate(R.layout.item_payment_receipt_action, container, false);
        ReceiptCardHelper.bindLegacyReceiptActionCard(this, card, booking);
        container.addView(card);
    }

    // ---- Timeline ----

    /** Keys of the timeline entries already shown, so a quiet refresh can highlight only what is new. */
    private final java.util.Set<String> seenTimeline = new java.util.HashSet<>();

    /** The full, newest-first timeline (see {@link TransactionTimeline} for its sources). */
    private void buildTimelineSection(LinearLayout container) {
        TransactionTimelineView.render(this, container, TransactionTimeline.of(this, booking), seenTimeline);
    }

    /** The amenity request list arrived or changed: redraw just the timeline. */
    private final Runnable amenityRequestsListener = () -> {
        if (isFinishing() || isDestroyed() || booking == null) return;
        buildTimelineSection(findViewById(R.id.sectionTimelineContent));
    };

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

    /**
     * "Stays": ONE booking or reservation with several stays - each stay's dates and nights, its rooms (Room number once assigned) and
     * each room's amenities, then that stay's own subtotal. Every figure is the server's; the transaction's money breakdown follows below.
     * Nothing for a single-stay transaction (its rooms and amenities are shown in the sections that always existed).
     */
    /** "Oct 25 → Oct 26, 2026" from the server's yyyy-MM-dd dates; the raw text when it cannot be read. */
    private String formatStayDates(@Nullable String in, @Nullable String out) {
        return StayCardText.stayDates(in, out);
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
        tvLabel.setAllCaps(false);
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
