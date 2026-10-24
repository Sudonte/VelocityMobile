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
    private MaterialButton btnConfirmStay;
    private View layoutStayEditor;
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
        btnConfirmStay = view.findViewById(R.id.btnConfirmStay);
        layoutStayEditor = view.findViewById(R.id.layoutStayEditor);

        btnAddStay.setOnClickListener(v -> {
            if (guard.tryAcquire()) onAddStay();
        });
        btnConfirmStay.setOnClickListener(v -> onConfirmStay()); // one question at a time is enforced by the wizard itself

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
        FragmentManager fm = getChildFragmentManager();
        if (getState().activeConfirmed) {
            // A confirmed stay lives in the "Your stays" card; its editor is gone until the guest taps Edit.
            Fragment dates = fm.findFragmentByTag(TAG_DATES);
            Fragment rooms = fm.findFragmentByTag(TAG_ROOMS);
            androidx.fragment.app.FragmentTransaction tx = fm.beginTransaction();
            if (dates != null) tx.remove(dates);
            if (rooms != null) tx.remove(rooms);
            tx.commitNowAllowingStateLoss();
        } else {
            fm.beginTransaction().replace(R.id.stayDatesContainer, new Step2DatesFragment(), TAG_DATES).commitNowAllowingStateLoss();
            showRooms();
        }
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
        List<BookingWizardState.Stay> stays = state.allStays();
        boolean anyConfirmed = false;
        for (BookingWizardState.Stay s : stays) anyConfirmed |= s.confirmed;
        boolean confirmedActive = state.activeConfirmed;
        // The card lists every stay as soon as one is confirmed (or there are several); a stay still being edited shows its editor instead.
        boolean showCard = several || anyConfirmed;
        layoutStaysSummary.setVisibility(showCard ? View.VISIBLE : View.GONE);
        layoutStayEditor.setVisibility(confirmedActive ? View.GONE : View.VISIBLE);
        tvActiveHeading.setText(editingIndex == active && !confirmedActive && stays.get(active).checkIn != null && stays.get(active).checkOut != null
                ? getString(R.string.stay_heading_format, rangeOf(stays.get(active))) : getString(R.string.stay_heading_single));

        layoutStayRows.removeAllViews();
        if (showCard) {
            for (int i = 0; i < stays.size(); i++) {
                layoutStayRows.addView(buildRow(i, stays.get(i), i == active && !confirmedActive));
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

        // "Confirm this stay" only exists once the stay has a room; "Add another stay" only once it is confirmed.
        btnConfirmStay.setVisibility(!confirmedActive && activeStay.isComplete() && duplicate < 0 ? View.VISIBLE : View.GONE);
        // (a Modify saves one stay, so it never offers another)
        btnAddStay.setVisibility(confirmedActive && !getWizardActivity().isEditMode() && duplicate < 0 && count < BookingWizardState.MAX_STAYS ? View.VISIBLE : View.GONE);
        btnAddStay.setEnabled(true);
        if (tvIncomplete != null && activeStay.isComplete()
                && (confirmedActive || !getString(R.string.confirm_stay_first).contentEquals(tvIncomplete.getText()))) tvIncomplete.setVisibility(View.GONE);
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
        edit.setContentDescription(getString(R.string.stay_edit_cd_format, rangeOf(stay)));
        edit.setOnClickListener(v -> {
            if (guard.tryAcquire()) onOpenStay(index);
        });
        MaterialButton remove = row.findViewById(R.id.btnStayRowRemove);
        remove.setContentDescription(getString(R.string.stay_remove_cd_format, rangeOf(stay)));
        remove.setOnClickListener(v -> {
            if (guard.tryAcquire()) confirmRemove(index);
        });
        return row;
    }

    /** "Oct 25 → Oct 26 · 1 night" in bold (or what is still missing): the dates head every date range. */
    private CharSequence titleOf(int index, BookingWizardState.Stay stay) {
        String text;
        if (stay.checkIn == null || stay.checkOut == null) {
            text = getString(R.string.stay_summary_no_dates);
        } else {
            long nights = stay.nights();
            // no-break spaces keep "Oct 28 → Oct 29" and "1 night" whole when the line wraps beside the Edit / Remove buttons
            text = getString(R.string.stay_row_dates_format, shortDate.format(stay.checkIn.getTime()).replace(' ', ' '), shortDate.format(stay.checkOut.getTime()).replace(' ', ' '),
                    getResources().getQuantityString(R.plurals.receipt_nights_count, (int) nights, (int) nights).replace(' ', ' '));
        }
        SpannableStringBuilder sb = new SpannableStringBuilder(text);
        sb.setSpan(new StyleSpan(Typeface.BOLD), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    /** "Oct 26 → Oct 27" for messages about one date range ("these dates" while they are not chosen yet). */
    private String rangeOf(BookingWizardState.Stay stay) {
        if (stay.checkIn == null || stay.checkOut == null) return getString(R.string.stay_these_dates);
        return shortDate.format(stay.checkIn.getTime()) + " → " + shortDate.format(stay.checkOut.getTime());
    }

    /** The date range the guest tapped Edit on (its editor is titled "Edit Oct 26 → Oct 27"); -1 otherwise. */
    private int editingIndex = -1;

    /** "2 rooms · ₱9,300.00" */
    private String roomsLineOf(BookingWizardState.Stay stay) {
        if (stay.rooms.isEmpty()) return getString(R.string.wizard_summary_no_rooms);
        String line = getString(R.string.stay_row_rooms_format,
                getResources().getQuantityString(R.plurals.wizard_summary_rooms, stay.rooms.size(), stay.rooms.size()), MoneyFormat.format(stay.total()));
        return stay.confirmed ? line : getString(R.string.stay_row_not_confirmed_format, line);
    }

    private static String capitalized(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void onAddStay() {
        BookingWizardState state = getState();
        if (!state.activeConfirmed) return; // the button is only there for a confirmed stay
        if (state.hasDuplicateStay()) {
            showIncomplete(tvDuplicate.getText().toString());
            return;
        }
        if (state.stayCount() >= BookingWizardState.MAX_STAYS) {
            showIncomplete(getString(R.string.stays_max_format, BookingWizardState.MAX_STAYS));
            return;
        }
        if (state.addStay()) {
            editingIndex = -1;
            showStay();
            scrollTo(topInContent(tvActiveHeading) - dp(12)); // the new, empty stay's editor
        }
    }

    private void onOpenStay(int index) {
        BookingWizardState state = getState();
        if (index == state.activeStayIndex() && !state.activeConfirmed) {
            scrollTo(topInContent(tvActiveHeading) - dp(12));
            return;
        }
        state.activate(index);
        editingIndex = index;
        state.reopenActive(); // an edited stay has to be confirmed again
        showStay();
        scrollTo(topInContent(tvActiveHeading) - dp(12));
    }

    private void confirmRemove(int index) {
        new SafeDialogs.Material(requireContext())
                .setTitle(getString(R.string.stay_remove_title_format, rangeOf(getState().allStays().get(index))))
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
        if (getState().activeConfirmed) return true; // confirmed = it was valid when the guest confirmed it
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

    /** "Confirm this stay? Oct 25 -> Oct 26 - 1 night - 2 rooms - P9,300.00": Yes collapses the stay into the Your stays card. */
    private void onConfirmStay() {
        BookingWizardState state = getState();
        if (!validateActive()) return;
        if (state.hasDuplicateStay()) {
            showIncomplete(tvDuplicate.getVisibility() == View.VISIBLE ? tvDuplicate.getText().toString() : getString(R.string.stay_duplicate_generic));
            return;
        }
        BookingWizardState.Stay stay = state.allStays().get(state.activeStayIndex());
        String summary = getString(R.string.confirm_stay_summary_format, summaryDates(stay),
                getResources().getQuantityString(R.plurals.wizard_summary_rooms, stay.rooms.size(), stay.rooms.size()), MoneyFormat.format(stay.total()));
        getWizardActivity().confirmStep(getString(R.string.confirm_stay_title), summary, getString(R.string.confirm_stay_yes), () -> {
            if (!isAdded()) return;
            if (getView() instanceof ViewGroup && Motion.enabled(requireContext())) {
                androidx.transition.TransitionManager.beginDelayedTransition((ViewGroup) content);
            }
            state.confirmActive();
            editingIndex = -1;
            tvIncomplete.setVisibility(View.GONE);
            showStay();
            scrollTo(0);
        });
    }

    /** "Oct 25 -> Oct 26 - 1 night" */
    private String summaryDates(BookingWizardState.Stay stay) {
        long nights = stay.nights();
        return getString(R.string.stay_row_dates_format, shortDate.format(stay.checkIn.getTime()), shortDate.format(stay.checkOut.getTime()),
                getResources().getQuantityString(R.plurals.receipt_nights_count, (int) nights, (int) nights));
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
        if (!state.activeConfirmed) {
            showIncomplete(getString(R.string.confirm_stay_first));
            return false;
        }
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
            if (!stays.get(i).isComplete() || !stays.get(i).confirmed) {
                state.activate(i);
                showStay();
                showIncomplete(getString(stays.get(i).isComplete() ? R.string.stay_unconfirmed_format : R.string.stay_incomplete_format, capitalized(rangeOf(stays.get(i)))));
                return false;
            }
        }
        // The next steps start from the first stay; the others stay saved.
        state.activate(0);
        return true;
    }

    /** Next opens "Review your stays" first: each stay's dates, rooms with their price per night and nights, subtotals, and the total. */
    @Override
    public boolean interceptNext(Runnable proceed) {
        BookingWizardState state = getState();
        List<ReviewSheet.Section> sections = WizardReview.stays(requireContext(), state);
        List<ReviewSheet.Row> totals = new java.util.ArrayList<>();
        totals.add(ReviewSheet.Row.bold(getString(R.string.stays_total_label), MoneyFormat.format(state.roomsTotal())));
        ReviewSheet.show(getWizardActivity(), getString(R.string.review_stays_title), null, sections, totals,
                getString(R.string.review_edit_stays), getString(R.string.review_continue), null, proceed);
        return true; // Next is handled either way (a second tap while the sheet is up does nothing)
    }

    @Override
    public String nextConfirmTitle() {
        return getString(R.string.proceed_to_amenities);
    }

    @Override
    public String nextConfirmSummary() {
        BookingWizardState state = getState();
        int rooms = 0;
        for (BookingWizardState.Stay s : state.allStays()) rooms += s.rooms.size();
        return getString(R.string.proceed_stays_summary_format, getResources().getQuantityString(R.plurals.stays_count, state.stayCount(), state.stayCount()),
                getResources().getQuantityString(R.plurals.wizard_summary_rooms, rooms, rooms), MoneyFormat.format(state.roomsTotal()));
    }

    @Override
    public int validationMessageRes() {
        return 0; // the step shows its own inline message
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
