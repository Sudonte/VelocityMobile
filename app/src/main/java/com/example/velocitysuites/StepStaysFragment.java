package com.example.velocitysuites;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * Dates and rooms in one step. ONE booking (or reservation) holds one or more stays, each with its own dates and rooms: the guest picks the
 * check-in and check-out dates and, right under them, the rooms available for those dates (the existing {@link Step2DatesFragment} and
 * {@link Step1RoomSelectionFragment} run as children of this step). "Add another stay" saves the stay and opens an empty next one.
 *
 * From the second stay on, a "Your stays" card lists every stay (dates, nights, rooms, price) with the same Edit and Remove for each, the stay
 * being edited highlighted, and the total of all of them. Two stays may overlap or touch but never have identical dates (inline error, Next
 * disabled), and Next also waits for every stay to have a room. One stay behaves exactly as it always did.
 */
public class StepStaysFragment extends WizardStepFragment {

    private static final String TAG_DATES = "stay_dates";
    private static final String TAG_ROOMS = "stay_rooms";

    private final SimpleDateFormat shortDate = new SimpleDateFormat("MMM d", Locale.US);
    private final ClickGuard guard = new ClickGuard();

    private NestedScrollView scroll;
    private View content;
    private View layoutStaysSummary;
    private LinearLayout layoutStayRows;
    private TextView tvStaysTotal;
    private TextView tvActiveHeading;
    private TextView tvIncomplete;
    private TextView tvDuplicate;
    private MaterialButton btnAddStay;
    /** Set when a valid range was just picked: once the room list is ready, the page scrolls down to "Add room". */
    private boolean scrollToAddRoomWhenReady;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_step_stays, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        scroll = view.findViewById(R.id.staysScroll);
        content = view.findViewById(R.id.staysContent);
        layoutStaysSummary = view.findViewById(R.id.layoutStaysSummary);
        layoutStayRows = view.findViewById(R.id.layoutStayRows);
        tvStaysTotal = view.findViewById(R.id.tvStaysTotal);
        tvActiveHeading = view.findViewById(R.id.tvActiveStayHeading);
        tvIncomplete = view.findViewById(R.id.tvStayIncomplete);
        tvDuplicate = view.findViewById(R.id.tvStayDuplicate);
        btnAddStay = view.findViewById(R.id.btnAddAnotherStay);

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
        scrollToAddRoomWhenReady = datesValid();
        showRooms();
        refreshSummary();
    }

    /** Called by the room list once the availability for the dates is on screen - the moment "Add room" is where it will stay. */
    void onRoomsListReady() {
        if (!isAdded() || !scrollToAddRoomWhenReady) return;
        scrollToAddRoomWhenReady = false;
        Fragment rooms = getChildFragmentManager().findFragmentByTag(TAG_ROOMS);
        View add = rooms != null && rooms.getView() != null ? rooms.getView().findViewById(R.id.btnAddRoom) : null;
        if (add == null || add.getVisibility() != View.VISIBLE) return;
        scroll.post(() -> scrollTo(topInContent(add) - dp(24)));
    }

    /** Smooth scroll, or a plain jump when the guest turned animations off. */
    private void scrollTo(int y) {
        if (scroll == null) return;
        int target = Math.max(0, y);
        if (Motion.enabled(requireContext())) scroll.smoothScrollTo(0, target);
        else scroll.scrollTo(0, target);
    }

    private int topInContent(View v) {
        int y = 0;
        View cur = v;
        while (cur != null && cur != content) {
            y += cur.getTop();
            ViewParent parent = cur.getParent();
            cur = parent instanceof View ? (View) parent : null;
        }
        return y;
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
            getWizardActivity().setNextEnabledQuietly(false); // no dates, no rooms: nothing to continue with yet
        }
    }

    private void refreshSummary() {
        if (!isAdded() || getView() == null) return;
        BookingWizardState state = getState();
        int count = state.stayCount();
        boolean several = count > 1;
        int active = state.activeStayIndex();
        layoutStaysSummary.setVisibility(several ? View.VISIBLE : View.GONE);
        tvActiveHeading.setText(several ? getString(R.string.stay_heading_format, active + 1) : getString(R.string.stay_heading_single));

        layoutStayRows.removeAllViews();
        List<BookingWizardState.Stay> stays = state.allStays();
        if (several) {
            for (int i = 0; i < stays.size(); i++) {
                layoutStayRows.addView(buildRow(i, stays.get(i), i == active));
            }
            tvStaysTotal.setText(MoneyFormat.format(state.groupTotal()));
        }

        // Identical dates in two stays: said right here, under the dates, and Next waits.
        int duplicate = state.duplicateOf(active);
        BookingWizardState.Stay activeStay = stays.get(active);
        if (duplicate >= 0 && activeStay.checkIn != null && activeStay.checkOut != null) {
            tvDuplicate.setText(getString(R.string.stay_duplicate_error_format, shortDate.format(activeStay.checkIn.getTime()), shortDate.format(activeStay.checkOut.getTime())));
            tvDuplicate.setVisibility(View.VISIBLE);
        } else {
            tvDuplicate.setVisibility(View.GONE);
        }

        btnAddStay.setEnabled(activeStay.isComplete() && duplicate < 0 && count < BookingWizardState.MAX_STAYS);
        if (tvIncomplete != null && activeStay.isComplete()) tvIncomplete.setVisibility(View.GONE);
        // Next needs valid dates and a room in EVERY stay, and no two stays with the same dates.
        getWizardActivity().setNextEnabledQuietly(state.staysReadyToContinue());
        getWizardActivity().refreshSummaryBar();
    }

    private View buildRow(int index, BookingWizardState.Stay stay, boolean active) {
        View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_stay_row, layoutStayRows, false);
        row.setTag(index);
        if (active) row.setBackgroundColor(requireContext().getColor(R.color.velocity_red_soft));

        ((TextView) row.findViewById(R.id.tvStayRowTitle)).setText(titleOf(index, stay));
        ((TextView) row.findViewById(R.id.tvStayRowRooms)).setText(roomsLineOf(stay));
        row.findViewById(R.id.tvStayRowEditing).setVisibility(active ? View.VISIBLE : View.GONE);

        MaterialButton edit = row.findViewById(R.id.btnStayRowEdit);
        edit.setContentDescription(getString(R.string.stay_edit_cd_format, index + 1));
        edit.setOnClickListener(v -> {
            if (guard.tryAcquire()) onOpenStay(index);
        });
        MaterialButton remove = row.findViewById(R.id.btnStayRowRemove);
        remove.setContentDescription(getString(R.string.stay_remove_cd_format, index + 1));
        remove.setOnClickListener(v -> {
            if (guard.tryAcquire()) confirmRemove(index);
        });
        return row;
    }

    /** "Stay 1" in bold, then "Oct 25 → Oct 26 · 1 night" (or what is still missing). */
    private CharSequence titleOf(int index, BookingWizardState.Stay stay) {
        String name = getString(R.string.stay_label_format, index + 1);
        String rest;
        if (stay.checkIn == null || stay.checkOut == null) {
            rest = getString(R.string.stay_summary_no_dates);
        } else {
            long nights = stay.nights();
            // no-break spaces keep "Oct 28 → Oct 29" and "1 night" whole when the line wraps beside the Edit / Remove buttons
            rest = getString(R.string.stay_row_dates_format, shortDate.format(stay.checkIn.getTime()).replace(' ', ' '), shortDate.format(stay.checkOut.getTime()).replace(' ', ' '),
                    getResources().getQuantityString(R.plurals.receipt_nights_count, (int) nights, (int) nights).replace(' ', ' '));
        }
        SpannableStringBuilder sb = new SpannableStringBuilder(name).append("  ").append(rest);
        sb.setSpan(new StyleSpan(Typeface.BOLD), 0, name.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    /** "2 rooms · ₱9,300.00" */
    private String roomsLineOf(BookingWizardState.Stay stay) {
        if (stay.rooms.isEmpty()) return getString(R.string.wizard_summary_no_rooms);
        return getString(R.string.stay_row_rooms_format,
                getResources().getQuantityString(R.plurals.wizard_summary_rooms, stay.rooms.size(), stay.rooms.size()), MoneyFormat.format(stay.total()));
    }

    private void onAddStay() {
        BookingWizardState state = getState();
        if (!validateActive()) return;
        if (state.hasDuplicateStay()) {
            showIncomplete(tvDuplicate.getText().toString());
            return;
        }
        if (state.stayCount() >= BookingWizardState.MAX_STAYS) {
            showIncomplete(getString(R.string.stays_max_format, BookingWizardState.MAX_STAYS));
            return;
        }
        if (state.addStay()) {
            showStay();
            scrollTo(topInContent(tvActiveHeading) - dp(12)); // the new, empty stay's editor
        }
    }

    private void onOpenStay(int index) {
        if (index == getState().activeStayIndex()) {
            scrollTo(topInContent(tvActiveHeading) - dp(12));
            return;
        }
        if (!validateActive()) return; // finish (or fix) the stay being edited before leaving it
        getState().activate(index);
        showStay();
        scrollTo(topInContent(tvActiveHeading) - dp(12));
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
        // Two stays with the same dates: the inline message says which, and nothing moves on.
        if (state.hasDuplicateStay()) {
            refreshSummary();
            showIncomplete(tvDuplicate.getVisibility() == View.VISIBLE ? tvDuplicate.getText().toString() : getString(R.string.stay_duplicate_generic));
            return false;
        }
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
