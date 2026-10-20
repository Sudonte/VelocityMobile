package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Rect;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.network.ApiMapper;
import com.example.velocitysuites.network.dto.AdditionalAmenityInfoDto;
import com.example.velocitysuites.network.dto.DirectBookingResponseDto;
import com.example.velocitysuites.ui.TestApplication;
import com.google.gson.Gson;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * "Your rooms": each amenity under the room the guest chose, from the first source that names it (the amenity-requests API, the stay
 * bill, this phone's saved submissions, the approval notification), anything unplaced under "Other amenities", only approved lines
 * counted, nothing counted twice - and one shared view that fits at 360 / 393dp in light and dark.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, application = TestApplication.class)
public class TransactionRoomsTest {

    @org.junit.Rule
    public final ThemeResetRule themeReset = new ThemeResetRule();

    private static final AmenityRecord.Status APPROVED = AmenityRecord.Status.APPROVED;
    private static final AmenityRecord.Status PENDING = AmenityRecord.Status.PENDING;
    private static final AmenityRecord.Status REJECTED = AmenityRecord.Status.REJECTED;

    @Before
    @After
    public void reset() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        RuntimeEnvironment.setQualifiers("w360dp-h800dp-xxhdpi");
        AmenityRoomSources.clear(ApplicationProvider.getApplicationContext());
    }

    private static Context ctx() {
        return new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_VelocitySuites);
    }

    // ---- data: Booking #569-like (rooms 1-001 Deluxe and 602 Executive), 4 approved lines = PHP 300

    private static String stayJson(String rooms, String lines, String requested, double amenitiesTotal) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Manila"));
        return "{\"id\":569,\"check_in\":\"" + today.minusDays(1) + "\",\"check_out\":\"" + today.plusDays(1) + "\","
                + "\"rooms_requested\":2,\"adults\":2,\"number_of_guests\":2,\"booking_status\":\"CHECKED_IN\",\"total_amount_due\":8000,"
                + "\"confirmed_at\":\"2026-09-30T14:00:00Z\",\"room_type\":{\"id\":1,\"name\":\"Deluxe\",\"rate\":\"1000.00\"},\"room_type_id\":1,\"payments\":[],"
                + "\"stay_bill\":{\"check_in\":\"2026-10-16\",\"scheduled_check_out\":\"2026-10-18\",\"actual_check_out\":\"2026-10-18\","
                + "\"scheduled_nights\":2,\"actual_nights\":2,\"extra_nights\":0,\"room_charge\":7560,\"total\":7860,\"total_paid\":7560,\"balance\":300,"
                + "\"rooms\":[" + rooms + "],\"additional_amenities\":[" + lines + "],\"additional_amenities_total\":" + amenitiesTotal
                + ",\"requested_amenities\":[" + requested + "]}}";
    }

    private static final String TWO_ROOMS = "{\"room_number\":\"1-001\",\"room_type\":\"Deluxe\",\"rate\":1000,\"nights\":2,\"subtotal\":3780},"
            + "{\"room_number\":\"602\",\"room_type\":\"Executive\",\"rate\":1000,\"nights\":2,\"subtotal\":3780}";

    private static String line(String name, int qty, double unit, String room) {
        return "{\"amenity_name\":\"" + name + "\",\"quantity\":" + qty + ",\"unit_price\":" + unit + ",\"subtotal\":" + (unit * qty)
                + (room == null ? "" : ",\"room_number\":\"" + room + "\"") + "}";
    }

    private static Booking booking(String rooms, String lines, String requested, double total) {
        return ApiMapper.toBooking(new Gson().fromJson(stayJson(rooms, lines, requested, total), DirectBookingResponseDto.class));
    }

    /** The bill of #569: pillow + blanket for 1-001, towel + blanket for 602, no room on the bill lines (what the server sends today). */
    private static Booking billOnly() {
        return booking(TWO_ROOMS, line("Additional Pillow", 1, 50, null) + "," + line("Additional Blanket", 1, 100, null) + ","
                + line("Additional Towel", 1, 50, null) + "," + line("Additional Blanket", 1, 100, null), "", 300);
    }

    private static AmenityRecord rec(String name, int qty, double subtotal, String room, AmenityRecord.Status status) {
        return new AmenityRecord(0, name, qty, subtotal, room, "", status);
    }

    /** What the guest chose on the request screen. */
    private static List<AmenityRecord> chosen(AmenityRecord.Status status) {
        return Arrays.asList(
                rec("Additional Pillow", 1, 50, "1-001", status), rec("Additional Blanket", 1, 100, "1-001", status),
                rec("Additional Towel", 1, 50, "602", status), rec("Additional Blanket", 1, 100, "602", status));
    }

    private static List<String> items(TransactionRooms.Card card) {
        List<String> out = new ArrayList<>();
        for (TransactionRooms.AmenityItem i : card.approved) out.add(i.name + " x" + i.quantity + " " + (int) i.subtotal);
        return out;
    }

    private static void assertSplitIsRight(TransactionRooms model) {
        assertEquals(2, model.cards.size());
        TransactionRooms.Card first = model.cards.get(0);
        TransactionRooms.Card second = model.cards.get(1);
        assertEquals("1-001", first.room.number);
        assertEquals("Deluxe", first.room.type);
        assertEquals(Arrays.asList("Additional Pillow x1 50", "Additional Blanket x1 100"), items(first));
        assertEquals(150.0, first.subtotal, 0.001);
        assertEquals("602", second.room.number);
        assertEquals("Executive", second.room.type);
        assertEquals(Arrays.asList("Additional Towel x1 50", "Additional Blanket x1 100"), items(second));
        assertEquals(150.0, second.subtotal, 0.001);
        assertNull("everything was placed", model.other);
        assertEquals(300.0, model.amenitiesTotal, 0.001);
    }

    // ---- the four sources

    @Test
    public void sourceA_theAmenityRequestsApi_placesEachItemInItsRoom() {
        List<AmenityRecord> api = new ArrayList<>(chosen(APPROVED));
        TransactionRooms model = TransactionRooms.of(billOnly(), api, Collections.emptyList(), Collections.emptyList());
        assertSplitIsRight(model);
        assertEquals(Collections.singletonList(TransactionRooms.Source.API), model.linkedBy);
    }

    @Test
    public void sourceA_parsesTheRealResponse_andReadsPendingAndRejectedFromIt() {
        String json = "{\"can_request\":true,\"requests\":["
                + "{\"id\":1,\"name\":\"Additional Pillow\",\"quantity\":1,\"unit_price\":50,\"subtotal\":50,\"room_id\":11,\"room_number\":\"1-001\",\"room_type\":\"Deluxe\",\"status\":\"approved\"},"
                + "{\"id\":2,\"name\":\"Additional Blanket\",\"quantity\":1,\"unit_price\":100,\"subtotal\":100,\"room_number\":\"1-001\",\"status\":\"approved\"},"
                + "{\"id\":3,\"name\":\"Additional Towel\",\"quantity\":1,\"unit_price\":50,\"subtotal\":50,\"room_number\":\"602\",\"status\":\"approved\"},"
                + "{\"id\":4,\"name\":\"Additional Blanket\",\"quantity\":1,\"unit_price\":100,\"subtotal\":100,\"room_number\":\"602\",\"status\":\"approved\"},"
                + "{\"id\":5,\"name\":\"Massage\",\"quantity\":1,\"unit_price\":300,\"subtotal\":300,\"room_number\":\"602\",\"status\":\"pending\"},"
                + "{\"id\":6,\"name\":\"Spa\",\"quantity\":1,\"unit_price\":500,\"subtotal\":500,\"room_number\":\"1-001\",\"status\":\"rejected\"}]}";
        Booking b = billOnly();
        AmenityRoomSources.putApi(b, new Gson().fromJson(json, AdditionalAmenityInfoDto.class));
        TransactionRooms model = TransactionRooms.of(b);
        assertSplitIsRight(model);
        assertEquals("pending is listed under its room, never counted", 1, model.cards.get(1).waiting.size());
        assertEquals("Massage", model.cards.get(1).waiting.get(0).name);
        assertEquals("not approved is listed under its room, never counted", "Spa", model.cards.get(0).rejected.get(0).name);
        assertEquals(300.0, model.amenitiesTotal, 0.001);
    }

    @Test
    public void sourceB_aRoomOnTheStayBillLine_isUsed() {
        Booking b = booking(TWO_ROOMS, line("Additional Pillow", 1, 50, "1-001") + "," + line("Additional Blanket", 1, 100, "1-001") + ","
                + line("Additional Towel", 1, 50, "602") + "," + line("Additional Blanket", 1, 100, "602"), "", 300);
        TransactionRooms model = TransactionRooms.of(b, null, Collections.emptyList(), Collections.emptyList());
        assertSplitIsRight(model);
        assertEquals(Collections.singletonList(TransactionRooms.Source.BILL), model.linkedBy);
    }

    @Test
    public void sourceC_whatThisPhoneSubmitted_isMatchedToTheApprovedLines() {
        TransactionRooms model = TransactionRooms.of(billOnly(), null, chosen(PENDING), Collections.emptyList());
        assertSplitIsRight(model);
        assertEquals(Collections.singletonList(TransactionRooms.Source.LOCAL), model.linkedBy);
    }

    @Test
    public void sourceC_survivesARestart_becauseItIsSaved() {
        Booking b = billOnly();
        AmenityRoomSources.remember(ApplicationProvider.getApplicationContext(), b, chosen(PENDING));
        // a new process: memory is empty, the saved file is not
        AmenityRoomSources.clear(null);
        AmenityRoomSources.attach(ApplicationProvider.getApplicationContext());
        assertSplitIsRight(TransactionRooms.of(b));
    }

    @Test
    public void sourceD_theApprovalNotificationNamesTheRoom() {
        AmenityRecord a = NotificationText.amenityRecordIn("Your request for 1 × Additional Pillow (Room 1-001) (₱50.00) was approved.");
        assertNotNull(a);
        assertEquals("Additional Pillow", a.name);
        assertEquals("1-001", a.room);
        assertEquals(50.0, a.subtotal, 0.001);
        assertEquals(APPROVED, a.status);
        assertEquals(REJECTED, NotificationText.amenityRecordIn("Your request for 2 x Spa (Room 602) was rejected. Reason: closed").status);
        assertNull(NotificationText.amenityRecordIn("Your booking was confirmed"));

        List<AmenityRecord> fromNotifications = Arrays.asList(
                NotificationText.amenityRecordIn("Your request for 1 × Additional Pillow (Room 1-001) (₱50.00) was approved."),
                NotificationText.amenityRecordIn("Your request for 1 × Additional Blanket (Room 1-001) (₱100.00) was approved."),
                NotificationText.amenityRecordIn("Your request for 1 × Additional Towel (Room 602) (₱50.00) was approved."),
                NotificationText.amenityRecordIn("Your request for 1 × Additional Blanket (Room 602) (₱100.00) was approved."));
        TransactionRooms model = TransactionRooms.of(billOnly(), null, Collections.emptyList(), fromNotifications);
        assertSplitIsRight(model);
        assertEquals(Collections.singletonList(TransactionRooms.Source.NOTIFICATION), model.linkedBy);
    }

    @Test
    public void sourceD_readsTheNotificationsOfThisTransaction_only() {
        Booking b = billOnly();
        assertTrue("no notifications loaded: nothing to read", AmenityRoomSources.fromNotifications(b).isEmpty());
        assertEquals("569", b.getId());
    }

    // ---- anything unplaced, and no double counting

    @Test
    public void whateverNoSourceCanPlace_goesUnderOtherAmenities_andStillCounts() {
        Booking b = booking(TWO_ROOMS, line("Additional Pillow", 1, 50, null) + "," + line("Additional Blanket", 1, 100, null) + ","
                + line("Additional Towel", 1, 50, null) + "," + line("Additional Blanket", 1, 100, null), "", 300);
        // the API only knows the pillow
        TransactionRooms model = TransactionRooms.of(b, Collections.singletonList(rec("Additional Pillow", 1, 50, "1-001", APPROVED)),
                Collections.emptyList(), Collections.emptyList());
        assertEquals(Collections.singletonList("Additional Pillow x1 50"), items(model.cardFor("1-001")));
        assertTrue(model.cardFor("602").approved.isEmpty());
        assertNotNull(model.other);
        assertEquals(Arrays.asList("Additional Blanket x2 200", "Additional Towel x1 50"), items(model.other));
        assertEquals(250.0, model.other.subtotal, 0.001);
        assertEquals("the bill is still the total", 300.0, model.amenitiesTotal, 0.001);
    }

    @Test
    public void withNoSourceAtAll_everythingIsOtherAmenities_andTheRoomsSaySoWhenEmpty() {
        TransactionRooms model = TransactionRooms.of(billOnly(), null, Collections.emptyList(), Collections.emptyList());
        assertTrue(model.cards.get(0).isEmpty() && model.cards.get(1).isEmpty());
        assertEquals(300.0, model.other.subtotal, 0.001);
        assertEquals(300.0, model.amenitiesTotal, 0.001);
        assertTrue(model.linkedBy.isEmpty());
    }

    @Test
    public void aLineSeenByEverySource_isCountedOnce() {
        Booking b = booking(TWO_ROOMS, line("Additional Pillow", 1, 50, "1-001") + "," + line("Additional Blanket", 1, 100, "1-001") + ","
                + line("Additional Towel", 1, 50, "602") + "," + line("Additional Blanket", 1, 100, "602"), "", 300);
        List<AmenityRecord> notifications = new ArrayList<>(chosen(APPROVED));
        TransactionRooms model = TransactionRooms.of(b, new ArrayList<>(chosen(APPROVED)), chosen(PENDING), notifications);
        assertSplitIsRight(model);
        assertEquals("not 1200", 300.0, model.amenitiesTotal, 0.001);
    }

    @Test
    public void aSourceNeverAddsMoreThanTheBillApproved() {
        // the API lists a pillow the bill does not (yet) contain: it is not an approved charge, so it is not counted
        TransactionRooms model = TransactionRooms.of(billOnly(), Collections.singletonList(rec("Additional Slippers", 2, 100, "602", APPROVED)),
                Collections.emptyList(), Collections.emptyList());
        assertEquals(300.0, model.amenitiesTotal, 0.001);
        assertTrue(model.cardFor("602").approved.stream().noneMatch(i -> i.name.contains("Slippers")));
    }

    @Test
    public void aRequestForThePillowAsked_inTheOtherRoomThanTheBillOrder_staysInItsRoom() {
        // quantities are summed per room: two blankets in 602, none in 1-001
        List<AmenityRecord> api = Arrays.asList(rec("Additional Pillow", 1, 50, "1-001", APPROVED), rec("Additional Blanket", 2, 200, "602", APPROVED),
                rec("Additional Towel", 1, 50, "1-001", APPROVED));
        TransactionRooms model = TransactionRooms.of(billOnly(), api, Collections.emptyList(), Collections.emptyList());
        assertEquals(Arrays.asList("Additional Pillow x1 50", "Additional Towel x1 50"), items(model.cardFor("1-001")));
        assertEquals(Collections.singletonList("Additional Blanket x2 200"), items(model.cardFor("602")));
        assertEquals(100.0, model.cardFor("1-001").subtotal, 0.001);
        assertEquals(200.0, model.cardFor("602").subtotal, 0.001);
        assertNull(model.other);
    }

    // ---- pending / rejected are never counted

    @Test
    public void pendingAndRejected_areListed_butNeverCounted() {
        List<AmenityRecord> api = new ArrayList<>(chosen(APPROVED));
        api.add(rec("Massage", 1, 300, "602", PENDING));
        api.add(rec("Spa", 1, 500, "602", REJECTED));
        api.add(rec("Late checkout", 1, 200, null, PENDING));
        TransactionRooms model = TransactionRooms.of(billOnly(), api, Collections.emptyList(), Collections.emptyList());
        assertEquals(300.0, model.amenitiesTotal, 0.001);
        assertEquals("Massage", model.cardFor("602").waiting.get(0).name);
        assertEquals("Spa", model.cardFor("602").rejected.get(0).name);
        assertEquals("a waiting request with no room is under Other amenities", "Late checkout", model.other.waiting.get(0).name);
        assertEquals(0.0, model.other.subtotal, 0.001);
    }

    // ---- rooms

    @Test
    public void twoRoomsOfTheSameType_stayTwoSeparateCards() {
        String rooms = "{\"room_number\":\"602\",\"room_type\":\"Executive\",\"rate\":1000,\"nights\":2,\"subtotal\":2000},"
                + "{\"room_number\":\"601\",\"room_type\":\"Executive\",\"rate\":1000,\"nights\":2,\"subtotal\":2000}";
        Booking b = booking(rooms, line("Additional Pillow", 1, 50, null) + "," + line("Additional Pillow", 1, 50, null), "", 100);
        List<AmenityRecord> api = Arrays.asList(rec("Additional Pillow", 1, 50, "601", APPROVED), rec("Additional Pillow", 1, 50, "602", APPROVED));
        TransactionRooms model = TransactionRooms.of(b, api, Collections.emptyList(), Collections.emptyList());
        assertEquals(2, model.cards.size());
        assertEquals("ordered by room number", "601", model.cards.get(0).room.number);
        assertEquals("602", model.cards.get(1).room.number);
        assertEquals("Executive", model.cards.get(0).room.type);
        assertEquals(50.0, model.cards.get(0).subtotal, 0.001);
        assertEquals(50.0, model.cards.get(1).subtotal, 0.001);
    }

    @Test
    public void roomNumbersAreOrderedNaturally() {
        assertTrue(TransactionRooms.naturalCompare("2-001", "10-001") < 0);
        assertTrue(TransactionRooms.naturalCompare("1-001", "602") < 0);
        assertTrue(TransactionRooms.naturalCompare("602", "1-001") > 0);
        assertEquals(0, TransactionRooms.naturalCompare("303", "303"));
        assertEquals("Room 303", "Room " + AmenityRecord.normalizeRoom("Room 303"));
        assertEquals("1-001", AmenityRecord.normalizeRoom(" room 1-001 "));
        assertNull(AmenityRecord.normalizeRoom("  "));
    }

    @Test
    public void aSourceNamingARoomTheTransactionDoesNotList_isBelieved() {
        TransactionRooms model = TransactionRooms.of(billOnly(), Collections.singletonList(rec("Additional Pillow", 1, 50, "703", APPROVED)),
                Collections.emptyList(), Collections.emptyList());
        assertNotNull(model.cardFor("703"));
        assertEquals(3, model.cards.size());
    }

    // ---- the shared view

    private static void collect(View v, List<String> out) {
        if (v instanceof TextView && v.getVisibility() == View.VISIBLE && ((TextView) v).getText().length() > 0) out.add(((TextView) v).getText().toString());
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) collect(((ViewGroup) v).getChildAt(i), out);
    }

    private static String texts(View v) {
        List<String> out = new ArrayList<>();
        collect(v, out);
        return String.join(" | ", out);
    }

    @Test
    public void theView_showsEachRoomWithItsTypeChip_itsAmenities_itsFooter_andTheTotal() {
        List<AmenityRecord> api = new ArrayList<>(chosen(APPROVED));
        api.add(rec("Massage", 1, 300, "602", PENDING));
        api.add(rec("Spa", 1, 500, "1-001", REJECTED));
        String all = texts(TransactionRoomsView.build(ctx(), TransactionRooms.of(billOnly(), api, Collections.emptyList(), Collections.emptyList())));
        assertTrue(all, all.startsWith("Your rooms | Room 1-001 | Deluxe | Approved amenities | Additional Pillow × 1 | ₱50.00 | Additional Blanket × 1 | ₱100.00"));
        assertTrue(all, all.contains("Not approved | Spa × 1 | ₱500.00 | Rejected | Room amenities | ₱150.00 | Room 602 | Executive"));
        assertTrue(all, all.contains("Additional Towel × 1 | ₱50.00 | Additional Blanket × 1 | ₱100.00 | Waiting for approval | Massage × 1 | ₱300.00 | Pending | Room amenities | ₱150.00"));
        assertTrue(all, all.endsWith("Amenities total | ₱300.00"));
        assertFalse(all, all.contains("Other amenities"));
        assertFalse(all, all.contains("All rooms"));
    }

    @Test
    public void theView_unplacedLines_goUnderOtherAmenities() {
        String all = texts(TransactionRoomsView.build(ctx(), TransactionRooms.of(billOnly(), null, Collections.emptyList(), Collections.emptyList())));
        assertTrue(all, all.contains("Room 1-001 | Deluxe | No amenities requested for this room | Room 602 | Executive | No amenities requested for this room | Other amenities | Approved amenities"));
        assertTrue(all, all.contains("Additional Blanket × 2 | ₱200.00"));
        assertTrue(all, all.contains("Other amenities subtotal | ₱300.00"));
        assertTrue(all, all.endsWith("Amenities total | ₱300.00"));
    }

    @Test
    public void theView_beforeAnyRoomNumber_namesTheRoomType() {
        LocalDate in = LocalDate.now(ZoneId.of("Asia/Manila")).plusDays(3);
        Booking b = new Booking("5", "1", "Deluxe", "Deluxe", in.toString(), in.plusDays(1).toString(), 2, 1000, "Confirmed", "Oct 01, 2026");
        b.setHasBooking(true);
        b.setDirectBooking(true);
        String all = texts(TransactionRoomsView.build(ctx(), b));
        assertTrue(all, all.contains("Room (Deluxe) | No amenities requested for this room"));
        assertFalse(all, all.contains("Amenities total"));
    }

    @Test
    public void totalsStayConsistent_withTheSharedTotal() {
        Booking b = billOnly();
        TransactionStatusHelper.Summary s = TransactionStatusHelper.summarize(b);
        assertEquals(s.grandTotal, BookingTotals.totalOf(b, null), 0.001);
        List<NextStayDetails.Row> rows = NextStayDetails.rows(ctx(), b);
        boolean hasRooms = false;
        for (NextStayDetails.Row r : rows) hasRooms |= r.kind == NextStayDetails.Kind.ROOMS;
        assertTrue(hasRooms);
        LinearLayout box = new LinearLayout(ctx());
        NextStayDetails.render(ctx(), box, rows, b);
        assertNotNull(box.findViewById(R.id.yourRoomsSection));
    }

    // ---- 360 / 393dp, light / dark, 1 / 2 / 3 rooms, mixed states

    private static TransactionRooms mixed(int roomCount) {
        String[] numbers = {"1-001", "602", "603"};
        String[] types = {"Deluxe", "Executive", "Executive"};
        StringBuilder rooms = new StringBuilder();
        for (int i = 0; i < roomCount; i++) {
            if (i > 0) rooms.append(',');
            rooms.append("{\"room_number\":\"").append(numbers[i]).append("\",\"room_type\":\"").append(types[i]).append("\",\"rate\":1000,\"nights\":2,\"subtotal\":2000}");
        }
        Booking b = booking(rooms.toString(), line("Additional Pillow", roomCount, 50, null) + "," + line("Additional Extra Long Bath Towel Set", 1, 250, null), "", 50.0 * roomCount + 250);
        List<AmenityRecord> api = new ArrayList<>();
        for (int i = 0; i < roomCount; i++) api.add(rec("Additional Pillow", 1, 50, numbers[i], APPROVED));
        api.add(rec("Additional Extra Long Bath Towel Set", 1, 250, numbers[roomCount - 1], APPROVED));
        api.add(rec("Complimentary Welcome Fruit Basket", 1, 1250, numbers[0], PENDING));
        api.add(rec("Premium Spa Treatment Package", 2, 12500, numbers[roomCount - 1], REJECTED));
        return TransactionRooms.of(b, api, Collections.emptyList(), Collections.emptyList());
    }

    private static void assertFits(View root, int widthPx, String at) {
        if (root instanceof LinearLayout && ((LinearLayout) root).getOrientation() == LinearLayout.HORIZONTAL) {
            Rect prev = null;
            for (int i = 0; i < ((LinearLayout) root).getChildCount(); i++) {
                View c = ((LinearLayout) root).getChildAt(i);
                if (c.getVisibility() == View.GONE) continue;
                Rect r = new Rect(c.getLeft(), c.getTop(), c.getRight(), c.getBottom());
                assertTrue("inside its row" + at, r.left >= 0 && r.right <= root.getWidth());
                if (prev != null) assertFalse("no overlap in a row" + at, Rect.intersects(prev, r) && r.width() > 0 && prev.width() > 0);
                prev = r;
            }
        }
        if (root instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) assertFits(((ViewGroup) root).getChildAt(i), widthPx, at);
        }
    }

    @Test
    public void theView_fitsAt360And393_inLightAndDark_withOneTwoAndThreeRooms() {
        String[] modes = {"w360dp-h800dp-xxhdpi", "w360dp-h800dp-night-xxhdpi", "w393dp-h851dp-xxhdpi", "w393dp-h851dp-night-xxhdpi"};
        int[] widths = {360, 360, 393, 393};
        for (int m = 0; m < modes.length; m++) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            RuntimeEnvironment.setQualifiers(modes[m]);
            Context c = ctx();
            for (int rooms = 1; rooms <= 3; rooms++) {
                String at = " (" + modes[m] + ", " + rooms + " rooms)";
                LinearLayout view = TransactionRoomsView.build(c, mixed(rooms));
                int w = Math.round(widths[m] * c.getResources().getDisplayMetrics().density) - Math.round(32 * c.getResources().getDisplayMetrics().density);
                view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
                assertFits(view, w, at);
                String all = texts(view);
                assertTrue(all + at, all.contains("Not approved") && all.contains("Waiting for approval") && all.contains("Rejected") && all.contains("Pending"));
                assertFalse("normal case, never ALL CAPS" + at, all.equals(all.toUpperCase()) );
                // label left, amount right on the footer rows
                View total = view.findViewById(R.id.yourRoomsAmenitiesTotal);
                assertNotNull(total);
                ViewGroup row = (ViewGroup) total;
                assertTrue("amount right of label" + at, row.getChildAt(1).getLeft() > row.getChildAt(0).getLeft());
                assertEquals("amount flush right" + at, row.getWidth(), row.getChildAt(1).getRight());
            }
        }
    }
}
