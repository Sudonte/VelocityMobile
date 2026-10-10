package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.Booking;
import com.example.velocitysuites.BookingDetailsActivity;
import com.example.velocitysuites.BookingWizardActivity;
import com.example.velocitysuites.BookingWizardState;
import com.example.velocitysuites.R;
import com.example.velocitysuites.Room;
import com.example.velocitysuites.RoomAmenity;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One look across the app: controls (buttons, text fields, chips, bars) stay flat and square - elevation 0, no press
 * animator, radius 0 - while every card is rounded (12dp) with a subtle elevation (2dp). Checked across every wizard
 * step of both flows, Booking Details, the Booking/Reservation lists and Landing, on the real, themed views.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FlatStyleTest {

    private Context app;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        ScreenTestSupport.freshRepository(app, new ScreenTestSupport.FakeApi());
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static void collect(View v, List<String> problems, String where) {
        String name = v.getClass().getSimpleName() + idName(v);
        // Controls stay flat; only cards carry the app-wide subtle elevation (checked below).
        if (!(v instanceof MaterialCardView) && v.getElevation() != 0f) problems.add(where + " " + name + " elevation=" + v.getElevation());
        if (v instanceof MaterialButton) {
            MaterialButton b = (MaterialButton) v;
            if (b.getCornerRadius() != 0) problems.add(where + " " + name + " cornerRadius=" + b.getCornerRadius());
            if (b.getStateListAnimator() != null) problems.add(where + " " + name + " has a press animator");
        }
        if (v instanceof MaterialCardView) {
            MaterialCardView c = (MaterialCardView) v;
            // One card look app-wide: 12dp corners and a subtle 2dp elevation (dimens card_corner_radius / card_elevation).
            float radius = v.getResources().getDimension(R.dimen.card_corner_radius);
            float elevation = v.getResources().getDimension(R.dimen.card_elevation);
            if (c.getRadius() != radius) problems.add(where + " " + name + " radius=" + c.getRadius() + " (expected " + radius + ")");
            if (c.getCardElevation() != elevation) problems.add(where + " " + name + " cardElevation=" + c.getCardElevation() + " (expected " + elevation + ")");
        }
        if (v instanceof TextInputLayout) {
            TextInputLayout t = (TextInputLayout) v;
            if (t.getBoxCornerRadiusTopStart() != 0f || t.getBoxCornerRadiusBottomEnd() != 0f) problems.add(where + " " + name + " rounded box");
        }
        if (v instanceof Chip) {
            Chip chip = (Chip) v;
            if (chip.getShapeAppearanceModel().getTopLeftCornerSize().getCornerSize(new android.graphics.RectF(0, 0, 100, 48)) != 0f) {
                problems.add(where + " " + name + " rounded chip");
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), problems, where);
        }
    }

    private static String idName(View v) {
        if (v.getId() == View.NO_ID) return "";
        try {
            return "#" + v.getResources().getResourceEntryName(v.getId());
        } catch (android.content.res.Resources.NotFoundException generated) {
            return "#(generated)";
        }
    }

    private static Room room(String id) {
        return new Room(id, "Room " + id, "Deluxe", 4, 2500.0, "desc", 0, true, Collections.<RoomAmenity>emptyList(), "Queen", "24 sqm", "No smoking");
    }

    @Test
    public void everyWizardStepOfBothFlowsHasFlatControlsAndRoundedCards() {
        List<String> problems = new ArrayList<>();
        for (BookingWizardState.Mode mode : BookingWizardState.Mode.values()) {
            ActivityController<BookingWizardActivity> controller =
                    Robolectric.buildActivity(BookingWizardActivity.class, BookingWizardActivity.newIntent(app, mode));
            controller.setup();
            idle();
            BookingWizardActivity activity = controller.get();
            activity.getState().selectedRooms.add(room("1"));
            int steps = mode == BookingWizardState.Mode.RESERVATION ? 8 : 7;
            for (int step = 1; step <= steps; step++) {
                activity.goToStep(step);
                idle();
                collect(activity.getWindow().getDecorView(), problems, mode + " step " + step);
            }
        }
        assertTrue(problems.toString(), problems.isEmpty());
    }

    @Test
    public void bookingDetailsScreenHasFlatControlsAndRoundedCards() {
        Booking b = new Booking("5", "1", "Deluxe", "Deluxe", "Oct 09, 2026", "Oct 11, 2026", 2, 4000.0, "Pending", "Oct 08, 2026");
        b.setTotalIncludesAmenities(true);
        ActivityController<BookingDetailsActivity> controller = Robolectric.buildActivity(BookingDetailsActivity.class,
                new Intent(app, BookingDetailsActivity.class).putExtra(BookingDetailsActivity.EXTRA_BOOKING, b));
        controller.setup();
        idle();
        List<String> problems = new ArrayList<>();
        collect(controller.get().getWindow().getDecorView(), problems, "details");
        assertTrue(problems.toString(), problems.isEmpty());
    }

    @Test
    public void bookingAndReservationListsHaveFlatControlsAndRoundedCards() {
        ActivityController<com.example.velocitysuites.BookingAndReservationActivity> controller =
                Robolectric.buildActivity(com.example.velocitysuites.BookingAndReservationActivity.class,
                        new Intent(app, com.example.velocitysuites.BookingAndReservationActivity.class));
        controller.setup();
        idle();
        List<String> problems = new ArrayList<>();
        View content = controller.get().findViewById(android.R.id.content);
        collect(content, problems, "lists");
        // the navigation drawer/bottom navigation belong to the shared app chrome, not these screens
        problems.removeIf(p -> p.contains("BottomNavigationView") || p.contains("NavigationView") || p.contains("DrawerLayout"));
        // ...and so do the shared guest header (logo / notification / profile buttons) and the offline banner,
        // which every guest screen includes - restyling them would change screens outside Booking/Reservation.
        for (String shared : new String[]{"headerLogoContainer", "headerNotificationContainer", "headerNotificationBadge",
                "headerProfileContainer", "logoContainer", "offlineBanner"}) {
            problems.removeIf(p -> p.contains("#" + shared));
        }
        assertTrue(problems.toString(), problems.isEmpty());
    }

    @Test
    public void landingPageHasFlatControlsAndRoundedCards() {
        ScreenTestSupport.FakeApi api = new ScreenTestSupport.FakeApi();
        ScreenTestSupport.freshRepository(app, api);
        ActivityController<com.example.velocitysuites.LandingActivity> controller =
                Robolectric.buildActivity(com.example.velocitysuites.LandingActivity.class);
        controller.setup();
        idle();
        // Give every section real content so room cards (with amenity chips), offers, announcements and amenity
        // tiles are all inflated and checked, not just the empty shell.
        com.example.velocitysuites.network.dto.RoomTypeDto deluxe = LandingFixtures.roomType(1, "Deluxe", "1800.00", 3);
        com.example.velocitysuites.network.dto.RoomAmenityDto wifi = new com.example.velocitysuites.network.dto.RoomAmenityDto();
        wifi.name = "Wi-Fi";
        deluxe.amenities.add(wifi);
        api.<com.example.velocitysuites.network.dto.RoomsResponse>call("getRoomsFresh", 0).succeed(LandingFixtures.rooms(deluxe));
        api.<List<com.example.velocitysuites.network.dto.AnnouncementDto>>call("getAnnouncementsFresh", 0)
                .succeed(LandingFixtures.announcements(LandingFixtures.announcement(1, "Pool closed")));
        api.<List<com.example.velocitysuites.network.dto.AmenityDto>>call("getAmenitiesFresh", 0)
                .succeed(LandingFixtures.amenities(LandingFixtures.amenity(1, "Wi-Fi", "Connectivity")));
        api.<List<com.example.velocitysuites.network.dto.PromotionDto>>call("getPromotionsFresh", 0)
                .succeed(LandingFixtures.promotions(LandingFixtures.promotion(1, "Weekend Escape")));
        api.<List<com.example.velocitysuites.network.dto.DiscountDto>>call("getDiscountsFresh", 0)
                .succeed(LandingFixtures.discounts(LandingFixtures.discount(1, "Senior Citizen")));
        idle();

        List<String> problems = new ArrayList<>();
        collect(controller.get().getWindow().getDecorView(), problems, "landing");
        // The Google map is a platform view that draws its own chrome; it is not ours to restyle.
        problems.removeIf(p -> p.contains("MapView"));
        assertTrue(problems.toString(), problems.isEmpty());
    }

    @Test
    public void dialogsAndBottomSheetsAreShadowless() {
        ActivityController<BookingWizardActivity> controller =
                Robolectric.buildActivity(BookingWizardActivity.class, BookingWizardActivity.newIntent(app, BookingWizardState.Mode.BOOKING));
        controller.setup();
        idle();
        BookingWizardActivity activity = controller.get();

        new MaterialAlertDialogBuilder(activity).setTitle("Cancel Booking").setMessage("Are you sure?")
                .setPositiveButton(R.string.yes_cancel_booking, null).setNegativeButton(R.string.no_label, null).show();
        idle();
        Dialog alert = ShadowDialog.getLatestDialog();
        assertEquals("dialog window has no shadow", 0f, alert.getWindow().getDecorView().getElevation(), 0f);
        List<String> problems = new ArrayList<>();
        collect(alert.getWindow().getDecorView(), problems, "alert dialog");
        assertTrue(problems.toString(), problems.isEmpty());

        BottomSheetDialog sheet = new BottomSheetDialog(activity);
        sheet.setContentView(R.layout.dialog_discount_details);
        sheet.show();
        idle();
        problems.clear();
        collect(sheet.getWindow().getDecorView(), problems, "bottom sheet");
        assertTrue(problems.toString(), problems.isEmpty());
        assertNull(sheet.findViewById(R.id.btnDetailSelect).getStateListAnimator());
    }
}
