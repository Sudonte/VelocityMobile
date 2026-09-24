package com.example.velocitysuites.debug;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.velocitysuites.Booking;
import com.example.velocitysuites.BookingDetailsActivity;
import com.example.velocitysuites.Notification;
import com.example.velocitysuites.NotificationDetailsActivity;
import com.example.velocitysuites.PaymentReceiptActivity;
import com.example.velocitysuites.ReceiptDetail;
import com.example.velocitysuites.R;
import com.example.velocitysuites.ThemePreferences;

/**
 * DEBUG-ONLY developer entry point (Phase 6B) for visually previewing the
 * Payment Receipt / Booking Details / Transaction History / Notification
 * Details screens against static fixture data (DebugReceiptFixtures) -
 * never against the real backend. Lives entirely under src/debug: this
 * class, and the whole com.example.velocitysuites.debug package, is not
 * compiled or packaged into a release build at all - there is no
 * BuildConfig.DEBUG check needed here (there's nothing to check against in
 * release; the class simply does not exist). Has no LAUNCHER intent filter
 * (see src/debug/AndroidManifest.xml) - not reachable from the home
 * screen or any normal in-app navigation; a developer launches it via:
 * adb shell am start -n com.example.velocitysuites/.debug.DebugReceiptPreviewActivity
 */
public class DebugReceiptPreviewActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Debug Receipt Preview (never in release builds)");
        title.setTextSize(16);
        title.setPadding(0, 0, 0, dp(16));
        root.addView(title);

        // Reuses the app's real ThemePreferences (Profile Management's own
        // Appearance toggle) - plain local SharedPreferences + AppCompatDelegate,
        // no login/network involved, so it's safe to expose directly here for
        // manual Light/Dark verification without needing to sign in first.
        LinearLayout themeRow = new LinearLayout(this);
        themeRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams themeRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        themeRowParams.bottomMargin = dp(16);
        themeRow.setLayoutParams(themeRowParams);
        Button lightBtn = new Button(this);
        lightBtn.setText("Light Mode");
        lightBtn.setAllCaps(false);
        lightBtn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        lightBtn.setOnClickListener(v -> ThemePreferences.setMode(this, ThemePreferences.MODE_LIGHT));
        Button darkBtn = new Button(this);
        darkBtn.setText("Dark Mode");
        darkBtn.setAllCaps(false);
        LinearLayout.LayoutParams darkParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        darkParams.leftMargin = dp(8);
        darkBtn.setLayoutParams(darkParams);
        darkBtn.setOnClickListener(v -> ThemePreferences.setMode(this, ThemePreferences.MODE_DARK));
        themeRow.addView(lightBtn);
        themeRow.addView(darkBtn);
        root.addView(themeRow);

        addButton(root, "Partial Receipt (PR)", v ->
                startActivity(receiptIntent(DebugReceiptFixtures.partialReceipt())));
        addButton(root, "Full Payment Receipt (FR)", v ->
                startActivity(receiptIntent(DebugReceiptFixtures.fullPaymentReceipt())));
        addButton(root, "Official Receipt (OR)", v ->
                startActivity(receiptIntent(DebugReceiptFixtures.officialReceipt())));
        addButton(root, "Booking Details - PR + OR", v ->
                startActivity(BookingDetailsActivity.newIntent(this, DebugReceiptFixtures.bookingWithPartialAndOfficial())));
        addButton(root, "Booking Details - FR + OR", v ->
                startActivity(BookingDetailsActivity.newIntent(this, DebugReceiptFixtures.bookingWithFullPaymentAndOfficial())));
        addButton(root, "Converted Reservation (RES-000100)", v ->
                startActivity(BookingDetailsActivity.newIntent(this, DebugReceiptFixtures.convertedReservationOriginal())));
        addButton(root, "Converted Booking (BOOK-000250)", v ->
                startActivity(BookingDetailsActivity.newIntent(this, DebugReceiptFixtures.convertedReservationBooking())));
        addButton(root, "Notification - PR", v ->
                startActivity(NotificationDetailsActivity.newIntent(this, DebugReceiptFixtures.partialReceiptNotification())));
        addButton(root, "Notification - FR", v ->
                startActivity(NotificationDetailsActivity.newIntent(this, DebugReceiptFixtures.fullPaymentReceiptNotification())));
        addButton(root, "Notification - OR", v ->
                startActivity(NotificationDetailsActivity.newIntent(this, DebugReceiptFixtures.officialReceiptNotification())));
        addButton(root, "Notification - Legacy (no receipt_number)", v ->
                startActivity(NotificationDetailsActivity.newIntent(this, DebugReceiptFixtures.legacyNotification())));

        androidx.core.widget.NestedScrollView scroll = new androidx.core.widget.NestedScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    /** Opens PaymentReceiptActivity's real receipt-number-mode rendering, fed by the debug override hook instead of a network call - see PaymentReceiptActivity#debugPreviewOverride's own doc. */
    private Intent receiptIntent(ReceiptDetail fixture) {
        PaymentReceiptActivity.debugPreviewOverride = fixture;
        return PaymentReceiptActivity.newIntentForReceipt(this, fixture.getReceiptNumber());
    }

    private interface OnClick {
        void onClick(android.view.View v);
    }

    private void addButton(LinearLayout root, String label, OnClick onClick) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        b.setLayoutParams(params);
        b.setOnClickListener(onClick::onClick);
        root.addView(b);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
