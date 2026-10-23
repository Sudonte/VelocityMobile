package com.example.velocitysuites;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.button.MaterialButton;

/**
 * Host for both the "New Booking" and "New Reservation" wizards - one
 * Activity, parameterized by EXTRA_MODE, hosting the shared
 * WizardStepFragment steps (see BookingWizardState's docblock for why a
 * shared in-memory state object rather than per-Activity-hop Intent extras).
 * Steps 1-6 are identical across every run. Step count itself is
 * mode/edit-dependent (see stepCount, set once in onCreate()):
 * <ul>
 *   <li>Booking mode, and Reservation edit/Modify mode: 7 steps, step 7 is
 *   Step8ReviewPaymentFragment (review + T&amp;C; payment method is chosen on
 *   payment.xml for Booking, or via this same step's own edit-mode chip
 *   picker for Modify) - completely unchanged from before this class's
 *   payment-method-step addition.</li>
 *   <li>Reservation mode, fresh (not editing): 8 steps - step 7 is the new
 *   Step7PaymentMethodFragment (Cash/GCash, no payment collected), step 8 is
 *   Step8ReviewPaymentFragment showing the guest's step-7 choice read-only
 *   and submitting the reservation directly (no payment.xml hand-off at all,
 *   for either method - GCash always defers to a later Pay Now action).</li>
 * </ul>
 */
public class BookingWizardActivity extends SafeActivity {

    public static final String EXTRA_MODE = "EXTRA_MODE";
    public static final String EXTRA_EDIT_BOOKING = "EXTRA_EDIT_BOOKING";

    private int stepCount = 7;
    private final ClickGuard navGuard = new ClickGuard();
    /** The confirmation or review sheet on screen: while one is up, a repeat tap cannot open a second. */
    private android.app.Dialog modalDialog;

    private BookingWizardState state;
    private int currentStepIndex = 1;
    /** Non-null only when this wizard run is a Modify of an existing Reservation, not a fresh New Reservation/Booking. */
    private String editingReservationId;
    /** The reservation's payment method before Modify started ("CASH"/"GCASH") - compared against state.paymentMethod on Save to decide whether a switch-to-gcash/switch-to-cash call is needed. */
    private String originalPaymentMethod;

    private TextView tvStepLabel;
    private TextView tvStepsRemaining;
    private WizardStepIndicatorView stepIndicator;
    private MaterialButton btnPrevious;
    private MaterialButton btnNext;

    public static Intent newIntent(Context context, BookingWizardState.Mode mode) {
        Intent intent = new Intent(context, BookingWizardActivity.class);
        intent.putExtra(EXTRA_MODE, mode.name());
        return intent;
    }

    /**
     * Launches the wizard in edit mode for an existing Reservation's one-time
     * Modify (always RESERVATION mode - a direct Booking is never modifiable
     * this way). The full Booking object rides along as a Serializable extra
     * (simplest option here - Booking already implements Serializable and
     * this is a same-process hop) so Step 1 can be seeded before the first
     * frame renders, same as the PendingWizardRooms carry-over case.
     */
    public static Intent newEditIntent(Context context, Booking booking) {
        Intent intent = new Intent(context, BookingWizardActivity.class);
        intent.putExtra(EXTRA_MODE, BookingWizardState.Mode.RESERVATION.name());
        intent.putExtra(EXTRA_EDIT_BOOKING, booking);
        return intent;
    }

    public BookingWizardState getState() {
        return state;
    }

    public boolean isEditMode() {
        return editingReservationId != null;
    }

    public String getEditingReservationId() {
        return editingReservationId;
    }

    public String getOriginalPaymentMethod() {
        return originalPaymentMethod;
    }

    /** False while a confirmation or review sheet is showing: a fast second tap must not open another. */
    public boolean canOpenModal() {
        return modalDialog == null || !modalDialog.isShowing();
    }

    /** Remembers the dialog or sheet just shown, for {@link #canOpenModal()}. */
    public void trackModal(android.app.Dialog dialog) {
        modalDialog = dialog;
    }

