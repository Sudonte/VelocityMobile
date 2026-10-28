package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.Booking;
import com.example.velocitysuites.R;
import com.example.velocitysuites.TransactionHistoryAdapter;
import com.example.velocitysuites.TransactionRow;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.file.Files;
import java.util.Collections;

/** Transaction History without Export, and the shared card chips (an unverified booking reads "Awaiting verification"). */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
public class TransactionHistoryPolishTest {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private static boolean hasField(Class<?> type, String name) {
        try {
            type.getField(name);
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    @Test
    public void export_isGone_fromIdsStringsClassesAndTheManifest() throws Exception {
        assertFalse("no Export button", hasField(R.id.class, "btnExport"));
        for (String name : new String[]{"export_label", "export_options_title", "export_as_pdf", "export_as_csv", "msg_export_success",
                "msg_exporting_format", "msg_export_error", "msg_export_nothing", "msg_export_error_write", "msg_export_error_unexpected"}) {
            assertFalse(name, hasField(R.string.class, name));
        }
        try {
            Class.forName("com.example.velocitysuites.TransactionExporter");
            throw new AssertionError("TransactionExporter is still in the app");
        } catch (ClassNotFoundException expected) {
            // gone
        }
        File manifest = new File("src/main/AndroidManifest.xml");
        if (manifest.exists()) {
            String text = new String(Files.readAllBytes(manifest.toPath()), "UTF-8");
            // the only FileProvider is the one for camera photos (cache/photos/): never the old export folder
            assertTrue(text.contains("FileProvider"));
            String paths = new String(Files.readAllBytes(new File("src/main/res/xml/file_paths.xml").toPath()), "UTF-8");
            assertTrue(paths.contains("<cache-path name=\"photos\" path=\"photos/\""));
            assertFalse("no export folder is shared", paths.contains("external") || paths.contains("files-path"));
            assertFalse(text.contains("WRITE_EXTERNAL_STORAGE"));
        }
    }

    private static Booking booking(String status, boolean verified) {
        Booking b = new Booking("569", "1", "Deluxe", "Deluxe", "Nov 01, 2026", "Nov 02, 2026", 2, 3000, status, "Oct 01, 2026");
        b.setHasBooking(true);
        b.setDirectBooking(true);
        b.setStaffVerified(verified);
        return b;
    }

    private static View card(Context context, Booking b) {
        TransactionHistoryAdapter adapter = new TransactionHistoryAdapter(row -> { });
        adapter.submit(TransactionRow.fromAll(Collections.singletonList(b)));
        FrameLayout parent = LayoutHarness.parentOfWidth(context, 360);
        TransactionHistoryAdapter.ViewHolder holder = adapter.onCreateViewHolder(parent, 0);
        adapter.onBindViewHolder(holder, 0);
        LayoutHarness.layoutAtWidth(holder.itemView, context, 360);
        return holder.itemView;
    }

    @Test
    public void card_unverifiedBooking_readsAwaitingVerification_amber_andVerifiedReadsConfirmed() {
        Context c = LayoutHarness.themedContext();
        TextView chip = card(c, booking("Confirmed", false)).findViewById(R.id.tvTxnBookingStatus);
        assertEquals("Awaiting verification", chip.getText().toString());
        assertEquals(c.getColor(R.color.status_pending_fg), chip.getCurrentTextColor());

        TextView ok = card(c, booking("Confirmed", true)).findViewById(R.id.tvTxnBookingStatus);
        assertEquals("Confirmed", ok.getText().toString());
        assertEquals(c.getColor(R.color.status_cancelled_fg), ok.getCurrentTextColor());
    }

    @Test
    public void card_usesTheSameCardStyleAndTypographyAsBookingAndReservation() {
        Context c = LayoutHarness.themedContext();
        View card = card(c, booking("Pending", false));
        TextView ref = card.findViewById(R.id.tvTxnRef);
        assertEquals("16sp like the Booking & Reservation card", 16f * c.getResources().getDisplayMetrics().scaledDensity, ref.getTextSize(), 1f);
        assertFalse(ref.isAllCaps());
        assertEquals(0f, ref.getLetterSpacing(), 0.0001f);
        TextView chipA = card.findViewById(R.id.tvTxnStatusBadge);
        TextView chipB = card.findViewById(R.id.tvTxnBookingStatus);
        assertEquals("both chips are 12sp", 12f * c.getResources().getDisplayMetrics().scaledDensity, chipB.getTextSize(), 1f);
        assertNotNull(chipA);
        assertEquals("card margin 14dp, the same left edge as the header",
                LayoutHarness.dp(c, 14), ((ViewGroup.MarginLayoutParams) card.getLayoutParams()).leftMargin);
    }

    @Test
    public void notificationsAndHistoryHeaders_shareTheSame14dpLeftEdgeAsTheirCards() {
        Context c = LayoutHarness.themedContext();
        int edge = LayoutHarness.dp(c, 14);
        android.view.LayoutInflater inflater = android.view.LayoutInflater.from(c);

        View notifications = inflater.inflate(R.layout.notification, null);
        for (int id : new int[]{R.id.screenTitle, R.id.tilSearchNotifications, R.id.layoutNotificationStatusFilter}) {
            assertEquals("notifications " + c.getResources().getResourceEntryName(id), edge,
                    ((ViewGroup.MarginLayoutParams) notifications.findViewById(id).getLayoutParams()).leftMargin);
        }
        assertEquals(edge, notifications.findViewById(R.id.layoutNotifSummaryRow).getPaddingLeft());
        assertEquals(edge, ((ViewGroup.MarginLayoutParams) notifications.findViewById(R.id.tilSearchNotifications).getLayoutParams()).rightMargin);

        View history = inflater.inflate(R.layout.transactionhistory, null);
        assertEquals(edge, ((ViewGroup.MarginLayoutParams) history.findViewById(R.id.screenTitle).getLayoutParams()).leftMargin);
        assertEquals(edge, ((ViewGroup.MarginLayoutParams) history.findViewById(R.id.screenDescription).getLayoutParams()).leftMargin);
    }
}
