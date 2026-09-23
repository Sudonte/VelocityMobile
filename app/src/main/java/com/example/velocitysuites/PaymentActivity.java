package com.example.velocitysuites;

import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import com.example.velocitysuites.network.dto.RequestableAmenityDto;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class PaymentActivity extends BaseNavigationActivity {

    /** Set by BillingSummaryActivity's Confirm Booking button: this payment is converting a plain Reservation into a paid Booking. */
    public static final String EXTRA_FROM_RESERVATION_PAYMENT = "FROM_RESERVATION_PAYMENT";
    /**
     * Set by Step8ReviewPaymentFragment's Confirm Booking button (Booking
     * mode only - unaffected by the Reservation wizard's Payment Method step
     * addition): no Booking row exists yet - PendingBookingPayload carries
     * the reviewed wizard state, and RoomRepository#createDirectBooking() is
     * only called once this screen's GCash submission succeeds (see
     * submitPendingBookingGroups()). A Booking must never be created before
     * that succeeds.
     */
    public static final String EXTRA_PENDING_BOOKING = "PENDING_BOOKING";
    /**
     * No longer set by any caller - a fresh Reservation's Confirm button
     * (Step8ReviewPaymentFragment, reservation mode) now calls
     * RoomRepository#createReservation() directly and never opens this
     * screen during creation, for either Cash or GCash (GCash always defers
     * payment to a later Pay Now action instead). The pending-reservation-
     * mode code below (isPendingReservationMode and everything gated on it -
     * submitPendingReservationCreateOnlyGroups()/submitPendingReservationCashGroups()/
     * submitPendingReservationGroups()/PendingReservationPayload) is kept in
     * place as dead code rather than removed, since this is a large,
     * heavily-tested file and the risk of a surgical removal outweighs the
     * benefit - the existing "Pay Now" entry point (BOOKING_ID extra) is a
     * fully separate, still-live code path, unaffected by this.
     */
    public static final String EXTRA_PENDING_RESERVATION = "PENDING_RESERVATION";
    /**
     * Legacy flag, no longer set by any caller now that Reservation creation
     * is always deferred into this screen's pending-reservation mode (see
     * EXTRA_PENDING_RESERVATION) - kept only as a defensive extra-read in
     * case of a future/other caller. isPendingReservationMode alone already
     * implies the same "Proceed & Pay Later" labeling/behavior.
     */
    public static final String EXTRA_FRESH_RESERVATION = "FRESH_RESERVATION";

    private boolean fromReservationPayment;
    private boolean freshReservation;
    private boolean isPendingBookingMode;
    private boolean isPendingReservationMode;
    /** Booking-mode reviewed wizard state, staged by Step8ReviewPaymentFragment (EXTRA_PENDING_BOOKING only - EXTRA_PENDING_RESERVATION no longer sets this, see that field's docblock). */
    private BookingWizardState pendingWizardState;
    /** New Booking is GCash-only; New Reservation offers GCash+Cash - see
     *  BookingAndReservationActivity#goToPaymentFor(). Defaults to true so
     *  entry points that don't set it (e.g. the Payment List) keep both
     *  options, matching existing pre-existing bookings/reservations that
     *  may already be Pay Later. */
    private boolean allowCash = true;
    /**
     * True when this screen is paying an already-created transaction (Pay
     * Now from the Dashboard/Active Reservation List/etc, via BOOKING_ID) -
     * as opposed to choosing a payment method during wizard-driven creation.
     * The reservation's payment_method was already chosen once at creation
     * (see RoomRepository#createReservation()) and must never be re-offered
     * here - see preselectPaymentMethod().
     */
    private boolean lockedPaymentMethod;

    private String selectedPaymentMethod = "GCash";
    private boolean isCashSelected = false;
    private boolean isFormattingGcashReference = false;
    private boolean isFormattingGcashNumber = false;
    /**
     * Re-entrancy guard against duplicate payment submissions: rapid taps can
     * queue a second click before the modal loading dialog blocks the UI, and
     * stacked confirmation dialogs could each submit. Stays true after success
     * (the screen is left via finalizePayment); reset only on error.
     */
    private boolean isSubmittingPayment = false;
    private Booking currentBooking;
    private RoomRepository repository;
    private String bookingId;

    // Payment amount: either a Partial Payment (exactly 20/30/40/50% of the
    // booking total - no custom percentages allowed) or a Full Payment
    // (must exactly match the total booking amount, validated live).
    private static final double[] PARTIAL_PERCENTAGES = {0.20, 0.30, 0.40, 0.50};
    private boolean isFullPaymentMode = false;
    private double selectedPartialPercent = PARTIAL_PERCENTAGES[0];
    private double roomTotalValue = 0;
    private double grandTotalValue = 0;
    private double alreadyPaidValue = 0;
    private double payNowValue = 0;
    private Uri receiptUri;

    private TextView tvTotalAmount, tvBookingRef, tvPaymentStatus;
    private TextView tvRoomCharges, tvGrandTotal, tvPortalAmount;
    private TextView tvAlreadyPaidSummary;
    private TextView tvTransactionType, tvRepresentativeSummary, tvCheckInSummary, tvCheckOutSummary, tvNightsSummary;
    private TextView tvAmenitiesTotalSummary, tvTotalAmountSummary, tvDiscountSummary, tvOutstandingBalanceSummary, tvNoAmenitiesSummary;
    private View layoutRepresentativeSummary, layoutDiscountSummary;
    private LinearLayout layoutSelectedRoomsSummary, layoutSelectedAmenitiesSummary;
    private TextView tvRemainingBalance, tvPaymentModeSub, tvReceiptStatus, tvCashNote, tvAmountToPay, tvAmountError;
    private TextInputEditText etAmountToPay;
    private View checkoutSummarySection, gcashPortalSection, cardReceiptPreview;
    private View tvPartialPercentLabel;
    private TextView tvSelectedPaymentReadOnly;
    private MaterialButton btnBookNow, btnReserveNow;
    private View cardBookReserveCta;
    private View emptyStateSection;
    private NestedScrollView screenContent;
    private ImageView ivReceiptPreview;
    private MaterialButton proceedToGcashButton, completePaymentButton, backToSummaryButton, btnUploadReceipt;
    private TextInputEditText etGcashNumber;
    private TextInputEditText etGcashReferenceNumber;
    private TextInputLayout tilGcashReferenceNumber;
    private ChipGroup cgPaymentMethod, cgPaymentAmount;
    private com.google.android.material.progressindicator.LinearProgressIndicator paymentBreakdownProgress;

    // Payment verification status banner (Pending Verification / Verified / Rejected)
    private View cardPaymentStatusBanner, layoutPendingPaymentActions, layoutGcashMacroSteps, cardGcashSubmissionForm;
    private ImageView ivPaymentStatusIcon;
    private TextView tvPaymentStatusBannerTitle, tvPaymentStatusBannerMessage;
    private MaterialButton btnCancelPayment, btnConvertToReservation, btnResubmitPayment;

    // GCash 5-step wizard (Scan QR / Mobile Number / Reference Number / Receipt / Review & Submit)
    private int currentGcashStep = 1;
    private TextView tvGcashStepLabel;
    private WizardStepIndicatorView gcashStepIndicator;
    private View gcashStep1Qr, gcashStep2Number, gcashStep3Reference, gcashStep4Receipt, gcashStep5Review;
    private MaterialButton btnGcashStep1Next, btnGcashStep2Back, btnGcashStep2Next, btnGcashStep3Back, btnGcashStep3Next,
            btnGcashStep4Back, btnGcashStep4Next, btnGcashStep5Back, btnDownloadQrCode;
    private TextView tvReviewBookingIdValue, tvReviewPaymentMethod, tvReviewPaymentType, tvReviewPaymentPercentage, tvReviewAmount, tvReviewRemainingBalance, tvReviewGcashNumber, tvReviewReferenceNumber;
    private TextView tvReviewRoomSummary, tvReviewCheckIn, tvReviewCheckOut, tvReviewGuestCount, tvReviewRepresentativeName, tvReviewTotalBookingAmount, tvReviewReceiptFileName, tvViewReceiptAction;
    private ImageView ivReviewReceiptThumb;

    // Receipt Replace/Remove
    private View layoutReceiptActions;
    private MaterialButton btnReplaceReceipt, btnRemoveReceipt;

    private ActivityResultLauncher<String> receiptPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.payment);
        setupGuestNavigation(R.id.nav_payment);
        animateScreenContent();

        repository = RoomRepository.getInstance(this);
        bookingId = getIntent().getStringExtra("BOOKING_ID");
        fromReservationPayment = getIntent().getBooleanExtra(EXTRA_FROM_RESERVATION_PAYMENT, false);
        isPendingBookingMode = getIntent().getBooleanExtra(EXTRA_PENDING_BOOKING, false);
        isPendingReservationMode = getIntent().getBooleanExtra(EXTRA_PENDING_RESERVATION, false);
        // A pending reservation always offers the "Proceed & Pay Later" CTA -
        // the Reservation doesn't exist yet either way, so labeling/behavior
        // is identical to the legacy already-created-fresh-reservation case.
        freshReservation = getIntent().getBooleanExtra(EXTRA_FRESH_RESERVATION, false) || isPendingReservationMode;
        allowCash = isPendingBookingMode ? false : getIntent().getBooleanExtra("ALLOW_CASH", true);
        lockedPaymentMethod = bookingId != null && !isPendingBookingMode && !isPendingReservationMode;
        if (isPendingBookingMode) {
            pendingWizardState = PendingBookingPayload.consume();
        } else if (isPendingReservationMode) {
            pendingWizardState = PendingReservationPayload.consume();
        }

        initViews();
        if (freshReservation && proceedToGcashButton != null) {
            proceedToGcashButton.setText(R.string.proceed_and_pay_later);
        }
        setupNavigationLogic();
        setupBookNow();
        setupAmountInput();
        setupReceiptUpload();
        setupGcashNumberInput();
        setupPaymentVerificationActions();
        setupPaymentMethodToggle();
        setupPaymentAmountChips();

        updatePaymentGreeting();

        if (isPendingBookingMode || isPendingReservationMode) {
            if (pendingWizardState == null || pendingWizardState.selectedRooms.isEmpty()) {
                // Process death or a stale/duplicate launch with nothing to pay for.
                Toast.makeText(this, R.string.error_no_booking_data, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            loadPendingWizardSummary();
            return;
        }

        repository.refreshBookings(new RoomRepository.RepositoryCallback<java.util.List<Booking>>() {
            @Override
            public void onSuccess(java.util.List<Booking> result) {
                loadBookingData();
            }

            @Override
            public void onError(String message) {
                loadBookingData();
            }
        });
    }

    private void initViews() {
        tvTotalAmount = findViewById(R.id.tvTotalAmount);
        tvBookingRef = findViewById(R.id.tvBookingRef);
        tvPaymentStatus = findViewById(R.id.tvPaymentStatus);
        tvRoomCharges = findViewById(R.id.tvRoomCharges);
        tvGrandTotal = findViewById(R.id.tvGrandTotal);
        tvPortalAmount = findViewById(R.id.tvPortalAmount);
        tvAlreadyPaidSummary = findViewById(R.id.tvAlreadyPaidSummary);
        tvTransactionType = findViewById(R.id.tvTransactionType);
        layoutRepresentativeSummary = findViewById(R.id.layoutRepresentativeSummary);
        tvRepresentativeSummary = findViewById(R.id.tvRepresentativeSummary);
        layoutSelectedRoomsSummary = findViewById(R.id.layoutSelectedRoomsSummary);
        tvCheckInSummary = findViewById(R.id.tvCheckInSummary);
        tvCheckOutSummary = findViewById(R.id.tvCheckOutSummary);
        tvNightsSummary = findViewById(R.id.tvNightsSummary);
        layoutSelectedAmenitiesSummary = findViewById(R.id.layoutSelectedAmenitiesSummary);
        tvNoAmenitiesSummary = findViewById(R.id.tvNoAmenitiesSummary);
        tvAmenitiesTotalSummary = findViewById(R.id.tvAmenitiesTotalSummary);
        tvTotalAmountSummary = findViewById(R.id.tvTotalAmountSummary);
        layoutDiscountSummary = findViewById(R.id.layoutDiscountSummary);
        tvDiscountSummary = findViewById(R.id.tvDiscountSummary);
        tvOutstandingBalanceSummary = findViewById(R.id.tvOutstandingBalanceSummary);
        paymentBreakdownProgress = findViewById(R.id.paymentBreakdownProgress);

        etAmountToPay = findViewById(R.id.etAmountToPay);
        tvAmountToPay = findViewById(R.id.tvAmountToPay);
        tvAmountError = findViewById(R.id.tvAmountError);
        tvRemainingBalance = findViewById(R.id.tvRemainingBalance);
        tvPaymentModeSub = findViewById(R.id.tvPaymentModeSub);

        checkoutSummarySection = findViewById(R.id.checkoutSummarySection);
        gcashPortalSection = findViewById(R.id.gcashPortalSection);
        cardReceiptPreview = findViewById(R.id.cardReceiptPreview);
        ivReceiptPreview = findViewById(R.id.ivReceiptPreview);
        tvReceiptStatus = findViewById(R.id.tvReceiptStatus);

        proceedToGcashButton = findViewById(R.id.proceedToGcashButton);
        completePaymentButton = findViewById(R.id.completePaymentButton);
        backToSummaryButton = findViewById(R.id.backToSummaryButton);
        btnUploadReceipt = findViewById(R.id.btnUploadReceipt);

        etGcashNumber = findViewById(R.id.etGcashNumber);
        etGcashReferenceNumber = findViewById(R.id.etGcashReferenceNumber);
        tilGcashReferenceNumber = findViewById(R.id.tilGcashReferenceNumber);

        cgPaymentMethod = findViewById(R.id.cgPaymentMethod);
        tvCashNote = findViewById(R.id.tvCashNote);

        cgPaymentAmount = findViewById(R.id.cgPaymentAmount);
        tvPartialPercentLabel = findViewById(R.id.tvPartialPercentLabel);
        tvSelectedPaymentReadOnly = findViewById(R.id.tvSelectedPaymentReadOnly);

        btnBookNow = findViewById(R.id.btnBookNow);
        btnReserveNow = findViewById(R.id.btnReserveNow);
        cardBookReserveCta = findViewById(R.id.cardBookReserveCta);
        emptyStateSection = findViewById(R.id.emptyStateSection);
        screenContent = findViewById(R.id.screenContent);

        cardPaymentStatusBanner = findViewById(R.id.cardPaymentStatusBanner);
        layoutPendingPaymentActions = findViewById(R.id.layoutPendingPaymentActions);
        layoutGcashMacroSteps = findViewById(R.id.layoutGcashMacroSteps);
        cardGcashSubmissionForm = findViewById(R.id.cardGcashSubmissionForm);
        ivPaymentStatusIcon = findViewById(R.id.ivPaymentStatusIcon);
        tvPaymentStatusBannerTitle = findViewById(R.id.tvPaymentStatusBannerTitle);
        tvPaymentStatusBannerMessage = findViewById(R.id.tvPaymentStatusBannerMessage);
        btnCancelPayment = findViewById(R.id.btnCancelPayment);
        btnConvertToReservation = findViewById(R.id.btnConvertToReservation);
        btnResubmitPayment = findViewById(R.id.btnResubmitPayment);

        layoutReceiptActions = findViewById(R.id.layoutReceiptActions);
        btnReplaceReceipt = findViewById(R.id.btnReplaceReceipt);
        btnRemoveReceipt = findViewById(R.id.btnRemoveReceipt);

        tvGcashStepLabel = findViewById(R.id.tvGcashStepLabel);
        gcashStepIndicator = findViewById(R.id.gcashStepIndicator);
        if (gcashStepIndicator != null) gcashStepIndicator.setStepCount(5);
        gcashStep1Qr = findViewById(R.id.gcashStep1Qr);
        gcashStep2Number = findViewById(R.id.gcashStep2Number);
        gcashStep3Reference = findViewById(R.id.gcashStep3Reference);
        gcashStep4Receipt = findViewById(R.id.gcashStep4Receipt);
        gcashStep5Review = findViewById(R.id.gcashStep5Review);
        btnGcashStep1Next = findViewById(R.id.btnGcashStep1Next);
        btnDownloadQrCode = findViewById(R.id.btnDownloadQrCode);
        if (btnDownloadQrCode != null) {
            btnDownloadQrCode.setOnClickListener(v -> downloadGcashQrCode());
        }
        btnGcashStep2Back = findViewById(R.id.btnGcashStep2Back);
        btnGcashStep2Next = findViewById(R.id.btnGcashStep2Next);
        btnGcashStep3Back = findViewById(R.id.btnGcashStep3Back);
        btnGcashStep3Next = findViewById(R.id.btnGcashStep3Next);
        btnGcashStep4Back = findViewById(R.id.btnGcashStep4Back);
        btnGcashStep4Next = findViewById(R.id.btnGcashStep4Next);
        btnGcashStep5Back = findViewById(R.id.btnGcashStep5Back);
        tvReviewBookingIdValue = findViewById(R.id.tvReviewBookingIdValue);
        tvReviewPaymentMethod = findViewById(R.id.tvReviewPaymentMethod);
        tvReviewPaymentType = findViewById(R.id.tvReviewPaymentType);
        tvReviewPaymentPercentage = findViewById(R.id.tvReviewPaymentPercentage);
        tvReviewAmount = findViewById(R.id.tvReviewAmount);
        tvReviewRemainingBalance = findViewById(R.id.tvReviewRemainingBalance);
        tvReviewGcashNumber = findViewById(R.id.tvReviewGcashNumber);
        tvReviewReferenceNumber = findViewById(R.id.tvReviewReferenceNumber);
        tvReviewRoomSummary = findViewById(R.id.tvReviewRoomSummary);
        tvReviewCheckIn = findViewById(R.id.tvReviewCheckIn);
        tvReviewCheckOut = findViewById(R.id.tvReviewCheckOut);
        tvReviewGuestCount = findViewById(R.id.tvReviewGuestCount);
        tvReviewRepresentativeName = findViewById(R.id.tvReviewRepresentativeName);
        tvReviewTotalBookingAmount = findViewById(R.id.tvReviewTotalBookingAmount);
        tvReviewReceiptFileName = findViewById(R.id.tvReviewReceiptFileName);
        tvViewReceiptAction = findViewById(R.id.tvViewReceiptAction);
        ivReviewReceiptThumb = findViewById(R.id.ivReviewReceiptThumb);
        setupGcashStepNavigation();
    }

    /**
     * Saves the hotel's official GCash QR code (a bundled drawable, not a
     * per-transaction generated image) to the device's Pictures library via
     * MediaStore. minSdk is 29 (Android 10+), so scoped storage already
     * applies on every device this app runs on - no WRITE_EXTERNAL_STORAGE
     * permission request is needed or requested here, matching the modern,
     * permission-free MediaStore insert path for that API level and above.
     */
    private void downloadGcashQrCode() {
        try {
            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.gcash_official_qr);
            if (bitmap == null) {
                Toast.makeText(this, R.string.qr_download_failed, Toast.LENGTH_LONG).show();
                return;
            }
            String fileName = "VelocitySuites_GCash_QR_" + System.currentTimeMillis() + ".jpg";
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/VelocitySuites");

            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                Toast.makeText(this, R.string.qr_download_failed, Toast.LENGTH_LONG).show();
                return;
            }
            boolean saved = false;
            try (java.io.OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os != null) {
                    saved = bitmap.compress(Bitmap.CompressFormat.JPEG, 95, os);
                }
            }
            Toast.makeText(this, saved ? R.string.qr_download_success : R.string.qr_download_failed, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            // Never crash on a save failure (denied media permission on an
            // unusual OEM build, out of storage, etc.) - the guest can still
            // scan the on-screen QR and complete payment regardless.
            Toast.makeText(this, R.string.qr_download_failed, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Wires Back/Next across the 5-step GCash form (Scan QR / Mobile Number / Reference
     * Number / Receipt / Review). Each Next gate re-validates its own step's field(s) -
     * the same checks processPayment() used to do all at once - so a guest can never reach
     * Review with an invalid field, and updateSubmitButtonState() (still called from each
     * field's text watcher / receipt callbacks) keeps every step's own Next button in sync
     * as the guest types, exactly mirroring how completePaymentButton used to behave.
     */
    private void setupGcashStepNavigation() {
        if (btnGcashStep1Next != null) {
            btnGcashStep1Next.setOnClickListener(v -> {
                currentGcashStep = 2;
                renderGcashStep();
            });
        }
        if (btnGcashStep2Back != null) {
            btnGcashStep2Back.setOnClickListener(v -> {
                currentGcashStep = 1;
                renderGcashStep();
            });
        }
        if (btnGcashStep2Next != null) {
            btnGcashStep2Next.setOnClickListener(v -> {
                String number = etGcashNumber != null && etGcashNumber.getText() != null ? etGcashNumber.getText().toString().trim() : "";
                if (!isValidGcashNumber(number)) {
                    if (etGcashNumber != null) etGcashNumber.setError(getString(R.string.error_invalid_gcash_number));
                    return;
                }
                if (etGcashNumber != null) etGcashNumber.setError(null);
                currentGcashStep = 3;
                renderGcashStep();
            });
        }
        if (btnGcashStep3Back != null) {
            btnGcashStep3Back.setOnClickListener(v -> {
                currentGcashStep = 2;
                renderGcashStep();
            });
        }
        if (btnGcashStep3Next != null) {
            btnGcashStep3Next.setOnClickListener(v -> {
                Integer referenceError = gcashReferenceValidationError();
                if (referenceError != null) {
                    setGcashReferenceError(getString(referenceError));
                    if (etGcashReferenceNumber != null) etGcashReferenceNumber.requestFocus();
                    return;
                }
                // Fast, local pre-check against this guest's own already-loaded bookings (never
                // another guest's - refreshBookings() only ever returns the authenticated
                // guest's own records) so an obvious re-typed/reused reference is caught here
                // instead of only after the receipt upload + a full round trip on Step 5. This
                // is a UX convenience only, not the authoritative check - the server's own
                // uniqueness constraint at final submission (see submitPaymentToServer()/
                // reconcileDuplicateReference()) remains the real safety net, since this local
                // cache can be stale relative to submissions made moments ago elsewhere.
                if (findBookingByReference(repository.getBookings(), gcashReferenceDigitsOnly()) != null) {
                    setGcashReferenceError(getString(R.string.error_gcash_reference_duplicate));
                    if (etGcashReferenceNumber != null) etGcashReferenceNumber.requestFocus();
                    return;
                }
                setGcashReferenceError(null);
                currentGcashStep = 4;
                renderGcashStep();
            });
        }
        if (btnGcashStep4Back != null) {
            btnGcashStep4Back.setOnClickListener(v -> {
                currentGcashStep = 3;
                renderGcashStep();
            });
        }
        if (btnGcashStep4Next != null) {
            btnGcashStep4Next.setOnClickListener(v -> {
                if (receiptUri == null) {
                    Toast.makeText(this, R.string.error_attach_receipt, Toast.LENGTH_LONG).show();
                    return;
                }
                currentGcashStep = 5;
                renderGcashStep();
            });
        }
        if (btnGcashStep5Back != null) {
            btnGcashStep5Back.setOnClickListener(v -> {
                currentGcashStep = 4;
                renderGcashStep();
            });
        }
    }

    /** Shows only the current step's view, updates the "Step X of 5" label + WizardStepIndicatorView, and (on Step 5) refreshes the review summary. */
    private void renderGcashStep() {
        if (gcashStep1Qr == null) return;
        gcashStep1Qr.setVisibility(currentGcashStep == 1 ? View.VISIBLE : View.GONE);
        if (gcashStep2Number != null) gcashStep2Number.setVisibility(currentGcashStep == 2 ? View.VISIBLE : View.GONE);
        if (gcashStep3Reference != null) gcashStep3Reference.setVisibility(currentGcashStep == 3 ? View.VISIBLE : View.GONE);
        if (gcashStep4Receipt != null) gcashStep4Receipt.setVisibility(currentGcashStep == 4 ? View.VISIBLE : View.GONE);
        if (gcashStep5Review != null) gcashStep5Review.setVisibility(currentGcashStep == 5 ? View.VISIBLE : View.GONE);
        if (tvGcashStepLabel != null) tvGcashStepLabel.setText(getString(R.string.registration_step_format, currentGcashStep, 5));
        if (gcashStepIndicator != null) gcashStepIndicator.setCurrentStep(currentGcashStep);
        if (currentGcashStep == 5) populateGcashReviewStep();
    }

    /** Echoes back everything the guest entered across Steps 1-4, read-only, right before the final submit. */
    private void populateGcashReviewStep() {
        if (tvReviewBookingIdValue != null) {
            // Just the bare id ("#125") - tvBookingRef's own text is already
            // self-labeled ("Booking Ref: 125"/"Reservation Ref: 125"),
            // which would read redundantly (or, for a still-unconverted
            // Reservation being paid via Pay Now, contradictorily) next to
            // this row's own "Booking ID" label. This payment step always
            // concerns the transaction becoming/already being a Booking
            // (see class-level payment/booking_id business rule), so the
            // label itself is intentionally fixed regardless of the
            // underlying isHasBooking() state at review time.
            //
            // Always explicitly set - never leave this TextView showing
            // whatever its XML default happened to be. In pending-booking/
            // pending-reservation mode currentBooking is genuinely null here
            // by design - no Booking row exists server-side until this GCash
            // submission succeeds (see EXTRA_PENDING_BOOKING's docblock) - so
            // a real id is never available yet. Match the same "not created
            // yet" convention loadPendingWizardSummary() already uses for
            // tvBookingRef/tvPaymentStatus (pending_booking_ref_label/
            // pending_booking_status_label) instead of a bare, unexplained
            // "N/A" - a currency string or a blank value must never appear
            // next to a "Booking ID" label either.
            tvReviewBookingIdValue.setText(currentBooking != null
                    ? getString(R.string.hash_id_format, currentBooking.getId())
                    : getString(R.string.pending_booking_id_review_label));
        }
        // Room/s, dates, guest count and representative name all come from
        // whichever source of truth this screen is already reviewing against
        // - a real, already-created Booking (Pay Now) or the in-progress
        // wizard state (New Booking, not yet created) - same fallback pattern
        // as the Booking ID row above, never left blank or unset.
        SimpleDateFormat reviewDateFmt = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
        if (tvReviewRoomSummary != null) {
            tvReviewRoomSummary.setText(currentBooking != null
                    ? android.text.TextUtils.join(", ", currentBooking.getAllRoomTypeNames())
                    : pendingWizardRoomTypeCommaSummary());
        }
        if (tvReviewCheckIn != null) {
            if (currentBooking != null) {
                tvReviewCheckIn.setText(currentBooking.getCheckInDate());
            } else if (pendingWizardState != null && pendingWizardState.checkIn != null) {
                tvReviewCheckIn.setText(reviewDateFmt.format(pendingWizardState.checkIn.getTime()));
            }
        }
        if (tvReviewCheckOut != null) {
            if (currentBooking != null) {
                tvReviewCheckOut.setText(currentBooking.getCheckOutDate());
            } else if (pendingWizardState != null && pendingWizardState.checkOut != null) {
                tvReviewCheckOut.setText(reviewDateFmt.format(pendingWizardState.checkOut.getTime()));
            }
        }
        if (tvReviewGuestCount != null) {
            int guestCount = currentBooking != null
                    ? currentBooking.getGuests()
                    : (pendingWizardState != null ? pendingWizardState.adults + pendingWizardState.children : 0);
            tvReviewGuestCount.setText(getString(R.string.guests_count_format, guestCount));
        }
        if (tvReviewRepresentativeName != null) {
            String repName = currentBooking != null ? currentBooking.getRepresentativeName() : null;
            if (repName == null && pendingWizardState != null) {
                StringBuilder sb = new StringBuilder();
                if (pendingWizardState.guestFirstName != null) sb.append(pendingWizardState.guestFirstName.trim()).append(' ');
                if (pendingWizardState.guestLastName != null) sb.append(pendingWizardState.guestLastName.trim());
                repName = sb.toString().trim();
            }
            tvReviewRepresentativeName.setText(repName == null || repName.isEmpty()
                    ? getString(R.string.label_not_available) : repName);
        }
        if (tvReviewPaymentMethod != null) tvReviewPaymentMethod.setText(R.string.payment_method_gcash);
        // isFullPaymentMode already IS this payment's PARTIAL/FULL type (see
        // onPaymentPercentChipChecked()) - 100% chip selected = Full Payment,
        // any of 20/30/40/50% is Partial Payment. Same rule PaymentStateUtil#
        // isFullyPaid() applies after the fact from amounts once this
        // payment is recorded, so the two can never disagree.
        if (tvReviewPaymentType != null) {
            tvReviewPaymentType.setText(isFullPaymentMode ? R.string.review_payment_type_full : R.string.review_payment_type_partial);
        }
        if (tvReviewPaymentPercentage != null) {
            tvReviewPaymentPercentage.setText(getString(R.string.payment_percentage_format, selectedGcashPercentageForRequest()));
        }
        if (tvReviewTotalBookingAmount != null) {
            tvReviewTotalBookingAmount.setText(String.format(Locale.US, "₱%,.2f", grandTotalValue));
        }
        if (tvReviewAmount != null) tvReviewAmount.setText(String.format(Locale.US, "₱%,.2f", payNowValue));
        if (tvReviewRemainingBalance != null) {
            double balanceAfterThisPayment = Math.max(0, remainingDueValue() - payNowValue);
            tvReviewRemainingBalance.setText(String.format(Locale.US, "₱%,.2f", balanceAfterThisPayment));
        }
        if (tvReviewGcashNumber != null) {
            String number = etGcashNumber != null && etGcashNumber.getText() != null ? etGcashNumber.getText().toString().trim() : "";
            tvReviewGcashNumber.setText("+63 " + number);
        }
        if (tvReviewReferenceNumber != null) {
            // Step 3 keeps the raw 13 digits (no live formatting); Step 5 is the
            // one place the grouped "XXXX XXX XXXXXX" display format is applied.
            tvReviewReferenceNumber.setText(GcashReferenceFormatter.format(gcashReferenceDigitsOnly()));
        }
        if (ivReviewReceiptThumb != null && receiptUri != null) {
            ivReviewReceiptThumb.setImageURI(receiptUri);
        }
        if (tvReviewReceiptFileName != null) {
            String fileName = receiptUri != null ? queryFileName(receiptUri) : null;
            tvReviewReceiptFileName.setText(fileName != null
                    ? getString(R.string.review_receipt_file_name_format, fileName)
                    : getString(R.string.review_receipt_file_name_unknown));
        }
    }

    /**
     * The Book Now / Reserve Now CTA is shown at the top of the Payment
     * screen only when no specific transaction is being paid right now (e.g.
     * the guest arrived via the Payment nav-drawer item) - see
     * updateBookReserveCtaVisibility() - so a guest with nothing to pay yet
     * can jump straight into creating a booking or reservation.
     */
    private void setupBookNow() {
        if (btnBookNow != null) {
            btnBookNow.setOnClickListener(v -> startActivity(
                    StartingTransactionActivity.newIntent(this, BookingWizardState.Mode.BOOKING)));
        }
        if (btnReserveNow != null) {
            btnReserveNow.setOnClickListener(v -> startActivity(
                    StartingTransactionActivity.newIntent(this, BookingWizardState.Mode.RESERVATION)));
        }
    }

    /**
     * Book Now/Reserve Now must disappear once this screen is actually
     * showing a real transaction to pay for (a loaded Booking/Reservation,
     * or a pending-booking review from the wizard) - only the generic
     * no-transaction entry point (nav-drawer "Payment") offers them.
     */
    private void updateBookReserveCtaVisibility(boolean hasActiveTransaction) {
        if (cardBookReserveCta != null) {
            cardBookReserveCta.setVisibility(hasActiveTransaction ? View.GONE : View.VISIBLE);
        }
    }

    /**
     * Cash always behaves like the website's Pay Later + Cash: no receipt,
     * no reference number, the amount is just an optional heads-up for
     * staff (not verified online) - so choosing it skips the GCash portal
     * step entirely and submits directly from the summary screen.
     */
    private void setupPaymentMethodToggle() {
        if (cgPaymentMethod == null) return;

        // New Booking is GCash-only (see PaymentActivity#allowCash) - hide
        // the Cash chip entirely and force GCash so the guest can't select
        // a method the server won't accept for this transaction.
        if (!allowCash) {
            com.google.android.material.chip.Chip chipCash = findViewById(R.id.chipCash);
            if (chipCash != null) chipCash.setVisibility(View.GONE);
            cgPaymentMethod.check(R.id.chipGcash);
        }

        cgPaymentMethod.setOnCheckedStateChangeListener((group, checkedIds) -> {
            isCashSelected = checkedIds.contains(R.id.chipCash);
            selectedPaymentMethod = isCashSelected ? "Cash" : "GCash";
            if (tvCashNote != null) {
                tvCashNote.setVisibility(isCashSelected ? View.VISIBLE : View.GONE);
            }
            if (proceedToGcashButton != null) {
                proceedToGcashButton.setText(isCashSelected ? R.string.confirm_cash_payment
                        : (freshReservation ? R.string.proceed_and_pay_later : R.string.proceed_to_gcash));
            }
            applyCashPartialInterlock();
        });
    }

    /**
     * Cash reservations can only be paid in full - the balance is settled
     * walk-in at checkout, so there's no partial/deposit concept to offer.
     * Selecting Cash hides the 20/30/40/50% chips and forces Full Payment;
     * selecting GCash restores all five options.
     */
    private void applyCashPartialInterlock() {
        if (cgPaymentAmount == null) return;
        int[] partialChipIds = {R.id.chipPercent20, R.id.chipPercent30, R.id.chipPercent40, R.id.chipPercent50};
        for (int id : partialChipIds) {
            com.google.android.material.chip.Chip chip = findViewById(id);
            if (chip != null) chip.setVisibility(isCashSelected ? View.GONE : View.VISIBLE);
        }
        if (isCashSelected) {
            if (cgPaymentAmount.getCheckedChipId() != R.id.chipPercentFull) {
                cgPaymentAmount.check(R.id.chipPercentFull);
            } else {
                applyPaymentAmountSelection();
            }
        }
    }

    private void setupAmountInput() {
        if (etAmountToPay == null) return;
        etAmountToPay.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                payNowValue = parseAmount(s.toString());
                updateAmountToPay();
                validateAmount();
            }
        });
    }

    /**
     * A single unified 5-option selector (20% / 30% / 40% / 50% / Full
     * Payment) replaces the old separate Payment Type (Partial/Full) toggle
     * nested with its own percent chips + free-typed Custom percentage -
     * one flat ChipGroup, one listener.
     */
    private void setupPaymentAmountChips() {
        if (cgPaymentAmount == null) return;
        cgPaymentAmount.setOnCheckedStateChangeListener((group, checkedIds) -> applyPaymentAmountSelection());
    }

    /**
     * Reads whichever chip is checked, updates isFullPaymentMode/
     * selectedPartialPercent accordingly, and either auto-fills the amount
     * field with what's still owed (Full Payment, field stays editable in
     * case the guest needs to adjust it) or computes the fixed percentage
     * of the remaining due amount (20/30/40/50%, read-only field).
     */
    private void applyPaymentAmountSelection() {
        if (cgPaymentAmount == null) return;
        if (renderStoredPaymentPercentageIfPresent()) {
            return;
        }
        int checkedId = cgPaymentAmount.getCheckedChipId();
        isFullPaymentMode = checkedId == R.id.chipPercentFull;
        if (checkedId == R.id.chipPercent20) selectedPartialPercent = PARTIAL_PERCENTAGES[0];
        else if (checkedId == R.id.chipPercent30) selectedPartialPercent = PARTIAL_PERCENTAGES[1];
        else if (checkedId == R.id.chipPercent40) selectedPartialPercent = PARTIAL_PERCENTAGES[2];
        else if (checkedId == R.id.chipPercent50) selectedPartialPercent = PARTIAL_PERCENTAGES[3];

        if (etAmountToPay != null) {
            etAmountToPay.setFocusable(isFullPaymentMode);
            etAmountToPay.setFocusableInTouchMode(isFullPaymentMode);
            etAmountToPay.setCursorVisible(isFullPaymentMode);
            etAmountToPay.setClickable(isFullPaymentMode);
        }
        if (tvPaymentModeSub != null) {
            // Once a prior payment already exists, spell out the actual
            // remaining amount alongside the hint - Full Payment always pays
            // exactly what's still owed, while the 20/30/40/50% chips are
            // always a percentage of the original Grand Total (see
            // applySelectedPartialPercent()), only ever capped by the
            // remaining balance to prevent overpayment - never recalculated
            // against a shrinking base.
            boolean hasPriorPayment = alreadyPaidValue > 0.009;
            String dueFormatted = String.format(Locale.US, "₱%,.2f", remainingDueValue());
            if (isFullPaymentMode) {
                tvPaymentModeSub.setText(hasPriorPayment
                        ? getString(R.string.full_payment_hint_with_due, dueFormatted)
                        : getString(R.string.full_payment_hint));
            } else {
                tvPaymentModeSub.setText(hasPriorPayment
                        ? getString(R.string.partial_percent_label_with_due, dueFormatted)
                        : getString(R.string.partial_percent_label));
            }
        }
        if (isFullPaymentMode) {
            // Auto-fill with the amount still owed (not the original total -
            // a booking with an existing partial payment must only need the
            // remainder) so the guest can proceed straight to GCash without
            // having to type it in manually - the field stays editable in
            // case they need to adjust it.
            double due = remainingDueValue();
            payNowValue = due;
            if (etAmountToPay != null) {
                etAmountToPay.setText(due > 0 ? String.format(Locale.US, "%.2f", due) : "");
            }
            updateAmountToPay();
            validateAmount();
        } else {
            applySelectedPartialPercent();
        }
    }

    /**
     * 20/30/40/50% is always a percentage of the original, fixed Grand
     * Total - never of whatever remains after an earlier verified payment,
     * so a guest's "20%" always means the same peso amount no matter how
     * many payments came before it (per spec: "Second verified payment: 20%
     * of the original Grand Total"). The only adjustment allowed is a cap at
     * the remaining balance, so a percentage chip can never push total paid
     * past the Grand Total - a full remaining-balance payment still goes
     * through Full Payment mode above, which already charges exactly what's
     * owed rather than the original total again.
     */
    private void applySelectedPartialPercent() {
        double percentOfGrandTotal = Math.round(grandTotalValue * selectedPartialPercent * 100.0) / 100.0;
        payNowValue = Math.min(percentOfGrandTotal, remainingDueValue());
        if (etAmountToPay != null) {
            etAmountToPay.setText(String.format(Locale.US, "%.2f", payNowValue));
        }
        updateAmountToPay();
        validateAmount();
    }

    /**
     * Locks the chip selector to a stored percentage/amount ONLY while that
     * exact submission is still awaiting receptionist verification
     * (isPaymentPendingVerification()) - there's genuinely nothing new to
     * decide until it's verified or rejected, so showing an editable
     * selector next to an in-flight submission would let the guest submit a
     * second, conflicting payment before the first is even resolved.
     *
     * This used to lock permanently onto whatever percentage the reservation's
     * FIRST-ever GCash payment used, for every later Pay Now visit for the
     * rest of the reservation's life - fine for a single one-shot payment,
     * but it meant a guest who paid 20% first could never choose 30/40/50/
     * 100% for a second payment against the remaining balance once that
     * first payment was verified: getSelectedPaymentPercentage()/
     * getRequiredPaymentAmount() stay populated with that first payment's
     * values (the reservation-level fields they're read from were never
     * designed to reset between separate payments), so the old
     * `storedPercentage != null` check alone kept firing forever. Requiring
     * isPaymentPendingVerification() too means a verified or rejected prior
     * payment now correctly falls through to the interactive selector below.
     */
    private boolean renderStoredPaymentPercentageIfPresent() {
        Double storedPercentage = currentBooking != null ? currentBooking.getSelectedPaymentPercentage() : null;
        Double storedAmount = currentBooking != null ? currentBooking.getRequiredPaymentAmount() : null;
        boolean submissionPendingVerification = currentBooking != null && currentBooking.isPaymentPendingVerification();

        if (!submissionPendingVerification || storedPercentage == null || storedAmount == null) {
            if (cgPaymentAmount != null) cgPaymentAmount.setVisibility(View.VISIBLE);
            if (tvPartialPercentLabel != null) tvPartialPercentLabel.setVisibility(View.VISIBLE);
            if (tvSelectedPaymentReadOnly != null) tvSelectedPaymentReadOnly.setVisibility(View.GONE);
            return false;
        }

        isFullPaymentMode = storedPercentage >= 100;
        if (!isFullPaymentMode) selectedPartialPercent = PaymentPercentageUtil.apiPercentageToFraction(storedPercentage);
        payNowValue = storedAmount;

        if (etAmountToPay != null) {
            etAmountToPay.setText(String.format(Locale.US, "%.2f", payNowValue));
            etAmountToPay.setFocusable(false);
            etAmountToPay.setFocusableInTouchMode(false);
            etAmountToPay.setCursorVisible(false);
            etAmountToPay.setClickable(false);
        }
        if (cgPaymentAmount != null) cgPaymentAmount.setVisibility(View.GONE);
        if (tvPartialPercentLabel != null) tvPartialPercentLabel.setVisibility(View.GONE);
        if (tvSelectedPaymentReadOnly != null) {
            tvSelectedPaymentReadOnly.setVisibility(View.VISIBLE);
            String percentText = storedPercentage == Math.floor(storedPercentage)
                    ? String.valueOf(storedPercentage.intValue())
                    : String.valueOf(storedPercentage);
            tvSelectedPaymentReadOnly.setText(getString(R.string.selected_payment_readonly_format,
                    percentText, String.format(Locale.US, "%,.2f", payNowValue)));
        }
        updateAmountToPay();
        validateAmount();
        return true;
    }

    /**
     * The 20/30/40/50/100 value to send with a brand-new GCash reservation's
     * createReservation() call, captured once here (from the chip group this
     * same screen already shows before the reservation exists) so the
     * backend can store it and payment.xml never has to ask again on a
     * later Pay Now visit (see loadBookingData()'s read-only rendering).
     */
    private int selectedGcashPercentageForRequest() {
        return isFullPaymentMode ? 100 : PaymentPercentageUtil.fractionToApiPercentage(selectedPartialPercent);
    }

    /**
     * Full Payment must exactly match the total (validated live on every keystroke);
     * Partial Payment is always exactly one of the fixed percentages, so it's always
     * valid once a booking has loaded. Disables Proceed/Complete on mismatch.
     */
    private boolean validateAmount() {
        boolean valid;
        if (isFullPaymentMode) {
            double due = remainingDueValue();
            valid = due > 0 && Math.abs(payNowValue - due) <= 0.009;
            if (tvAmountError != null) {
                tvAmountError.setText(getString(R.string.error_full_amount_mismatch,
                        String.format(Locale.US, "₱%,.2f", due)));
                tvAmountError.setVisibility(valid ? View.GONE : View.VISIBLE);
            }
        } else {
            valid = payNowValue > 0;
            if (tvAmountError != null) tvAmountError.setVisibility(View.GONE);
        }
        if (proceedToGcashButton != null) proceedToGcashButton.setEnabled(valid);
        return valid;
    }

    private double parseAmount(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * What's actually still owed right now - grandTotalValue is the
     * booking's original full price and never shrinks, so every payment
     * amount calculation (Full Payment auto-fill/validation, Partial
     * Payment percentages, the "balance after this payment" preview) must
     * be based on this, not the raw total, or a guest revisiting a
     * partially-paid booking either gets asked to pay the full price again
     * (overpaying) or can never satisfy Full Payment's exact-match check at
     * all (permanently blocked).
     */
    private double remainingDueValue() {
        return Math.max(0, grandTotalValue - alreadyPaidValue);
    }

    private void updateAmountToPay() {
        double balance = remainingDueValue() - payNowValue;
        if (tvAmountToPay != null) {
            tvAmountToPay.setText(String.format(Locale.US, "₱%,.2f", payNowValue));
        }
        if (tvRemainingBalance != null) {
            if (balance <= 0.009) {
                tvRemainingBalance.setText(R.string.no_balance_label);
            } else {
                tvRemainingBalance.setText(String.format(Locale.US, "₱%,.2f", balance));
            }
        }
        if (tvPortalAmount != null) {
            tvPortalAmount.setText(String.format(Locale.US, "₱%,.2f", payNowValue));
        }
    }

    private static final long MAX_RECEIPT_SIZE_BYTES = 50L * 1024 * 1024;

    private void setupReceiptUpload() {
        receiptPickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                String mime = getContentResolver().getType(uri);
                // Matches the backend's own mimes:jpeg,png,jpg,webp validation
                // (Api\PaymentController::store()) - reject up front with a
                // friendly message instead of letting a mismatched format
                // (GIF/BMP/HEIC...) fail later as a generic server error.
                if (mime != null && !mime.equalsIgnoreCase("image/jpeg") && !mime.equalsIgnoreCase("image/png")
                        && !mime.equalsIgnoreCase("image/webp")) {
                    Toast.makeText(this, R.string.error_receipt_invalid_format, Toast.LENGTH_LONG).show();
                    return;
                }
                long size = queryFileSize(uri);
                if (size > MAX_RECEIPT_SIZE_BYTES) {
                    Toast.makeText(this, R.string.error_receipt_too_large, Toast.LENGTH_LONG).show();
                    return;
                }
                receiptUri = uri;
                ivReceiptPreview.setImageURI(uri);
                cardReceiptPreview.setVisibility(View.VISIBLE);
                tvReceiptStatus.setText(R.string.receipt_attached_success);
                tvReceiptStatus.setTextColor(getResources().getColor(R.color.velocity_green_primary, getTheme()));
                if (layoutReceiptActions != null) layoutReceiptActions.setVisibility(View.VISIBLE);
                if (btnUploadReceipt != null) btnUploadReceipt.setVisibility(View.GONE);
                updateSubmitButtonState();
            }
        });
        if (btnUploadReceipt != null) {
            btnUploadReceipt.setOnClickListener(v -> receiptPickerLauncher.launch("image/*"));
        }
        if (btnReplaceReceipt != null) {
            btnReplaceReceipt.setOnClickListener(v -> receiptPickerLauncher.launch("image/*"));
        }
        if (btnRemoveReceipt != null) {
            btnRemoveReceipt.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.confirm_remove_receipt_title)
                    .setMessage(R.string.confirm_remove_receipt_msg)
                    .setPositiveButton(R.string.confirm_dialog_positive, (d, w) -> removeReceipt())
                    .setNegativeButton(R.string.cancel_label, null)
                    .show());
        }
    }

    /**
     * Picked-file size via the content provider's OpenableColumns.SIZE
     * (no need to actually stream the file) - -1 if the provider doesn't
     * report a size, in which case the 50MB cap is skipped rather than
     * blocking a legitimate receipt on an incomplete provider response.
     */
    private long queryFileSize(android.net.Uri uri) {
        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);
                if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                    return cursor.getLong(sizeIndex);
                }
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private String queryFileName(android.net.Uri uri) {
        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1 && !cursor.isNull(nameIndex)) {
                    return cursor.getString(nameIndex);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void showReceiptPreviewDialog() {
        if (receiptUri == null) return;
        ImageView fullImage = new ImageView(this);
        fullImage.setAdjustViewBounds(true);
        fullImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        fullImage.setImageURI(receiptUri);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        fullImage.setPadding(padding, padding, padding, padding);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.view_receipt)
                .setView(fullImage)
                .setPositiveButton(R.string.close_label, null)
                .show();
    }

    private void removeReceipt() {
        receiptUri = null;
        if (ivReceiptPreview != null) ivReceiptPreview.setImageDrawable(null);
        if (cardReceiptPreview != null) cardReceiptPreview.setVisibility(View.GONE);
        if (layoutReceiptActions != null) layoutReceiptActions.setVisibility(View.GONE);
        if (btnUploadReceipt != null) btnUploadReceipt.setVisibility(View.VISIBLE);
        if (tvReceiptStatus != null) {
            tvReceiptStatus.setText(R.string.receipt_not_attached);
            tvReceiptStatus.setTextColor(getResources().getColor(R.color.velocity_text_secondary, getTheme()));
        }
        updateSubmitButtonState();
    }

    private void setupGcashNumberInput() {
        if (etGcashNumber == null) return;
        etGcashNumber.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                // Re-entrancy guard, same pattern as the reference-number watcher below -
                // the s.replace() call re-fires this same watcher.
                if (isFormattingGcashNumber) return;
                isFormattingGcashNumber = true;

                String raw = s.toString();
                String normalized = normalizeGcashLocalNumber(raw);
                if (!normalized.equals(raw)) {
                    s.replace(0, s.length(), normalized);
                    etGcashNumber.setSelection(normalized.length());
                }

                isFormattingGcashNumber = false;
                updateSubmitButtonState();
            }
        });
        if (etGcashReferenceNumber != null) {
            etGcashReferenceNumber.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(android.text.Editable s) {
                    // Re-entrancy guard: the s.replace() below re-fires this same
                    // watcher - without this flag stripping a bad character would
                    // recurse into itself.
                    if (isFormattingGcashReference) return;
                    isFormattingGcashReference = true;

                    int cursor = etGcashReferenceNumber.getSelectionStart();
                    String raw = s.toString();
                    // Track the cursor by how many actual digits precede it (not raw
                    // offset) so stripping a pasted separator/letter doesn't make the
                    // caret jump to an unrelated position.
                    int digitsBeforeCursor = 0;
                    for (int i = 0; i < cursor && i < raw.length(); i++) {
                        if (Character.isDigit(raw.charAt(i))) digitsBeforeCursor++;
                    }

                    // Step 3 stays raw digits only, continuously, with no live
                    // space/hyphen insertion - grouping is applied only when the
                    // value is displayed for review in Step 5.
                    String digits = GcashReferenceFormatter.digitsOnly(raw);
                    if (digits.length() > 13) digits = digits.substring(0, 13);

                    if (!digits.equals(raw)) {
                        s.replace(0, s.length(), digits);
                    }

                    etGcashReferenceNumber.setSelection(Math.min(digitsBeforeCursor, digits.length()));

                    isFormattingGcashReference = false;
                    updateSubmitButtonState();

                    // Once the guest corrects the value to exactly 13 digits, clear any
                    // error shown from a previous invalid attempt right away instead of
                    // making them blur the field or press Next again.
                    if (digits.length() == 13 && tilGcashReferenceNumber != null && tilGcashReferenceNumber.isErrorEnabled()) {
                        setGcashReferenceError(null);
                    }
                }
            });
            etGcashReferenceNumber.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus || gcashReferenceDigitsOnly().isEmpty()) return;
                // Validate on blur only, and only once the guest has actually typed
                // something - an untouched/empty field must not show an error just
                // from tapping in and back out.
                Integer error = gcashReferenceValidationError();
                if (error != null) setGcashReferenceError(getString(error));
            });
        }
    }

    private boolean isValidGcashNumber(String number) {
        return number != null && number.matches("9\\d{9}");
    }

    /**
     * Normalizes whatever the guest typed or pasted into this field (it sits after a
     * fixed "+63 " prefix, so only the local part is entered here) down to the
     * 10-digit "9XXXXXXXXX" shape isValidGcashNumber()/the backend both expect.
     * Handles every shape a guest might reasonably type/paste - 09XXXXXXXXX (drops
     * the leading 0), +639XXXXXXXXX/639XXXXXXXXX (drops the 63 country code the "+63"
     * prefix already shows), or the bare 9XXXXXXXXX this field technically wants -
     * so all of them land on the same stored value instead of silently truncating
     * (there is no maxLength on this field anymore; the cap here is the only limit).
     */
    private String normalizeGcashLocalNumber(String raw) {
        String digits = GcashReferenceFormatter.digitsOnly(raw);
        // A real local number always starts with 9 (PH mobile prefixes), so a leading
        // "0" or "63" can only ever be the 09.../+63639... form the guest typed or
        // pasted, never a genuine first digit - safe to strip unconditionally,
        // including mid-typing, rather than waiting for a specific final length.
        if (digits.startsWith("63")) {
            digits = digits.substring(2);
        } else if (digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        if (digits.length() > 10) digits = digits.substring(0, 10);
        return digits;
    }

    private String gcashReferenceDigitsOnly() {
        return etGcashReferenceNumber != null && etGcashReferenceNumber.getText() != null
                ? GcashReferenceFormatter.digitsOnly(etGcashReferenceNumber.getText().toString()) : "";
    }

    /**
     * Single source of truth for the "must be exactly 13 digits" rule - shared by the
     * Step 3 Next button and the final pre-submit re-check so both enforce the identical
     * rule and show the identical message, instead of two hand-written copies drifting apart.
     * Reuses gcashReferenceDigitsOnly() (the same extraction the formatter/submission path
     * uses) rather than a second hand-rolled strip, so this can never disagree with what
     * actually gets sent to the server.
     */
    private Integer gcashReferenceValidationError() {
        String raw = etGcashReferenceNumber != null && etGcashReferenceNumber.getText() != null
                ? etGcashReferenceNumber.getText().toString().trim() : "";
        if (raw.isEmpty()) return R.string.error_gcash_reference_required;
        String digits = gcashReferenceDigitsOnly();
        if (digits.length() != raw.replace(" ", "").length()) return R.string.error_gcash_reference_numeric;
        if (digits.length() != 13) return R.string.error_gcash_reference_length;
        return null;
    }

    /** Routes the Step 3 field's error through the TextInputLayout (inline, below the box) instead of the bare EditText popup, and restores the helper text once cleared. */
    private void setGcashReferenceError(String message) {
        if (tilGcashReferenceNumber == null) return;
        if (message != null) {
            tilGcashReferenceNumber.setErrorEnabled(true);
            tilGcashReferenceNumber.setError(message);
        } else {
            tilGcashReferenceNumber.setError(null);
            tilGcashReferenceNumber.setErrorEnabled(false);
        }
    }

    /**
     * Keeps every step's own Next button (plus the final Submit on Step 5) in sync as the
     * guest types/attaches a receipt - each Next only needs its own step's field(s) valid,
     * while Submit still requires all three (defense-in-depth, mirrors the pre-wizard
     * single-form behavior this replaces).
     */
    private void updateSubmitButtonState() {
        String number = etGcashNumber != null && etGcashNumber.getText() != null ? etGcashNumber.getText().toString().trim() : "";
        boolean referenceValid = gcashReferenceDigitsOnly().length() == 13;
        setButtonEnabled(btnGcashStep2Next, isValidGcashNumber(number));
        setButtonEnabled(btnGcashStep3Next, referenceValid);
        setButtonEnabled(btnGcashStep4Next, receiptUri != null);
        boolean valid = isValidGcashNumber(number) && referenceValid && receiptUri != null;
        setButtonEnabled(completePaymentButton, valid);
    }

    private void setButtonEnabled(MaterialButton button, boolean enabled) {
        if (button == null) return;
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.5f);
    }

    private void setupPaymentVerificationActions() {
        if (btnCancelPayment != null) {
            btnCancelPayment.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.confirm_cancel_payment_title)
                    .setMessage(R.string.confirm_cancel_payment_msg)
                    .setPositiveButton(R.string.confirm_dialog_positive, (d, w) -> cancelPendingPayment())
                    .setNegativeButton(R.string.cancel_label, null)
                    .show());
        }
        if (btnConvertToReservation != null) {
            btnConvertToReservation.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.confirm_convert_reservation_title)
                    .setMessage(R.string.confirm_convert_reservation_msg)
                    .setPositiveButton(R.string.confirm_dialog_positive, (d, w) -> convertPendingPaymentToReservation())
                    .setNegativeButton(R.string.cancel_label, null)
                    .show());
        }
        if (btnResubmitPayment != null) {
            btnResubmitPayment.setOnClickListener(v -> {
                receiptUri = null;
                if (etGcashNumber != null) etGcashNumber.setText("");
                if (etGcashReferenceNumber != null) etGcashReferenceNumber.setText("");
                setGcashReferenceError(null);
                removeReceipt();
                if (layoutGcashMacroSteps != null) layoutGcashMacroSteps.setVisibility(View.VISIBLE);
                if (cardGcashSubmissionForm != null) cardGcashSubmissionForm.setVisibility(View.VISIBLE);
                if (cardPaymentStatusBanner != null) cardPaymentStatusBanner.setVisibility(View.GONE);
                currentGcashStep = 1;
                renderGcashStep();
            });
        }
    }

    private void cancelPendingPayment() {
        if (currentBooking == null || currentBooking.getLatestPaymentId() == null) return;
        // Same double-tap guard as submitPaymentToServer()/submitCashPaymentToServer() -
        // this dialog has no loading spinner to visually block a fast repeat tap.
        if (isSubmittingPayment) return;
        isSubmittingPayment = true;
        repository.cancelPayment(currentBooking.getLatestPaymentId(), new RoomRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                Toast.makeText(PaymentActivity.this, R.string.cancel_payment_success_msg, Toast.LENGTH_LONG).show();
                openScreen(TransactionHistoryActivity.class);
            }

            @Override
            public void onError(String message) {
                isSubmittingPayment = false;
                Toast.makeText(PaymentActivity.this, getString(R.string.error_payment_submission_failed_format, message), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void convertPendingPaymentToReservation() {
        if (currentBooking == null || currentBooking.getLatestPaymentId() == null) return;
        if (isSubmittingPayment) return;
        isSubmittingPayment = true;
        repository.voidPayment(currentBooking.getLatestPaymentId(), new RoomRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                Toast.makeText(PaymentActivity.this, R.string.convert_to_reservation_success_msg, Toast.LENGTH_LONG).show();
                Intent intent = new Intent(PaymentActivity.this, BookingAndReservationActivity.class);
                intent.putExtra(BookingAndReservationActivity.EXTRA_OPEN_SECTION, BookingAndReservationActivity.SECTION_RESERVATION);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(String message) {
                isSubmittingPayment = false;
                Toast.makeText(PaymentActivity.this, getString(R.string.error_payment_submission_failed_format, message), Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Renders the Pending Verification / Verified / Rejected banner, hiding the submission form while there's nothing left for the guest to do. */
    private void renderPaymentVerificationStatus(Booking b) {
        if (cardPaymentStatusBanner == null) return;
        String status = b != null ? b.getPaymentVerificationStatus() : null;

        if (status == null || status.isEmpty()) {
            cardPaymentStatusBanner.setVisibility(View.GONE);
            if (layoutGcashMacroSteps != null) layoutGcashMacroSteps.setVisibility(View.VISIBLE);
            if (cardGcashSubmissionForm != null) cardGcashSubmissionForm.setVisibility(View.VISIBLE);
            renderGcashStep();
            return;
        }

        cardPaymentStatusBanner.setVisibility(View.VISIBLE);
        boolean pending = "pending_verification".equalsIgnoreCase(status) || "pending".equalsIgnoreCase(status);
        boolean verified = "verified".equalsIgnoreCase(status);
        boolean rejected = "rejected".equalsIgnoreCase(status);

        if (verified) {
            setStatusBannerIcon(R.drawable.ic_check_circle, R.color.velocity_green_primary, R.color.velocity_green_soft);
            tvPaymentStatusBannerTitle.setText(R.string.status_verified_title);
            tvPaymentStatusBannerMessage.setText(R.string.status_verified_message);
            if (layoutPendingPaymentActions != null) layoutPendingPaymentActions.setVisibility(View.GONE);
            if (btnResubmitPayment != null) btnResubmitPayment.setVisibility(View.GONE);
            if (layoutGcashMacroSteps != null) layoutGcashMacroSteps.setVisibility(View.GONE);
            if (cardGcashSubmissionForm != null) cardGcashSubmissionForm.setVisibility(View.GONE);
        } else if (rejected) {
            setStatusBannerIcon(R.drawable.ic_close, R.color.velocity_red_primary, R.color.velocity_red_soft);
            tvPaymentStatusBannerTitle.setText(R.string.status_rejected_title);
            String reason = b.getRejectionReason();
            String message = getString(R.string.status_rejected_message_generic);
            if (reason != null && !reason.trim().isEmpty()) {
                message += "\n" + getString(R.string.status_rejected_reason_format, reason);
            }
            tvPaymentStatusBannerMessage.setText(message);
            if (layoutPendingPaymentActions != null) layoutPendingPaymentActions.setVisibility(View.GONE);
            if (btnResubmitPayment != null) btnResubmitPayment.setVisibility(View.VISIBLE);
            if (layoutGcashMacroSteps != null) layoutGcashMacroSteps.setVisibility(View.GONE);
            if (cardGcashSubmissionForm != null) cardGcashSubmissionForm.setVisibility(View.GONE);
        } else if (pending) {
            setStatusBannerIcon(R.drawable.ic_clock, R.color.velocity_orange_primary, R.color.velocity_orange_soft);
            tvPaymentStatusBannerTitle.setText(R.string.status_pending_verification_title);
            tvPaymentStatusBannerMessage.setText(R.string.status_pending_verification_message);
            if (layoutPendingPaymentActions != null) layoutPendingPaymentActions.setVisibility(View.VISIBLE);
            if (btnResubmitPayment != null) btnResubmitPayment.setVisibility(View.GONE);
            if (layoutGcashMacroSteps != null) layoutGcashMacroSteps.setVisibility(View.GONE);
            if (cardGcashSubmissionForm != null) cardGcashSubmissionForm.setVisibility(View.GONE);
        }
    }

    private void setStatusBannerIcon(int drawableRes, int tintColorRes, int bgColorRes) {
        if (ivPaymentStatusIcon == null) return;
        ivPaymentStatusIcon.setImageResource(drawableRes);
        ivPaymentStatusIcon.setImageTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(tintColorRes, getTheme())));
        ivPaymentStatusIcon.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(bgColorRes, getTheme())));
    }

    private void setupNavigationLogic() {
        if (proceedToGcashButton != null) {
            proceedToGcashButton.setOnClickListener(v -> {
                if (currentBooking == null && !isPendingBookingMode && !isPendingReservationMode) {
                    Toast.makeText(this, R.string.error_no_booking_data, Toast.LENGTH_SHORT).show();
                    return;
                }

                // A booking that's already fully paid must never be paid twice.
                // Not applicable in pending mode - nothing has been paid yet.
                if (!isPendingBookingMode && !isPendingReservationMode && currentBooking.getRemainingBalance() <= 0.009) {
                    Toast.makeText(this, R.string.no_balance_label, Toast.LENGTH_SHORT).show();
                    return;
                }

                // Partial Payment is always exactly one of the fixed 20/30/40/50%
                // chips; Full Payment must exactly match the total - both are
                // enforced live by validateAmount(), which also disables this
                // button on mismatch, but re-check here as a guard.
                if (!validateAmount()) {
                    return;
                }

                if (isCashSelected) {
                    showCashConfirmationDialog();
                    return;
                }

                // "Proceed & Pay Later" on a brand-new Reservation defers GCash
                // payment entirely - the Reservation already exists, so there's
                // nothing to submit yet. Skip the GCash portal/receipt form and
                // go straight to a pending confirmation instead.
                if (freshReservation) {
                    showReservationPendingConfirmationDialog();
                    return;
                }

                showPaymentConfirmationDialog();
            });
        }

        if (backToSummaryButton != null) {
            backToSummaryButton.setOnClickListener(v -> {
                gcashPortalSection.setVisibility(View.GONE);
                checkoutSummarySection.setVisibility(View.VISIBLE);
            });
        }

        if (completePaymentButton != null) {
            completePaymentButton.setOnClickListener(v -> processPayment());
        }

        if (tvViewReceiptAction != null) {
            tvViewReceiptAction.setOnClickListener(v -> showReceiptPreviewDialog());
        }
        if (ivReviewReceiptThumb != null) {
            ivReviewReceiptThumb.setOnClickListener(v -> showReceiptPreviewDialog());
        }
    }

    private void showPaymentConfirmationDialog() {
        double due = remainingDueValue();
        double balance = due - payNowValue;

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_payment_confirmation, null);
        TextView tvType = dialogView.findViewById(R.id.confirmPaymentType);
        TextView tvAmount = dialogView.findViewById(R.id.confirmAmount);
        TextView tvTotal = dialogView.findViewById(R.id.confirmTotalDue);
        TextView tvBalance = dialogView.findViewById(R.id.confirmRemainingBalance);

        tvType.setText(R.string.amount_to_pay_label);
        tvAmount.setText(String.format(Locale.US, "₱%,.2f", payNowValue));
        tvTotal.setText(String.format(Locale.US, "₱%,.2f", due));
        tvBalance.setText(balance <= 0.009 ? "₱0.00" : String.format(Locale.US, "₱%,.2f", balance));

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.confirm_payment_title)
                .setView(dialogView)
                .setPositiveButton(R.string.confirm_and_proceed, (dialog, which) -> {
                    checkoutSummarySection.setVisibility(View.GONE);
                    gcashPortalSection.setVisibility(View.VISIBLE);
                    updateAmountToPay();
                    currentGcashStep = 1;
                    renderGcashStep();
                })
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    /**
     * Shown instead of showPaymentConfirmationDialog() when "Proceed & Pay
     * Later" is tapped for a GCash Reservation. In pending-reservation mode
     * (the only case reachable now - see PendingReservationPayload) the
     * Reservation doesn't exist yet at all: this first creates it (with zero
     * payment calls - the purest "pay later" outcome) and only then shows
     * the confirmation. The legacy already-created branch is kept only as a
     * defensive fallback for EXTRA_FRESH_RESERVATION, which no caller sets anymore.
     */
    private void showReservationPendingConfirmationDialog() {
        if (isPendingReservationMode) {
            createPendingReservationsThenShowPendingDialog();
            return;
        }
        showReservationPendingDialogForId(currentBooking != null ? currentBooking.getId() : "");
    }

    private void showReservationPendingDialogForId(String reservationId) {
        // finalizePayment() (not a hardcoded destination) - it already
        // correctly branches on the server-refreshed isHasBooking()/
        // isPendingReservationMode state (see its own docblock), same as the
        // Cash Pay Later dialog above (onPendingReservationCashCreated())
        // already does. Both callers of this dialog reach it either with no
        // payment attached at all (GCash Pay Later, create-only) or a
        // payment that failed to attach - neither ever actually converted
        // the reservation, so this always lands back on Reservations, not
        // the unrelated Dashboard a guest would otherwise be dropped on.
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.reservation_pending_title)
                .setMessage(getString(R.string.reservation_pending_thank_you_msg, reservationId))
                .setPositiveButton(R.string.confirmed_button, (dialog, which) -> finalizePayment())
                .setCancelable(false)
                .show();
    }


    /** Uploads the Senior/PWD ID photo (if any) against a just-created reservation before moving on - non-fatal either way, same tolerance as the rest of this pending-creation flow. */
    private void uploadIdCardIfNeeded(Booking created, Runnable onDone) {
        BookingWizardState state = pendingWizardState;
        if (state == null || "None".equals(state.idCardType) || state.idCardImageUri == null) {
            onDone.run();
            return;
        }
        repository.uploadIdCard(created.getId(), state.idCardImageUri, new RoomRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                onDone.run();
            }

            @Override
            public void onError(String message) {
                onDone.run();
            }
        });
    }

    /**
     * Proceed & Pay Later: create the reservation with no payment call at
     * all, then show the existing pending-confirmation dialog. One single
     * call sends every selected room type/quantity and every selected
     * amenity as one atomic request (see RoomRepository#createReservation(
     * List, ...)'s own doc) - replaces the old one-call-per-room-type
     * sequential loop, which produced N separate, client-side-linked-only
     * Reservation rows for a multi-room-type selection (see
     * MULTI_ROOM_TRANSACTION_BACKEND_SPEC.md).
     */
    private void createPendingReservationsThenShowPendingDialog() {
        if (isSubmittingPayment) return;
        isSubmittingPayment = true;

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_loading, null);
        TextView tvMsg = dialogView.findViewById(R.id.loadingMessage);
        tvMsg.setText(R.string.processing_payment);
        builder.setView(dialogView);
        builder.setCancelable(false);
        AlertDialog dialog = builder.create();
        dialog.show();

        List<List<Room>> groups = new ArrayList<>(pendingWizardState.selectedRoomsGroupedByType().values());
        // Reachable only via the "Proceed & Pay Later" branch (freshReservation
        // always true for a new reservation) - GCash-only, no cash "create
        // only" path exists (Cash always goes through submitCashPaymentToServer()
        // instead, which registers the cash intent in the same tap - see below).
        repository.createReservation(groups, pendingWizardState.checkIn, pendingWizardState.checkOut,
                pendingWizardState.adults, pendingWizardState.children,
                pendingWizardState.guestFirstName, pendingWizardState.guestMiddleName, pendingWizardState.guestLastName,
                pendingWizardState.idCardType, pendingWizardState.additionalGuests, pendingWizardState.selectedAmenities,
                "gcash", UUID.randomUUID().toString(),
                new RoomRepository.RepositoryCallback<Booking>() {
                    @Override
                    public void onSuccess(Booking created) {
                        isSubmittingPayment = false;
                        currentBooking = created;
                        bookingId = created.getId();
                        uploadIdCardIfNeeded(created, () -> {
                            dismissSafely(dialog);
                            showReservationPendingDialogForId(created.getId());
                        });
                    }

                    @Override
                    public void onError(String message) {
                        isSubmittingPayment = false;
                        dismissSafely(dialog);
                        Toast.makeText(PaymentActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * Cash confirmation - no receipt/reference number step, since cash
     * can't be verified online (staff reconcile it in person, same as the
     * website). Submits directly on confirm.
     */
    private void showCashConfirmationDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_payment_confirmation, null);
        TextView tvType = dialogView.findViewById(R.id.confirmPaymentType);
        TextView tvAmount = dialogView.findViewById(R.id.confirmAmount);
        TextView tvTotal = dialogView.findViewById(R.id.confirmTotalDue);
        TextView tvBalance = dialogView.findViewById(R.id.confirmRemainingBalance);

        double due = remainingDueValue();
        double balance = due - payNowValue;

        tvType.setText(R.string.payment_method_cash);
        tvAmount.setText(payNowValue > 0.009 ? String.format(Locale.US, "₱%,.2f", payNowValue) : getString(R.string.no_balance_label));
        tvTotal.setText(String.format(Locale.US, "₱%,.2f", due));
        tvBalance.setText(balance <= 0.009 ? "₱0.00" : String.format(Locale.US, "₱%,.2f", balance));

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.confirm_payment_title)
                .setView(dialogView)
                .setPositiveButton(R.string.confirm_and_proceed, (dialog, which) -> submitCashPaymentToServer())
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    private void submitCashPaymentToServer() {
        if (isSubmittingPayment) {
            return;
        }
        isSubmittingPayment = true;

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_loading, null);
        TextView tvMsg = dialogView.findViewById(R.id.loadingMessage);
        tvMsg.setText(R.string.processing_payment);
        builder.setView(dialogView);
        builder.setCancelable(false);
        AlertDialog dialog = builder.create();
        dialog.show();

        if (isPendingReservationMode) {
            List<List<Room>> groups = new ArrayList<>(pendingWizardState.selectedRoomsGroupedByType().values());
            String paymentType = isFullPaymentMode ? "full" : "partial";
            // One single call sends every selected room type/quantity and
            // every selected amenity as one atomic request (see
            // RoomRepository#createReservation(List, ...)'s own doc) -
            // replaces the old one-call-per-room-type sequential loop.
            repository.createReservation(groups, pendingWizardState.checkIn, pendingWizardState.checkOut,
                    pendingWizardState.adults, pendingWizardState.children,
                    pendingWizardState.guestFirstName, pendingWizardState.guestMiddleName, pendingWizardState.guestLastName,
                    pendingWizardState.idCardType, pendingWizardState.additionalGuests, pendingWizardState.selectedAmenities,
                    "cash", UUID.randomUUID().toString(),
                    new RoomRepository.RepositoryCallback<Booking>() {
                        @Override
                        public void onSuccess(Booking created) {
                            uploadIdCardIfNeeded(created, () ->
                                    repository.submitPayment(created.getId(), "cash", paymentType, null, payNowValue,
                                            selectedGcashPercentageForRequest(), new RoomRepository.RepositoryCallback<Void>() {
                                                @Override
                                                public void onSuccess(Void result) {
                                                    onPendingReservationCashCreated(dialog, created, false);
                                                }

                                                @Override
                                                public void onError(String message) {
                                                    // Reservation WAS created - don't roll it back, it just stays unpaid.
                                                    onPendingReservationCashCreated(dialog, created, true);
                                                }
                                            }));
                        }

                        @Override
                        public void onError(String message) {
                            isSubmittingPayment = false;
                            dismissSafely(dialog);
                            Toast.makeText(PaymentActivity.this, getString(R.string.error_payment_submission_failed_format, message), Toast.LENGTH_LONG).show();
                        }
                    });
            return;
        }

        // Cash has no reference number - the backend only requires one for
        // GCash (see Api\PaymentController).
        String paymentType = isFullPaymentMode ? "full" : "partial";
        repository.submitPayment(bookingId, "cash", paymentType, null, payNowValue, selectedGcashPercentageForRequest(), new RoomRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                dismissSafely(dialog);
                new MaterialAlertDialogBuilder(PaymentActivity.this)
                        .setTitle(R.string.payment_success_title)
                        .setMessage(R.string.cash_payment_submitted_msg)
                        .setPositiveButton(R.string.close_label, (d, which) -> finalizePayment())
                        .setCancelable(false)
                        .show();
            }

            @Override
            public void onError(String message) {
                isSubmittingPayment = false;
                dismissSafely(dialog);
                Toast.makeText(PaymentActivity.this, getString(R.string.error_payment_submission_failed_format, message), Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Cash pending-reservation creation completed (the single create() call
     * above already produced exactly one Reservation) - registers the cash
     * intent immediately, consistent with every other Cash flow in this app.
     * A Reservation created here never auto-converts (only GCash does), so
     * it stays in the Reservation list until the guest walks in.
     */
    private void onPendingReservationCashCreated(AlertDialog dialog, Booking created, boolean paymentAttachFailed) {
        // isSubmittingPayment deliberately stays true here (matches every
        // other success path in this class) - the confirmation dialog below
        // leads to finalizePayment(), which navigates away, so there's no
        // "resubmit" affordance left on this screen to guard against.
        dismissSafely(dialog);
        currentBooking = created;
        bookingId = created.getId();
        if (paymentAttachFailed) {
            Toast.makeText(this, R.string.reservation_payment_attach_partial_failure_message, Toast.LENGTH_LONG).show();
        }
        // Same "Thank you for trusting us" treatment as Proceed & Pay Later -
        // the reservation is genuinely created either way, cash just isn't
        // verified online (see the class-level Cash confirmation comment).
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.reservation_pending_title)
                .setMessage(getString(R.string.reservation_pending_thank_you_msg, currentBooking.getId()))
                .setPositiveButton(R.string.confirmed_button, (d, which) -> finalizePayment())
                .setCancelable(false)
                .show();
    }

    private void loadBookingData() {
        currentBooking = null;
        if (bookingId != null) {
            for (Booking b : repository.getBookings()) {
                if (b.getId().equals(bookingId)) {
                    currentBooking = b;
                    break;
                }
            }
        }

        // Defense in depth: a Booking transaction (isHasBooking() true - the same
        // server-authoritative flag the Payment Status card's Pay Now gating uses)
        // must never reach the payment form here, whether Full or Partial Payment.
        // The only legitimate entry points already only ever pass a still-
        // unconverted Reservation's id, but this blocks any other/future/manual
        // deep link from bypassing that UI-level restriction.
        if (currentBooking != null && currentBooking.isHasBooking()) {
            Toast.makeText(this, R.string.error_booking_not_payable, Toast.LENGTH_LONG).show();
            currentBooking = null;
            bookingId = null;
        }

        if (currentBooking != null) {
            // Same correction the reservation list and View Details already
            // apply (RoomRepository#correctPendingReservationTotal()) before
            // this screen reads getTotalAmount() - without it, a still-
            // unconverted Reservation's total here would be room-cost-only
            // (excluding paid amenities), while every other screen already
            // shows the amenities-inclusive total from Billing Summary. It
            // mutates currentBooking in place, so populateExistingTransactionSummary()
            // (run either way, since this is a best-effort correction) always
            // reads the corrected value.
            repository.correctPendingReservationTotal(currentBooking, new RoomRepository.RepositoryCallback<Booking>() {
                @Override
                public void onSuccess(Booking corrected) {
                    populateExistingTransactionSummary();
                }

                @Override
                public void onError(String message) {
                    populateExistingTransactionSummary();
                }
            });
        } else {
            if (checkoutSummarySection != null) checkoutSummarySection.setVisibility(View.GONE);
            if (emptyStateSection != null) emptyStateSection.setVisibility(View.VISIBLE);
            updateBookReserveCtaVisibility(false);
            if (bookingId != null) {
                // A specific booking was requested (e.g. via a dashboard/list "Pay
                // Now" tap) but is no longer in the cache - most likely it was
                // already paid or cancelled elsewhere in the meantime.
                Toast.makeText(this, R.string.error_no_active_booking, Toast.LENGTH_LONG).show();
                bookingId = null;
            }
        }
    }

    private void populateExistingTransactionSummary() {
        double amount = currentBooking.getTotalAmount();

        roomTotalValue = amount;
        grandTotalValue = amount;
        alreadyPaidValue = currentBooking.getAmountPaid();

        String formattedGrandTotal = String.format(Locale.US, "₱%,.2f", grandTotalValue);
        tvTotalAmount.setText(formattedGrandTotal);
        tvGrandTotal.setText(formattedGrandTotal);
        // A still-pending Reservation must never be labeled "Booking" here -
        // isHasBooking() is the same server-authoritative flag the rest of
        // this Activity already trusts (see loadBookingData()'s defense-in-depth
        // check above), so this label and that gate can never disagree.
        boolean hasBooking = currentBooking.isHasBooking();
        tvBookingRef.setText(hasBooking
                ? getString(R.string.booking_ref_label, currentBooking.getId())
                : getString(R.string.reservation_ref_label, currentBooking.getId()));
        tvPaymentStatus.setText(currentBooking.getStatus().toUpperCase(Locale.getDefault()));

        if (tvTransactionType != null) {
            tvTransactionType.setText(hasBooking
                    ? R.string.transaction_type_booking_payment
                    : R.string.transaction_type_reservation_payment);
        }
        renderRepresentativeSummary(currentBooking.getRepresentativeName());
        renderExistingBookingRoomsSummary(currentBooking);
        if (tvCheckInSummary != null) {
            tvCheckInSummary.setText(getString(R.string.payment_summary_checkin_format, currentBooking.getCheckInDate()));
        }
        if (tvCheckOutSummary != null) {
            tvCheckOutSummary.setText(getString(R.string.payment_summary_checkout_format, currentBooking.getCheckOutDate()));
        }
        if (tvNightsSummary != null) {
            int nights = (int) nightsBetweenDates(currentBooking.getCheckInDate(), currentBooking.getCheckOutDate());
            tvNightsSummary.setText(getResources().getQuantityString(R.plurals.payment_summary_nights_format, nights, nights));
        }
        renderExistingBookingAmenitiesSummary(currentBooking);
        if (!hasBooking) {
            // A still-unconverted Reservation has no Billing row yet, so
            // getRoomCharge()/getAmenityCharge()/getAmenities() all stay at
            // their zero/empty defaults (see ApiMapper's ReservationDto
            // mapping) - fetch the same itemized amenity data
            // correctPendingReservationTotal() already used to correct
            // getTotalAmount() above, independently, so this card can show a
            // real Room Charges/Amenities Total split and an itemized
            // amenity list instead of lumping everything under Room Charges.
            loadItemizedAmenitiesForPendingReservation(currentBooking);
        }

        // roomCharge/amenityCharge are reliably split for an already-converted
        // Booking (from the real Billing row - see ApiMapper). An unconverted
        // Reservation instead gets its split from loadItemizedAmenitiesForPendingReservation()
        // above, asynchronously - fall back to the full amount as Room Charges
        // until that resolves, rather than showing a wrong ₱0.00 Room Charges.
        double roomCharge = currentBooking.getRoomCharge();
        double amenityCharge = currentBooking.getAmenityCharge();
        boolean hasSplitFigures = roomCharge > 0.009 || amenityCharge > 0.009;
        double roomChargeDisplay = hasSplitFigures ? roomCharge : amount;
        if (tvRoomCharges != null) tvRoomCharges.setText(String.format(Locale.US, "₱%,.2f", roomChargeDisplay));
        if (tvAmenitiesTotalSummary != null) {
            tvAmenitiesTotalSummary.setText(String.format(Locale.US, "₱%,.2f", amenityCharge));
        }
        if (tvTotalAmountSummary != null) {
            tvTotalAmountSummary.setText(String.format(Locale.US, "₱%,.2f",
                    roomChargeDisplay + amenityCharge + currentBooking.getAdditionalGuestFee()));
        }
        boolean hasDiscount = currentBooking.getDiscountAmount() > 0.009;
        if (layoutDiscountSummary != null) layoutDiscountSummary.setVisibility(hasDiscount ? View.VISIBLE : View.GONE);
        if (hasDiscount && tvDiscountSummary != null) {
            tvDiscountSummary.setText(String.format(Locale.US, "-₱%,.2f", currentBooking.getDiscountAmount()));
        }
        if (tvAlreadyPaidSummary != null) {
            tvAlreadyPaidSummary.setText(String.format(Locale.US, "₱%,.2f", alreadyPaidValue));
        }
        if (tvOutstandingBalanceSummary != null) {
            tvOutstandingBalanceSummary.setText(String.format(Locale.US, "₱%,.2f", remainingDueValue()));
        }
        if (paymentBreakdownProgress != null) {
            int paidPercent = grandTotalValue > 0 ? (int) ((alreadyPaidValue / grandTotalValue) * 100) : 0;
            paymentBreakdownProgress.setProgress(Math.min(100, paidPercent));
        }

        applyPaymentAmountSelection();
        renderPaymentVerificationStatus(currentBooking);
        preselectPaymentMethod(currentBooking);
        if (checkoutSummarySection != null) checkoutSummarySection.setVisibility(View.VISIBLE);
        if (emptyStateSection != null) emptyStateSection.setVisibility(View.GONE);
        updateBookReserveCtaVisibility(true);
    }

    private long nightsBetweenDates(String checkIn, String checkOut) {
        return StayDateCalculator.nightsBetween(checkIn, checkOut);
    }

    /** Hides the Guest/Representative block entirely rather than showing a blank name - null/empty means neither name part was ever captured on this transaction. */
    private void renderRepresentativeSummary(String representativeName) {
        boolean hasName = representativeName != null && !representativeName.trim().isEmpty();
        if (layoutRepresentativeSummary != null) {
            layoutRepresentativeSummary.setVisibility(hasName ? View.VISIBLE : View.GONE);
        }
        if (hasName && tvRepresentativeSummary != null) {
            tvRepresentativeSummary.setText(representativeName);
        }
    }

    /**
     * Real itemized rooms (Booking#getRooms()) first - see
     * MULTI_ROOM_TRANSACTION_BACKEND_SPEC.md - falling back to a single
     * synthetic block built from this record's own legacy single-room fields
     * (every transaction today, since the backend doesn't return itemized
     * rooms yet). Deliberately never reads BookingGroupState's sibling
     * records here (unlike BookingAndReservationActivity's list/detail
     * screens) - this screen is always paying one specific Booking/
     * Reservation id, so only that one record's own rooms belong on it.
     */
    private void renderExistingBookingRoomsSummary(Booking booking) {
        if (layoutSelectedRoomsSummary == null) return;
        layoutSelectedRoomsSummary.removeAllViews();
        if (!booking.getRooms().isEmpty()) {
            for (BookingRoom room : booking.getRooms()) {
                layoutSelectedRoomsSummary.addView(buildRoomSummaryRow(
                        room.getRoomTypeName(), room.getQuantity(), room.getSubtotal()));
            }
        } else {
            String name = booking.getRoomName() != null && !booking.getRoomName().trim().isEmpty()
                    ? booking.getRoomName() : booking.getRoomType();
            int quantity = Math.max(1, booking.getRoomsRequested());
            double roomCharge = booking.getRoomCharge() > 0.009 ? booking.getRoomCharge() : booking.getTotalAmount();
            layoutSelectedRoomsSummary.addView(buildRoomSummaryRow(name, quantity, roomCharge));
        }
    }

    /**
     * Real itemized amenities (Booking#getAmenities()) first, then a single
     * unitemized line using the aggregate dollar total (getAmenityCharge() -
     * every transaction today, since the backend doesn't return itemized
     * amenities yet), then the "none selected" message only when there's
     * truly no paid-amenity charge at all.
     */
    private void renderExistingBookingAmenitiesSummary(Booking booking) {
        if (layoutSelectedAmenitiesSummary == null) return;
        layoutSelectedAmenitiesSummary.removeAllViews();
        boolean hasItemized = !booking.getAmenities().isEmpty();
        boolean hasUnitemizedCharge = !hasItemized && booking.getAmenityCharge() > 0.009;
        if (hasItemized) {
            for (BookingAmenity amenity : booking.getAmenities()) {
                layoutSelectedAmenitiesSummary.addView(buildAmenitySummaryRow(
                        amenity.getAmenityName(), amenity.getQuantity(), amenity.getSubtotal()));
            }
        } else if (hasUnitemizedCharge) {
            layoutSelectedAmenitiesSummary.addView(buildAmenitySummaryRow(
                    getString(R.string.payment_summary_amenity_unitemized_label), 1, booking.getAmenityCharge()));
        }
        if (tvNoAmenitiesSummary != null) {
            tvNoAmenitiesSummary.setVisibility(hasItemized || hasUnitemizedCharge ? View.GONE : View.VISIBLE);
        }
    }

    /**
     * Deliberately a separate, independent fetch rather than having
     * RoomRepository#correctPendingReservationTotal() stash this same data
     * onto the shared Booking object - that method's roomCharge/amenityCharge
     * fields are read by other screens (BookingDetailsActivity, PaymentReceiptActivity)
     * under the assumption that a non-zero value only ever came from a real
     * Billing row, never a client-side reconstruction for a still-pending
     * Reservation (see BookingDetailsActivity#buildPaymentSummarySection()'s
     * own doc comment) - mutating that shared state here would change what
     * those other screens show without being asked to. This screen keeps its
     * own copy of the split instead.
     */
    private void loadItemizedAmenitiesForPendingReservation(Booking booking) {
        repository.fetchRequestableAmenities(booking.getId(), new RoomRepository.RepositoryCallback<List<RequestableAmenityDto>>() {
            @Override
            public void onSuccess(List<RequestableAmenityDto> items) {
                // The guest may have left this booking behind (e.g. Cancel Payment
                // navigated away, or a different booking loaded) by the time this
                // resolves - only apply it to the summary still on screen.
                if (currentBooking != booking) return;
                renderPendingReservationAmenitiesSplit(booking, items);
            }

            @Override
            public void onError(String message) {
                // Best-effort only - Room Charges/Amenities Total/Selected Amenities
                // simply keep the full-amount-as-Room-Charges fallback already shown.
            }
        });
    }

    private void renderPendingReservationAmenitiesSplit(Booking booking, List<RequestableAmenityDto> items) {
        if (layoutSelectedAmenitiesSummary == null) return;
        layoutSelectedAmenitiesSummary.removeAllViews();
        double amenitiesTotal = 0;
        boolean hasAny = false;
        if (items != null) {
            for (RequestableAmenityDto item : items) {
                double price = Math.max(0, item.price);
                int quantity = Math.max(0, item.original_quantity);
                if (quantity <= 0) continue;
                double subtotal = price * quantity;
                amenitiesTotal += subtotal;
                hasAny = true;
                layoutSelectedAmenitiesSummary.addView(buildAmenitySummaryRow(item.amenity_name, quantity, subtotal));
            }
        }
        if (tvNoAmenitiesSummary != null) tvNoAmenitiesSummary.setVisibility(hasAny ? View.GONE : View.VISIBLE);

        // getTotalAmount() is already the amenities-inclusive corrected total by
        // this point (see loadBookingData()'s correctPendingReservationTotal()
        // call, which always resolves before populateExistingTransactionSummary()
        // runs) - subtracting this same itemized amenities total back out
        // recovers the room-only figure without double-counting either way.
        double roomChargeOnly = Math.max(0, booking.getTotalAmount() - amenitiesTotal);
        if (tvRoomCharges != null) tvRoomCharges.setText(String.format(Locale.US, "₱%,.2f", roomChargeOnly));
        if (tvAmenitiesTotalSummary != null) {
            tvAmenitiesTotalSummary.setText(String.format(Locale.US, "₱%,.2f", amenitiesTotal));
        }
        // Keep the Selected Room(s) block's own "Amount" line in sync with the
        // same room-only figure - it's still showing the pre-split full total
        // (booking.getRooms() is always empty for a pending Reservation, so
        // renderExistingBookingRoomsSummary() had no better figure to fall
        // back on before this split resolved).
        if (booking.getRooms().isEmpty() && layoutSelectedRoomsSummary != null) {
            layoutSelectedRoomsSummary.removeAllViews();
            String name = booking.getRoomName() != null && !booking.getRoomName().trim().isEmpty()
                    ? booking.getRoomName() : booking.getRoomType();
            int quantity = Math.max(1, booking.getRoomsRequested());
            layoutSelectedRoomsSummary.addView(buildRoomSummaryRow(name, quantity, roomChargeOnly));
        }
    }

    /** One vertical "• Room Name / Quantity: N Room(s) / Amount: ₱X,XXX.XX" block - see the Payment Summary card's own doc comment on why this is never a horizontal label/value row. */
    private View buildRoomSummaryRow(String roomName, int quantity, double amount) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = convertDpToPx(10);
        row.setLayoutParams(lp);

        TextView tvName = new TextView(this);
        tvName.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        tvName.setText(getString(R.string.payment_summary_room_name_bullet_format, roomName));
        tvName.setTextSize(14f);
        tvName.setTypeface(Typeface.DEFAULT_BOLD);
        tvName.setTextColor(getResources().getColor(R.color.velocity_text_primary, getTheme()));

        TextView tvQty = new TextView(this);
        tvQty.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        tvQty.setText(getResources().getQuantityString(R.plurals.room_qty_format, quantity, quantity));
        tvQty.setTextSize(13f);
        tvQty.setTextColor(getResources().getColor(R.color.velocity_text_secondary, getTheme()));

        TextView tvAmount = new TextView(this);
        tvAmount.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        tvAmount.setText(getString(R.string.payment_summary_room_amount_format, String.format(Locale.US, "₱%,.2f", amount)));
        tvAmount.setTextSize(13f);
        tvAmount.setTextColor(getResources().getColor(R.color.velocity_text_secondary, getTheme()));

        row.addView(tvName);
        row.addView(tvQty);
        row.addView(tvAmount);
        return row;
    }

    /** One "• Amenity Name (×N) — ₱X,XXX.XX" line, full card width so a long amenity name wraps naturally instead of being squeezed. */
    private View buildAmenitySummaryRow(String amenityName, int quantity, double amount) {
        TextView tv = new TextView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = convertDpToPx(6);
        tv.setLayoutParams(lp);
        tv.setText(getString(R.string.payment_summary_amenity_line_format, amenityName, quantity,
                String.format(Locale.US, "₱%,.2f", amount)));
        tv.setTextSize(13f);
        tv.setTextColor(getResources().getColor(R.color.velocity_text_primary, getTheme()));
        return tv;
    }

    // ---- Pending Booking/Reservation mode: no row exists yet, so the summary is computed straight from the reviewed wizard state instead of a repository lookup ----

    private long pendingWizardNights() {
        return pendingWizardState.nights();
    }

    private double pendingWizardRoomsTotal() {
        double total = 0;
        for (List<Room> group : pendingWizardState.selectedRoomsGroupedByType().values()) {
            total += group.get(0).getPricePerNight() * pendingWizardNights() * group.size();
        }
        return total;
    }

    private double pendingWizardAmenitiesTotal() {
        double total = 0;
        for (AddOnAmenity a : pendingWizardState.selectedAmenities) {
            total += a.getSubtotal();
        }
        return total;
    }

    /** Compact single-line "2× Deluxe Room, 1× Executive Room" summary - only used by the GCash step's own read-only Order Review card (tvReviewRoomSummary), which has room for one line, not the itemized Payment Summary card above (see renderPendingWizardRoomsSummary()). */
    private String pendingWizardRoomTypeCommaSummary() {
        StringBuilder sb = new StringBuilder();
        for (List<Room> group : pendingWizardState.selectedRoomsGroupedByType().values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(group.size()).append("× ").append(group.get(0).getName());
        }
        return sb.toString();
    }

    private void renderPendingWizardRoomsSummary() {
        if (layoutSelectedRoomsSummary == null) return;
        layoutSelectedRoomsSummary.removeAllViews();
        for (List<Room> group : pendingWizardState.selectedRoomsGroupedByType().values()) {
            Room representative = group.get(0);
            double subtotal = representative.getPricePerNight() * pendingWizardNights() * group.size();
            layoutSelectedRoomsSummary.addView(buildRoomSummaryRow(representative.getName(), group.size(), subtotal));
        }
    }

    private void renderPendingWizardAmenitiesSummary() {
        if (layoutSelectedAmenitiesSummary == null) return;
        layoutSelectedAmenitiesSummary.removeAllViews();
        boolean hasAmenities = !pendingWizardState.selectedAmenities.isEmpty();
        if (hasAmenities) {
            for (AddOnAmenity a : pendingWizardState.selectedAmenities) {
                layoutSelectedAmenitiesSummary.addView(buildAmenitySummaryRow(a.getName(), a.getQuantity(), a.getSubtotal()));
            }
        }
        if (tvNoAmenitiesSummary != null) {
            tvNoAmenitiesSummary.setVisibility(hasAmenities ? View.GONE : View.VISIBLE);
        }
    }

    private String pendingWizardRepresentativeName() {
        StringBuilder sb = new StringBuilder();
        if (pendingWizardState.guestFirstName != null) sb.append(pendingWizardState.guestFirstName.trim()).append(' ');
        if (pendingWizardState.guestMiddleName != null && !pendingWizardState.guestMiddleName.trim().isEmpty()) {
            sb.append(pendingWizardState.guestMiddleName.trim()).append(' ');
        }
        if (pendingWizardState.guestLastName != null) sb.append(pendingWizardState.guestLastName.trim());
        return sb.toString().trim().replaceAll("\\s+", " ");
    }

    private void loadPendingWizardSummary() {
        currentBooking = null;
        double roomsTotal = pendingWizardRoomsTotal();
        double amenitiesTotal = pendingWizardAmenitiesTotal();
        double amount = roomsTotal + amenitiesTotal;

        roomTotalValue = amount;
        grandTotalValue = amount;
        alreadyPaidValue = 0;

        String formattedGrandTotal = String.format(Locale.US, "₱%,.2f", grandTotalValue);
        if (tvTotalAmount != null) tvTotalAmount.setText(formattedGrandTotal);
        if (tvGrandTotal != null) tvGrandTotal.setText(formattedGrandTotal);
        if (tvBookingRef != null) tvBookingRef.setText(R.string.pending_booking_ref_label);
        if (tvPaymentStatus != null) tvPaymentStatus.setText(R.string.pending_booking_status_label);

        if (tvTransactionType != null) {
            tvTransactionType.setText(pendingWizardState.isBookingMode()
                    ? R.string.tab_new_booking : R.string.tab_new_reservation);
        }
        renderRepresentativeSummary(pendingWizardRepresentativeName());
        renderPendingWizardRoomsSummary();
        if (pendingWizardState.checkIn != null && pendingWizardState.checkOut != null) {
            SimpleDateFormat fmt = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
            if (tvCheckInSummary != null) {
                tvCheckInSummary.setText(getString(R.string.payment_summary_checkin_format, fmt.format(pendingWizardState.checkIn.getTime())));
            }
            if (tvCheckOutSummary != null) {
                tvCheckOutSummary.setText(getString(R.string.payment_summary_checkout_format, fmt.format(pendingWizardState.checkOut.getTime())));
            }
        }
        if (tvNightsSummary != null) {
            int nights = (int) pendingWizardNights();
            tvNightsSummary.setText(getResources().getQuantityString(R.plurals.payment_summary_nights_format, nights, nights));
        }
        renderPendingWizardAmenitiesSummary();

        if (tvRoomCharges != null) tvRoomCharges.setText(String.format(Locale.US, "₱%,.2f", roomsTotal));
        if (tvAmenitiesTotalSummary != null) tvAmenitiesTotalSummary.setText(String.format(Locale.US, "₱%,.2f", amenitiesTotal));
        if (tvTotalAmountSummary != null) tvTotalAmountSummary.setText(formattedGrandTotal);
        if (layoutDiscountSummary != null) layoutDiscountSummary.setVisibility(View.GONE);
        if (tvAlreadyPaidSummary != null) tvAlreadyPaidSummary.setText(String.format(Locale.US, "₱%,.2f", 0.0));
        if (tvOutstandingBalanceSummary != null) tvOutstandingBalanceSummary.setText(formattedGrandTotal);
        if (paymentBreakdownProgress != null) paymentBreakdownProgress.setProgress(0);

        // Direct-booking creation (DirectBookingService on the server) now
        // accepts a partial/deposit amount_paid the same way Reservation
        // payments do - the same unified 20/30/40/50%/Full selector stays
        // usable here exactly like the Reservation path in loadBookingData();
        // applyPaymentAmountSelection() already computes correctly against
        // grandTotalValue since alreadyPaidValue is 0 for a fresh pending booking.
        applyPaymentAmountSelection();
        if (cardPaymentStatusBanner != null) cardPaymentStatusBanner.setVisibility(View.GONE);
        if (layoutGcashMacroSteps != null) layoutGcashMacroSteps.setVisibility(View.VISIBLE);
        if (cardGcashSubmissionForm != null) cardGcashSubmissionForm.setVisibility(View.VISIBLE);
        if (checkoutSummarySection != null) checkoutSummarySection.setVisibility(View.VISIBLE);
        if (emptyStateSection != null) emptyStateSection.setVisibility(View.GONE);
        updateBookReserveCtaVisibility(true);
    }

    /**
     * A reservation may already carry a last-known payment method (e.g. Cash
     * chosen at booking time, or GCash left over from a previously voided/
     * "Convert to Reservation" attempt) - pre-check the matching chip so the
     * guest isn't forced to re-pick a method they'd already settled on. The
     * chip stays fully switchable either way (Cash<->GCash) before they tap
     * Proceed. Only applies when both methods are actually offered
     * (see #allowCash) - a GCash-only New Booking always stays on GCash.
     */
    private void preselectPaymentMethod(Booking booking) {
        if (cgPaymentMethod == null) return;
        String method = booking.getPaymentMethod();
        if (method == null) return;
        boolean isCash = "Cash".equalsIgnoreCase(method);

        if (lockedPaymentMethod) {
            // Pay Now on an already-created transaction: the payment method
            // was chosen once at creation and must be read-only here - hide
            // whichever chip doesn't match instead of just pre-checking the
            // right one, so there's nothing left to switch to (same idiom
            // setupPaymentMethodToggle() already uses for the GCash-only New
            // Booking case).
            com.google.android.material.chip.Chip chipCash = findViewById(R.id.chipCash);
            com.google.android.material.chip.Chip chipGcash = findViewById(R.id.chipGcash);
            if (chipCash != null) chipCash.setVisibility(isCash ? View.VISIBLE : View.GONE);
            if (chipGcash != null) chipGcash.setVisibility(isCash ? View.GONE : View.VISIBLE);
            if (isCash && chipCash != null) chipCash.setClickable(false);
            if (!isCash && chipGcash != null) chipGcash.setClickable(false);
            cgPaymentMethod.check(isCash ? R.id.chipCash : R.id.chipGcash);
            isCashSelected = isCash;
            selectedPaymentMethod = isCash ? "Cash" : "GCash";
            return;
        }

        if (!allowCash) return;
        cgPaymentMethod.check(isCash ? R.id.chipCash : R.id.chipGcash);
    }

    private void updatePaymentGreeting() {
        TextView paymentGreeting = findViewById(R.id.paymentGreeting);
        if (paymentGreeting != null) {
            SharedPreferences prefs = getSharedPreferences("VelocityPrefs", MODE_PRIVATE);
            String fullName = prefs.getString("userName", "Guest");
            String firstName = fullName.split(" ")[0];
            paymentGreeting.setText(getString(R.string.payment_greeting, firstName));
        }
    }

    private int convertDpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }


    private void processPayment() {
        if (currentBooking == null && !isPendingBookingMode && !isPendingReservationMode) {
            Toast.makeText(this, R.string.error_booking_not_found, Toast.LENGTH_SHORT).show();
            return;
        }

        String number = etGcashNumber.getText() != null ? etGcashNumber.getText().toString().trim() : "";
        if (!isValidGcashNumber(number)) {
            etGcashNumber.setError(getString(R.string.error_invalid_gcash_number));
            return;
        }
        etGcashNumber.setError(null);

        // The reference/transaction number from the guest's actual GCash
        // receipt - required so the receptionist can cross-check it against
        // the uploaded receipt image, rather than a client-fabricated value.
        Integer referenceError = gcashReferenceValidationError();
        if (referenceError != null) {
            setGcashReferenceError(getString(referenceError));
            currentGcashStep = 3;
            renderGcashStep();
            if (etGcashReferenceNumber != null) etGcashReferenceNumber.requestFocus();
            return;
        }
        // Same fast local pre-check as Step 3's Next button (see its own comment) - re-run here
        // too since this final submit path is reachable without necessarily having just passed
        // through that button (e.g. Back-then-forward navigation), and the guest may have
        // re-typed a different, already-used reference after first passing Step 3.
        if (findBookingByReference(repository.getBookings(), gcashReferenceDigitsOnly()) != null) {
            setGcashReferenceError(getString(R.string.error_gcash_reference_duplicate));
            currentGcashStep = 3;
            renderGcashStep();
            if (etGcashReferenceNumber != null) etGcashReferenceNumber.requestFocus();
            return;
        }
        setGcashReferenceError(null);
        // Stripped of the display-only grouping spaces - the backend/database must only
        // ever receive and store the raw 13-digit identifier (see gcashReferenceDigitsOnly()).
        String typedReference = gcashReferenceDigitsOnly();

        // Receipt image is mandatory so the receptionist can validate the GCash payment.
        if (receiptUri == null) {
            Toast.makeText(this, R.string.error_attach_receipt, Toast.LENGTH_LONG).show();
            if (tvReceiptStatus != null) {
                tvReceiptStatus.setText(R.string.error_attach_receipt);
                tvReceiptStatus.setTextColor(getResources().getColor(R.color.velocity_red_primary, getTheme()));
            }
            return;
        }

        showFinalGcashConfirmationDialog(number, typedReference);
    }

    /**
     * Final confirmation right before the GCash payment is actually submitted to the
     * server. The amount-confirmation dialog shown earlier (showPaymentConfirmationDialog)
     * only gates entry into the GCash portal step - it happens before the guest enters
     * their number/receipt, so it doesn't cover this (one-way) submission itself.
     */
    private void showFinalGcashConfirmationDialog(String number, String referenceNumber) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.confirm_payment_title)
                .setMessage(getString(R.string.confirm_submit_gcash_payment_msg, String.format(Locale.US, "₱%,.2f", payNowValue)))
                .setPositiveButton(R.string.confirm_dialog_positive, (dialog, which) -> submitPaymentToServer(number, referenceNumber))
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    private void submitPaymentToServer(String gcashNumber, String referenceNumber) {
        if (isSubmittingPayment) {
            return;
        }
        // Short-circuits an offline submit attempt immediately instead of letting the
        // guest sit through the full connect timeout before Retrofit's own failure
        // handling (RoomRepository#networkErrorMessage()) eventually reports it.
        if (!NetworkUtils.isOnline(this)) {
            Toast.makeText(this, R.string.error_no_internet_connection, Toast.LENGTH_LONG).show();
            return;
        }
        isSubmittingPayment = true;
        // Explicit disable on top of the isSubmittingPayment guard + modal loading dialog
        // below - defense-in-depth against a duplicate tap landing before the dialog paints.
        if (completePaymentButton != null) {
            completePaymentButton.setEnabled(false);
            completePaymentButton.setText(R.string.submitting_payment_button);
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_loading, null);
        TextView tvMsg = dialogView.findViewById(R.id.loadingMessage);
        tvMsg.setText(R.string.processing_payment);
        builder.setView(dialogView);
        builder.setCancelable(false);
        AlertDialog dialog = builder.create();
        dialog.show();

        String paymentType = isFullPaymentMode ? "full" : "partial";

        if (isPendingBookingMode) {
            List<List<Room>> groups = new ArrayList<>(pendingWizardState.selectedRoomsGroupedByType().values());
            submitPendingBookingGroups(dialog, gcashNumber, referenceNumber, groups);
            return;
        }

        if (isPendingReservationMode) {
            List<List<Room>> groups = new ArrayList<>(pendingWizardState.selectedRoomsGroupedByType().values());
            submitPendingReservationSingleCall(dialog, gcashNumber, referenceNumber, groups);
            return;
        }

        repository.submitGcashPayment(bookingId, paymentType, referenceNumber, payNowValue, gcashNumber, receiptUri, selectedGcashPercentageForRequest(), new RoomRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                dismissSafely(dialog);
                showGcashSuccessDialog(referenceNumber, payNowValue);
            }

            @Override
            public void onError(String message) {
                if (isDuplicateReferenceError(message)) {
                    // Don't take the server's word for it yet - it may be reporting OUR
                    // OWN just-submitted payment back to us (see reconcileDuplicateReference()).
                    reconcileDuplicateReference(referenceNumber,
                            () -> {
                                dismissSafely(dialog);
                                showGcashSuccessDialog(referenceNumber, payNowValue);
                            },
                            () -> {
                                isSubmittingPayment = false;
                                dismissSafely(dialog);
                                restoreSubmitButtonAfterError();
                                showGenuineDuplicateReferenceError();
                            });
                } else {
                    isSubmittingPayment = false;
                    dismissSafely(dialog);
                    restoreSubmitButtonAfterError();
                    Toast.makeText(PaymentActivity.this, getString(R.string.error_payment_submission_failed_format, message), Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    /** Server-side uniqueness (Api\PaymentController::store()'s reference_number.unique rule) is the one check Step 5 can't pre-validate client-side. */
    private boolean isDuplicateReferenceError(String message) {
        return message != null && message.toLowerCase(Locale.US).contains("reference number") && message.toLowerCase(Locale.US).contains("already");
    }

    /**
     * A "reference number already used" rejection doesn't necessarily mean a genuine
     * conflict with someone else's payment - it's exactly what the server would also say
     * if THIS guest's own just-submitted attempt actually went through (e.g. the request
     * reached the server and was saved, but the response was lost to a dropped connection/
     * timeout before the app could see it - see submitPaymentToServer()'s 20s read timeout).
     * Resolve the ambiguity by refreshing this guest's own bookings/reservations and
     * checking whether any of them already carries this exact reference number in its
     * payment history. That list is scoped to the authenticated guest only, so a match
     * here can only be this guest's own prior attempt - never a different guest's payment -
     * making it safe to treat as success rather than blocking a legitimate submission.
     * Falls back to onGenuineDuplicate (no match found, or the refresh itself failed) -
     * the original "please enter a different reference number" flow, unchanged.
     */
    private void reconcileDuplicateReference(String referenceNumber, Runnable onRecoveredAsOwnPayment, Runnable onGenuineDuplicate) {
        repository.refreshBookings(new RoomRepository.RepositoryCallback<List<Booking>>() {
            @Override
            public void onSuccess(List<Booking> result) {
                Booking match = findBookingByReference(result, referenceNumber);
                if (match != null) {
                    currentBooking = match;
                    bookingId = match.getId();
                    onRecoveredAsOwnPayment.run();
                } else {
                    onGenuineDuplicate.run();
                }
            }

            @Override
            public void onError(String message) {
                onGenuineDuplicate.run();
            }
        });
    }

    private Booking findBookingByReference(List<Booking> bookings, String referenceNumber) {
        if (referenceNumber == null || bookings == null) return null;
        for (Booking b : bookings) {
            for (Booking.PaymentRecord record : b.getPaymentHistory()) {
                if (referenceNumber.equalsIgnoreCase(record.referenceNumber)) {
                    return b;
                }
            }
        }
        return null;
    }

    /** The one place a genuine (not self-caused) duplicate-reference rejection is surfaced - sends the guest back to Step 3 to fix it. */
    private void showGenuineDuplicateReferenceError() {
        currentGcashStep = 3;
        renderGcashStep();
        setGcashReferenceError(getString(R.string.error_gcash_reference_duplicate));
        if (etGcashReferenceNumber != null) etGcashReferenceNumber.requestFocus();
        Toast.makeText(PaymentActivity.this, R.string.error_gcash_reference_duplicate, Toast.LENGTH_LONG).show();
    }

    private void restoreSubmitButtonAfterError() {
        if (completePaymentButton == null) return;
        completePaymentButton.setText(R.string.submit_payment_verification_button);
        updateSubmitButtonState();
    }

    // dismissSafely(AlertDialog) is now inherited from BaseNavigationActivity - every
    // loading dialog shown during a submission (Create Booking/Reservation, GCash
    // payment, Modify) is dismissed from inside a Retrofit callback, which can still
    // fire after the guest has left this screen; see that shared implementation's doc.

    /**
     * The actual Booking-creating call - only reached after the GCash portal
     * step above has already collected a valid number + receipt, satisfying
     * "Booking cannot be created before successful GCash payment submission."
     * A single atomic call carrying every selected room type/quantity and
     * every selected amenity - see RoomRepository#createDirectBooking()'s
     * own doc. This replaced the old one-call-per-room-type-group
     * sequential loop (MULTI_ROOM_TRANSACTION_BACKEND_SPEC.md): that loop
     * reused the SAME GCash reference number for every group's own
     * separate Payment record, which the backend's unique-reference-number
     * rule silently rejected for every group after the first, so only the
     * first selected room type - and none of that group's own amenities
     * beyond index 0 - ever actually reached the server. `payNowValue` (the
     * guest's real chosen amount, Partial or Full) is sent as-is against
     * the whole transaction's true grand total - never split
     * proportionally across groups, since there is now only one Payment
     * record for the entire Booking.
     */
    private void submitPendingBookingGroups(AlertDialog dialog, String gcashNumber, String referenceNumber, List<List<Room>> groups) {
        Uri idCardForThisCall = "None".equals(pendingWizardState.idCardType) ? null : pendingWizardState.idCardImageUri;

        repository.createDirectBooking(groups, pendingWizardState.checkIn, pendingWizardState.checkOut,
                pendingWizardState.adults, pendingWizardState.children,
                pendingWizardState.guestFirstName, pendingWizardState.guestMiddleName, pendingWizardState.guestLastName,
                pendingWizardState.idCardType, idCardForThisCall,
                pendingWizardState.additionalGuests, pendingWizardState.selectedAmenities,
                "gcash", referenceNumber, gcashNumber, receiptUri,
                payNowValue, selectedGcashPercentageForRequest(), UUID.randomUUID().toString(),
                new RoomRepository.RepositoryCallback<Booking>() {
                    @Override
                    public void onSuccess(Booking booking) {
                        dismissSafely(dialog);
                        onPendingBookingCreated(booking, referenceNumber);
                    }

                    @Override
                    public void onError(String message) {
                        if (isDuplicateReferenceError(message)) {
                            // Same self-vs-genuine ambiguity as submitPaymentToServer()'s
                            // direct branch - this call may have actually succeeded
                            // server-side despite the client seeing an error (e.g. a
                            // dropped response after a successful create).
                            reconcileDuplicateReference(referenceNumber,
                                    () -> {
                                        dismissSafely(dialog);
                                        showGcashSuccessDialog(referenceNumber, payNowValue);
                                    },
                                    () -> {
                                        dismissSafely(dialog);
                                        isSubmittingPayment = false;
                                        restoreSubmitButtonAfterError();
                                        showGenuineDuplicateReferenceError();
                                    });
                            return;
                        }
                        dismissSafely(dialog);
                        isSubmittingPayment = false;
                        restoreSubmitButtonAfterError();
                        Toast.makeText(PaymentActivity.this, getString(R.string.error_payment_submission_failed_format, message), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void onPendingBookingCreated(Booking booking, String transactionRef) {
        currentBooking = booking;
        bookingId = booking.getId();
        showGcashSuccessDialog(transactionRef, payNowValue);
    }

    /**
     * Pending-reservation GCash submission: one single call creates the
     * Reservation with every selected room type/quantity and every selected
     * amenity (see RoomRepository#createReservation(List, ...)'s own doc),
     * then attaches the GCash payment to it (a successful GCash payment
     * auto-converts the Reservation into a Booking server-side - see
     * ReservationWorkflowService::tryAutoConvert()). If creation itself
     * fails there's nothing to pay for. If creation succeeds but attaching
     * the payment fails, the Reservation is deliberately NOT rolled back -
     * an unpaid, unconverted Reservation is a normal, valid state (unlike a
     * Booking, which must never exist unpaid) - so it's kept and reported
     * as a distinct partial failure instead of the generic one.
     */
    private void submitPendingReservationSingleCall(AlertDialog dialog, String gcashNumber, String referenceNumber,
                                                      List<List<Room>> groups) {
        repository.createReservation(groups, pendingWizardState.checkIn, pendingWizardState.checkOut,
                pendingWizardState.adults, pendingWizardState.children,
                pendingWizardState.guestFirstName, pendingWizardState.guestMiddleName, pendingWizardState.guestLastName,
                pendingWizardState.idCardType, pendingWizardState.additionalGuests, pendingWizardState.selectedAmenities,
                "gcash", UUID.randomUUID().toString(),
                new RoomRepository.RepositoryCallback<Booking>() {
                    @Override
                    public void onSuccess(Booking created) {
                        uploadIdCardIfNeeded(created, () ->
                                repository.submitGcashPayment(created.getId(), isFullPaymentMode ? "full" : "partial",
                                        referenceNumber, payNowValue, gcashNumber, receiptUri,
                                        selectedGcashPercentageForRequest(), new RoomRepository.RepositoryCallback<Void>() {
                                            @Override
                                            public void onSuccess(Void result) {
                                                onPendingReservationPaidCreated(dialog, created, referenceNumber, false);
                                            }

                                            @Override
                                            public void onError(String message) {
                                                onPendingReservationPaidCreated(dialog, created, referenceNumber, true);
                                            }
                                        }));
                    }

                    @Override
                    public void onError(String message) {
                        isSubmittingPayment = false;
                        dismissSafely(dialog);
                        restoreSubmitButtonAfterError();
                        Toast.makeText(PaymentActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void onPendingReservationPaidCreated(AlertDialog dialog, Booking created, String referenceNumber, boolean paymentAttachFailed) {
        dismissSafely(dialog);
        currentBooking = created;
        bookingId = created.getId();
        if (paymentAttachFailed) {
            // The reservation exists but the payment attach failed -
            // honestly reflect that as a still-pending reservation rather
            // than claiming a payment success that didn't happen.
            // isSubmittingPayment deliberately left true here, matching the
            // pre-existing behavior this replaces - showReservationPendingDialogForId()'s
            // own confirm button navigates away, same as the success path below.
            Toast.makeText(this, R.string.reservation_payment_attach_partial_failure_message, Toast.LENGTH_LONG).show();
            showReservationPendingDialogForId(currentBooking.getId());
            return;
        }
        showGcashSuccessDialog(referenceNumber, payNowValue);
    }

    /**
     * This is a submission acknowledgement only - GCash payments start Pending
     * Verification (see status_pending_verification_title/message), so no
     * downloadable/official receipt is offered here. That only becomes available
     * once the receptionist actually verifies the payment (see TransactionDetailsActivity/
     * TransactionHistoryActivity's own receipt views, gated on isPaymentVerified()).
     */
    private void showGcashSuccessDialog(String transactionRef, double amountPaid) {
        if (currentBooking != null) {
            currentBooking.setTransactionRef(transactionRef);
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.payment_success_title)
                .setMessage(R.string.payment_submitted_msg)
                .setPositiveButton(R.string.close_label, (dialog, which) -> finalizePayment())
                .setCancelable(false)
                .show();
    }

    /**
     * Routing here is state-driven, not mode-driven: a pending-reservation
     * Cash submission never converts (only GCash auto-converts - see
     * ReservationWorkflowService::tryAutoConvert() on the server), so
     * isPendingReservationMode alone can't decide the destination the way
     * isPendingBookingMode always could (Booking mode is GCash-only and
     * always converts on success). refreshBookings() is called first so
     * currentBooking.isHasBooking() reflects the server's authoritative
     * post-payment state (submitGcashPayment()/submitPayment() only return
     * Void - the locally cached Booking object is otherwise stale until this
     * refresh) - this also guarantees BookingAndReservationActivity picks up
     * the change immediately even if it's already on the back stack and
     * reached via onNewIntent() rather than a fresh onCreate().
     */
    private void finalizePayment() {
        // Provisional in-memory patch so something reasonable renders even if
        // the refresh below fails - overwritten by the real server state on success.
        if (currentBooking != null) {
            currentBooking.setPaymentMethod(selectedPaymentMethod);
            currentBooking.setPaymentDate(new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US).format(new Date()));
            currentBooking.setAmountPaid(alreadyPaidValue + payNowValue);
            boolean fullyPaid = currentBooking.getRemainingBalance() <= 0.009;
            currentBooking.setBillingStatus(fullyPaid ? "paid" : "partial");
        }

        String idToResolve = currentBooking != null ? currentBooking.getId() : null;
        refreshBookingsAfterPayment(idToResolve, true);
    }

    /**
     * The payment itself already succeeded by the time this runs - a failure here is
     * just this follow-up refresh call, but if left alone it would leave currentBooking
     * on its pre-conversion state and misroute a just-converted booking to the
     * Reservation section instead of Bookings. One retry absorbs a transient blip
     * (e.g. a dropped connection right after the payment request completed) before
     * falling back to whatever state is already held locally.
     */
    private void refreshBookingsAfterPayment(@Nullable String idToResolve, boolean allowRetry) {
        repository.refreshBookings(new RoomRepository.RepositoryCallback<List<Booking>>() {
            @Override
            public void onSuccess(List<Booking> result) {
                resolveCurrentBookingAndNavigate(idToResolve, result);
            }

            @Override
            public void onError(String message) {
                if (allowRetry) {
                    refreshBookingsAfterPayment(idToResolve, false);
                } else {
                    resolveCurrentBookingAndNavigate(idToResolve, null);
                }
            }
        });
    }

    private void resolveCurrentBookingAndNavigate(@Nullable String idToResolve, @Nullable List<Booking> freshBookings) {
        if (idToResolve != null && freshBookings != null) {
            for (Booking b : freshBookings) {
                if (b.getId().equals(idToResolve)) {
                    currentBooking = b;
                    break;
                }
            }
        }

        boolean justConverted = currentBooking != null && currentBooking.isHasBooking();

        // fromReservationPayment alone used to force this "converted to a
        // confirmed booking" branch unconditionally for any existing-
        // reservation Pay Now - kept keyed off the real, server-refreshed
        // isHasBooking() instead (ReservationWorkflowService::
        // recordDepositPayment() -> tryAutoConvert() converts synchronously
        // on a successful GCash payment, but this stays correct even if
        // that timing ever changes again, since it never assumes when
        // conversion happens - only asks the server what actually happened).
        if (justConverted) {
            // One consistent redirect regardless of which sub-path led here
            // (a brand-new direct booking, or an existing Reservation just
            // converted via this GCash payment) - both cases are now a
            // real, paid Booking. navigateToBookingSection() itself shows
            // the Booking ID (see showTransactionCreatedDialog()) before
            // opening Bookings -> All Bookings, scrolled to + highlighting
            // this exact booking by its real id (see EXTRA_HIGHLIGHT_ID).
            navigateToBookingSection();
            return;
        }

        if (isPendingReservationMode) {
            // Cash reservation, still pending/unconverted - already shown its
            // own "Thank you for trusting us" dialog before finalizePayment()
            // was even called (see onPendingReservationCashCreated()).
            // navigateToReservationSection() still shows the Reservation ID
            // one more time, prominently, right before the actual redirect.
            navigateToReservationSection();
            return;
        }

        Toast.makeText(this, R.string.payment_submitted_msg, Toast.LENGTH_LONG).show();
        openScreen(TransactionHistoryActivity.class);
    }

    private void navigateToBookingSection() {
        TransactionCreatedDialogHelper.show(this, true, currentBooking != null ? currentBooking.getId() : null, () -> {
            Intent intent = new Intent(this, BookingAndReservationActivity.class);
            intent.putExtra(BookingAndReservationActivity.EXTRA_OPEN_SECTION, BookingAndReservationActivity.SECTION_BOOKING);
            if (currentBooking != null) {
                intent.putExtra(BookingAndReservationActivity.EXTRA_HIGHLIGHT_ID, currentBooking.getId());
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }

    private void navigateToReservationSection() {
        TransactionCreatedDialogHelper.show(this, false, currentBooking != null ? currentBooking.getId() : null, () -> {
            Intent intent = new Intent(this, BookingAndReservationActivity.class);
            intent.putExtra(BookingAndReservationActivity.EXTRA_OPEN_SECTION, BookingAndReservationActivity.SECTION_RESERVATION);
            if (currentBooking != null) {
                intent.putExtra(BookingAndReservationActivity.EXTRA_HIGHLIGHT_ID, currentBooking.getId());
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }
}