    /**
     * The one confirmation every step uses: a short title, ONE summary line, "Yes, continue" (or the given label) and Cancel. Nothing
     * opens while another is showing.
     */
    public void confirmStep(String title, @Nullable String summary, String yesLabel, Runnable onYes) {
        confirmStep(title, summary, yesLabel, getString(R.string.cancel_label), onYes);
    }

    public void confirmStep(String title, @Nullable String summary, String yesLabel, String noLabel, Runnable onYes) {
        if (!canOpenModal() || isFinishing() || isDestroyed()) return;
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder = new SafeDialogs.Material(this)
                .setTitle(title)
                .setPositiveButton(yesLabel, (d, w) -> onYes.run())
                .setNegativeButton(noLabel, null);
        if (summary != null && !summary.isEmpty()) builder.setMessage(summary);
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        trackModal(dialog);
        SafeDialogs.show(dialog);
    }

    /** Lets a step (currently only Step 2's room selection) dynamically gate Next - e.g. disabled until at least one room is selected. */
    public void setNextEnabled(boolean enabled) {
        if (currentStepIndex < stepCount) {
            btnNext.setEnabled(enabled);
        }
        // Called whenever the room selection changes - keep the running summary in step with it.
        updateSummaryBar();
        WizardStepFragment current = currentFragment();
        if (current != null) current.onSelectionChanged();
    }

    /** Enables or disables Next without telling the step it changed (a step that decides for itself, e.g. Dates and rooms, uses this to avoid a loop). */
    public void setNextEnabledQuietly(boolean enabled) {
        if (currentStepIndex < stepCount) {
            btnNext.setEnabled(enabled);
        }
    }

    /** Redraws the one-line summary under the step (stays, rooms, guests, running group total). */
    public void refreshSummaryBar() {
        updateSummaryBar();
    }

    /** Advances past the current step without re-running its own validateBeforeNext() - for a step's own "Skip" affordance (e.g. amenities). */
    public void goToNextStepSkippingValidation() {
        if (currentStepIndex < stepCount) {
            showStep(currentStepIndex + 1);
        }
    }

    private boolean wasStopped;

    @Override
    protected void onStop() {
        super.onStop();
        wasStopped = true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (wasStopped) {
            wasStopped = false;
            recheckAvailabilityOnReturn();
        }
    }

    /**
     * The guest left the app (or came back from the payment screen) and may have been away long enough for
     * another guest to take their room: read availability again, fresh. Rooms that are gone are dropped from the
     * selection and Room Selection opens with a message; everything else they entered stays. Silent when nothing
     * changed or the check fails - Confirm re-checks again before anything is sent.
     */
    private void recheckAvailabilityOnReturn() {
        if (state == null || state.checkIn == null || state.checkOut == null || currentStepIndex < 1) return;
        // Coming back from a refused booking: the review step handles that itself, with the server's own words.
        if (PendingBookingPayload.hasRoomsUnavailable()) return;
        RoomRepository.getInstance(this).refreshRoomsFresh(state.checkIn, state.checkOut, new RoomRepository.RepositoryCallback<List<Room>>() {
            @Override
            public void onSuccess(List<Room> fresh) {
                if (isFinishing() || isDestroyed()) return;
                List<RoomAvailabilityReconciler.Shortfall> lost = RoomAvailabilityReconciler.reconcile(state.selectedRooms, fresh);
                if (!lost.isEmpty()) {
                    Toast.makeText(BookingWizardActivity.this, describeShortfalls(lost), Toast.LENGTH_LONG).show();
                    goToStep(1);
                    return;
                }
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                // silent
            }
        });
    }

