package com.example.velocitysuites;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Step 1: Check-In/Check-Out dates - the very first thing the guest picks,
 * before any room is chosen. Checkout must always be later than checkin, and
 * checkin must be CheckInWindow.earliest() (hotel-local today + 2 days) or later.
 * Room availability (cross-guest inventory for a room TYPE) is deliberately
 * NOT checked here any more (this step used to run after Room Selection and
 * had to protect whatever room was already staged) - now that Room Selection
 * is step 2, it's step 2's own job to fetch date-aware availability and
 * reconcile any pre-existing selection against whatever dates are picked
 * here (see Step1RoomSelectionFragment). What IS checked here is a
 * completely separate concern: Guest Transaction Date Validation - this
 * guest's OWN other active Bookings/Reservations can't overlap this stay,
 * regardless of room type (see RoomRepository#findConflictingStayForGuest()).
 */
public class Step2DatesFragment extends WizardStepFragment {

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);

    private RoomRepository repository;
    private TextInputLayout tilCheckIn;
    private TextInputLayout tilCheckOut;
    private TextInputEditText etCheckIn;
    private TextInputEditText etCheckOut;
    private android.view.View cardNightsSummary;
    private android.widget.TextView tvNightsSummary;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_step2_dates, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = RoomRepository.getInstance(requireContext());

        android.widget.TextView tvDetailsLabel = view.findViewById(R.id.tvDatesStepDetailsLabel);
        tvDetailsLabel.setText(getState().isBookingMode() ? R.string.step_booking_details_title : R.string.step_details_title);

        tilCheckIn = view.findViewById(R.id.tilCheckIn);
        tilCheckOut = view.findViewById(R.id.tilCheckOut);
        etCheckIn = view.findViewById(R.id.etCheckIn);
        etCheckOut = view.findViewById(R.id.etCheckOut);
        cardNightsSummary = view.findViewById(R.id.cardNightsSummary);
        tvNightsSummary = view.findViewById(R.id.tvNightsSummary);

        etCheckIn.setOnClickListener(v -> showDatePicker(true));
        etCheckOut.setOnClickListener(v -> showDatePicker(false));

        if (getState().checkIn != null) {
            etCheckIn.setText(dateFormat.format(getState().checkIn.getTime()));
        }
        if (getState().checkOut != null) {
            etCheckOut.setText(dateFormat.format(getState().checkOut.getTime()));
        }
        updateNightsSummary();
    }

    /** Auto-calculated nights + selected date range, shown directly on this step per spec (previously only ever shown later, in Step 7's summary). */
    private void updateNightsSummary() {
        if (cardNightsSummary == null) return;
        BookingWizardState state = getState();
        if (state.checkIn == null || state.checkOut == null) {
            cardNightsSummary.setVisibility(View.GONE);
            return;
        }
        cardNightsSummary.setVisibility(View.VISIBLE);
        tvNightsSummary.setText(getString(R.string.stay_duration_summary_format,
                state.nights(), dateFormat.format(state.checkIn.getTime()), dateFormat.format(state.checkOut.getTime())));
    }

    @Override
    public String stepTitle() {
        return "Dates";
    }

    @Override
    public boolean validateBeforeNext() {
        BookingWizardState state = getState();
        if (state.checkIn == null || state.checkOut == null) {
            if (state.checkIn == null) tilCheckIn.setError(getString(R.string.select_date_hint));
            if (state.checkOut == null) tilCheckOut.setError(getString(R.string.select_date_hint));
            return false;
        }
        // A fresh run must start inside the window. A Modify keeps the
        // reservation's existing check-in untouched (it may legitimately be
        // today/past by now), so it is only re-checked if the guest changed it.
        boolean checkInUnchangedOnEdit = getWizardActivity().isEditMode() && state.originalCheckIn != null
                && CheckInWindow.toLocalDate(state.originalCheckIn).equals(CheckInWindow.toLocalDate(state.checkIn));
        if (!checkInUnchangedOnEdit && !CheckInWindow.isAllowed(state.checkIn)) {
            tilCheckIn.setError(getString(R.string.error_checkin_outside_window));
            return false;
        }
        if (!state.checkOut.after(state.checkIn)) {
            tilCheckOut.setError(getString(R.string.error_invalid_dates));
            return false;
        }
        tilCheckIn.setError(null);
        return validateNoGuestDateConflict();
    }

    /**
     * Guest Transaction Date Validation - blocks Next while this check-in/
     * check-out range genuinely OVERLAPS another of THIS guest's own active
     * Bookings/Reservations (any room type - see
     * RoomRepository#findConflictingStayForGuest()). An EXACT date match is
     * never returned as a conflict by that method - a guest may create
     * multiple Bookings/Reservations using the same dates, as long as room
     * availability and every other condition is independently satisfied -
     * so any non-null result here is always a genuine, different-range
     * overlap. Completely separate from Room Availability Validation
     * (cross-guest inventory for a specific room type, judged server-side
     * and by Step1RoomSelectionFragment on step 2) - both must independently
     * pass. On a Modify run, the reservation being edited AND every sibling
     * of its own BookingGroupState group (same multi-room-type transaction,
     * same dates by construction) are excluded from its own conflict check -
     * excluding only the one edited id would make Modify falsely report
     * the guest's own sibling record as a conflicting stay.
     */
    private boolean validateNoGuestDateConflict() {
        BookingWizardState state = getState();
        String excludeId = getWizardActivity().getEditingReservationId();
        List<String> excludeIds = new ArrayList<>();
        if (excludeId != null) {
            excludeIds.add(excludeId);
            excludeIds.addAll(BookingGroupState.getGroupMembers(requireContext(), excludeId));
        }
        Booking conflict = repository.findConflictingStayForGuest(state.checkIn, state.checkOut, excludeIds);
        if (conflict == null) {
            tilCheckOut.setError(null);
            return true;
        }
        String message = getString(R.string.error_overlapping_stay_dates);
        tilCheckOut.setError(message);
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
        return false;
    }

    private void showDatePicker(boolean isCheckIn) {
        BookingWizardState state = getState();
        if (!isCheckIn && state.checkIn == null) {
            tilCheckIn.setError(getString(R.string.error_select_checkin_first));
            Toast.makeText(requireContext(), R.string.error_select_checkin_first, Toast.LENGTH_SHORT).show();
            return;
        }

        Calendar activeCal = (isCheckIn ? state.checkIn : state.checkOut);
        if (activeCal == null) {
            // Opens on the earliest allowed check-in (today, hotel time) so the
            // picker never starts on an out-of-range date.
            activeCal = CheckInWindow.toDeviceMidnight(CheckInWindow.earliest());
            if (!isCheckIn && state.checkIn != null) {
                activeCal = (Calendar) state.checkIn.clone();
                activeCal.add(Calendar.DAY_OF_YEAR, 1);
            }
        }
        Calendar activeCalFinal = activeCal;

        DatePickerDialog picker = new DatePickerDialog(requireContext(), (view, year, month, dayOfMonth) -> {
            Calendar newDate = Calendar.getInstance();
            newDate.set(year, month, dayOfMonth, 0, 0, 0);
            newDate.set(Calendar.MILLISECOND, 0);

            if (isCheckIn) {
                state.checkIn = newDate;
                etCheckIn.setText(dateFormat.format(state.checkIn.getTime()));
                tilCheckIn.setError(null);

                // A previously picked checkout that's no longer after the new
                // checkin is cleared rather than silently kept or auto-shifted
                // - the guest re-picks it explicitly (clear validation, no
                // surprise date). Room availability for this new range (if a
                // room was already staged from an earlier pass through step 2)
                // is re-checked when Room Selection is next shown, not here.
                if (state.checkOut != null && !state.checkOut.after(state.checkIn)) {
                    state.checkOut = null;
                    etCheckOut.setText("");
                    tilCheckOut.setError(null);
                }
            } else {
                if (newDate.before(state.checkIn) || newDate.equals(state.checkIn)) {
                    Toast.makeText(requireContext(), R.string.error_invalid_dates, Toast.LENGTH_SHORT).show();
                } else {
                    state.checkOut = newDate;
                    etCheckOut.setText(dateFormat.format(state.checkOut.getTime()));
                    tilCheckOut.setError(null);
                }
            }
            updateNightsSummary();
            // Immediate feedback once both dates are in place, rather than
            // waiting for a Next tap - validateBeforeNext() still re-checks
            // this as the authoritative gate (e.g. if the guest never
            // triggers this picker again after some other state changes).
            if (state.checkIn != null && state.checkOut != null && state.checkOut.after(state.checkIn)) {
                validateNoGuestDateConflict();
            }
        }, activeCalFinal.get(Calendar.YEAR), activeCalFinal.get(Calendar.MONTH), activeCalFinal.get(Calendar.DAY_OF_MONTH));

        if (isCheckIn) {
            // Earliest check-in is hotel-local today + 2 days; earlier dates are disabled in the picker
            // itself, later ones are all selectable (no upper limit).
            picker.getDatePicker().setMinDate(CheckInWindow.earliestDeviceMillis());
        } else {
            // Check-out keeps its existing rule: at least one night after check-in.
            Calendar minDate = (Calendar) state.checkIn.clone();
            minDate.add(Calendar.DAY_OF_YEAR, 1);
            minDate.set(Calendar.HOUR_OF_DAY, 0);
            minDate.set(Calendar.MINUTE, 0);
            minDate.set(Calendar.SECOND, 0);
            minDate.set(Calendar.MILLISECOND, 0);
            picker.getDatePicker().setMinDate(minDate.getTimeInMillis());
        }
        picker.show();
    }
}
