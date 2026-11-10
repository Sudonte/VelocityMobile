package com.example.velocitysuites;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

/**
 * Intro/confirmation screen shown between "Book Now"/"Reserve Now" (or the
 * NEW BOOKING/NEW RESERVATION buttons on bookingandreservation.xml) and Step 1
 * of the 7-step BookingWizardActivity. One Activity parameterized by
 * EXTRA_MODE (same pattern as BookingWizardActivity itself) picking one of
 * two layouts - startingbooking.xml / startingreservation.xml - rather than
 * two near-duplicate Activity classes.
 */
public class StartingTransactionActivity extends SafeActivity {

    private static final String EXTRA_MODE = "EXTRA_MODE";

    /**
     * The way into a new booking or reservation. Dates come first: without chosen dates waiting (a New booking button, Book Again, the
     * payment screen's Book Now) this opens Room Browsing on its date fields with "Choose your check-in and check-out dates first.",
     * keeping any rooms already picked; with dates it opens the intro screen and then Step 1, prefilled.
     */
    /** The New booking / New reservation buttons: like {@link #newIntent}, but Room Browsing then offers only that action. */
    public static Intent newIntentFromNewButton(Context context, BookingWizardState.Mode mode) {
        Intent intent = newIntent(context, mode);
        if (RoomBrowsingActivity.class.getName().equals(intent.getComponent().getClassName())) {
            intent.putExtra(RoomBrowsingActivity.EXTRA_MODE, mode.name());
        }
        return intent;
    }

    public static Intent newIntent(Context context, BookingWizardState.Mode mode) {
        if (!PendingWizardRooms.hasDates()) {
            Intent browse = new Intent(context, RoomBrowsingActivity.class);
            java.util.List<String> ids = new java.util.ArrayList<>();
            for (Room r : PendingWizardRooms.peek()) ids.add(r.getId());
            if (!ids.isEmpty()) browse.putExtra("ROOM_IDS", String.join(",", ids));
            browse.putExtra(RoomBrowsingActivity.EXTRA_DATES_FIRST, true);
            return browse;
        }
        Intent intent = new Intent(context, StartingTransactionActivity.class);
        intent.putExtra(EXTRA_MODE, mode.name());
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String modeExtra = getIntent().getStringExtra(EXTRA_MODE);
        BookingWizardState.Mode mode = "RESERVATION".equals(modeExtra)
                ? BookingWizardState.Mode.RESERVATION
                : BookingWizardState.Mode.BOOKING;
        setContentView(mode == BookingWizardState.Mode.RESERVATION
                ? R.layout.startingreservation
                : R.layout.startingbooking);

        findViewById(R.id.btnStartingBack).setOnClickListener(v -> finish());
        populateRoomRecap();

        MaterialButton btnStartAction = findViewById(R.id.btnStartAction);
        btnStartAction.setOnClickListener(v -> {
            // Debounce a fast double-tap opening two wizard instances - this
            // screen is left on the back stack (not finished) while the
            // wizard runs, so there's no later lifecycle callback to reset
            // the button on; disabling it permanently here is fine since the
            // guest never returns to this exact screen instance afterward.
            btnStartAction.setEnabled(false);
            startActivity(BookingWizardActivity.newIntent(this, mode));
        });
    }

    /**
     * Peeks (does not consume) any room(s) already selected on landing.xml/
     * roombrowsing.xml, so the wizard's own consume() in onCreate still sees
     * them. The incoming list carries one Room entry per selected unit (the
     * app's "duplicate entries ARE the quantity" convention - see
     * BookingWizardState), so it's grouped by id here for display - showing
     * "Executive Room" three times for a quantity of 3 would misrepresent the
     * selection as three separate, unrelated picks instead of one room type
     * at quantity 3.
     */
    private void populateRoomRecap() {
        View recapCard = findViewById(R.id.layoutRoomRecap);
        List<Room> rooms = PendingWizardRooms.peek();
        if (rooms.isEmpty()) {
            recapCard.setVisibility(View.GONE);
            return;
        }
        recapCard.setVisibility(View.VISIBLE);

        Map<String, Room> representatives = new LinkedHashMap<>();
        Map<String, Integer> quantities = new LinkedHashMap<>();
        for (Room r : rooms) {
            representatives.putIfAbsent(r.getId(), r);
            quantities.merge(r.getId(), 1, Integer::sum);
        }

        StringBuilder names = new StringBuilder();
        for (Room r : representatives.values()) {
            int qty = quantities.get(r.getId());
            if (names.length() > 0) names.append("\n");
            // "Deluxe Room × 2 · ₱1,200.00 per night" (a rate, never a total: no dates are chosen yet)
            names.append("• ").append(getString(R.string.stay_room_line_rate_format, r.getName(), qty, MoneyFormat.format(r.getPricePerNight())));
        }

        TextView tvCount = findViewById(R.id.tvRoomRecapCount);
        tvCount.setText(getResources().getQuantityString(R.plurals.room_recap_count_format, rooms.size(), rooms.size()));
        TextView tvNames = findViewById(R.id.tvRoomRecapNames);
        tvNames.setText(names.toString());
        findViewById(R.id.tvRoomRecapTotal).setVisibility(View.GONE);
    }
}
