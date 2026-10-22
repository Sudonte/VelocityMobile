package com.example.velocitysuites;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * Steps 1 and 2 in one: the guest picks the check-in and check-out dates and, right under them, the rooms available for those dates
 * (the existing {@link Step2DatesFragment} and {@link Step1RoomSelectionFragment} run as children of this step). "Add another stay"
 * saves the stay and opens an empty "Stay 2" with its own dates and rooms; a summary of every stay (dates, nights, rooms, price) with
 * edit and remove is shown from the second stay on, with the group total. One stay behaves exactly as it always did.
 */
public class StepStaysFragment extends WizardStepFragment {

    private static final String TAG_DATES = "stay_dates";
    private static final String TAG_ROOMS = "stay_rooms";

    private final SimpleDateFormat shortDate = new SimpleDateFormat("MMM dd", Locale.US);
    private final ClickGuard guard = new ClickGuard();

    private View layoutStaysSummary;
    private LinearLayout layoutStayRows;
    private TextView tvGroupTotal;
    private TextView tvActiveHeading;
    private TextView tvIncomplete;
    private MaterialButton btnAddStay;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_step_stays, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        layoutStaysSummary = view.findViewById(R.id.layoutStaysSummary);
        layoutStayRows = view.findViewById(R.id.layoutStayRows);
        tvGroupTotal = view.findViewById(R.id.tvStaysGroupTotal);
        tvActiveHeading = view.findViewById(R.id.tvActiveStayHeading);
        tvIncomplete = view.findViewById(R.id.tvStayIncomplete);
        btnAddStay = view.findViewById(R.id.btnAddAnotherStay);

        // Several stays belong to a NEW booking or reservation only - a Modify edits the one reservation it opened.
        boolean editing = getWizardActivity().isEditMode();
        btnAddStay.setVisibility(editing ? View.GONE : View.VISIBLE);
        btnAddStay.setOnClickListener(v -> {
            if (guard.tryAcquire()) onAddStay();
        });

