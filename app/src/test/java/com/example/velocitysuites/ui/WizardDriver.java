package com.example.velocitysuites.ui;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.velocitysuites.BookingWizardActivity;
import com.example.velocitysuites.BookingWizardState;
import com.example.velocitysuites.CheckInWindow;
import com.example.velocitysuites.R;
import com.example.velocitysuites.Room;
import com.example.velocitysuites.RoomAmenity;

import org.robolectric.Robolectric;
import org.robolectric.shadows.ShadowDialog;

import java.util.Calendar;
import java.util.Collections;

/** Test helper: drives the wizard the way a guest does - confirming stays, answering "Proceed to ...?", reading the sheets. */
final class WizardDriver {

    private WizardDriver() {
    }

    static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    /** Lets a second pass before the next tap: the screens swallow a repeat tap inside 700 ms, and the test clock only moves when told. */
    static void advance() {
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));
    }

    static BookingWizardActivity open(Context app, BookingWizardState.Mode mode) {
        BookingWizardActivity a = Robolectric.buildActivity(BookingWizardActivity.class, BookingWizardActivity.newIntent(app, mode)).setup().get();
        idle();
        return a;
    }

    static Room room(String id, double price) {
        return room(id, price, 2);
    }

    static Room room(String id, double price, int capacity) {
        return new Room(id, "Room " + id, "Deluxe", capacity, price, "desc", 0, true, Collections.<RoomAmenity>emptyList(), "Queen", "24 sqm", "No smoking");
    }

    static Calendar day(int days) {
        return CheckInWindow.toDeviceMidnight(CheckInWindow.today().plusDays(days));
    }

    /** Puts dates and {@code qty} rooms of {@code room} into the stay being edited and shows Step 1 for it (nothing is confirmed). */
    static void fill(BookingWizardActivity a, int checkInDays, int nights, Room room, int qty) {
        BookingWizardState state = a.getState();
        state.checkIn = day(checkInDays);
        state.checkOut = day(checkInDays + nights);
        state.selectedRooms.clear();
        for (int i = 0; i < qty; i++) state.selectedRooms.add(room);
        a.goToStep(1);
        idle();
    }

    static Fragment step(BookingWizardActivity a) {
        return a.getSupportFragmentManager().findFragmentById(R.id.wizardFragmentContainer);
    }

    @SuppressWarnings("unchecked")
    static <T extends View> T find(BookingWizardActivity a, int id) {
        View root = step(a).getView();
        assertNotNull("step view", root);
        T v = root.findViewById(id);
        assertNotNull("view " + a.getResources().getResourceName(id), v);
        return v;
    }

    static String text(View v) {
        return ((TextView) v).getText().toString();
    }

    static String collect(View v) {
        StringBuilder sb = new StringBuilder();
        if (v instanceof TextView && v.getVisibility() == View.VISIBLE) sb.append(((TextView) v).getText()).append('\n');
        if (v instanceof ViewGroup && v.getVisibility() == View.VISIBLE) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) sb.append(collect(((ViewGroup) v).getChildAt(i)));
        }
        return sb.toString();
    }

    /** The visible text with no-break spaces turned into plain ones, so assertions read naturally. */
    static String plain(String s) {
        return s.replace((char) 160, ' ');
    }

    // ---- dialogs ----

    static Dialog latestDialog() {
        return ShadowDialog.getLatestDialog();
    }

    static String dialogText(Dialog d) {
        return plain(collect(d.getWindow().getDecorView()));
    }

    /** Taps Yes (or Cancel) on the question now showing. */
    static void answer(boolean yes) {
        AlertDialog d = (AlertDialog) latestDialog();
        assertNotNull("a question is showing", d);
        assertTrue("it is showing", d.isShowing());
        d.getButton(yes ? DialogInterface.BUTTON_POSITIVE : DialogInterface.BUTTON_NEGATIVE).performClick();
        advance();
    }

    /** Taps "Confirm this stay" and answers Yes. */
    static void confirmStay(BookingWizardActivity a) {
        find(a, R.id.btnConfirmStay).performClick();
        idle();
        answer(true);
    }

    /** The stay being edited, filled and confirmed. */
    static void fillAndConfirm(BookingWizardActivity a, int checkInDays, int nights, Room room, int qty) {
        fill(a, checkInDays, nights, room, qty);
        confirmStay(a);
    }

    /** "Add another stay", then a filled and confirmed stay. */
    static void addConfirmedStay(BookingWizardActivity a, int checkInDays, int nights, Room room, int qty) {
        advance();
        find(a, R.id.btnAddAnotherStay).performClick();
        advance();
        fillAndConfirm(a, checkInDays, nights, room, qty);
    }

    /** A booking wizard on Step 1 with one confirmed stay. */
    static BookingWizardActivity onConfirmedStay(Context app, int checkInDays, int nights, Room room, int qty) {
        BookingWizardActivity a = open(app, BookingWizardState.Mode.BOOKING);
        fillAndConfirm(a, checkInDays, nights, room, qty);
        return a;
    }

    /** Taps Next and, if the step shows a review sheet, Continue; then answers "Proceed to ...?" with Yes. */
    static void nextThroughEverything(BookingWizardActivity a) {
        a.findViewById(R.id.btnWizardNext).performClick();
        idle();
        Dialog d = latestDialog();
        if (d != null && d.isShowing() && d.findViewById(R.id.btnReviewContinue) != null) {
            d.findViewById(R.id.btnReviewContinue).performClick();
            idle();
        }
        d = latestDialog();
        if (d instanceof AlertDialog && d.isShowing()) answer(true);
    }
}
