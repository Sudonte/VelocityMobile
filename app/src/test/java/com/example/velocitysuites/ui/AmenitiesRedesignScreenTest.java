package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.AdditionalAmenitiesActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.network.dto.AdditionalAmenityInfoDto;
import com.example.velocitysuites.network.dto.AmenityDto;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The redesigned Request Additional Amenities screen and its picker sheet: each amenity once (by id), active ones only,
 * a bold name, a description only when there is one, a compact stepper with stock limits, line subtotals, the sheet's
 * footer total, the room card's summary, the disabled Send Request hint, and a clean layout at 360dp in light and dark.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AmenitiesRedesignScreenTest {

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
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static AmenityDto amenity(long id, String name, String price, int stock, String description) {
        AmenityDto dto = new AmenityDto();
        dto.id = id;
        dto.amenity_name = name;
        dto.charge = price;
        dto.quantity = stock;
        dto.description = description;
        return dto;
    }

    private static AdditionalAmenityInfoDto info() {
        AdditionalAmenityInfoDto dto = new AdditionalAmenityInfoDto();
        dto.can_request = true;
        dto.requests = new ArrayList<>();
        AdditionalAmenityInfoDto.Room room = new AdditionalAmenityInfoDto.Room();
        room.room_id = 11;
        room.room_number = "303";
        room.room_type = "Deluxe";
        dto.rooms = Arrays.asList(room);
        return dto;
    }

    private AdditionalAmenitiesActivity open(List<AmenityDto> catalog) {
        Intent intent = new Intent(app, AdditionalAmenitiesActivity.class)
                .putExtra(AdditionalAmenitiesActivity.EXTRA_DIRECT_BOOKING, true)
                .putExtra(AdditionalAmenitiesActivity.EXTRA_ID, "42");
        AdditionalAmenitiesActivity a = Robolectric.buildActivity(AdditionalAmenitiesActivity.class, intent).setup().get();
        idle();
        api.<AdditionalAmenityInfoDto>last("getBookingAdditionalAmenities").succeed(info());
        idle();
        api.<List<AmenityDto>>last("getAmenities").succeed(catalog);
        idle();
        return a;
    }

    private static LinearLayout openPicker(AdditionalAmenitiesActivity a) {
        ((LinearLayout) a.findViewById(R.id.additionalAmenitiesRooms)).getChildAt(0).performClick();
        idle();
        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull("the picker opened", dialog);
        return dialog.findViewById(R.id.pickerList);
    }

    private static TextView in(View row, int id) {
        return row.findViewById(id);
    }

    private static List<AmenityDto> towelsAndMore() {
        return new ArrayList<>(Arrays.asList(
                amenity(7, "Additional Towel", "50.00", 10, "A fresh bath towel delivered to your room."),
                amenity(8, "Additional Blanket", "100.00", 3, null),
                amenity(9, "Additional Pillow", "50.00", 10, "N/A")));
    }

    // ---- the catalog: once each, active only -------------------------------------------------------------

    @Test
    public void eachAmenityAppearsOnce_aRepeatedIdIsCollapsed() {
        List<AmenityDto> catalog = towelsAndMore();
        catalog.add(amenity(7, "Additional Towel", "50.00", 10, "A fresh bath towel delivered to your room.")); // the server sent id 7 twice
        AdditionalAmenitiesActivity a = open(catalog);
        LinearLayout list = openPicker(a);
        assertEquals("three amenities, not four", 3, list.getChildCount());
        int towels = 0;
        for (int i = 0; i < list.getChildCount(); i++) if ("Additional Towel".equals(in(list.getChildAt(i), R.id.tvAmenityOptionName).getText().toString())) towels++;
        assertEquals("the towel is listed once", 1, towels);
    }

    @Test
    public void twoAmenitiesWithTheSameNameButDifferentIds_areBothKept() {
        List<AmenityDto> catalog = towelsAndMore();
        catalog.add(amenity(10, "Additional Towel", "50.00", 10, null)); // a different id: a different amenity as far as the server says
        AdditionalAmenitiesActivity a = open(catalog);
        assertEquals(4, openPicker(a).getChildCount());
    }

    @Test
    public void onlyActiveAmenitiesAreOffered() {
        List<AmenityDto> catalog = towelsAndMore();
        AmenityDto hidden = amenity(20, "Spa Voucher", "900.00", 5, null);
        hidden.is_active = false;
        AmenityDto retired = amenity(21, "Old Item", "10.00", 5, null);
        retired.status = "inactive";
        catalog.add(hidden);
        catalog.add(retired);
        AdditionalAmenitiesActivity a = open(catalog);
        LinearLayout list = openPicker(a);
        assertEquals(3, list.getChildCount());
        for (int i = 0; i < list.getChildCount(); i++) {
            String name = in(list.getChildAt(i), R.id.tvAmenityOptionName).getText().toString();
            assertFalse(name, name.equals("Spa Voucher") || name.equals("Old Item"));
        }
    }

    @Test
    public void anEmptyCatalog_saysNoAmenitiesAreAvailable() {
        AdditionalAmenitiesActivity a = open(new ArrayList<>());
        assertEquals("No amenities are available right now.", ((TextView) a.findViewById(R.id.additionalAmenitiesStateText)).getText().toString());
        assertEquals(View.GONE, a.findViewById(R.id.additionalAmenitiesBottomBar).getVisibility());
    }

    // ---- a row ---------------------------------------------------------------------------------------------

    @Test
    public void theNameIsBold_theDescriptionShowsOnlyWhenThereIsOne_neverAPlaceholder() {
        AdditionalAmenitiesActivity a = open(towelsAndMore());
        LinearLayout list = openPicker(a);
        TextView name = in(list.getChildAt(0), R.id.tvAmenityOptionName);
        assertTrue("bold", name.getTypeface().getWeight() >= 700);
        assertEquals(View.VISIBLE, in(list.getChildAt(0), R.id.tvAmenityOptionDescription).getVisibility());
        assertEquals("A fresh bath towel delivered to your room.", in(list.getChildAt(0), R.id.tvAmenityOptionDescription).getText().toString());
        assertEquals("no description: no line", View.GONE, in(list.getChildAt(1), R.id.tvAmenityOptionDescription).getVisibility());
        assertEquals("a placeholder is not shown either", View.GONE, in(list.getChildAt(2), R.id.tvAmenityOptionDescription).getVisibility());
        for (int i = 0; i < list.getChildCount(); i++) {
            assertFalse("never N/A", in(list.getChildAt(i), R.id.tvAmenityOptionDescription).getText().toString().equalsIgnoreCase("N/A"));
        }
        assertEquals("price below", "₱50.00 each", in(list.getChildAt(0), R.id.tvAmenityOptionPrice).getText().toString());
    }

    @Test
    public void aLongDescription_isTwoLinesWithMore_thatExpandsIt() {
        String longText = "A soft cotton towel set with a bath towel, a hand towel and a face cloth, replaced daily on request by housekeeping.";
        AdditionalAmenitiesActivity a = open(new ArrayList<>(Arrays.asList(amenity(7, "Towel Set", "80.00", 5, longText))));
        View row = openPicker(a).getChildAt(0);
        TextView description = in(row, R.id.tvAmenityOptionDescription);
        TextView more = in(row, R.id.tvAmenityOptionMore);
        assertEquals(2, description.getMaxLines());
        assertEquals(View.VISIBLE, more.getVisibility());
        more.performClick();
        assertEquals(Integer.MAX_VALUE, description.getMaxLines());
        assertEquals("Less", more.getText().toString());
        more.performClick();
        assertEquals(2, description.getMaxLines());
    }

    @Test
    public void theStepper_minusIsOffAtZero_plusStopsAtTheStock_andTheRowShowsItsSubtotal() {
        AdditionalAmenitiesActivity a = open(new ArrayList<>(Arrays.asList(amenity(8, "Additional Blanket", "100.00", 2, null))));
        View row = openPicker(a).getChildAt(0);
        View minus = row.findViewById(R.id.btnAmenityOptionMinus);
        View plus = row.findViewById(R.id.btnAmenityOptionPlus);
        assertFalse("minus is off at 0", minus.isEnabled());
        assertTrue(plus.isEnabled());
        assertEquals("no subtotal at 0", View.GONE, in(row, R.id.tvAmenityOptionSubtotal).getVisibility());

        plus.performClick();
        assertTrue(minus.isEnabled());
        assertEquals("1 × ₱100.00 = ₱100.00", in(row, R.id.tvAmenityOptionSubtotal).getText().toString());
        plus.performClick();
        assertEquals("2 × ₱100.00 = ₱200.00", in(row, R.id.tvAmenityOptionSubtotal).getText().toString());
        assertFalse("plus is off at the stock limit (2)", plus.isEnabled());
        plus.performClick();
        assertEquals("never above the stock", "2", in(row, R.id.tvAmenityOptionQty).getText().toString());

        minus.performClick();
        minus.performClick();
        assertFalse(minus.isEnabled());
        assertEquals("0", in(row, R.id.tvAmenityOptionQty).getText().toString());
    }

    @Test
    public void aChosenRowIsHighlighted() {
        AdditionalAmenitiesActivity a = open(towelsAndMore());
        View row = openPicker(a).getChildAt(0);
        com.google.android.material.card.MaterialCardView card = row.findViewById(R.id.cardAmenityOption);
        int before = card.getStrokeColor();
        row.findViewById(R.id.btnAmenityOptionPlus).performClick();
        assertEquals(app.getColor(R.color.velocity_red_primary), card.getStrokeColor());
        assertFalse("the border changed", before == card.getStrokeColor());
    }

    // ---- the sheet footer, the room card, the bottom bar -----------------------------------------------------

    @Test
    public void sheetFooterAndRoomCard_sayHowManyItemsAndHowMuch_andSendRequestHasItsHint() {
        AdditionalAmenitiesActivity a = open(towelsAndMore());
        assertEquals("No amenities added", in(((LinearLayout) a.findViewById(R.id.additionalAmenitiesRooms)).getChildAt(0), R.id.tvAmenityRoomSelection).getText().toString());
        assertEquals(View.VISIBLE, a.findViewById(R.id.additionalAmenitiesHint).getVisibility());
        assertFalse(a.findViewById(R.id.additionalAmenitiesSubmit).isEnabled());
        assertEquals("Add at least one amenity to send a request.", ((TextView) a.findViewById(R.id.additionalAmenitiesHint)).getText().toString());
        assertEquals("₱0.00", ((TextView) a.findViewById(R.id.additionalAmenitiesTotal)).getText().toString());

        LinearLayout list = openPicker(a);
        list.getChildAt(0).findViewById(R.id.btnAmenityOptionPlus).performClick();
        list.getChildAt(0).findViewById(R.id.btnAmenityOptionPlus).performClick();
        list.getChildAt(2).findViewById(R.id.btnAmenityOptionPlus).performClick();
        TextView footer = ShadowDialog.getLatestDialog().findViewById(R.id.tvPickerTotal);
        assertEquals("3 items · ₱150.00", footer.getText().toString());
        ShadowDialog.getLatestDialog().findViewById(R.id.btnPickerDone).performClick();
        idle();

        View card = ((LinearLayout) a.findViewById(R.id.additionalAmenitiesRooms)).getChildAt(0);
        assertEquals("3 items · ₱150.00", in(card, R.id.tvAmenityRoomSelection).getText().toString());
        assertEquals(View.VISIBLE, card.findViewById(R.id.tvAmenityRoomPicked).getVisibility());
        assertEquals("2× Additional Towel, 1× Additional Pillow", in(card, R.id.tvAmenityRoomPicked).getText().toString());
        assertEquals("no longer red: a neutral gray", app.getColor(R.color.velocity_text_secondary), in(card, R.id.tvAmenityRoomSelection).getCurrentTextColor());
        assertEquals(View.GONE, a.findViewById(R.id.additionalAmenitiesHint).getVisibility());
        assertTrue(a.findViewById(R.id.additionalAmenitiesSubmit).isEnabled());
        assertEquals("₱150.00", ((TextView) a.findViewById(R.id.additionalAmenitiesTotal)).getText().toString());
        assertEquals("primary text colour, not red", app.getColor(R.color.velocity_text_primary),
                ((TextView) a.findViewById(R.id.additionalAmenitiesTotal)).getCurrentTextColor());
    }

    @Test
    public void theStaySummaryNamesTheBookingTheRoomAndTheDates() {
        com.example.velocitysuites.RoomRepository repository = com.example.velocitysuites.RoomRepository.getInstance(app);
        repository.refreshBookingsSplit((merged, reservationsError, directError) -> { });
        com.example.velocitysuites.network.dto.DirectBookingResponseDto booking = ScreenTestSupport.directBooking(42, 1750);
        booking.check_in = "2026-10-20";
        booking.check_out = "2026-10-21";
        ScreenTestSupport.answerBookingLoad(api, 0, ScreenTestSupport.reservations(), ScreenTestSupport.directBookings(booking));
        idle();
        AdditionalAmenitiesActivity a = open(towelsAndMore());
        assertEquals(View.VISIBLE, a.findViewById(R.id.additionalAmenitiesStay).getVisibility());
        assertEquals("Booking #42 · Room 303 · Oct 20 → Oct 21, 2026", ((TextView) a.findViewById(R.id.tvAmenitiesStaySummary)).getText().toString());
        assertEquals("one short intro line", "Pick amenities for your room. Nothing is charged until the front desk approves.",
                ((TextView) a.findViewById(R.id.tvAmenitiesIntro)).getText().toString());
    }

    // ---- layout at 360dp, light and dark ------------------------------------------------------------------------

    private void assertScreenAndSheetFit(AdditionalAmenitiesActivity a) {
        View root = a.getWindow().getDecorView();
        int w = app.getResources().getDisplayMetrics().widthPixels;
        int h = app.getResources().getDisplayMetrics().heightPixels;
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
        View submit = a.findViewById(R.id.additionalAmenitiesSubmit);
        View total = a.findViewById(R.id.additionalAmenitiesTotal);
        View bar = a.findViewById(R.id.additionalAmenitiesBottomBar);
        assertFalse("Total and Send Request do not overlap", LayoutHarness.overlaps(root, total, submit));
        assertTrue("the bar is pinned to the bottom", LayoutHarness.boundsIn(root, bar).bottom >= h - LayoutHarness.dp(app, 4));
        assertTrue("Send Request is on screen and 48dp tall", LayoutHarness.boundsIn(root, submit).right <= w && submit.getHeight() >= LayoutHarness.dp(app, 48));
        assertEquals("no letter-spacing on Send Request", 0f, ((TextView) submit).getLetterSpacing(), 0.0001f);
        assertEquals("the button label is one line", 1, ((TextView) submit).getLineCount());
        assertFalse("the hint is inside the bar, above the Total row", LayoutHarness.overlaps(root, a.findViewById(R.id.additionalAmenitiesHint), total));

        LinearLayout list = openPicker(a);
        Dialog dialog = ShadowDialog.getLatestDialog();
        View content = dialog.findViewById(R.id.btnPickerDone).getRootView();
        int cw = w;
        content.measure(View.MeasureSpec.makeMeasureSpec(cw, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        content.layout(0, 0, content.getMeasuredWidth(), content.getMeasuredHeight());
        View done = dialog.findViewById(R.id.btnPickerDone);
        View footer = dialog.findViewById(R.id.tvPickerTotal);
        assertFalse("footer total and Done do not overlap", LayoutHarness.overlaps(content, footer, done));
        assertTrue("Done is 48dp tall", done.getHeight() >= LayoutHarness.dp(app, 48));
        assertEquals("no letter-spacing on Done", 0f, ((TextView) done).getLetterSpacing(), 0.0001f);
        for (int i = 0; i < list.getChildCount(); i++) {
            View row = list.getChildAt(i);
            View name = row.findViewById(R.id.tvAmenityOptionName);
            View minus = row.findViewById(R.id.btnAmenityOptionMinus);
            View plus = row.findViewById(R.id.btnAmenityOptionPlus);
            assertFalse("name and stepper overlap", LayoutHarness.overlaps(content, name, minus));
            assertFalse("stepper buttons overlap", LayoutHarness.overlaps(content, minus, plus));
            assertTrue("48dp touch targets", minus.getWidth() >= LayoutHarness.dp(app, 48) && plus.getHeight() >= LayoutHarness.dp(app, 48));
            Rect r = LayoutHarness.boundsIn(content, plus);
            assertTrue("stepper inside the sheet", r.right <= content.getMeasuredWidth());
        }
    }

    @Test
    public void screenAndSheet_fitAt360dp_inLightMode() {
        assertScreenAndSheetFit(open(towelsAndMore()));
    }

    @Test
    @Config(sdk = 35, qualifiers = "w360dp-h800dp-night-xxhdpi", application = TestApplication.class)
    public void screenAndSheet_fitAt360dp_inDarkMode() {
        assertScreenAndSheetFit(open(towelsAndMore()));
    }
}