    /** One line per room type that had to be trimmed - the same words wherever the wizard reports it. */
    String describeShortfalls(List<RoomAvailabilityReconciler.Shortfall> shortfalls) {
        StringBuilder text = new StringBuilder();
        for (RoomAvailabilityReconciler.Shortfall shortfall : shortfalls) {
            if (text.length() > 0) text.append('\n');
            text.append(shortfall.isGone()
                    ? getString(R.string.error_room_no_longer_available_format, shortfall.room.getName())
                    : getResources().getQuantityString(R.plurals.error_room_availability_decreased_format, shortfall.available, shortfall.available, shortfall.room.getName()));
        }
        return text.toString();
    }

    /** Jumps directly to an arbitrary step - e.g. Step 7's fresh availability recheck sending the guest back to Step 2 to adjust a room quantity that's no longer available. */
    public void goToStep(int stepIndex) {
        showStep(stepIndex);
    }

    /** For the review step's Confirm action to trigger normal Next-button-style validation on demand, without a visible Next button on the last step. */
    public boolean isLastStep() {
        return currentStepIndex >= stepCount;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.booking_wizard);

        String modeExtra = getIntent().getStringExtra(EXTRA_MODE);
        BookingWizardState.Mode mode = "RESERVATION".equals(modeExtra)
                ? BookingWizardState.Mode.RESERVATION
                : BookingWizardState.Mode.BOOKING;
        state = new BookingWizardState(mode);
        Booking editingBooking = (Booking) getIntent().getSerializableExtra(EXTRA_EDIT_BOOKING);
        if (editingBooking != null) {
            seedStateForEdit(editingBooking);
        } else {
            // Carry over room(s) already picked on landing.xml/roombrowsing.xml
            // (RoomDetailsDialog's Book Now/Reserve Now, or the multi-room cart)
            // so Step 1 never starts empty when a room was already chosen.
            state.selectedRooms.addAll(PendingWizardRooms.consume());
            // Dates searched in Room Browsing pre-fill the date step - only if they still satisfy the
            // check-in window and the stay is at least one night (otherwise the guest just picks again).
            java.util.Calendar[] searched = PendingWizardRooms.consumeDates();
            if (searched != null && searched[1].after(searched[0]) && CheckInWindow.isAllowed(searched[0])) {
                state.checkIn = searched[0];
                state.checkOut = searched[1];
            }

            // "Book Again"/"Reserve Again" on a cancelled transaction - not an
            // edit (this is a brand-new transaction, never seedStateForEdit()'s
            // Modify path), so only the guest-info fields carry over.
            Booking bookAgainOriginal = PendingBookAgainPrefill.consume();
            if (bookAgainOriginal != null) {
                seedStateForBookAgain(bookAgainOriginal);
            }
        }

        // Dates and rooms are one step now (Step 1), so a Booking and a Reservation Modify have 6 steps; only a fresh (non-edit)
        // Reservation gains the Payment Method step and has 7.
        stepCount = (mode == BookingWizardState.Mode.RESERVATION && !isEditMode()) ? 7 : 6;

        // Header color mirrors startingbooking.xml (velocity_red_primary) vs
        // startingreservation.xml (velocity_red_dark) - the only visual
        // difference between those two screens. Set once here from the
        // wizard's (final, never-reassigned) mode, so it's identical across
        // every step of this run (7 or 8, see stepCount above) and can never
        // flip mid-flow: this header view is never recreated (only the
        // fragment container swaps per step in showStep()).
        View headerBar = findViewById(R.id.wizardHeaderBar);
        headerBar.setBackgroundColor(getResources().getColor(
                mode == BookingWizardState.Mode.RESERVATION ? R.color.velocity_red_dark : R.color.velocity_red_primary));

        TextView tvTitle = findViewById(R.id.tvWizardTitle);
        tvTitle.setText(isEditMode() ? R.string.modify_reservation_label
                : (mode == BookingWizardState.Mode.BOOKING ? R.string.tab_new_booking : R.string.tab_new_reservation));

        tvStepLabel = findViewById(R.id.tvWizardStepLabel);
        tvStepsRemaining = findViewById(R.id.tvWizardStepsRemaining);
        stepIndicator = findViewById(R.id.wizardStepIndicator);
        stepIndicator.setStepCount(stepCount);

