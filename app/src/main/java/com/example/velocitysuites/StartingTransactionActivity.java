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
public class StartingTransactionActivity extends AppCompatActivity {

    private static final String EXTRA_MODE = "EXTRA_MODE";

    public static Intent newIntent(Context context, BookingWizardState.Mode mode) {
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
        double total = 0;
        for (Room r : representatives.values()) {
            int qty = quantities.get(r.getId());
            if (names.length() > 0) names.append("\n");
            names.append("• ").append(r.getName());
            if (qty > 1) names.append(" × ").append(qty);
            total += r.getPricePerNight() * qty;
        }

        TextView tvCount = findViewById(R.id.tvRoomRecapCount);
        tvCount.setText(getString(R.string.room_recap_count_format, rooms.size()));
        TextView tvNames = findViewById(R.id.tvRoomRecapNames);
        tvNames.setText(names.toString());
        TextView tvTotal = findViewById(R.id.tvRoomRecapTotal);
        tvTotal.setText(String.format(Locale.US, "₱%,.0f / night", total));
    }
}
