package com.example.velocitysuites;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only official payment receipt - the guest-facing counterpart to
 * BookingDetailsActivity's "Payment Receipt" action card, which is the only
 * place this Activity is ever launched from. Never editable (see the task's
 * "receipt immutability" requirement): every value here is read straight off
 * the Booking snapshot passed in, nothing is re-fetched or made interactive.
 *
 * Distinct from the guest's own uploaded GCash proof
 * (Booking#getReceiptUrl(), shown separately in BookingDetailsActivity as
 * "Receipt image") - this is the *official* Velocity Suites receipt, and per
 * this app's existing business rules it may only exist once
 * Booking#isStaffVerified() is true. That gate is re-checked here too (not
 * just at the launching button), so a stale Intent/back-stack re-entry can't
 * bypass it - though the real authoritative gate has to be server-side (see
 * PAYMENT_RECEIPT_BACKEND_SPEC.md), which this Android-only repo can't add.
 */
public class PaymentReceiptActivity extends AppCompatActivity {

    private static final String TAG = "PaymentReceiptActivity";
    private static final String EXTRA_BOOKING = "EXTRA_BOOKING";
    private static final String EXTRA_AUTO_DOWNLOAD = "EXTRA_AUTO_DOWNLOAD";
    /**
     * Phase 4 prep only (PAYMENT_RECEIPT_HISTORY_BACKEND_SPEC.md §17) - not
     * yet read in onCreate() below. Establishes the safe Intent-extra key a
     * future notification deep-link should use to open this Activity at a
     * SPECIFIC receipt (Partial/Full-Payment/Official) by its
     * receipt_number, once notifications carry one as structured data
     * rather than only inside the free-text message. Do not derive this by
     * parsing the visible notification message string - fragile, and the
     * backend doesn't guarantee any particular wording there.
     */
    public static final String EXTRA_RECEIPT_NUMBER = "EXTRA_RECEIPT_NUMBER";

    private final ClickGuard clickGuard = new ClickGuard();
    private Booking booking;
    private View receiptCard;
    /** Receipt-number mode only (see EXTRA_RECEIPT_NUMBER) - null in legacy Booking-snapshot mode. */
    @Nullable
    private String receiptNumber;
    /**
     * The currently-rendered receipt, if any - a plain instance field, never
     * cached anywhere static/shared (RoomRepository does not cache
     * ReceiptDetail either - see its getReceipt() doc). Each Activity
     * instance renders exactly one receipt for its own lifetime; opening a
     * different receipt_number always means a new Activity instance via a
     * new Intent, never this same instance being re-populated - so PR and OR
     * can never bleed into one another (see PAYMENT_RECEIPT_HISTORY_BACKEND_SPEC.md
     * §27).
     */
    @Nullable
    private ReceiptDetail receiptDetail;
    /** The View actually visible on screen right now (receiptCard in legacy mode, layoutDynamicReceiptContent in receipt-number mode) - what Download Receipt renders to PDF. */
    private View activeReceiptView;
    /**
     * Every sibling Booking/Reservation created together with this one as a
     * multi-room-type transaction (see BookingGroupState/BookingDetailsActivity's
     * identical field) - null if this transaction isn't grouped. The receipt is
     * launched from BookingDetailsActivity's action card, which only ever passes
     * this one anchor Booking - without resolving its siblings here too, the
     * receipt would silently show only one room type and one sibling's own
     * totals for a "Deluxe + Suite" transaction, disagreeing with the Booking
     * Details screen the guest just came from.
     */
    @Nullable
    private List<Booking> groupMembers;

    public static Intent newIntent(Context context, Booking booking, boolean autoDownload) {
        Intent intent = new Intent(context, PaymentReceiptActivity.class);
        intent.putExtra(EXTRA_BOOKING, booking);
        intent.putExtra(EXTRA_AUTO_DOWNLOAD, autoDownload);
        return intent;
    }

    /**
     * Used by the Receipts section (ReceiptCardHelper#buildReceiptsSection())
     * where a Booking is already in hand alongside the receipt_number - see
     * EXTRA_RECEIPT_NUMBER's own doc. onCreate() below checks
     * EXTRA_RECEIPT_NUMBER first regardless of which overload was used, so
     * the Booking extra this sets is simply unused once receipt-number mode
     * takes over.
     */
    public static Intent newIntentForReceipt(Context context, Booking booking, String receiptNumber) {
        Intent intent = newIntent(context, booking, false);
        intent.putExtra(EXTRA_RECEIPT_NUMBER, receiptNumber);
        return intent;
    }

    /**
     * Notification-deep-link variant - no Booking snapshot available/needed
     * (see NotificationDetailsActivity#bindPrimaryAction()'s structured-
     * receipt-number path): a bare receipt_number is enough to open the
     * exact PR/FR/OR this notification was about, fetched fresh from
     * GET /guest/receipts/{receiptNumber} exactly like the Booking-carrying
     * overload above.
     */
    public static Intent newIntentForReceipt(Context context, String receiptNumber) {
        Intent intent = new Intent(context, PaymentReceiptActivity.class);
        intent.putExtra(EXTRA_RECEIPT_NUMBER, receiptNumber);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_receipt);

        ImageButton btnBack = findViewById(R.id.btnReceiptBack);
        btnBack.setOnClickListener(v -> finish());

        // receipt-number mode takes priority whenever present - a caller that
        // supplies EXTRA_RECEIPT_NUMBER always gets the backend-authoritative
        // ReceiptDetail rendering (PARTIAL_RECEIPT/FULL_PAYMENT_RECEIPT/
        // OFFICIAL_RECEIPT), never the legacy Booking-snapshot path below,
        // even if EXTRA_BOOKING was also supplied (newIntentForReceipt()
        // includes both today, but the Booking extra is unused in this mode).
        // If the fetch itself fails (404/network/malformed response), this
        // shows the error state - it deliberately does NOT fall back to
        // rendering the passed-in Booking snapshot instead, since that would
        // be exactly the "manufacture a local receipt" the backend must stay
        // authoritative against (see PAYMENT_RECEIPT_HISTORY_BACKEND_SPEC.md
        // §25/§29).
        receiptNumber = getIntent().getStringExtra(EXTRA_RECEIPT_NUMBER);
        if (receiptNumber != null && !receiptNumber.trim().isEmpty()) {
            initReceiptNumberMode();
            return;
        }