        btnPrevious = findViewById(R.id.btnWizardPrevious);
        btnNext = findViewById(R.id.btnWizardNext);

        // Edge-to-edge devices (target SDK 35+) draw under the status/navigation bars: keep the header
        // below the status bar and the Back/Next bar above the navigation bar and the keyboard.
        View wizardRoot = findViewById(R.id.wizardRoot);
        final int headerTop = headerBar.getPaddingTop();
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(wizardRoot, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars() | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
            androidx.core.graphics.Insets ime = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime());
            headerBar.setPadding(headerBar.getPaddingLeft(), headerTop + bars.top, headerBar.getPaddingRight(), headerBar.getPaddingBottom());
            v.setPadding(bars.left, 0, bars.right, Math.max(bars.bottom, ime.bottom));
            return insets;
        });
        findViewById(R.id.btnWizardBack).setOnClickListener(v -> handleBackPress());
        // A fast double-tap on Back/Next must move exactly one step - the second tap would otherwise
        // run against the NEXT step's (often empty) validation and skip right past it.
        btnPrevious.setOnClickListener(v -> {
            if (navGuard.tryAcquire()) goToPreviousStep();
        });
        btnNext.setOnClickListener(v -> {
            if (navGuard.tryAcquire()) attemptGoToNextStep();
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackPress();
            }
        });

        if (savedInstanceState != null) {
            currentStepIndex = savedInstanceState.getInt("currentStepIndex", 1);
            // BookingWizardState itself is deliberately not saved/restored across
            // process death (see its own class doc) - only this index is, via
            // onSaveInstanceState() below. So if the OS recreated this Activity
            // (e.g. backgrounded and reclaimed) partway through a fresh wizard
            // run, `state` above is a brand-new, empty object with no rooms/
            // dates/guest info, yet currentStepIndex would still point past
            // Step 1 - silently showing a later step with blank/wrong fields
            // and no indication anything went wrong. isEditMode()'s path is
            // unaffected (seedStateForEdit() already fully repopulated state
            // just above, from the same Intent extra Android redelivers on
            // recreation) - only reset for a fresh, in-progress run.
            if (!isEditMode() && currentStepIndex > 1) {
                // The wizard saved its state to a private cache file when it went to the background (see onSaveInstanceState()).
                BookingWizardState restored = WizardStateStore.load(this, mode);
                if (restored != null) {
                    state = restored;
                } else {
                    currentStepIndex = 1;
                    Toast.makeText(this, R.string.wizard_progress_lost_restart, Toast.LENGTH_LONG).show();
                }
            }
        }
        if (isEditMode() && state.selectedRooms.isEmpty()) {
            loadRoomsForEditThenShow(editingBooking, currentStepIndex);
        } else {
            showStep(currentStepIndex);
        }
    }

    /**
     * Pre-fills every step's data from an existing Reservation so Modify
     * genuinely walks Step 1 of 7 -> Step 7 of 7 instead of reopening the old
     * inline edit form. Room resolution is synchronous against
     * RoomRepository's already-loaded in-memory cache (the guest necessarily
     * passed through landing/room-browsing earlier this session to have a
     * reservation at all) - if the cache is empty (cold process, direct deep
     * link), Step 1 simply starts without a pre-selected room and the guest
     * re-picks it, same graceful fallback as a brand new wizard run.
     */
    private void applyRepresentativeFields(RepresentativeName.Parts p) {
        state.repLast = p.last;
        state.repFirst = p.first;
        state.repMiddle = p.middle;
        // A transaction made before this choice existed: no representative = the owner, otherwise someone else.
        state.who = p.isBlank() ? BookingWizardState.Who.OWNER : BookingWizardState.Who.OTHER;
    }

    private void seedStateForEdit(Booking booking) {
        editingReservationId = booking.getId();
        originalPaymentMethod = booking.getPaymentMethod() != null ? booking.getPaymentMethod() : "CASH";
        state.paymentMethod = "GCASH".equalsIgnoreCase(originalPaymentMethod) ? "gcash" : "cash";

        applyRepresentativeFields(RepresentativeName.fieldsFor(this, booking));
        state.guestFirstName = booking.getGuestFirstName();
        state.guestMiddleName = booking.getGuestMiddleName();
        state.guestLastName = booking.getGuestLastName();
        state.adults = Math.max(1, booking.getAdults());
        state.children = Math.max(0, booking.getChildren());
        state.idCardType = booking.getIdCardType() != null ? booking.getIdCardType() : "None";
        if (!"None".equals(state.idCardType)) {
            // The discount the guest already claimed and the ID already on file - both stay exactly
            // as they were unless the guest changes them on Step 6.
            state.discount = new Discount(booking.getDiscountId(), state.idCardType, null, 0, null);
            state.idCardOnFile = booking.isHasIdCard();
        }
        state.editOldTotal = booking.getTotalAmount();
        state.editAmountPaid = booking.getEffectiveTotalAmountPaid();
        if (booking.getAdditionalGuests() != null) {
            for (Booking.AdditionalGuest g : booking.getAdditionalGuests()) {
                state.additionalGuests.add(new BookingAndReservationActivity.AdditionalGuest(g.name, g.age, g.gender, g.relationship));
            }
        }

        SimpleDateFormat displayFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
        try {
            Calendar checkIn = Calendar.getInstance();
            checkIn.setTime(displayFormat.parse(booking.getCheckInDate()));
            state.checkIn = checkIn;
            state.originalCheckIn = (Calendar) checkIn.clone();
            Calendar checkOut = Calendar.getInstance();
            checkOut.setTime(displayFormat.parse(booking.getCheckOutDate()));
            state.checkOut = checkOut;
        } catch (ParseException | NullPointerException ignored) {
            // Leave state.checkIn/checkOut null - Step 1 (Dates) shows its normal empty/unset state.
        }

        seedRoomsForEdit(booking, RoomRepository.getInstance(this).getAllRooms());
        state.activeConfirmed = !state.selectedRooms.isEmpty() && state.checkIn != null && state.checkOut != null;

        // Seed already-selected paid amenities too - Step3AmenitiesFragment
        // reconciles this by id against its own freshly-fetched live catalog
        // (see its own comment), carrying over only the quantity, so the
        // category/description/stock placeholders here are never actually
        // shown/used. Without this, state.selectedAmenities would start empty
        // and Step8ReviewPaymentFragment#saveEditedReservation() would send an
        // empty amenities[] on Save, silently deleting every amenity the guest
        // never touched during this Modify.
        List<BookingAmenity> itemizedAmenities = booking.getAmenities();
        if (itemizedAmenities != null) {
            for (BookingAmenity a : itemizedAmenities) {
                AddOnAmenity seeded = new AddOnAmenity(a.getAmenityId(), a.getAmenityName(), "", "", a.getUnitPrice(), a.getQuantity());
                seeded.setQuantity(a.getQuantity());
                state.selectedAmenities.add(seeded);
            }
        }
    }

    /**
     * Puts the reservation's current room selection into the wizard state, resolved against
     * {@code cached} (the loaded room-type catalog). Called straight away when the catalog is
     * already in memory, or again once it has been fetched (see loadRoomsForEditThenShow()).
     */
    private void seedRoomsForEdit(Booking booking, List<Room> cached) {
        state.selectedRooms.clear();
        List<BookingRoom> itemizedRooms = booking.getRooms();
        if (itemizedRooms != null && !itemizedRooms.isEmpty()) {
            // Multi-room-type reservation (BookingRoom line items) - seed every
            // selected type, not just one, so a Modify on a reservation that
            // already has e.g. Deluxe x2 + Family x1 doesn't silently drop the
            // Family rooms the moment Save is pressed (see
            // Step8ReviewPaymentFragment#saveEditedReservation(), which now
            // always sends whatever ends up in state.selectedRooms).
            for (BookingRoom line : itemizedRooms) {
                for (Room r : cached) {
                    if (r.getId().equals(line.getRoomTypeId())) {
                        for (int i = 0; i < Math.max(1, line.getQuantity()); i++) {
                            state.selectedRooms.add(r);
                        }
                        break;
                    }
                }
            }
        } else {
            // Legacy single-room-type reservation - only room_type_id/rooms_requested
            // to seed from.
            String roomTypeId = booking.getRoomTypeId();
            if (roomTypeId != null) {
                int qty = Math.max(1, booking.getRoomsRequested());
                for (Room r : cached) {
                    if (r.getId().equals(roomTypeId)) {
                        for (int i = 0; i < qty; i++) {
                            state.selectedRooms.add(r);
                        }
                        break;
                    }
                }
            }
        }
    }

    /**
     * The room catalog wasn't in memory (cold start straight into the edit): fetch it first, then
     * seed the reservation's rooms and open the wizard - so the guest never lands on a wizard
     * whose rooms were silently dropped. Failure leaves nothing half-edited: tell them and close.
     */
    private void loadRoomsForEditThenShow(Booking booking, int stepIndex) {
        View loading = findViewById(R.id.wizardLoading);
        loading.setVisibility(View.VISIBLE);
        btnNext.setEnabled(false);
        RoomRepository.getInstance(this).refreshRoomsFresh(new RoomRepository.RepositoryCallback<List<Room>>() {
            @Override
            public void onSuccess(List<Room> result) {
                if (isFinishing() || isDestroyed()) return;
                loading.setVisibility(View.GONE);
                seedRoomsForEdit(booking, result);
                if (state.selectedRooms.isEmpty()) {
                    Toast.makeText(BookingWizardActivity.this, R.string.edit_reservation_load_failed, Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                showStep(stepIndex);
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                loading.setVisibility(View.GONE);
                Toast.makeText(BookingWizardActivity.this, R.string.edit_reservation_load_failed, Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    /**
     * Pre-fills Steps 4-5's guest-info fields from a cancelled transaction
     * for "Book Again"/"Reserve Again" (see BookingAndReservationActivity#
     * startBookAgain()) - a brand-new transaction, not a Modify, so unlike
     * seedStateForEdit() this never sets editingReservationId/
     * originalPaymentMethod and deliberately leaves dates/amenities/the ID
     * card image alone: the original dates are almost always already in the
     * past by the time a cancelled transaction is booked again, and the ID
     * image lives on the server against the old (cancelled) record - the
     * guest picks fresh dates and re-attaches the ID photo same as any new
     * transaction. Room selection itself is already handled separately via
     * PendingWizardRooms, before this is called.
     */
    private void seedStateForBookAgain(Booking original) {
        applyRepresentativeFields(RepresentativeName.fieldsFor(this, original));
        state.guestFirstName = original.getGuestFirstName();
        state.guestMiddleName = original.getGuestMiddleName();
        state.guestLastName = original.getGuestLastName();
        state.adults = Math.max(1, original.getAdults());
        state.children = Math.max(0, original.getChildren());
        state.idCardType = original.getIdCardType() != null ? original.getIdCardType() : "None";
        if (!"None".equals(state.idCardType)) {
            state.discount = new Discount(original.getDiscountId(), state.idCardType, null, 0, null);
        }
        if (original.getAdditionalGuests() != null) {
            for (Booking.AdditionalGuest g : original.getAdditionalGuests()) {
                state.additionalGuests.add(new BookingAndReservationActivity.AdditionalGuest(g.name, g.age, g.gender, g.relationship));
            }
        }
    }

    private void handleBackPress() {
        if (currentStepIndex > 1) {
            goToPreviousStep();
        } else {
            leaveWizard();
        }
    }

    /** Back on the first step leaves the wizard: with choices made, ask first ("Discard this booking?"). Back between steps never asks. */
    private void leaveWizard() {
        if (!isEditMode() && !state.hasUnsavedChoices()) {
            finish();
            return;
        }
        int title = isEditMode() ? R.string.wizard_discard_changes_title
                : (state.isBookingMode() ? R.string.wizard_discard_booking_title : R.string.wizard_discard_reservation_title);
        confirmStep(getString(title), getString(R.string.wizard_discard_message), getString(R.string.wizard_discard_yes),
                getString(R.string.wizard_discard_keep), this::finish);
    }

    private void goToPreviousStep() {
        if (currentStepIndex <= 1) {
            leaveWizard();
            return;
        }
        showStep(currentStepIndex - 1);
    }

    private void attemptGoToNextStep() {
        WizardStepFragment current = currentFragment();
        if (current != null && !current.validateBeforeNext()) {
            int message = current.validationMessageRes();
            if (message != 0) Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentStepIndex >= stepCount || !canOpenModal()) return;
        final int from = currentStepIndex;
        Runnable advance = () -> {
            if (from != currentStepIndex) return; // already moved on
            String title = current != null ? current.nextConfirmTitle() : null;
            if (title == null) {
                showStep(from + 1);
                return;
            }
            confirmStep(title, current.nextConfirmSummary(), getString(R.string.wizard_confirm_yes), () -> {
                if (from == currentStepIndex) showStep(from + 1);
            });
        };
        if (current != null && current.interceptNext(advance)) return;
        advance.run();
    }

    private void showStep(int stepIndex) {
        boolean forward = stepIndex >= currentStepIndex;
        currentStepIndex = stepIndex;
        // Only the first two steps (stays, amenities) work on one stay at a time; from the guest steps on everything reads all stays.
        if (stepIndex >= 3) state.activate(0);
        Fragment fragment = createFragmentForStep(stepIndex);
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        if (Motion.enabled(this)) {
            if (forward) tx.setCustomAnimations(R.anim.wizard_enter_forward, R.anim.wizard_exit);
            else tx.setCustomAnimations(R.anim.wizard_enter_back, R.anim.wizard_exit);
        }
        tx.replace(R.id.wizardFragmentContainer, fragment, "wizard_step_" + stepIndex);
        tx.commitNow();
        updateChrome();
    }

    private Fragment createFragmentForStep(int stepIndex) {
        // Only a fresh (non-edit) Reservation run has 8 steps at all (see
        // stepCount's assignment in onCreate()) - step 7 there is the new
        // Payment Method chooser, and the review fragment moves to step 8.
        // Every other run (Booking mode, or Reservation Modify) stays at 7
        // steps with the review fragment still occupying step 7, unchanged.
        boolean hasPaymentMethodStep = stepCount == 7;
        switch (stepIndex) {
            // Dates and the rooms available for them, together (and "Add another stay").
            case 1: return new StepStaysFragment();
            case 2: return new Step3AmenitiesFragment();
            case 3: return new Step4GuestIdentificationFragment();
            case 4: return new Step5AdditionalGuestsFragment();
            case 5: return new Step6IdVerificationFragment();
            case 6: return hasPaymentMethodStep ? new Step7PaymentMethodFragment() : new Step8ReviewPaymentFragment();
            case 7: return new Step8ReviewPaymentFragment();
            default: throw new IllegalStateException("Unknown wizard step: " + stepIndex);
        }
    }

    private WizardStepFragment currentFragment() {
        Fragment f = getSupportFragmentManager().findFragmentById(R.id.wizardFragmentContainer);
        return f instanceof WizardStepFragment ? (WizardStepFragment) f : null;
    }

    private void updateChrome() {
        stepIndicator.setCurrentStep(currentStepIndex);
        WizardStepFragment current = currentFragment();
        String title = current != null ? current.stepTitle() : "";
        tvStepLabel.setText(getString(R.string.wizard_step_x_of_y_format, currentStepIndex, stepCount));
        TextView tvStepName = findViewById(R.id.tvWizardStepName);
        if (tvStepName != null) tvStepName.setText(title);
        com.google.android.material.progressindicator.LinearProgressIndicator progressBar = findViewById(R.id.wizardProgressBar);
        if (progressBar != null) progressBar.setProgressCompat(Math.round(100f * currentStepIndex / stepCount), true);
        if (tvStepsRemaining != null) {
            int remaining = stepCount - currentStepIndex;
            tvStepsRemaining.setText(remaining > 0
                    ? getResources().getQuantityString(R.plurals.wizard_steps_remaining, remaining, remaining)
                    : getString(R.string.wizard_final_step_label));
        }
        btnPrevious.setVisibility(currentStepIndex > 1 ? View.VISIBLE : View.INVISIBLE);
        // The final review step shows its own Confirm Booking/Confirm
        // Reservation button inside the fragment (gated on the T&C checkbox)
        // instead of this generic Next button.
        btnNext.setVisibility(currentStepIndex < stepCount ? View.VISIBLE : View.GONE);
        // Reset to enabled by default on every step change - Step 1's own
        // onWizardStepShown() below re-disables it when nothing is selected
        // yet (see setNextEnabled()); every other step has no such gate.
        btnNext.setEnabled(true);
        if (current != null) {
            current.onWizardStepShown();
        }
        updateSummaryBar();
    }

    /**
     * The bar under the step content: what is picked so far on the left ("2 stays · 4 rooms · 1 guest", or the dates for one stay) and
     * the total on the right. When add-ons are part of the total, a second line says how it adds up ("Rooms ₱18,600.00 + Amenities
     * ₱300.00 = ₱18,900.00"), so a total that changed between steps is never a surprise. Hidden until there is something to show.
     */
    private void updateSummaryBar() {
        View bar = findViewById(R.id.wizardSummaryBar);
        if (bar == null) return;
        // The final review step shows the same facts in its own summary card.
        if (state.checkIn == null || state.checkOut == null || currentStepIndex >= stepCount || currentFragment() instanceof Step3AmenitiesFragment) {
            bar.setVisibility(View.GONE); // the amenities step has its own live total at the bottom
            return;
        }
        int rooms = 0;
        for (BookingWizardState.Stay s : state.allStays()) rooms += s.rooms.size();
        String roomsText = rooms > 0 ? getResources().getQuantityString(R.plurals.wizard_summary_rooms, rooms, rooms) : getString(R.string.wizard_summary_no_rooms);
        int guests = state.adults + state.children;
        String guestsText = getResources().getQuantityString(R.plurals.wizard_summary_guests, guests, guests);
        String first;
        if (state.isGroup()) {
            first = getResources().getQuantityString(R.plurals.stays_count, state.stayCount(), state.stayCount());
        } else {
            SimpleDateFormat fmt = new SimpleDateFormat("MMM dd", Locale.US);
            first = getString(R.string.wizard_summary_dates_format, fmt.format(state.checkIn.getTime()), fmt.format(state.checkOut.getTime()));
        }
        ((TextView) findViewById(R.id.wizardSummaryLeft)).setText(getString(R.string.wizard_summary_left_format, first, roomsText, guestsText));
        double roomsTotal = state.roomsTotal();
        double amenities = state.amenitiesTotal();
        ((TextView) findViewById(R.id.wizardSummaryTotal)).setText(MoneyFormat.format(roomsTotal + amenities));
        TextView breakdown = findViewById(R.id.wizardSummaryBreakdown);
        if (amenities > 0.004) {
            breakdown.setText(getString(R.string.wizard_summary_breakdown_format, MoneyFormat.format(roomsTotal), MoneyFormat.format(amenities), MoneyFormat.format(roomsTotal + amenities)));
            breakdown.setVisibility(View.VISIBLE);
        } else {
            breakdown.setVisibility(View.GONE);
        }
        bar.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("currentStepIndex", currentStepIndex);
        // Rooms, amenities, names, ages and the rest can be larger than a Bundle may carry, so they go to a private cache file.
        if (!isEditMode() && state != null) {
            state.commitActive();
            WizardStateStore.save(this, state);
        }
    }

    @Override
    protected void onDestroy() {
        if (isFinishing()) WizardStateStore.clear(this);
        super.onDestroy();
    }
}
