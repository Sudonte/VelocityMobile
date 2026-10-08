package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.text.Layout;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.R;
import com.google.android.material.button.MaterialButton;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * "View Receipt" / "Download Receipt" must show their FULL text on every screen size: no cut-off letters, no "...",
 * no hidden words - at 360dp (and a narrow 320dp) with the phone's font size at Default, Large, Largest and a
 * 2.0x accessibility scale. Real inflation with the Flat theme and a real measure/layout pass.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ReceiptButtonsLayoutTest {

    private static final float[] FONT_SCALES = {1.0f, 1.15f, 1.3f, 2.0f};
    private static final int[] WIDTHS_DP = {360, 320};

    private static void assertFullyVisible(Context context, MaterialButton button, String expectedText, String where) {
        assertEquals(where, expectedText, button.getText().toString());
        assertEquals(where + " no maxLines clip", Integer.MAX_VALUE, button.getMaxLines());
        assertTrue(where + " button was laid out", button.getWidth() > 0 && button.getHeight() > 0);
        Layout layout = button.getLayout();
        assertNotNull(where + " text layout", layout);
        for (int line = 0; line < layout.getLineCount(); line++) {
            assertEquals(where + " line " + line + " ellipsized", 0, layout.getEllipsisCount(line));
        }
        // every character is on some line: the lines together cover the whole text
        assertEquals(where + " all characters laid out", expectedText.length(), layout.getLineEnd(layout.getLineCount() - 1));
        // and the text really fits inside the button (nothing drawn past its padding)
        int available = button.getWidth() - button.getPaddingLeft() - button.getPaddingRight();
        for (int line = 0; line < layout.getLineCount(); line++) {
            assertTrue(where + " line " + line + " wider than the button", layout.getLineWidth(line) <= available + 1);
        }
        assertTrue(where + " touch target >= 48dp", button.getHeight() >= LayoutHarness.dp(context, 48) - 1);
    }

    @Test
    public void bookingDetailsReceiptCard_showsFullLabels_atEveryFontScaleAndWidth() {
        for (float scale : FONT_SCALES) {
            for (int widthDp : WIDTHS_DP) {
                Context context = LayoutHarness.themedContext(scale, R.style.Theme_VelocitySuites_Flat);
                View card = LayoutInflater.from(context).inflate(R.layout.item_payment_receipt_action, LayoutHarness.parentOfWidth(context, widthDp), false);
                LayoutHarness.layoutAtWidth(card, context, widthDp);
                String where = "item_payment_receipt_action font " + scale + " width " + widthDp + "dp";
                MaterialButton view = card.findViewById(R.id.btnViewReceipt);
                MaterialButton download = card.findViewById(R.id.btnDownloadReceipt);
                assertFullyVisible(context, view, context.getString(R.string.view_receipt_button_label), where + " View");
                assertFullyVisible(context, download, context.getString(R.string.download_receipt_button_label), where + " Download");
                assertFalse(where + " buttons overlap", LayoutHarness.overlaps(card, view, download));
            }
        }
    }

    @Test
    public void receiptScreenDownloadButton_showsItsFullLabel_atEveryFontScale() {
        for (float scale : FONT_SCALES) {
            Context context = LayoutHarness.themedContext(scale, R.style.Theme_VelocitySuites_Flat);
            View screen = LayoutInflater.from(context).inflate(R.layout.activity_payment_receipt, LayoutHarness.parentOfWidth(context, 360), false);
            LayoutHarness.layoutAtWidth(screen, context, 360);
            MaterialButton download = screen.findViewById(R.id.btnReceiptDownload);
            // the button is hidden until a receipt loads; lay it out visibly to check the label itself
            download.setVisibility(View.VISIBLE);
            LayoutHarness.layoutAtWidth(screen, context, 360);
            assertFullyVisible(context, download, context.getString(R.string.download_receipt_button_label), "activity_payment_receipt font " + scale);
        }
    }

    @Test
    public void cancelBookingAndCancelReservationButtons_showTheFullLabel_atEveryFontScaleAndWidth() {
        for (float scale : FONT_SCALES) {
            for (int widthDp : WIDTHS_DP) {
                for (int labelRes : new int[]{R.string.cancel_booking_button, R.string.cancel_reservation_button}) {
                    Context context = LayoutHarness.themedContext(scale, R.style.Theme_VelocitySuites_Flat);
                    View card = LayoutInflater.from(context).inflate(R.layout.item_booking_card, LayoutHarness.parentOfWidth(context, widthDp), false);
                    MaterialButton cancel = card.findViewById(R.id.btnCancel);
                    cancel.setVisibility(View.VISIBLE);
                    cancel.setText(labelRes);
                    LayoutHarness.layoutAtWidth(card, context, widthDp);
                    assertFullyVisible(context, cancel, context.getString(labelRes),
                            "item_booking_card btnCancel font " + scale + " width " + widthDp + "dp");
                }
            }
        }
    }

    @SuppressWarnings("unused")
    private static String text(View v) {
        return ((TextView) v).getText().toString();
    }
}
