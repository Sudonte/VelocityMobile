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
public class BookingWizardActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "EXTRA_MODE";
    public static final String EXTRA_EDIT_BOOKING = "EXTRA_EDIT_BOOKING";

    private int stepCount = 7;

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

    /** Lets a step (currently only Step 2's room selection) dynamically gate Next - e.g. disabled until at least one room is selected. */
    public void setNextEnabled(boolean enabled) {
        if (currentStepIndex < stepCount) {
            btnNext.setEnabled(enabled);
        }
    }

    /** Advances past the current step without re-running its own validateBeforeNext() - for a step's own "Skip" affordance (e.g. amenities). */
    public void goToNextStepSkippingValidation() {
        if (currentStepIndex < stepCount) {
            showStep(currentStepIndex + 1);
        }
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

            // "Book Again"/"Reserve Again" on a cancelled transaction - not an
            // edit (this is a brand-new transaction, never seedStateForEdit()'s
            // Modify path), so only the guest-info fields carry over.
            Booking bookAgainOriginal = PendingBookAgainPrefill.consume();
            if (bookAgainOriginal != null) {
                seedStateForBookAgain(bookAgainOriginal);
            }
        }

        // Only a fresh (non-edit) Reservation gains the new Payment Method
        // step - see this class's docblock. Booking mode and Reservation
        // Modify both stay at 7 steps, completely unchanged.
        stepCount = (mode == BookingWizardState.Mode.RESERVATION && !isEditMode()) ? 8 : 7;

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
        findViewById(R.id.btnWizardBack).setOnClickListener(v -> handleBackPress());
        btnPrevious.setOnClickListener(v -> goToPreviousStep());
        btnNext.setOnClickListener(v -> attemptGoToNextStep());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackPress();
            }
        });

        if (savedInstanceState != null) {
            currentStepIndex = savedInstanceState.getInt("currentStepIndex", 1);
        }
        showStep(currentStepIndex);
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
    private void seedStateForEdit(Booking booking) {
        editingReservationId = booking.getId();
        originalPaymentMethod = booking.getPaymentMethod() != null ? booking.getPaymentMethod() : "CASH";
        state.paymentMethod = "GCASH".equalsIgnoreCase(originalPaymentMethod) ? "gcash" : "cash";

        state.guestFirstName = booking.getGuestFirstName();
        state.guestMiddleName = booking.getGuestMiddleName();
        state.guestLastName = booking.getGuestLastName();
        state.adults = Math.max(1, booking.getAdults());
        state.children = Math.max(0, booking.getChildren());
        state.idCardType = booking.getIdCardType() != null ? booking.getIdCardType() : "None";
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
            Calendar checkOut = Calendar.getInstance();
            checkOut.setTime(displayFormat.parse(booking.getCheckOutDate()));
            state.checkOut = checkOut;
        } catch (ParseException | NullPointerException ignored) {
            // Leave state.checkIn/checkOut null - Step 1 (Dates) shows its normal empty/unset state.
        }

        List<Room> cached = RoomRepository.getInstance(this).getAllRooms();
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
        state.guestFirstName = original.getGuestFirstName();
        state.guestMiddleName = original.getGuestMiddleName();
        state.guestLastName = original.getGuestLastName();
        state.adults = Math.max(1, original.getAdults());
        state.children = Math.max(0, original.getChildren());
        state.idCardType = original.getIdCardType() != null ? original.getIdCardType() : "None";
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
            finish();
        }
    }

    private void goToPreviousStep() {
        if (currentStepIndex <= 1) {
            finish();
            return;
        }
        showStep(currentStepIndex - 1);
    }

    private void attemptGoToNextStep() {
        WizardStepFragment current = currentFragment();
        if (current != null && !current.validateBeforeNext()) {
            Toast.makeText(this, R.string.wizard_step_incomplete, Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentStepIndex < stepCount) {
            showStep(currentStepIndex + 1);
        }
    }

    private void showStep(int stepIndex) {
        currentStepIndex = stepIndex;
        Fragment fragment = createFragmentForStep(stepIndex);
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        tx.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out);
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
        boolean hasPaymentMethodStep = stepCount == 8;
        switch (stepIndex) {
            // Dates first, then Room Selection - availability shown in step 2
            // is date-range-aware (see Step1RoomSelectionFragment) instead of
            // the plain undated count it used to show back when it was step 1.
            case 1: return new Step2DatesFragment();
            case 2: return new Step1RoomSelectionFragment();
            case 3: return new Step3AmenitiesFragment();
            case 4: return new Step4GuestIdentificationFragment();
            case 5: return new Step5AdditionalGuestsFragment();
            case 6: return new Step6IdVerificationFragment();
            case 7: return hasPaymentMethodStep ? new Step7PaymentMethodFragment() : new Step8ReviewPaymentFragment();
            case 8: return new Step8ReviewPaymentFragment();
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
        tvStepLabel.setText(getString(R.string.wizard_step_label_format, currentStepIndex, stepCount, title));
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
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("currentStepIndex", currentStepIndex);
    }
}