        // Children are rebuilt from the state, so coming back to this step (or switching stay) always shows the stay as saved.
        showStay();
    }

    @Override
    public String stepTitle() {
        return getString(R.string.step_title_stays);
    }

    @Override
    public void onWizardStepShown() {
        refreshSummary();
    }

    /** Selection or dates changed in a child: keep the summary, the Add button and Next in step. */
    @Override
    public void onSelectionChanged() {
        refreshSummary();
    }

    /** Called by the dates child once a date was picked: the room list is rebuilt for the new range (or hidden until both dates are valid). */
    void onDatesChanged() {
        if (!isAdded()) return;
        showRooms();
        refreshSummary();
    }

    private boolean datesValid() {
        BookingWizardState s = getState();
        return s.checkIn != null && s.checkOut != null && s.checkOut.after(s.checkIn)
                && s.isCheckInWithinWindow(getWizardActivity().isEditMode());
    }

    /** (Re)builds both children for the stay being edited. */
    private void showStay() {
        getChildFragmentManager().beginTransaction().replace(R.id.stayDatesContainer, new Step2DatesFragment(), TAG_DATES).commitNowAllowingStateLoss();
        showRooms();
        refreshSummary();
    }

    private void showRooms() {
        FragmentManager fm = getChildFragmentManager();
        Fragment old = fm.findFragmentByTag(TAG_ROOMS);
        if (datesValid()) {
            fm.beginTransaction().replace(R.id.stayRoomsContainer, new Step1RoomSelectionFragment(), TAG_ROOMS).commitNowAllowingStateLoss();
        } else {
            if (old != null) fm.beginTransaction().remove(old).commitNowAllowingStateLoss();
            getWizardActivity().setNextEnabled(false); // no dates, no rooms: nothing to continue with yet
        }
    }

    private void refreshSummary() {
        if (!isAdded() || getView() == null) return;
        BookingWizardState state = getState();
        int count = state.stayCount();
        boolean group = count > 1;
        layoutStaysSummary.setVisibility(group ? View.VISIBLE : View.GONE);
        tvActiveHeading.setVisibility(group ? View.VISIBLE : View.GONE);
        if (group) tvActiveHeading.setText(getString(R.string.stay_active_heading_format, state.activeStayIndex() + 1, count));

        layoutStayRows.removeAllViews();
        List<BookingWizardState.Stay> stays = state.allStays();
        for (int i = 0; i < stays.size(); i++) {
            layoutStayRows.addView(buildRow(i, stays.get(i), i == state.activeStayIndex(), stays.size()));
        }
        tvGroupTotal.setText(getString(R.string.stays_group_total_format, MoneyFormat.format(state.groupTotal())));

        BookingWizardState.Stay active = stays.get(state.activeStayIndex());
        btnAddStay.setEnabled(active.isComplete() && count < BookingWizardState.MAX_STAYS);
        if (tvIncomplete != null && active.isComplete()) tvIncomplete.setVisibility(View.GONE);
    }

    private View buildRow(int index, BookingWizardState.Stay stay, boolean active, int total) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(48));
        row.setPadding(0, dp(4), 0, dp(4));

        LinearLayout text = new LinearLayout(requireContext());
        text.setOrientation(LinearLayout.VERTICAL);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(requireContext());
        title.setText(getString(R.string.stay_label_format, index + 1));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setTextSize(13);
        title.setTextColor(requireContext().getColor(active ? R.color.velocity_red_primary : R.color.velocity_text_primary));
        text.addView(title);

        TextView detail = new TextView(requireContext());
        detail.setText(describe(stay));
        detail.setTextSize(12);
        detail.setTextColor(requireContext().getColor(R.color.velocity_text_secondary));
        text.addView(detail);

        if (!active) {
            MaterialButton edit = new MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            edit.setText(R.string.stay_edit);
            edit.setAllCaps(false);
            edit.setTextSize(12);
            edit.setMinHeight(dp(44));
            edit.setOnClickListener(v -> {
                if (guard.tryAcquire()) onOpenStay(index);
            });
            row.addView(edit);
        }
        if (total > 1) {
            MaterialButton remove = new MaterialButton(requireContext(), null, android.R.attr.borderlessButtonStyle);
            remove.setText(R.string.stay_remove);
            remove.setAllCaps(false);
            remove.setTextSize(12);
            remove.setMinHeight(dp(44));
            remove.setOnClickListener(v -> {
                if (guard.tryAcquire()) confirmRemove(index);
            });
            row.addView(remove);
        }
        return row;
    }

    /** "Nov 01 - Nov 03 · 2 nights · 2 rooms · ₱10,000.00" - or what is still missing. */
    private String describe(BookingWizardState.Stay stay) {
        if (stay.checkIn == null || stay.checkOut == null) return getString(R.string.stay_summary_no_dates);
        String dates = getString(R.string.stay_summary_dates_format, shortDate.format(stay.checkIn.getTime()), shortDate.format(stay.checkOut.getTime()));
        long nights = stay.nights();
        String nightsText = getResources().getQuantityString(R.plurals.receipt_nights_count, (int) nights, (int) nights);
        String roomsText = stay.rooms.isEmpty() ? getString(R.string.wizard_summary_no_rooms)
                : getResources().getQuantityString(R.plurals.wizard_summary_rooms, stay.rooms.size(), stay.rooms.size());
        return getString(R.string.stay_summary_line_format, dates, nightsText, roomsText, MoneyFormat.format(stay.total()));
    }

    private void onAddStay() {
        BookingWizardState state = getState();
        if (!validateActive()) return;
        if (state.stayCount() >= BookingWizardState.MAX_STAYS) {
            showIncomplete(getString(R.string.stays_max_format, BookingWizardState.MAX_STAYS));
            return;
        }
        if (state.addStay()) showStay();
    }

    private void onOpenStay(int index) {
        if (!validateActive()) return; // finish (or fix) the stay being edited before leaving it
        getState().activate(index);
        showStay();
    }

    private void confirmRemove(int index) {
        new SafeDialogs.Material(requireContext())
                .setTitle(getString(R.string.stay_remove_title_format, index + 1))
                .setMessage(R.string.stay_remove_message)
                .setNegativeButton(R.string.cancel_label, null)
                .setPositiveButton(R.string.stay_remove, (d, w) -> {
                    getState().removeStay(index);
                    showStay();
                })
                .show();
    }

    /** The stay being edited needs valid dates and at least one room before the guest can leave it. */
    private boolean validateActive() {
        Fragment dates = getChildFragmentManager().findFragmentByTag(TAG_DATES);
        Fragment rooms = getChildFragmentManager().findFragmentByTag(TAG_ROOMS);
        boolean datesOk = !(dates instanceof WizardStepFragment) || ((WizardStepFragment) dates).validateBeforeNext();
        boolean roomsOk = rooms instanceof WizardStepFragment && ((WizardStepFragment) rooms).validateBeforeNext();
        if (!datesOk) {
            showIncomplete(getString(R.string.stay_incomplete_dates));
            return false;
        }
        if (!roomsOk) {
            showIncomplete(getString(R.string.error_select_one_room));
            return false;
        }
        return true;
    }

    private void showIncomplete(String message) {
        if (tvIncomplete == null) return;
        tvIncomplete.setText(message);
        tvIncomplete.setVisibility(View.VISIBLE);
        tvIncomplete.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED);
    }

    @Override
    public boolean validateBeforeNext() {
        BookingWizardState state = getState();
        if (!validateActive()) return false;
        state.commitActive();
        // Every other stay must be complete too: open the first one that is not, with the reason shown.
        List<BookingWizardState.Stay> stays = state.allStays();
        for (int i = 0; i < stays.size(); i++) {
            if (!stays.get(i).isComplete()) {
                state.activate(i);
                showStay();
                showIncomplete(getString(R.string.stay_incomplete_format, i + 1));
                return false;
            }
        }
        // The next steps start from the first stay; the others stay saved.
        state.activate(0);
        return true;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
