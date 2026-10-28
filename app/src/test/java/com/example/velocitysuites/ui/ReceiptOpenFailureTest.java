package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.PaymentReceiptActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.network.dto.ReceiptDetailResponse;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** A receipt that cannot be opened shows "We couldn't open this receipt" with Retry and a hint - never a blank screen. */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ReceiptOpenFailureTest {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private Context app;
    private ScreenTestSupport.FakeApi api;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        api = new ScreenTestSupport.FakeApi();
        ScreenTestSupport.freshRepository(app, api);
        ScreenTestSupport.goOnline(app);
        PaymentReceiptActivity.debugPreviewFixtures.clear();
    }

    private PaymentReceiptActivity open(String number) {
        Intent intent = PaymentReceiptActivity.newIntentForReceipt(app, number);
        PaymentReceiptActivity activity = Robolectric.buildActivity(PaymentReceiptActivity.class, intent).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        return activity;
    }

    private static void assertErrorState(PaymentReceiptActivity a, String number) {
        assertEquals(View.VISIBLE, a.findViewById(R.id.layoutReceiptError).getVisibility());
        assertEquals(View.GONE, a.findViewById(R.id.layoutReceiptLoading).getVisibility());
        assertEquals(View.GONE, a.findViewById(R.id.receiptScrollView).getVisibility());
        assertEquals("We couldn't open this receipt", ((TextView) a.findViewById(R.id.tvReceiptErrorTitle)).getText().toString());
        assertTrue(((TextView) a.findViewById(R.id.tvReceiptErrorHint)).getText().toString().contains(number));
        assertEquals(View.VISIBLE, a.findViewById(R.id.btnReceiptRetry).getVisibility());
    }

    @Test
    public void aServerError_showsTheErrorState_andRetryAsksAgain() {
        PaymentReceiptActivity a = open("SR-20261028-000154");
        ScreenTestSupport.FakeCall<ReceiptDetailResponse> first = api.last("getReceipt");
        first.http(500);
        shadowOf(Looper.getMainLooper()).idle();
        assertErrorState(a, "SR-20261028-000154");
        assertEquals("Something went wrong on our side. Please try again later.", ((TextView) a.findViewById(R.id.tvReceiptErrorMessage)).getText().toString());

        a.findViewById(R.id.btnReceiptRetry).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("Retry asks the server again", 2, api.count("getReceipt"));
    }

    @Test
    public void aReceiptTheAppCannotRead_showsTheSameErrorState() {
        PaymentReceiptActivity a = open("SR-20261028-000154");
        ScreenTestSupport.FakeCall<ReceiptDetailResponse> call = api.last("getReceipt");
        call.succeed(new ReceiptDetailResponse()); // a 200 with no receipt in it
        shadowOf(Looper.getMainLooper()).idle();
        assertErrorState(a, "SR-20261028-000154");
    }

    @Test
    public void offline_showsTheErrorState() {
        PaymentReceiptActivity a = open("OR-20261028-000154");
        ScreenTestSupport.FakeCall<ReceiptDetailResponse> call = api.last("getReceipt");
        call.offline();
        shadowOf(Looper.getMainLooper()).idle();
        assertErrorState(a, "OR-20261028-000154");
    }
}
