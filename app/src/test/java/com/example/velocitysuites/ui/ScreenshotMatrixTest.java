package com.example.velocitysuites.ui;

import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.Booking;
import com.example.velocitysuites.BookingDetailsActivity;
import com.example.velocitysuites.DashboardActivity;
import com.example.velocitysuites.LandingActivity;
import com.example.velocitysuites.PaymentReceiptActivity;
import com.example.velocitysuites.ReceiptDetail;
import com.example.velocitysuites.ThemePreferences;
import com.example.velocitysuites.network.ApiMapper;
import com.example.velocitysuites.network.SessionManager;
import com.example.velocitysuites.network.dto.BookingDto;
import com.example.velocitysuites.network.dto.ReceiptDetailResponse;
import com.example.velocitysuites.network.dto.ReceiptSummaryDto;
import com.example.velocitysuites.network.dto.ReservationDto;
import com.google.gson.Gson;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Screenshots without a device: the Landing page, the Dashboard (with data and empty), the Guest Header and the Booking
 * Details / Payment Receipt screens, each in light and dark on a small (360x640dp) and a large (480x1000dp) phone, drawn
 * on the JVM with Robolectric's native graphics. The PNGs land in app/build/screenshots/matrix/ and every render also
 * asserts the picture is not blank, so a screen that fails to lay out fails the test. Set SKIP_SHOTS=1 to skip.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h640dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ScreenshotMatrixTest {

    private static final String[][] SIZES = {{"small", "360", "640"}, {"large", "480", "1000"}};
    private static final String[] MODES = {"light", "dark"};

    private Context app;
    private ScreenTestSupport.FakeApi api;

    @Before
    public void setUp() {
        org.junit.Assume.assumeTrue(System.getenv("SKIP_SHOTS") == null);
        app = ApplicationProvider.getApplicationContext();
        api = new ScreenTestSupport.FakeApi();
        ScreenTestSupport.freshRepository(app, api);
        ScreenTestSupport.goOnline(app);
        SessionManager.saveSession(app, "test-token", 1L, "Ana", "Reyes", "", "Ana Reyes", "ana@example.com",
                "09171234567", "Female", "Jan 01, 1990", "");
        PaymentReceiptActivity.debugPreviewFixtures.clear();
        PaymentReceiptActivity.debugPreviewAmenities.clear();
    }

    /**
     * ThemePreferences.setMode() makes AppCompat recreate EVERY activity it still tracks (a static list), including ones other
     * test classes left open, which throws a barrier-token error mid-test. Forget the old ones before each configuration.
     */
    @SuppressWarnings("unchecked")
    private static void forgetOtherTestsActivities() {
        try {
            java.lang.reflect.Field f = androidx.appcompat.app.AppCompatDelegate.class.getDeclaredField("sActivityDelegates");
            f.setAccessible(true);
            ((java.util.Collection<Object>) f.get(null)).clear();
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new AssertionError("AppCompatDelegate no longer keeps its delegates in sActivityDelegates - update this helper", e);
        }
    }

    private void configure(String[] size, String mode) {
        forgetOtherTestsActivities();
        boolean dark = "dark".equals(mode);
        RuntimeEnvironment.setQualifiers("w" + size[1] + "dp-h" + size[2] + "dp" + (dark ? "-night" : "") + "-xxhdpi");
        ThemePreferences.setMode(app, dark ? ThemePreferences.MODE_DARK : ThemePreferences.MODE_LIGHT);
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    /**
     * Lays the window out at the device width and {@code heightPx}, draws it and writes build/screenshots/matrix/NAME.png.
     * {@code cropToContent} trims the empty tail of a tall render; {@code cropTopPx > 0} keeps only that many top rows.
     */
    private static File render(Activity activity, String name, int heightPx, boolean cropToContent, int cropTopPx) throws Exception {
        // let the entrance animations (fade / slide in) finish before drawing, as they would on a real screen
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(3));
        View decor = activity.getWindow().getDecorView();
        int w = activity.getResources().getDisplayMetrics().widthPixels;
        for (int pass = 0; pass < 2; pass++) { // the landing hero sizes itself from the first pass's scroller height
            decor.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY));
            decor.layout(0, 0, w, heightPx);
        }
        finishEntranceAnimations(decor);
        Bitmap bitmap = Bitmap.createBitmap(w, heightPx, Bitmap.Config.ARGB_8888);
        decor.draw(new Canvas(bitmap));
        int last = heightPx - 1;
        if (cropToContent) {
            int bg = bitmap.getPixel(2, heightPx - 1);
            while (last > 400) {
                boolean blank = true;
                for (int x = 0; x < w; x += 7) if (bitmap.getPixel(x, last) != bg) { blank = false; break; }
                if (!blank) break;
                last--;
            }
            last = Math.min(heightPx - 1, last + 40);
        }
        if (cropTopPx > 0) last = Math.min(last, cropTopPx - 1);
        Bitmap out = Bitmap.createBitmap(bitmap, 0, 0, w, last + 1);
        assertNotBlank(name, out);
        File dir = new File("build/screenshots/matrix");
        dir.mkdirs();
        File file = new File(dir, name + ".png");
        try (FileOutputStream stream = new FileOutputStream(file)) {
            out.compress(Bitmap.CompressFormat.PNG, 100, stream);
        }
        return file;
    }

    /** Entrance animations (Animation objects started at launch) only run on a real frame; drop them so the picture shows the settled screen. */
    private static void finishEntranceAnimations(View view) {
        view.clearAnimation(); // a fade-in started at launch never gets its first frame in a manual draw, so it would stay at alpha 0
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) finishEntranceAnimations(group.getChildAt(i));
        }
    }

    private static void assertNotBlank(String name, Bitmap bitmap) {
        java.util.Set<Integer> colours = new java.util.HashSet<>();
        for (int y = 0; y < bitmap.getHeight(); y += 9) {
            for (int x = 0; x < bitmap.getWidth(); x += 9) colours.add(bitmap.getPixel(x, y));
        }
        assertTrue(name + " rendered blank (" + colours.size() + " colours)", colours.size() > 12);
    }

    private static int px(Activity a, int dp) {
        return Math.round(dp * a.getResources().getDisplayMetrics().density);
    }

    private static String day(int plus) {
        return LocalDate.now(ZoneId.of("Asia/Manila")).plusDays(plus).toString();
    }

    // ---- Landing: the first page, exactly the window ----

    @Test
    public void landing() throws Exception {
        for (String[] size : SIZES) {
            for (String mode : MODES) {
                configure(size, mode);
                ActivityController<LandingActivity> c = Robolectric.buildActivity(LandingActivity.class,
                        new Intent(app, LandingActivity.class)).setup();
                idle();
                Activity a = c.get();
                render(a, "landing_" + size[0] + "_" + mode, px(a, Integer.parseInt(size[2])), false, 0);
                c.pause().stop().destroy();
            }
        }
    }

    // ---- Dashboard ----

    private void answerDashboard(boolean withData) {
        if (!withData) {
            ScreenTestSupport.answerBookingLoad(api, api.count("getReservations") - 1, ScreenTestSupport.reservations(), ScreenTestSupport.directBookings());
            api.<Object>last("getNotifications").succeed(ScreenTestSupport.notificationPage(0));
            idle();
            return;
        }
        ReservationDto next = ScreenTestSupport.reservation(588, 3600, ScreenTestSupport.payment(1, "1800.00", "completed", "gcash"));
        next.check_in = day(2);
        next.check_out = day(4);
        ReservationDto later = ScreenTestSupport.reservation(590, 5400);
        later.check_in = day(20);
        later.check_out = day(23);
        ScreenTestSupport.answerBookingLoad(api, api.count("getReservations") - 1,
                ScreenTestSupport.reservations(next, later),
                ScreenTestSupport.directBookings(ScreenTestSupport.directBooking(338, 6000)));
        api.<Object>last("getNotifications").succeed(ScreenTestSupport.notificationPage(2,
                ScreenTestSupport.notification(1, "Payment verified", "payment", 588L, false),
                ScreenTestSupport.notification(2, "Reservation confirmed", "reservation", 588L, false),
                ScreenTestSupport.notification(3, "Welcome to Velocity Suites", "announcement", null, true)));
        idle();
    }

    @Test
    public void dashboard() throws Exception {
        for (String[] size : SIZES) {
            for (String mode : MODES) {
                for (boolean withData : new boolean[]{true, false}) {
                    configure(size, mode);
                    ActivityController<DashboardActivity> c = Robolectric.buildActivity(DashboardActivity.class).setup();
                    idle();
                    Activity a = c.get();
                    answerDashboard(withData);
                    String tag = size[0] + "_" + mode;
                    render(a, "dashboard_" + (withData ? "data" : "empty") + "_" + tag, px(a, 3200), true, 0);
                    if (withData) {
                        // the shared guest header is the top strip of every guest screen
                        render(a, "guest_header_" + tag, px(a, 64), false, 0);
                    }
                    c.pause().stop().destroy();
                    api = new ScreenTestSupport.FakeApi();
                    ScreenTestSupport.freshRepository(app, api);
                    ScreenTestSupport.goOnline(app);
                }
            }
        }
    }

    // ---- Booking Details (with its three receipts) and the three receipt screens ----

    private Booking bookingWithThreeReceipts() {
        Gson gson = new Gson();
        ReceiptDetailResponse last = gson.fromJson(ReceiptFromApiPayloadTest.RECEIPT_3, ReceiptDetailResponse.class);
        ReservationDto dto = ScreenTestSupport.reservation(588, 5000);
        dto.status = "CONVERTED_TO_BOOKING";
        dto.check_in = "2026-09-25";
        dto.check_out = "2026-09-27";
        dto.booking = new BookingDto();
        dto.booking.booking_status = "COMPLETED_BOOKING";
        dto.payment_summary = last.receipt.payment_summary;
        dto.payment_transactions = last.receipt.payment_transactions;
        dto.receipts = new ArrayList<>(Arrays.asList(
                summary("PR-20260920-000501", "PARTIAL_RECEIPT", "VERIFIED", 1000.0, 20, "2026-09-20T06:15:00.000000Z"),
                summary("SR-20260925-000077", "CHECKIN_SETTLEMENT_RECEIPT", "VERIFIED", 4000.0, 100, "2026-09-25T04:30:00.000000Z"),
                summary("OR-20260927-000310", "OFFICIAL_RECEIPT", "PAID", 5000.0, 100, "2026-09-27T03:00:00.000000Z")));
        return ApiMapper.toBooking(dto);
    }

    private static ReceiptSummaryDto summary(String number, String type, String status, double amount, int percentage, String issuedAt) {
        ReceiptSummaryDto s = new ReceiptSummaryDto();
        s.receipt_number = number;
        s.receipt_type = type;
        s.status = status;
        s.amount = amount;
        s.payment_percentage = percentage;
        s.issued_at = issuedAt;
        return s;
    }

    @Test
    public void bookingDetailsAndReceipts() throws Exception {
        List<String> payloads = Arrays.asList(ReceiptFromApiPayloadTest.RECEIPT_1, ReceiptFromApiPayloadTest.RECEIPT_2, ReceiptFromApiPayloadTest.RECEIPT_3);
        for (String[] size : SIZES) {
            for (String mode : MODES) {
                String tag = size[0] + "_" + mode;
                configure(size, mode);

                ActivityController<BookingDetailsActivity> details = Robolectric.buildActivity(BookingDetailsActivity.class,
                        BookingDetailsActivity.newIntent(app, bookingWithThreeReceipts())).setup();
                idle();
                render(details.get(), "booking_details_" + tag, px(details.get(), 3200), true, 0);
                details.pause().stop().destroy();

                for (int i = 0; i < payloads.size(); i++) {
                    configure(size, mode);
                    ReceiptDetail receipt = ApiMapper.toReceiptDetail(new Gson().fromJson(payloads.get(i), ReceiptDetailResponse.class));
                    PaymentReceiptActivity.debugPreviewFixtures.put(receipt.getReceiptNumber(), receipt);
                    ActivityController<PaymentReceiptActivity> c = Robolectric.buildActivity(PaymentReceiptActivity.class,
                            PaymentReceiptActivity.newIntentForReceipt(app, receipt.getReceiptNumber())).setup();
                    idle();
                    render(c.get(), "receipt_" + (i + 1) + "_" + tag, px(c.get(), 3200), true, 0);
                    c.pause().stop().destroy();
                }
            }
        }
    }
}