        // ---- LEGACY MODE - unchanged from before Phase 4 ----
        booking = (Booking) getIntent().getSerializableExtra(EXTRA_BOOKING);
        if (booking == null || !booking.isStaffVerified() || !MoneyFormat.isPositive(TransactionStatusHelper.verifiedPaidOf(booking))) {
            Toast.makeText(this, R.string.receipt_pending_desc, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        receiptCard = findViewById(R.id.receiptCard);
        activeReceiptView = receiptCard;

        resolveGroupMembers();
        populateReceipt();

        MaterialButton btnDownload = findViewById(R.id.btnReceiptDownload);
        btnDownload.setOnClickListener(v -> {
            if (clickGuard.tryAcquire()) downloadReceiptAsPdf();
        });
        if (getIntent().getBooleanExtra(EXTRA_AUTO_DOWNLOAD, false)) {
            receiptCard.post(this::downloadReceiptAsPdf);
        }
    }

    // ==================== Receipt-number mode (Phase 4) ====================

    /**
     * DEBUG-ONLY preview hook (Phase 6B) - see
     * com.example.velocitysuites.debug.DebugReceiptPreviewActivity, which
     * lives entirely under src/debug and does not exist in a release
     * build. Keyed by receipt_number (not a single last-wins field) - a
     * single static field here caused a real bug found during physical-
     * device verification: tapping a Booking Details receipt card or a
     * notification's "Payment Receipt" button always re-rendered whichever
     * fixture had most recently been opened from the top-level debug menu,
     * silently ignoring the actual receipt_number being requested (e.g. an
     * FR notification opened PR). Looking this map up BY the exact
     * receiptNumber this Activity was actually asked to show, instead of
     * trusting one shared last-write-wins field, closes that gap - the
     * SAME "never guess/never show the wrong receipt" rule the production
     * navigation code follows for real. Never assigned from anywhere but
     * src/debug; guarded by BuildConfig.DEBUG below so a release build
     * (DEBUG=false) can never take this branch regardless.
     */
    public static final java.util.Map<String, ReceiptDetail> debugPreviewFixtures = new java.util.HashMap<>();

    /**
     * DEBUG-ONLY companion to {@link #debugPreviewFixtures}: the amenity lines a fixture
     * receipt's transaction "has", keyed by the same receipt_number. A ReceiptDetail carries
     * room lines but not amenity lines (see resolveAmenitiesThenRender()), so a fixture needs
     * its amenities supplied separately - a fixture without an entry here is previewed as a
     * transaction with no amenities, never one that would go to the network, and an entry
     * mapped to null previews the "amenities could not be resolved" case. Same
     * src/debug-only assignment and BuildConfig.DEBUG guard as debugPreviewFixtures.
     */
    public static final java.util.Map<String, List<BookingAmenity>> debugPreviewAmenities = new java.util.HashMap<>();

    private void initReceiptNumberMode() {
        findViewById(R.id.btnReceiptRetry).setOnClickListener(v -> {
            if (clickGuard.tryAcquire()) loadReceiptByNumber();
        });
        // Download is wired once a receipt actually loads (see renderReceiptDetail()) -
        // hidden until then so it can never be tapped against stale/absent data
        // (item 22 of the Phase 4 checklist).
        findViewById(R.id.btnReceiptDownload).setVisibility(View.GONE);
        loadReceiptByNumber();
    }

    /**
     * PURE LOOKUP by receipt_number (RoomRepository#getReceipt() -> GET
     * guest/receipts/{receiptNumber}) - never derives a receipt number from
     * visible text, payment percentage, or remaining balance, and never
     * fabricates a receipt locally on failure. A 404/network/malformed-
     * response failure always shows the error state below, never a stale or
     * substituted receipt - the backend remains the sole source of truth
     * for both the receipt's content and whether this guest may see it at
     * all (see PAYMENT_RECEIPT_HISTORY_BACKEND_SPEC.md §29).
     */
    private void loadReceiptByNumber() {
        if (BuildConfig.DEBUG && debugPreviewFixtures.containsKey(receiptNumber)) {
            ReceiptDetail fixture = debugPreviewFixtures.get(receiptNumber);
            receiptDetail = fixture;
            // No entry = "resolved, none"; an entry mapped to null = "could not be resolved".
            renderReceiptDetail(fixture, debugPreviewAmenities.containsKey(receiptNumber)
                    ? debugPreviewAmenities.get(receiptNumber)
                    : new ArrayList<>(), 0);
            return;
        }

        showLoadingState();
        RoomRepository.getInstance(this).getReceipt(receiptNumber, new RoomRepository.RepositoryCallback<ReceiptDetail>() {
            @Override
            public void onSuccess(ReceiptDetail result) {
                if (isFinishing() || isDestroyed()) return;
                receiptDetail = result;
                resolveAmenitiesThenRender(result);
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                showErrorState(message);
            }
        });
    }

    /**
     * The receipt payload (ReceiptService::buildReceiptPayload()) carries the transaction's
     * room lines but NOT its amenity lines, and the itemized Payment Summary needs them - so
     * they are taken from the transaction record the payload belongs to, in order of cost:
     * the Booking this screen was opened from (Booking Details' Receipts section passes it),
     * else the shared bookings cache, else a direct fetch of that one transaction (a
     * notification opened cold, or a transaction older than the loaded window - the receipt
     * screen's own loading spinner simply stays up a moment longer). The transaction is
     * matched by ReceiptCardHelper#isReceiptForBooking(), never by id alone.
     * <p>
     * A fetch that fails still renders the receipt - with the amenities marked UNKNOWN (see
     * ReceiptBreakdown.AmenityStatus), never as "No amenities selected": an unreachable server
     * is not the same claim as an empty amenity list, and the Grand Total is the backend's
     * either way. Amenity lines are transaction-level facts (what was ordered), not
     * per-payment ones, so reading them from the current transaction record does not disturb
     * the payload's own point-in-time payment figures (see renderReceiptDetail()).
     */
    private void resolveAmenitiesThenRender(ReceiptDetail detail) {
        Booking local = findLocalTransactionFor(detail);
        if (local != null) {
            renderReceiptDetail(detail, local.getAmenities(), local.getAmenityCharge());
            return;
        }

        boolean reservationDerived = detail.getReservationId() != null && !detail.getReservationId().trim().isEmpty();
        String transactionId = reservationDerived ? detail.getReservationId() : detail.getBookingId();
        RoomRepository.getInstance(this).fetchTransactionById(transactionId, reservationDerived,
                new RoomRepository.RepositoryCallback<Booking>() {
                    @Override
                    public void onSuccess(Booking fetched) {
                        if (isFinishing() || isDestroyed()) return;
                        renderReceiptDetail(detail, fetched.getAmenities(), fetched.getAmenityCharge());
                    }

                    @Override
                    public void onError(String message) {
                        if (isFinishing() || isDestroyed()) return;
                        renderReceiptDetail(detail, null, 0);
                    }
                });
    }

    @Nullable
    private Booking findLocalTransactionFor(ReceiptDetail detail) {
        Booking passedIn = (Booking) getIntent().getSerializableExtra(EXTRA_BOOKING);
        if (passedIn != null && ReceiptCardHelper.isReceiptForBooking(detail, passedIn)) {
            return passedIn;
        }
        for (Booking cached : RoomRepository.getInstance(this).getAllBookings()) {
            if (ReceiptCardHelper.isReceiptForBooking(detail, cached)) {
                return cached;
            }
        }
        return null;
    }

    private void showLoadingState() {
        findViewById(R.id.layoutReceiptLoading).setVisibility(View.VISIBLE);
        findViewById(R.id.layoutReceiptError).setVisibility(View.GONE);
        findViewById(R.id.receiptScrollView).setVisibility(View.GONE);
    }

    private void showErrorState(String message) {
        findViewById(R.id.layoutReceiptLoading).setVisibility(View.GONE);
        findViewById(R.id.receiptScrollView).setVisibility(View.GONE);
        View errorLayout = findViewById(R.id.layoutReceiptError);
        errorLayout.setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.tvReceiptErrorMessage)).setText(
                message != null && !message.trim().isEmpty() ? message : getString(R.string.receipt_load_failed_desc));
    }

    private void showContentState() {
        findViewById(R.id.layoutReceiptLoading).setVisibility(View.GONE);
        findViewById(R.id.layoutReceiptError).setVisibility(View.GONE);
        findViewById(R.id.receiptScrollView).setVisibility(View.VISIBLE);
    }

    /**
     * Renders a fetched receipt entirely from ReceiptDetail - never
     * PaymentStatusResolver, Booking#getAmountPaid()/getPaymentHistory(), or
     * any other legacy client-side calculation (see
     * PAYMENT_RECEIPT_HISTORY_BACKEND_SPEC.md §30). For a PARTIAL_RECEIPT/
     * FULL_PAYMENT_RECEIPT, detail.getPaymentSummary()/getPaymentTransactions()
     * are already the frozen point-in-time snapshot the backend computed
     * (see ReceiptDetail's own doc) - displayed exactly as received, never
     * recomputed or replaced with a separately-cached Booking's current
     * live totals. The only thing NOT taken from the payload is the amenity
     * lines (it carries none) - see resolveAmenitiesThenRender(); {@code amenities}
     * null means they could not be resolved, an empty list means there are none, and
     * {@code aggregateAmenityCharge} is the transaction record's single amenity total,
     * which keeps an amenity charge on record from ever reading as "no amenities".
     */
    private void renderReceiptDetail(ReceiptDetail detail, @Nullable List<BookingAmenity> amenities, double aggregateAmenityCharge) {
        receiptCard = findViewById(R.id.receiptCard);
        receiptCard.setVisibility(View.GONE);

        ViewGroup content = findViewById(R.id.layoutDynamicReceiptContent);
        content.removeAllViews();
        content.setVisibility(View.VISIBLE);
        activeReceiptView = content;

        String typeLabel = ReceiptTypeMapper.labelForReceiptType(detail.getReceiptType());
        ((TextView) findViewById(R.id.tvReceiptHeaderTitle)).setText(typeLabel);

        buildHeaderSection(content, detail, typeLabel);
        buildStaySection(content, detail);
        buildGuestSection(content, detail);
        buildPaymentSummarySection(content, detail, amenities, aggregateAmenityCharge);
        buildTransactionHistorySection(content, detail);

        MaterialButton btnDownload = findViewById(R.id.btnReceiptDownload);
        btnDownload.setVisibility(View.VISIBLE);
        btnDownload.setOnClickListener(v -> {
            if (!clickGuard.tryAcquire()) return;
            downloadReceiptAsPdf(activeReceiptView,
                    detail.getReceiptNumber() != null ? detail.getReceiptNumber() : detail.getBookingId());
        });

        showContentState();
    }

    /** Receipt Header + Receipt Status - see item 5 of the Phase 4 checklist. */
    private void buildHeaderSection(ViewGroup parent, ReceiptDetail detail, String typeLabel) {
        LinearLayout content = newSectionCard(parent, null);

        ImageView logo = new ImageView(this);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(56), dp(56));
        logoParams.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        logo.setLayoutParams(logoParams);
        logo.setImageResource(R.drawable.velocity_suites_logo);
        logo.setContentDescription(getString(R.string.app_name));
        content.addView(logo);

        content.addView(centeredText(getString(R.string.app_name), 16, true, R.color.velocity_red_primary, dp(6)));
        content.addView(centeredText(getString(R.string.welcome_tagline), 11, false, R.color.velocity_text_secondary, dp(2)));
        content.addView(centeredText(typeLabel, 15, true, R.color.velocity_text_primary, dp(14)));

        // The status badge is the SAME status Transaction History shows for this transaction
        // (TransactionStatusHelper, the receptionist-verified amount vs the grand total), applied to the
        // figures this receipt prints: a Partial Receipt reads "Partially Paid" right above its remaining
        // balance, a Full/Official Receipt reads "Paid" - never a fixed "Verified".
        TransactionStatusHelper.Summary statusSummary = TransactionStatusHelper.summarize(detail);
        content.addView(newStatusBadge(statusSummary.status));

        addDivider(content, dp(16));
        addRowOrDash(content, getString(R.string.receipt_reference_label), detail.getReceiptNumber());
        // detail.getIssuedAt() is already a display-formatted string (or "")
        // - ApiMapper.toReceiptDetail() reformats the raw ISO issued_at via
        // reformatDateTime() before it ever reaches this model. Re-running it
        // through TimeUtils.formatDateTime() here (the previous code) fed an
        // already-formatted string back in as if it were a raw timestamp,
        // which TimeUtils can't parse - it silently fell back to its own
        // literal "N/A" on every single receipt, real date or not. addRow()
        // already hides the row for a null/empty value, so no ternary is
        // needed here.
        addRowOrDash(content, getString(R.string.receipt_issued_date_label), detail.getIssuedAt());
    }

    /** The centered status pill of the receipt header - text, colors and icon all from the shared status style. */
    private TextView newStatusBadge(TransactionStatusHelper.Status status) {
        TextView badge = new TextView(this);
        badge.setBackgroundResource(R.drawable.shape_intro_pill);
        badge.setPadding(dp(14), dp(6), dp(14), dp(6));
        badge.setGravity(android.view.Gravity.CENTER_VERTICAL);
        badge.setCompoundDrawablePadding(dp(6));
        badge.setAllCaps(true);
        badge.setMaxLines(1);
        badge.setTextSize(12);
        badge.setLetterSpacing(0.04f);
        badge.setTypeface(badge.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        params.topMargin = dp(8);
        badge.setLayoutParams(params);
        StatusBadges.bind(badge, status);
        return badge;
    }

    /**
     * Stay/Booking Information. The transaction's id(s), room type, stay dates and nights are always shown -
     * a value the payload didn't carry reads "—", never a blank or "null". Rows that simply don't apply (the
     * original reservation of a direct booking, an unassigned room number) are omitted.
     */
    private void buildStaySection(ViewGroup parent, ReceiptDetail detail) {
        LinearLayout content = newSectionCard(parent, getString(R.string.receipt_booking_information_title));
        // Reservation-derived receipts name the original reservation (the id guests know the transaction by);
        // a direct booking has none.
        addRow(content, getString(R.string.receipt_original_reservation_id_label), detail.getReservationId());
        addRowOrDash(content, getString(R.string.receipt_label_booking_id), detail.getBookingId());
        addRowOrDash(content, getString(R.string.details_label_room_type), formatRoomLinesValue(detail));
        addRowOrDash(content, getString(R.string.receipt_room_rate_label), formatRateValue(detail));
        addRowOrDash(content, getString(R.string.details_label_check_in), detail.getCheckIn());
        addRowOrDash(content, getString(R.string.details_label_check_out), detail.getCheckOut());
        if (detail.getStay() != null) {
            for (StayInfo.Row row : detail.getStay().rows(this)) {
                // The booked dates are already the Check-In / Check-Out rows just above.
                if (row.label.equals(getString(R.string.stay_scheduled_check_in))
                        || row.label.equals(getString(R.string.stay_scheduled_check_out))) continue;
                addRowOrDash(content, row.label, row.value);
            }
        } else {
            addRowOrDash(content, getString(R.string.receipt_number_of_nights_label),
                    detail.getNumberOfNights() > 0 ? String.valueOf(detail.getNumberOfNights()) : null);
        }
        if (!detail.getAssignedRoomNumbers().isEmpty()) {
            addRow(content, getString(R.string.details_label_room_number),
                    android.text.TextUtils.join(", ", detail.getAssignedRoomNumbers()));
        }
        int totalGuests = detail.getAdults() + detail.getChildren();
        addRow(content, getString(R.string.details_label_total_guests), totalGuests > 0 ? String.valueOf(totalGuests) : null);
    }

    /** "₱2,500.00 / night" for one room type; "Varies by room type" for several; null (shown as "—") when the payload has no room lines to read a rate from. */
    @Nullable
    private String formatRateValue(ReceiptDetail detail) {
        List<BookingRoom> lines = detail.getRoomLines();
        if (lines.isEmpty()) return null;
        if (lines.size() > 1) return getString(R.string.receipt_rate_varies);
        ReceiptBreakdown.RoomLine line = ReceiptBreakdown.roomLineOf(lines.get(0), Math.max(1, detail.getNumberOfNights()));
        return getString(R.string.receipt_rate_per_night_format, formatPrice(line.ratePerNight));
    }

    private String formatRoomLinesValue(ReceiptDetail detail) {
        List<BookingRoom> lines = detail.getRoomLines();
        if (!lines.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (BookingRoom room : lines) {
                if (room == null || room.getRoomTypeName() == null || room.getRoomTypeName().trim().isEmpty()) continue;
                if (sb.length() > 0) sb.append('\n');
                sb.append(room.getRoomTypeName().trim()).append(" ×").append(Math.max(1, room.getQuantity()));
            }
            if (sb.length() > 0) return sb.toString();
        }
        return detail.getRoomType();
    }

    /** Guest Information - item 6. */
    private void buildGuestSection(ViewGroup parent, ReceiptDetail detail) {
        LinearLayout content = newSectionCard(parent, getString(R.string.receipt_guest_information_title));
        addRowOrDash(content, getString(R.string.receipt_guest_account_name_label), detail.getGuestAccountName());
        addRow(content, getString(R.string.details_label_representative_name), detail.getRepresentativeName());
    }

    /**
     * Payment Summary - items 8/9/10, now itemized: every selected room (rate per
     * night, nights, subtotal) with the Rooms Total, every selected amenity (quantity,
     * unit price, subtotal) with the Amenities Total - or "No amenities selected" - the
     * charges/deductions, and the Grand Total LAST, which by construction equals the sum of
     * the lines above it (see ReceiptBreakdown/ReceiptSummaryRenderer). The Grand Total
     * itself is still exclusively detail.getPaymentSummary().grandTotal (never a
     * separately fetched/cached Booking's): the payload's point-in-time payment figures stay
     * authoritative, and any part of that total the itemized lines don't explain is shown as
     * "Other Charges &amp; Adjustments" rather than silently absorbed. This system has no
     * tax/service-charge concept anywhere (Billing/Payment models both confirmed to have no
     * such column) - there is deliberately no "taxes and fees" row, since fabricating one
     * would show data that was never actually collected.
     * <p>
     * The payment-PROGRESS lines that used to trail this card (percentage, amount paid,
     * balance, status, verifier) moved to their own card just below - they describe what
     * has been paid against the Grand Total, not how it is made up, and keeping them out
     * lets the Grand Total genuinely close the summary.
     */
    private void buildPaymentSummarySection(ViewGroup parent, ReceiptDetail detail, @Nullable List<BookingAmenity> amenities,
                                            double aggregateAmenityCharge) {
        LinearLayout content = newSectionCard(parent, getString(R.string.receipt_payment_summary_title));
        Booking.PaymentSummary summary = detail.getPaymentSummary();
        if (summary == null) {
            // The payload carried no payment summary: say so field by field ("—") instead of leaving an empty card.
            addRowOrDash(content, getString(R.string.receipt_grand_total_label), null);
            addRowOrDash(content, getString(R.string.details_label_amount_paid), null);
            addRowOrDash(content, getString(R.string.details_label_remaining_balance), null);
            return;
        }

        ReceiptSummaryRenderer.render(this, content, ReceiptBreakdown.forReceiptDetail(detail, amenities, aggregateAmenityCharge));
        buildPaymentDetailsSection(parent, detail, summary);
    }

    /**
     * Payment progress - detail.isAnchoredOnSinglePayment() distinguishes a
     * PARTIAL_RECEIPT/FULL_PAYMENT_RECEIPT (a frozen point-in-time snapshot, anchored on one
     * specific payment) from an OFFICIAL_RECEIPT (the live final checkout totals) - both
     * cases read exclusively from detail.getPaymentSummary(), never a separately
     * fetched/cached Booking.
     */
    private void buildPaymentDetailsSection(ViewGroup parent, ReceiptDetail detail, Booking.PaymentSummary summary) {
        LinearLayout content = newSectionCard(parent, getString(R.string.receipt_payment_information_title));
        TransactionStatusHelper.Summary statusSummary = TransactionStatusHelper.summarize(detail);
        String statusLabel = getString(TransactionStatusHelper.styleFor(statusSummary.status).labelRes);
        Booking.PaymentTransactionRecord paymentRecord = anchorTransactionOf(detail);

        if (summary.paymentPercentage != null) {
            addRow(content, getString(R.string.receipt_payment_percentage_label),
                    PaymentPercentageUtil.formatApiPercentageForDisplay(summary.paymentPercentage));
        }

        if (detail.isAnchoredOnSinglePayment() && detail.getAnchorPayment() != null) {
            // PARTIAL_RECEIPT/FULL_PAYMENT_RECEIPT - the frozen snapshot.
            ReceiptDetail.AnchorPayment anchor = detail.getAnchorPayment();
            addRowOrDash(content, getString(R.string.receipt_amount_paid_this_transaction_label), formatPrice(anchor.amountPaid));
            addRowOrDash(content, getString(R.string.receipt_total_paid_at_this_point_label), formatPrice(summary.totalAmountPaid));
            addRowOrDash(content, getString(R.string.receipt_remaining_balance_at_this_point_label), formatPrice(summary.remainingBalance));
            addRowOrDash(content, getString(R.string.receipt_payment_status_label), statusLabel);
            addPaymentMethodDateTime(content, anchor.paymentMethod, paymentRecord);
            // Only ever present for a PARTIAL_RECEIPT/FULL_PAYMENT_RECEIPT - an OFFICIAL_RECEIPT's
            // checkout-recorded payment has no separate staff-verification step at all (backend's own
            // transactionType() doc), so anchor_payment (and therefore this field) is genuinely null there.
            addRow(content, getString(R.string.receipt_verified_by_label), anchor.verifiedBy);
            addRow(content, getString(R.string.receipt_verified_at_label),
                    anchor.verifiedAt != null ? TimeUtils.formatDateTime(anchor.verifiedAt) : null);
        } else {
            // OFFICIAL_RECEIPT - final settlement; Total Amount Paid made prominent below.
            addRowOrDash(content, getString(R.string.details_label_remaining_balance), formatPrice(summary.remainingBalance));
            addRowOrDash(content, getString(R.string.receipt_payment_status_label), statusLabel);
            addPaymentMethodDateTime(content, paymentRecord != null ? paymentRecord.paymentMethod : null, paymentRecord);
            addDivider(content, dp(12));
            addProminentTotal(content, getString(R.string.receipt_total_amount_paid_label), formatPrice(summary.totalAmountPaid));
        }
    }

    /** The payment transaction this receipt is about: the one that carries its own receipt number, else the most recent. */
    @Nullable
    private Booking.PaymentTransactionRecord anchorTransactionOf(ReceiptDetail detail) {
        List<Booking.PaymentTransactionRecord> transactions = detail.getPaymentTransactions();
        if (transactions.isEmpty()) return null;
        String number = detail.getReceiptNumber();
        if (number != null) {
            for (Booking.PaymentTransactionRecord tx : transactions) {
                if (number.equals(tx.receiptNumber)) return tx;
            }
        }
        return transactions.get(transactions.size() - 1);
    }

    /** Payment Method, Payment Date and Payment Time - each "—" when the payload can't tell (never a blank or "N/A"). */
    private void addPaymentMethodDateTime(ViewGroup content, @Nullable String method, @Nullable Booking.PaymentTransactionRecord record) {
        String methodLabel = method == null || method.trim().isEmpty() ? null
                : "cash".equalsIgnoreCase(method.trim()) ? getString(R.string.payment_method_cash)
                : "gcash".equalsIgnoreCase(method.trim()) ? getString(R.string.payment_method_gcash)
                : method.trim();
        addRowOrDash(content, getString(R.string.details_label_payment_method), methodLabel);

        String rawDate = record != null ? record.paymentDate : null;
        String date = rawDate == null ? null : TimeUtils.formatDate(rawDate);
        String time = rawDate == null ? null : TimeUtils.formatTime(rawDate);
        addRowOrDash(content, getString(R.string.details_label_payment_date), "N/A".equals(date) ? null : date);
        addRowOrDash(content, getString(R.string.details_label_payment_time), "N/A".equals(time) ? null : time);
    }

    /**
     * Payment Transaction History - items 11-15. Reads EXCLUSIVELY from
     * detail.getPaymentTransactions() - for a PARTIAL_RECEIPT/
     * FULL_PAYMENT_RECEIPT this is already trimmed by the backend to "the
     * history as it stood at that point" (see ReceiptDetail's own doc); for
     * an OFFICIAL_RECEIPT it's the complete history. Never falls back to
     * Booking.paymentHistory/PaymentTransaction - those would show the
     * booking's CURRENT full history regardless of which receipt is being
     * viewed, exactly the bug PAYMENT_RECEIPT_HISTORY_BACKEND_SPEC.md §12
     * warns against.
     */
    private void buildTransactionHistorySection(ViewGroup parent, ReceiptDetail detail) {
        LinearLayout content = newSectionCard(parent, getString(R.string.receipt_payment_transaction_history_title));
        List<Booking.PaymentTransactionRecord> transactions = detail.getPaymentTransactions();

        if (transactions.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.receipt_no_transactions_desc);
            empty.setTextColor(getColor(R.color.velocity_text_secondary));
            empty.setTextSize(12);
            content.addView(empty);
            return;
        }

        for (int i = 0; i < transactions.size(); i++) {
            View row = ReceiptCardHelper.buildTransactionRow(this, content, transactions.get(i), i == transactions.size() - 1);
            content.addView(row);
        }
    }

    // ---- Small dynamic-UI builder helpers (receipt-number mode only) ----

    /** New rounded card appended to parent; returns its inner content LinearLayout for callers to add rows into. Null title omits the section-title TextView (used for the header card, which has its own bespoke title treatment). */
    private LinearLayout newSectionCard(ViewGroup parent, @Nullable String title) {
        com.google.android.material.card.MaterialCardView card = new com.google.android.material.card.MaterialCardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(14);
        card.setLayoutParams(cardParams);
        card.setRadius(dp(16));
        card.setCardElevation(0);
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(getColor(R.color.velocity_red_soft));
        card.setCardBackgroundColor(getColor(R.color.velocity_surface_elevated));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(18));

        if (title != null) {
            TextView titleView = new TextView(this);
            titleView.setText(title);
            titleView.setTextColor(getColor(R.color.velocity_red_primary));
            titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
            titleView.setTextSize(12);
            titleView.setLetterSpacing(0.04f);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            titleParams.bottomMargin = dp(10);
            titleView.setLayoutParams(titleParams);
            content.addView(titleView);
        }

        card.addView(content);
        parent.addView(card);
        return content;
    }

    /** Reuses item_receipt_row.xml (the same label/value row template the legacy card already uses) - hides itself when value is null/empty, matching bindRow()'s established convention. */
    private void addRow(ViewGroup parent, String label, @Nullable String value) {
        if (value == null || value.trim().isEmpty()) return;
        View row = getLayoutInflater().inflate(R.layout.item_receipt_row, parent, false);
        ((TextView) row.findViewById(R.id.tvRowLabel)).setText(label);
        ((TextView) row.findViewById(R.id.tvRowValue)).setText(value);
        parent.addView(row);
    }

    /** A row for a field the receipt is expected to show: a missing value reads "—" instead of the row vanishing or printing "null". */
    private void addRowOrDash(ViewGroup parent, String label, @Nullable String value) {
        addRow(parent, label, value == null || value.trim().isEmpty() ? getString(R.string.value_missing) : value);
    }

    private void addDivider(ViewGroup parent, int topMarginPx) {
        View divider = new View(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        params.topMargin = topMarginPx;
        params.bottomMargin = dp(10);
        divider.setLayoutParams(params);
        divider.setBackgroundColor(getColor(R.color.velocity_divider_hairline));
        parent.addView(divider);
    }

    /** The visually prominent "TOTAL AMOUNT PAID" panel for an Official Receipt - item 10. */
    private void addProminentTotal(ViewGroup parent, String label, String value) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(android.view.Gravity.CENTER);
        panel.setBackgroundResource(R.drawable.bg_receipt_total_panel);
        panel.setPadding(0, dp(16), 0, dp(16));

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setAllCaps(true);
        labelView.setGravity(android.view.Gravity.CENTER);
        labelView.setTextColor(getColor(R.color.velocity_text_secondary));
        labelView.setTextSize(11);
        labelView.setLetterSpacing(0.04f);
        panel.addView(labelView);

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setGravity(android.view.Gravity.CENTER);
        valueView.setTypeface(valueView.getTypeface(), android.graphics.Typeface.BOLD);
        valueView.setTextColor(getColor(R.color.velocity_red_primary));
        // One step below the Grand Total's 22sp panel (item_receipt_grand_total.xml) - this is
        // what was PAID, the Grand Total is what is OWED, and the outlined Grand Total panel
        // should read as the primary figure on the page.
        valueView.setTextSize(20);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valueParams.topMargin = dp(4);
        valueView.setLayoutParams(valueParams);
        panel.addView(valueView);

        parent.addView(panel);
    }

    private TextView centeredText(String text, float sizeSp, boolean bold, int colorRes, int topMarginPx) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setGravity(android.view.Gravity.CENTER);
        view.setTextColor(getColor(colorRes));
        view.setTextSize(sizeSp);
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = topMarginPx;
        view.setLayoutParams(params);
        return view;
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    /** Populates {@link #groupMembers} (this record included) if it belongs to a BookingGroupState group, else leaves it null - same logic as BookingDetailsActivity#resolveGroupMembers(). */
    private void resolveGroupMembers() {
        String groupRef = BookingGroupState.getGroupRef(this, booking.getId());
        if (groupRef == null) {
            groupMembers = null;
            return;
        }
        List<String> memberIds = BookingGroupState.getGroupMembers(this, booking.getId());
        List<Booking> allBookings = RoomRepository.getInstance(this).getAllBookings();
        List<Booking> resolved = new ArrayList<>();
        for (String memberId : memberIds) {
            for (Booking candidate : allBookings) {
                if (candidate.getId().equals(memberId)) {
                    resolved.add(candidate);
                    break;
                }
            }
        }
        groupMembers = resolved;
    }

    private void populateReceipt() {
        boolean gcash = "gcash".equalsIgnoreCase(booking.getPaymentMethod());

        // Grouped multi-room-type transaction (see resolveGroupMembers()) - every
        // amount below must be summed across every sibling, exactly mirroring
        // BookingDetailsActivity#buildPaymentSummarySection(), so this receipt can
        // never show a different Grand Total/Amount Paid than the Booking Details
        // screen it was launched from. Amenities are attached to exactly one
        // sibling at creation time (PaymentActivity#submitPendingReservationGroups()'s
        // index==0 rule), so summing every sibling's own amenityCharge picks that
        // one up correctly regardless of position. Adults/children/guests are
        // deliberately NOT summed here (see BookingDetailsActivity's identical
        // fix) - every sibling was created from the same whole-party
        // pendingWizardState.adults/children, so this one anchor record's own
        // value is already correct.
        double totalAmount;
        double amountPaid;
        double roomCharge;
        double amenityCharge;
        double additionalGuestFee;
        if (groupMembers != null && !groupMembers.isEmpty()) {
            BookingGroupAggregator.Totals totals = BookingGroupAggregator.sum(groupMembers);
            totalAmount = totals.totalAmount;
            // Receptionist-verified money only (see TransactionStatusHelper) - a direct booking's own
            // amountPaid also counts payments still awaiting verification.
            amountPaid = 0;
            for (Booking member : groupMembers) amountPaid += TransactionStatusHelper.verifiedPaidOf(member);
            roomCharge = totals.roomCharge;
            amenityCharge = totals.amenityCharge;
            additionalGuestFee = totals.additionalGuestFee;
        } else {
            totalAmount = TransactionStatusHelper.grandTotalOf(booking);
            amountPaid = TransactionStatusHelper.verifiedPaidOf(booking);
            roomCharge = booking.getRoomCharge();
            amenityCharge = booking.getAmenityCharge();
            additionalGuestFee = booking.getAdditionalGuestFee();
        }
        double remainingBalance = Math.max(0, totalAmount - amountPaid);
        // The status badge: the same rule Transaction History applies, on this receipt's own figures.
        TransactionStatusHelper.Status status = TransactionStatusHelper.resolve(booking.getStatus(), false, totalAmount, amountPaid);
        StatusBadges.bind(findViewById(R.id.tvReceiptStatusBadge), status);
        // paymentId is the system-generated internal payment id (Payment
        // Reference) - never to be confused with the guest-entered GCash
        // reference number bound to rowGcashReference below, which is a
        // completely separate value. This line previously repeated the
        // "Payment Reference" label a second time inside the value itself
        // (the left-hand label TextView right next to it already says
        // "Payment Reference"), rendering as the redundant "Payment
        // Reference   Payment Reference: 235" - now just "Ref: 235".
        String paymentId = booking.getLatestPaymentId();
        ((TextView) findViewById(R.id.tvReceiptReference)).setText(
                getString(R.string.receipt_reference_value_prefix) + " " + (paymentId != null ? paymentId : booking.getId()));
        // Receipt date for the header: when staff verified the payment (the moment this
        // receipt became available) if on record, else the payment's own date - a legacy
        // Booking snapshot carries no separate "issued at" the way a fetched receipt does.
        // Hidden when neither exists, never a placeholder.
        String receiptDate = booking.getPaymentVerifiedAtDisplay() != null && !booking.getPaymentVerifiedAtDisplay().trim().isEmpty()
                ? booking.getPaymentVerifiedAtDisplay()
                : booking.getPaymentDateOnly();
        bindRow(R.id.rowReceiptDate, getString(R.string.receipt_issued_date_label), receiptDate);

        // --- Guest Information ---
        // Guest Account Name = the authenticated account holder, not this specific
        // transaction's stay guest - same "userName"/"userEmail" SharedPreferences
        // keys DashboardActivity's own account summary already reads from (see
        // VelocityPrefs; kept in sync with the profile's first/middle/last fields
        // by both ProfileManagementActivity save paths). Representative Name below
        // is the separate, transaction-specific guest entered during creation and
        // may legitimately be someone else - each has its own distinct label so the
        // two are never confused for one another.
        android.content.SharedPreferences prefs = getSharedPreferences("VelocityPrefs", MODE_PRIVATE);
        bindRowOrDash(R.id.rowGuestName, getString(R.string.receipt_guest_account_name_label),
                prefs.getString("userName", null));
        bindRow(R.id.rowRepresentativeName, getString(R.string.details_label_representative_name), booking.getRepresentativeName());
        bindRow(R.id.rowGuestEmail, getString(R.string.receipt_guest_email_label),
                prefs.getString("userEmail", null));
        bindRow(R.id.rowGuestMobile, getString(R.string.mobile_label), prefs.getString("userMobile", null));

        // --- Booking Information ---
        bindRowOrDash(R.id.rowBookingId,
                getString(booking.isHasBooking() ? R.string.receipt_label_booking_id : R.string.receipt_label_reservation_id),
                booking.getId());
        // Itemized multi-room-type breakdown (Booking#getRooms()) only ever
        // populates once the backend ships the contract in
        // MULTI_ROOM_TRANSACTION_BACKEND_SPEC.md - empty on every
        // transaction today, so this falls back to the existing single Room
        // Type value below, which remains correct either way. The value
        // TextView already wraps rather than truncating (item_receipt_row.xml
        // has no maxLines/ellipsize), so a multi-line list is fully readable.
        bindRowOrDash(R.id.rowRoomType, getString(R.string.details_label_room_type), formatRoomTypeValue());
        // Only renders once a room is actually assigned at check-in - null/hidden
        // beforehand, never fabricated (Booking#getRoomNumber()).
        bindRow(R.id.rowRoomNumber, getString(R.string.details_label_room_number), booking.getRoomNumber());
        bindRowOrDash(R.id.rowCheckIn, getString(R.string.details_label_check_in), booking.getCheckInDate());
        bindRowOrDash(R.id.rowCheckOut, getString(R.string.details_label_check_out), booking.getCheckOutDate());
        bindRowOrDash(R.id.rowNights, getString(R.string.receipt_number_of_nights_label), computeNights(booking.getCheckInDate(), booking.getCheckOutDate()));
        bindRow(R.id.rowAdults, getString(R.string.details_label_adults), booking.getAdults() > 0 ? String.valueOf(booking.getAdults()) : null);
        bindRow(R.id.rowChildren, getString(R.string.details_label_children), booking.getChildren() > 0 ? String.valueOf(booking.getChildren()) : null);
        bindRow(R.id.rowTotalGuests, getString(R.string.details_label_total_guests), String.valueOf(booking.getGuests()));

        // --- Transaction Information ---
        boolean fullyPaid = status == TransactionStatusHelper.Status.PAID;
        bindRowOrDash(R.id.rowPaymentMethod, getString(R.string.details_label_payment_method),
                getString(gcash ? R.string.payment_method_gcash : R.string.payment_method_cash));
        bindGcashMobileRow(gcash);
        bindGcashReferenceRow(gcash);
        // Percentage is the deposit/full-payment percentage chosen at the time of
        // the latest submitted payment (Booking#getSelectedPaymentPercentage()'s
        // own doc) - shown regardless of fullyPaid, so a Full Payment (100%)
        // transaction still shows "Payment Percentage: 100%" instead of the row
        // being hidden entirely (the old `!fullyPaid` gate here hid it for every
        // Full Payment, since that always leaves remainingBalance at 0).
        bindRow(R.id.rowPaymentType, getString(R.string.details_label_payment_type),
                getString(fullyPaid ? R.string.review_payment_type_full : R.string.review_payment_type_partial));
        // selectedPercentage is already a whole percentage value (20/30/40/50/100 -
        // see Reservation::selected_payment_percentage, set directly from
        // Api\PaymentController::store()'s $selectedPercentage, never a 0.20-1.00
        // fraction) - do NOT multiply by 100 again here (that was this screen's
        // own bug: 50 x 100 = "5000%"). BookingDetailsActivity#formatPercentage()
        // already renders this same field correctly, unmultiplied - kept
        // consistent with it here.
        Double selectedPercentage = booking.getSelectedPaymentPercentage();
        bindRow(R.id.rowPaymentPercentage, getString(R.string.receipt_payment_percentage_label),
                selectedPercentage != null ? PaymentPercentageUtil.formatApiPercentageForDisplay(selectedPercentage) : null);
        // The shared payment status (Paid / Partially Paid ...) - the same word Transaction History shows.
        bindRow(R.id.rowPaymentStatus, getString(R.string.receipt_payment_status_label),
                getString(TransactionStatusHelper.styleFor(status).labelRes));
        bindRowOrDash(R.id.rowPaymentDate, getString(R.string.details_label_payment_date), booking.getPaymentDateOnly());
        bindRowOrDash(R.id.rowPaymentTime, getString(R.string.details_label_payment_time), booking.getPaymentTimeOnly());

        // --- Payment Summary ---
        TextView paymentTypeBadge = findViewById(R.id.tvPaymentTypeBadge);
        paymentTypeBadge.setText(fullyPaid ? R.string.receipt_payment_type_full : R.string.receipt_payment_type_partial);

        // Itemized Selected Rooms/Amenities/charges -> Grand Total, from the Booking's own
        // itemized lines and Billing split (Booking#getRooms()/getAmenities()/getRoomCharge()
        // etc.) via the same ReceiptBreakdown/ReceiptSummaryRenderer receipt-number mode
        // uses. totalAmount/amenityCharge/additionalGuestFee are the group-aggregated
        // figures resolved above; the discount is only ever populated for a reservation-
        // derived transaction (see Booking#getDiscountAmount()'s own doc) - a direct
        // Booking has no discount_preview source at all, so it correctly stays 0/hidden
        // for that path rather than showing a fabricated value.
        long stayNights = StayDateCalculator.nightsBetween(booking.getCheckInDate(), booking.getCheckOutDate());
        ReceiptSummaryRenderer.render(this, (ViewGroup) findViewById(R.id.layoutPaymentSummaryBreakdown),
                ReceiptBreakdown.forLegacyBooking(booking, groupMembers, stayNights, totalAmount, amenityCharge, additionalGuestFee));

        // Payment PROGRESS - moved out of the Payment Summary (see the layout) so the Grand Total closes it.
        bindRow(R.id.rowAmountPaid, getString(R.string.details_label_amount_paid), formatPrice(amountPaid));
        // Always shown - a settled receipt reads "₱0.00" remaining rather than the row vanishing.
        bindRowOrDash(R.id.rowRemainingBalance, getString(R.string.details_label_remaining_balance), formatPrice(remainingBalance));
        emphasizeRowValue(R.id.rowAmountPaid);

        // --- Payment Verification Information ---
        // No "verified by staff name" field exists on this Android-side model yet
        // (see PAYMENT_RECEIPT_BACKEND_SPEC.md) - bindRow hides the row rather than
        // showing a fabricated name.
        bindRow(R.id.rowVerifiedBy, getString(R.string.receipt_verified_by_label), null);
        bindRow(R.id.rowVerifiedAt, getString(R.string.receipt_verified_at_label), booking.getPaymentVerifiedAtDisplay());

        ((TextView) findViewById(R.id.tvTotalAmountPaid)).setText(formatPrice(amountPaid));
    }

    /** Like bindRow(), but a missing value reads "—" instead of the row disappearing - for the fields a receipt is expected to show. */
    private void bindRowOrDash(int includeId, String label, @Nullable String value) {
        bindRow(includeId, label, value == null || value.trim().isEmpty() ? getString(R.string.value_missing) : value);
    }

    private void bindRow(int includeId, String label, @Nullable String value) {
        View row = findViewById(includeId);
        if (row == null) return;
        if (value == null || value.trim().isEmpty()) {
            row.setVisibility(View.GONE);
            return;
        }
        ((TextView) row.findViewById(R.id.tvRowLabel)).setText(label);
        ((TextView) row.findViewById(R.id.tvRowValue)).setText(value);
    }

    /**
     * Bumps a row's value to bold, on top of item_receipt_row.xml's shared
     * (slightly reduced, see that layout) value text size - for the handful of
     * fields worth visually standing out (the two guest-entered GCash fields,
     * Total/Amount Paid) without giving every row its own bespoke layout. A
     * no-op if the row was hidden by bindRow() above (no value to emphasize).
     */
    private void emphasizeRowValue(int includeId) {
        View row = findViewById(includeId);
        if (row == null || row.getVisibility() != View.VISIBLE) return;
        TextView value = row.findViewById(R.id.tvRowValue);
        value.setTypeface(value.getTypeface(), android.graphics.Typeface.BOLD);
    }

    /**
     * The GCash mobile number entered in payment.xml Step 2
     * (Booking#getGcashNumber(), persisted with this exact payment - never
     * the account profile's own number, never another transaction's). Hidden
     * entirely only for a Cash payment, where it genuinely doesn't apply. For
     * a GCash payment, the row always stays visible: if the value is present
     * it's shown in full (never masked/truncated), and if it's missing from
     * the payment record the row still shows explicitly rather than silently
     * disappearing - a guest (or support agent looking at the same screen)
     * seeing no row at all can't tell "not applicable" apart from "data
     * didn't load", whereas an explicit "not on file" message makes clear
     * this is a data gap on a GCash transaction, not a rendering bug.
     */
    private void bindGcashMobileRow(boolean gcash) {
        if (!gcash) {
            bindRow(R.id.rowGcashMobileNumber, getString(R.string.receipt_gcash_mobile_label), null);
            return;
        }
        String raw = booking.getGcashNumber();
        boolean hasValue = raw != null && !raw.trim().isEmpty();
        if (!hasValue) {
            android.util.Log.w(TAG, "gcash_number missing for paymentId=" + booking.getLatestPaymentId()
                    + " bookingId=" + booking.getId() + " - API returned null/empty for a GCash payment");
        }
        bindRow(R.id.rowGcashMobileNumber, getString(R.string.receipt_gcash_mobile_label),
                hasValue ? formatGcashMobileNumber(raw) : getString(R.string.receipt_gcash_value_missing));
        if (hasValue) emphasizeRowValue(R.id.rowGcashMobileNumber);
    }

    /**
     * The 13-digit GCash reference number entered in payment.xml Step 3
     * (Booking#getTransactionRef()). Same visibility rule as
     * bindGcashMobileRow() above - hidden only for Cash, otherwise always
     * shown. A stored value that isn't exactly 13 digits (a backend data
     * problem - PaymentActivity's own Step 3 input never lets a guest submit
     * anything but exactly 13) is NEVER shown as a bare digit string (a guest
     * reading "49" next to "GCash Reference #" can't tell that apart from a
     * real value) - it's replaced with an explicit fallback message instead,
     * via the shared GcashReferenceFormatter.formatOrFallback() also used by
     * BookingDetailsActivity/TransactionDetailsActivity so all three screens
     * agree on what "incomplete" looks like.
     */
    private void bindGcashReferenceRow(boolean gcash) {
        if (!gcash) {
            bindRow(R.id.rowGcashReference, getString(R.string.receipt_gcash_reference_number_label), null);
            return;
        }
        String raw = booking.getTransactionRef();
        String digits = GcashReferenceFormatter.digitsOnly(raw);
        boolean complete = digits.length() == 13;
        if (!complete) {
            android.util.Log.w(TAG, (digits.isEmpty()
                    ? "reference_number missing for paymentId=" + booking.getLatestPaymentId()
                    : "reference_number has " + digits.length() + " digits (expected 13) for paymentId=" + booking.getLatestPaymentId()
                    + " - value=\"" + digits + "\"")
                    + " bookingId=" + booking.getId() + " - API returned incomplete data for a GCash payment");
        }
        bindRow(R.id.rowGcashReference, getString(R.string.receipt_gcash_reference_number_label),
                GcashReferenceFormatter.formatOrFallback(raw,
                        getString(R.string.receipt_gcash_value_missing),
                        getString(R.string.receipt_gcash_reference_legacy_incomplete)));
        if (complete) emphasizeRowValue(R.id.rowGcashReference);
    }

    @Nullable
    private String computeNights(@Nullable String checkIn, @Nullable String checkOut) {
        Long nights = StayDateCalculator.nightsBetweenOrNull(checkIn, checkOut);
        return nights != null ? String.valueOf(nights) : null;
    }

    private String formatPrice(double value) {
        return MoneyFormat.format(value);
    }

    /**
     * One line per room type (e.g. "Deluxe Room ×2\nExecutive Room ×1") - the
     * real itemized breakdown (Booking#getRooms()) once the backend returns
     * one (see MULTI_ROOM_TRANSACTION_BACKEND_SPEC.md, empty on every
     * transaction today), else today's actual multi-room-type case: each
     * BookingGroupState sibling IS one distinct room type (see
     * resolveGroupMembers()/BookingDetailsActivity's identical fallback) -
     * without this, a "Deluxe + Suite" transaction's receipt would silently
     * show only this one anchor sibling's single room type. Falls back to
     * the plain single Room Type value only when neither applies.
     */
    private String formatRoomTypeValue() {
        List<BookingRoom> rooms = booking.getRooms();
        if (!rooms.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (BookingRoom room : rooms) {
                if (room == null || room.getRoomTypeName() == null || room.getRoomTypeName().trim().isEmpty()) continue;
                if (sb.length() > 0) sb.append('\n');
                sb.append(room.getRoomTypeName().trim()).append(" ×").append(Math.max(1, room.getQuantity()));
            }
            if (sb.length() > 0) return sb.toString();
        }
        if (groupMembers != null && !groupMembers.isEmpty()) {
            java.util.LinkedHashMap<String, Integer> counts = new java.util.LinkedHashMap<>();
            for (Booking member : groupMembers) {
                int quantity = Math.max(1, member.getRoomsRequested());
                counts.merge(member.getRoomType(), quantity, Integer::sum);
            }
            StringBuilder sb = new StringBuilder();
            for (java.util.Map.Entry<String, Integer> entry : counts.entrySet()) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(entry.getKey()).append(" ×").append(entry.getValue());
            }
            return sb.toString();
        }
        return booking.getRoomType();
    }

    /**
     * Displays the exact GCash mobile number entered in payment.xml Step 2
     * (Booking#getGcashNumber()) - grouped with spaces for readability only,
     * every digit stays visible, nothing is masked/starred/truncated. This
     * app's own Step 2 input (isValidGcashNumber(), "9\\d{9}") only ever
     * stores the raw 10 digits "9XXXXXXXXX" - the "+63 " the guest sees while
     * typing is a prefixText decoration on that input field, never part of
     * the stored value - so that canonical case is displayed in local form
     * ("0912 345 6789", 4-3-4 grouping with the leading zero restored) to
     * match what the guest actually typed. A raw value that was itself
     * already stored in international form (a "+63"/"63" country code, e.g.
     * from a future input path or legacy data) is displayed as "+63 912 345
     * 6789" instead, since that's the form it's actually in - this method
     * never converts between the two, only reformats a value into the shape
     * it already is. Any other unexpected length/shape falls back to the raw
     * value verbatim rather than mangling or hiding real (if unusual) data.
     */
    @Nullable
    private String formatGcashMobileNumber(@Nullable String raw) {
        return GcashReferenceFormatter.formatMobileNumber(raw);
    }

    /**
     * Renders the receipt card view itself into a single-page PDF and saves
     * it via MediaStore.Downloads (API 29+, matches this project's minSdk, so
     * no WRITE_EXTERNAL_STORAGE permission is needed). Preserves everything
     * visible on screen - logo, hotel info, receipt number, every section -
     * since it's a direct render of the same view, not a separately
     * maintained template that could drift out of sync with it.
     */
    private void downloadReceiptAsPdf() {
        String paymentId = booking.getLatestPaymentId() != null ? booking.getLatestPaymentId() : booking.getId();
        downloadReceiptAsPdf(receiptCard, paymentId);
    }

    /**
     * Generalized so the SAME mechanism serves both legacy mode (renders
     * receiptCard) and receipt-number mode (renders whichever specific
     * PR/FR/OR content is currently visible - see renderReceiptDetail()).
     * This is a direct screenshot-to-PDF of whatever View is passed in, not
     * a server-generated document - the backend has no per-receipt-type PDF
     * endpoint today (see PAYMENT_RECEIPT_HISTORY_BACKEND_SPEC.md §26), so
     * this client-side render is genuinely type-agnostic: it already works
     * correctly for PR/FR/OR alike without needing any backend distinction,
     * since it just captures the exact section content already being shown
     * on screen for whichever receipt is currently open.
     */
    private void downloadReceiptAsPdf(@Nullable View target, @Nullable String idSuffix) {
        // The id ends up in a file name: keep only characters every file system accepts.
        String safeSuffix = idSuffix == null ? "" : idSuffix.replaceAll("[^A-Za-z0-9._-]", "_");
        String fileName = "VelocitySuites_Receipt_" + (safeSuffix.isEmpty() ? String.valueOf(System.currentTimeMillis()) : safeSuffix) + ".pdf";

        if (target == null || target.getWidth() <= 0 || target.getHeight() <= 0) {
            Toast.makeText(this, R.string.receipt_download_failed, Toast.LENGTH_LONG).show();
            return;
        }

        // EVERYTHING that can throw is inside the try: drawing a very tall view, the PDF writer, the
        // MediaStore insert, the write itself. The guest gets "could not save" - never a crash - and a
        // half-written file is removed rather than left in Downloads looking like a real receipt.
        PdfDocument document = null;
        android.net.Uri uri = null;
        try {
            document = new PdfDocument();
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(target.getWidth(), target.getHeight(), 1).create();
            PdfDocument.Page page = document.startPage(pageInfo);
            Canvas canvas = page.getCanvas();
            canvas.drawColor(android.graphics.Color.WHITE);
            target.draw(canvas);
            document.finishPage(page);

            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            // Invisible to other apps until fully written.
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new java.io.IOException("MediaStore did not return a Uri");
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new java.io.IOException("No output stream for " + uri);
                document.writeTo(out);
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            getContentResolver().update(uri, done, null, null);
            Toast.makeText(this, R.string.receipt_download_success, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            if (uri != null) {
                try {
                    getContentResolver().delete(uri, null, null);
                } catch (Exception ignored) {
                    // best effort
                }
            }
            Toast.makeText(this, R.string.receipt_download_failed, Toast.LENGTH_LONG).show();
        } finally {
            if (document != null) document.close();
        }
    }
}
