package com.example.velocitysuites.ui;

import static com.example.velocitysuites.ui.WizardDriver.idle;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.AdditionalAmenitiesActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.Room;
import com.example.velocitysuites.RoomBrowsingActivity;
import com.example.velocitysuites.network.dto.AdditionalAmenityInfoDto;
import com.example.velocitysuites.network.dto.AdditionalAmenitySubmitRequest;
import com.example.velocitysuites.network.dto.AmenityDto;
import com.google.android.material.textfield.TextInputLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

/**
 * 1.73.0, the simplified booking and reservation flow (the same steps, words and rules as the guest web). Part 1: Book now and Reserve (on a
 * room card, in the details sheet, and Book Selected / Reserve Selected) need check-in and check-out dates the guest chose; without them the
 * date fields come into view with one inline message and nothing opens. Part 5: Request Additional Amenities lists "Included with your room"
 * (price 0, never on the bill) and "Additional amenities" (paid), both requested through the front desk.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Release173Test {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private RoomBrowsingActivity browsing() {
        Context app = ApplicationProvider.getApplicationContext();
        ScreenTestSupport.freshRepository(app, new ScreenTestSupport.FakeApi());
        RoomBrowsingActivity a = Robolectric.buildActivity(RoomBrowsingActivity.class, new Intent(app, RoomBrowsingActivity.class)).setup().get();
        idle();
        return a;
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Calendar day(int offset) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.DAY_OF_MONTH, offset);
        return c;
    }

    // ---- Part 1

    @Test
    public void bookNowWithoutDates_asksForThemInline_andOpensNothing() {
        RoomBrowsingActivity a = browsing();
        Room room = WizardDriver.room("1", 1000);

        a.onBookNowClick(room);
        idle();

        TextInputLayout checkIn = a.findViewById(R.id.checkInLayout);
        assertEquals("Choose your check-in and check-out dates first.", checkIn.getError().toString());
        assertEquals(a.getString(R.string.error_dates_first), checkIn.getError().toString());
        assertNull("no booking screen opened", shadowOf(a).getNextStartedActivity());
        assertNull("no details sheet opened", org.robolectric.shadows.ShadowDialog.getLatestDialog());
    }

    @Test
    public void reserveWithoutDates_isBlockedTheSameWay() {
        RoomBrowsingActivity a = browsing();

        a.onReserveNowClick(WizardDriver.room("1", 1000));
        idle();

        assertEquals("Choose your check-in and check-out dates first.", ((TextInputLayout) a.findViewById(R.id.checkInLayout)).getError().toString());
        assertNull(shadowOf(a).getNextStartedActivity());
        assertNull(org.robolectric.shadows.ShadowDialog.getLatestDialog());
    }

    @Test
    public void withOnlyACheckInDate_theMessageSitsUnderCheckOut() throws Exception {
        RoomBrowsingActivity a = browsing();
        set(a, "checkInDate", day(5));

        assertTrue("a missing check-out is not a choice", !a.requireDatesFirst());
        idle();

        assertNull(((TextInputLayout) a.findViewById(R.id.checkInLayout)).getError());
        assertEquals("Choose your check-in and check-out dates first.", ((TextInputLayout) a.findViewById(R.id.checkOutLayout)).getError().toString());
    }

    @Test
    public void bookSelectedAndReserveSelected_areBlockedWithoutDates() throws Exception {
        RoomBrowsingActivity a = browsing();
        Field cart = RoomBrowsingActivity.class.getDeclaredField("selectedQuantities");
        cart.setAccessible(true);
        @SuppressWarnings("unchecked") Map<String, Integer> quantities = (Map<String, Integer>) cart.get(a);
        quantities.put("1", 2);

        a.findViewById(R.id.btnCartBook).performClick();
        idle();
        assertNull("Book Selected opened nothing", shadowOf(a).getNextStartedActivity());
        assertEquals("Choose your check-in and check-out dates first.", ((TextInputLayout) a.findViewById(R.id.checkInLayout)).getError().toString());

        ((TextInputLayout) a.findViewById(R.id.checkInLayout)).setError(null);
        a.findViewById(R.id.btnCartReserve).performClick();
        idle();
        assertNull("Reserve Selected opened nothing", shadowOf(a).getNextStartedActivity());
        assertNotNull(((TextInputLayout) a.findViewById(R.id.checkInLayout)).getError());
    }

    @Test
    public void withBothDates_thereIsNoMessage() throws Exception {
        RoomBrowsingActivity a = browsing();
        set(a, "checkInDate", day(5));
        set(a, "checkOutDate", day(7));

        assertTrue(a.requireDatesFirst());
        assertNull(((TextInputLayout) a.findViewById(R.id.checkInLayout)).getError());
        assertNull(((TextInputLayout) a.findViewById(R.id.checkOutLayout)).getError());
    }

    // ---- Part 5: included and paid amenities ----

    private ScreenTestSupport.FakeApi amenityApi;

    private static AmenityDto amenity(long id, String name, String price) {
        AmenityDto dto = new AmenityDto();
        dto.id = id;
        dto.amenity_name = name;
        dto.charge = price;
        dto.quantity = 10;
        return dto;
    }

    private AdditionalAmenitiesActivity amenityScreen(AmenityDto... catalog) {
        Context app = ApplicationProvider.getApplicationContext();
        amenityApi = new ScreenTestSupport.FakeApi();
        ScreenTestSupport.freshRepository(app, amenityApi);
        Intent intent = new Intent(app, AdditionalAmenitiesActivity.class)
                .putExtra(AdditionalAmenitiesActivity.EXTRA_DIRECT_BOOKING, true)
                .putExtra(AdditionalAmenitiesActivity.EXTRA_ID, "42");
        AdditionalAmenitiesActivity a = Robolectric.buildActivity(AdditionalAmenitiesActivity.class, intent).setup().get();
        idle();
        AdditionalAmenityInfoDto info = new AdditionalAmenityInfoDto();
        info.can_request = true;
        info.requests = new ArrayList<>();
        AdditionalAmenityInfoDto.Room room = new AdditionalAmenityInfoDto.Room();
        room.room_id = 11;
        room.room_number = "101";
        room.room_type = "Standard";
        info.rooms = Arrays.asList(room);
        amenityApi.<AdditionalAmenityInfoDto>last("getBookingAdditionalAmenities").succeed(info);
        idle();
        amenityApi.<List<AmenityDto>>last("getAmenities").succeed(Arrays.asList(catalog));
        idle();
        return a;
    }

    private static android.widget.LinearLayout openPicker(AdditionalAmenitiesActivity a) {
        ((android.widget.LinearLayout) a.findViewById(R.id.additionalAmenitiesRooms)).getChildAt(0).performClick();
        idle();
        return org.robolectric.shadows.ShadowDialog.getLatestDialog().findViewById(R.id.pickerList);
    }

    private static String allText(View v) {
        StringBuilder sb = new StringBuilder();
        if (v instanceof android.widget.TextView) sb.append(((android.widget.TextView) v).getText()).append('\n');
        if (v instanceof android.view.ViewGroup) for (int i = 0; i < ((android.view.ViewGroup) v).getChildCount(); i++) sb.append(allText(((android.view.ViewGroup) v).getChildAt(i)));
        return sb.toString();
    }

    @Test
    public void theAmenityRequestListsTwoGroups_includedFirst_withTheIncludedPriceAndThePaidPrice() {
        AdditionalAmenitiesActivity a = amenityScreen(amenity(7, "Extra Bed", "200.00"), amenity(9, "Extra Pillow", "0.00"), amenity(8, "Breakfast", "75.50"), amenity(10, "Slippers", "0.00"));
        android.widget.LinearLayout list = openPicker(a);

        assertEquals("two headings and four rows", 6, list.getChildCount());
        assertTrue(allText(list.getChildAt(0)), allText(list.getChildAt(0)).contains("Included with your room"));
        assertTrue(allText(list.getChildAt(0)), allText(list.getChildAt(0)).contains("Not added to your bill"));
        assertTrue(allText(list.getChildAt(1)).contains("Extra Pillow") && allText(list.getChildAt(1)).contains("Included"));
        assertTrue(allText(list.getChildAt(2)).contains("Slippers") && allText(list.getChildAt(2)).contains("Included"));
        assertTrue(allText(list.getChildAt(3)), allText(list.getChildAt(3)).contains("Additional amenities"));
        assertTrue(allText(list.getChildAt(4)).contains("Extra Bed") && allText(list.getChildAt(4)).contains("₱200.00 each"));
        assertTrue(allText(list.getChildAt(5)).contains("Breakfast") && allText(list.getChildAt(5)).contains("₱75.50 each"));
        assertFalse("an included row shows no price to pay", allText(list.getChildAt(1)).contains("each"));
    }

    @Test
    public void anIncludedAmenityCanBeChosen_andAddsNothingToTheTotal_whileAPaidOneDoes() {
        AdditionalAmenitiesActivity a = amenityScreen(amenity(7, "Extra Bed", "200.00"), amenity(9, "Extra Pillow", "0.00"));
        android.widget.LinearLayout list = openPicker(a);

        View pillow = list.getChildAt(1);
        pillow.findViewById(R.id.btnAmenityOptionPlus).performClick();
        pillow.findViewById(R.id.btnAmenityOptionPlus).performClick();
        assertEquals("2 × Included", ((android.widget.TextView) pillow.findViewById(R.id.tvAmenityOptionSubtotal)).getText().toString());
        String total = ((android.widget.TextView) org.robolectric.shadows.ShadowDialog.getLatestDialog().findViewById(R.id.tvPickerTotal)).getText().toString();
        assertTrue(total, total.contains("2 items") && total.contains("₱0.00"));

        list.getChildAt(3).findViewById(R.id.btnAmenityOptionPlus).performClick();
        total = ((android.widget.TextView) org.robolectric.shadows.ShadowDialog.getLatestDialog().findViewById(R.id.tvPickerTotal)).getText().toString();
        assertTrue("only the paid one is added: " + total, total.contains("3 items") && total.contains("₱200.00"));
    }

    @Test
    public void bothKindsAreSentInOneRequest_forTheFrontDeskToApprove() {
        AdditionalAmenitiesActivity a = amenityScreen(amenity(7, "Extra Bed", "200.00"), amenity(9, "Extra Pillow", "0.00"));
        android.widget.LinearLayout list = openPicker(a);
        list.getChildAt(1).findViewById(R.id.btnAmenityOptionPlus).performClick();
        list.getChildAt(3).findViewById(R.id.btnAmenityOptionPlus).performClick();
        org.robolectric.shadows.ShadowDialog.getLatestDialog().findViewById(R.id.btnPickerDone).performClick();
        idle();

        a.findViewById(R.id.additionalAmenitiesSubmit).performClick();
        idle();
        androidx.appcompat.app.AlertDialog ask = (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        ask.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick();
        idle();

        Object[] args = amenityApi.lastArgs.get("submitBookingAdditionalAmenities");
        AdditionalAmenitySubmitRequest request = (AdditionalAmenitySubmitRequest) args[1];
        assertEquals(1, request.rooms.size());
        assertEquals("the included and the paid amenity", 2, request.rooms.get(0).items.size());
        List<Long> ids = new ArrayList<>();
        for (AdditionalAmenitySubmitRequest.Item item : request.rooms.get(0).items) ids.add(item.amenity_id);
        assertTrue(ids.toString(), ids.contains(7L) && ids.contains(9L));
    }

    @Test
    public void aHotelWithOnlyPaidAmenities_keepsThePlainListWithNoGroupTitles() {
        AdditionalAmenitiesActivity a = amenityScreen(amenity(7, "Extra Bed", "200.00"), amenity(8, "Breakfast", "75.50"));
        android.widget.LinearLayout list = openPicker(a);

        assertEquals(2, list.getChildCount());
        assertFalse(allText(list).contains("Included with your room"));
    }

    @Test
    public void theCatalogIsAskedForWithoutAPriceFilter_soIncludedAmenitiesArrive() {
        amenityScreen(amenity(7, "Extra Bed", "200.00"));
        Object[] args = amenityApi.lastArgs.get("getAmenities");
        assertNotNull(args);
        assertNull("no pricing_type filter", args[0]);
    }

    // ---- every way into a new transaction follows the dates-first rule ----

    @Test
    public void withoutDates_everyNewBookingAndReservationEntryOpensRoomBrowsingOnItsDates_keepingTheRooms() {
        for (com.example.velocitysuites.BookingWizardState.Mode mode : com.example.velocitysuites.BookingWizardState.Mode.values()) {
            com.example.velocitysuites.PendingWizardRooms.set(java.util.Arrays.asList(WizardDriver.room("1", 1000), WizardDriver.room("1", 1000)));
            Intent in = com.example.velocitysuites.StartingTransactionActivity.newIntent(ApplicationProvider.getApplicationContext(), mode);
            assertEquals(RoomBrowsingActivity.class.getName(), in.getComponent().getClassName());
            assertTrue(in.getBooleanExtra(RoomBrowsingActivity.EXTRA_DATES_FIRST, false));
            assertEquals("the rooms already picked stay selected", "1,1", in.getStringExtra("ROOM_IDS"));
        }
        com.example.velocitysuites.PendingWizardRooms.set(java.util.Collections.<Room>emptyList());
        Intent plain = com.example.velocitysuites.StartingTransactionActivity.newIntent(ApplicationProvider.getApplicationContext(), com.example.velocitysuites.BookingWizardState.Mode.BOOKING);
        assertEquals("the New booking button with nothing chosen", RoomBrowsingActivity.class.getName(), plain.getComponent().getClassName());
    }

    @Test
    public void withChosenDates_theWayInOpensTheIntroThenTheWizard() {
        com.example.velocitysuites.PendingWizardRooms.set(java.util.Arrays.asList(WizardDriver.room("1", 1000)));
        com.example.velocitysuites.PendingWizardRooms.setDates(day(5), day(7));
        Intent in = com.example.velocitysuites.StartingTransactionActivity.newIntent(ApplicationProvider.getApplicationContext(), com.example.velocitysuites.BookingWizardState.Mode.RESERVATION);
        assertEquals(com.example.velocitysuites.StartingTransactionActivity.class.getName(), in.getComponent().getClassName());
        com.example.velocitysuites.PendingWizardRooms.consume();
        com.example.velocitysuites.PendingWizardRooms.consumeDates();
    }

    @Test
    public void roomBrowsingOpenedDatesFirst_showsTheMessageUnderTheDatePicker() {
        Context app = ApplicationProvider.getApplicationContext();
        ScreenTestSupport.freshRepository(app, new ScreenTestSupport.FakeApi());
        RoomBrowsingActivity a = Robolectric.buildActivity(RoomBrowsingActivity.class,
                new Intent(app, RoomBrowsingActivity.class).putExtra(RoomBrowsingActivity.EXTRA_DATES_FIRST, true)).setup().get();
        idle();
        idle();
        assertEquals("Choose your check-in and check-out dates first.", ((TextInputLayout) a.findViewById(R.id.checkInLayout)).getError().toString());
    }

    @Test
    public void theNewBookingAndNewReservationButtons_goToTheDatesFirst() {
        Context app = ApplicationProvider.getApplicationContext();
        ScreenTestSupport.FakeApi api = new ScreenTestSupport.FakeApi();
        ScreenTestSupport.freshRepository(app, api);
        com.example.velocitysuites.PendingWizardRooms.set(java.util.Collections.<Room>emptyList());
        com.example.velocitysuites.BookingAndReservationActivity a = Robolectric.buildActivity(com.example.velocitysuites.BookingAndReservationActivity.class,
                new Intent(app, com.example.velocitysuites.BookingAndReservationActivity.class)).setup().get();
        idle();
        a.findViewById(R.id.btnNewBooking).performClick();
        Intent next = shadowOf(a).getNextStartedActivity();
        assertNotNull(next);
        assertEquals(RoomBrowsingActivity.class.getName(), next.getComponent().getClassName());
    }
}
