package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.R;
import com.example.velocitysuites.Room;
import com.example.velocitysuites.RoomBrowsingActivity;
import com.example.velocitysuites.network.dto.RoomTypeDto;
import com.example.velocitysuites.network.dto.RoomsResponse;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.slider.RangeSlider;
import com.google.android.material.textfield.TextInputLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.List;

/** The redesigned Room Browsing screen: search area, results bar, empty states, the Filters bottom sheet. */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class RoomBrowsingRedesignTest {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private Context app;
    private ScreenTestSupport.FakeApi api;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        api = new ScreenTestSupport.FakeApi();
        ScreenTestSupport.freshRepository(app, api);
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private ActivityController<RoomBrowsingActivity> launch() {
        ActivityController<RoomBrowsingActivity> c = Robolectric.buildActivity(RoomBrowsingActivity.class, new Intent(app, RoomBrowsingActivity.class));
        c.setup();
        idle();
        return c;
    }

    private static RoomTypeDto type(long id, String name, String rate, int capacity, int available) {
        RoomTypeDto t = LandingFixtures.roomType(id, name, rate, available);
        t.capacity = capacity;
        return t;
    }

    private void answerRooms(RoomTypeDto... types) {
        api.<RoomsResponse>last("getRoomsFresh").succeed(LandingFixtures.rooms(types));
        idle();
    }

    private RoomBrowsingActivity launchWithThreeRooms() {
        RoomBrowsingActivity a = launch().get();
        answerRooms(type(1, "Deluxe", "1850.00", 2, 5), type(2, "Suite", "3000.00", 4, 5), type(3, "Family", "3999.00", 6, 1));
        return a;
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private static <T> T get(Object target, String field) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        return (T) f.get(target);
    }

    private static void invoke(Object target, String method) throws Exception {
        Method m = target.getClass().getDeclaredMethod(method);
        m.setAccessible(true);
        m.invoke(target);
        idle();
    }

    private static Calendar day(LocalDate d) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth());
        return c;
    }

    private void setDates(RoomBrowsingActivity a, int inDays, int outDays) throws Exception {
        LocalDate today = com.example.velocitysuites.CheckInWindow.today();
        set(a, "checkInDate", day(today.plusDays(inDays)));
        set(a, "checkOutDate", day(today.plusDays(outDays)));
        invoke(a, "updateDateSummary");
    }

    private Dialog openSheet(RoomBrowsingActivity a) {
        a.findViewById(R.id.btnFilters).performClick();
        idle();
        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull("the Filters sheet opened", dialog);
        return dialog;
    }

    private static String text(RoomBrowsingActivity a, int id) {
        return ((TextView) a.findViewById(id)).getText().toString();
    }

    private static void layOut(RoomBrowsingActivity a, Context app) {
        View root = a.getWindow().getDecorView();
        int w = app.getResources().getDisplayMetrics().widthPixels;
        int h = app.getResources().getDisplayMetrics().heightPixels;
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
    }

    // ---- search area ----

    @Test
    public void searchArea_hasOneLineDescription_floatingLabels_helperText_noDuplicateNotice_andNoUpperCaseLabels() throws Exception {
        RoomBrowsingActivity a = launchWithThreeRooms();
        assertEquals("Find a room for your dates", text(a, R.id.screenDescription));
        TextInputLayout in = a.findViewById(R.id.checkInLayout);
        TextInputLayout out = a.findViewById(R.id.checkOutLayout);
        assertEquals("Check-in", in.getHint().toString());
        assertEquals("Check-out", out.getHint().toString());
        String earliest = com.example.velocitysuites.CheckInWindow.earliest().format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d", java.util.Locale.US));
        assertEquals("Earliest: " + earliest, in.getHelperText().toString());
        assertEquals("Pick a check-in date first", out.getHelperText().toString());

        setDates(a, 2, 4);
        assertEquals("the check-out helper states the length once valid", "2 nights", out.getHelperText().toString());
        assertNull("no separate night line", a.findViewById(R.id.tvNightsSummary));
        TextView title = a.findViewById(R.id.tvSearchHeader);
        assertEquals("Find Your Perfect Stay", title.getText().toString());
        assertTrue("bold shared title style", title.getTypeface().isBold() || title.getTypeface().getWeight() >= 700);
        assertEquals(0f, title.getLetterSpacing(), 0.0001f);
    }

    @Test
    public void viewAvailability_isFilledPrimary_sentenceCase_enabledOnlyForValidDates_andReadableBothWays() throws Exception {
        RoomBrowsingActivity a = launchWithThreeRooms();
        MaterialButton button = a.findViewById(R.id.btnSearchAvailability);
        assertEquals("View availability", button.getText().toString());
        assertFalse(button.isAllCaps());
        assertEquals(0f, button.getLetterSpacing(), 0.0001f);
        assertFalse("disabled until both dates are valid", button.isEnabled());
        int[] disabled = {-android.R.attr.state_enabled};
        int[] enabled = {android.R.attr.state_enabled};
        assertTrue("disabled text readable on the disabled fill", androidx.core.graphics.ColorUtils.calculateContrast(
                button.getTextColors().getColorForState(disabled, 0), button.getBackgroundTintList().getColorForState(disabled, 0)) >= 4.5);
        assertTrue("enabled text readable on the filled red", androidx.core.graphics.ColorUtils.calculateContrast(
                button.getTextColors().getColorForState(enabled, 0), button.getBackgroundTintList().getColorForState(enabled, 0)) >= 4.5);
        int before = api.count("getRoomsFresh");
        button.performClick(); // even a programmatic tap on the disabled button must not search
        idle();
        assertEquals("no search from a disabled button", before, api.count("getRoomsFresh"));

        setDates(a, 2, 4);
        assertTrue("enabled for two valid dates", button.isEnabled());
        set(a, "checkOutDate", get(a, "checkInDate")); // check-out on the check-in day
        invoke(a, "updateDateSummary");
        assertFalse("not for a check-out on/before check-in", button.isEnabled());
    }

    @Test
    public void reset_isHiddenUntilSomethingIsSet_andIsAPlainTextButton() throws Exception {
        RoomBrowsingActivity a = launchWithThreeRooms();
        MaterialButton reset = a.findViewById(R.id.btnResetSearch);
        assertEquals(View.GONE, reset.getVisibility());
        assertEquals(0f, reset.getLetterSpacing(), 0.0001f);
        setDates(a, 2, 3);
        assertEquals(View.VISIBLE, reset.getVisibility());
    }

    @Test
    public void datePickers_refuseDatesBeforeTheEarliestCheckIn_andACheckOutOnOrBeforeCheckIn() throws Exception {
        RoomBrowsingActivity a = launchWithThreeRooms();
        a.findViewById(R.id.etCheckIn).performClick();
        idle();
        androidx.fragment.app.Fragment inPicker = a.getSupportFragmentManager().findFragmentByTag("CHECK_IN_PICKER");
        assertNotNull(inPicker);
        CalendarConstraints inConstraints = inPicker.getArguments().getParcelable("CALENDAR_CONSTRAINTS_KEY");
        long earliest = com.example.velocitysuites.CheckInWindow.earliestUtcMillis();
        assertFalse("the day before the earliest check-in is not selectable", inConstraints.getDateValidator().isValid(earliest - 86_400_000L));
        assertTrue(inConstraints.getDateValidator().isValid(earliest));
        // a second tap while the picker is up must not stack another
        a.findViewById(R.id.etCheckIn).performClick();
        idle();
        ((com.google.android.material.datepicker.MaterialDatePicker<?>) inPicker).dismiss();
        idle();

        setDates(a, 3, 5);
        a.findViewById(R.id.etCheckOut).performClick();
        idle();
        androidx.fragment.app.Fragment outPicker = a.getSupportFragmentManager().findFragmentByTag("CHECK_OUT_PICKER");
        assertNotNull(outPicker);
        CalendarConstraints outConstraints = outPicker.getArguments().getParcelable("CALENDAR_CONSTRAINTS_KEY");
        long checkIn = com.example.velocitysuites.CheckInWindow.today().plusDays(3).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
        assertFalse("check-out on the check-in day", outConstraints.getDateValidator().isValid(checkIn));
        assertTrue("the day after is fine", outConstraints.getDateValidator().isValid(checkIn + 86_400_000L));
    }

    // ---- results bar, list and empty states ----

    @Test
    public void resultsBar_isOneAlignedRow_boldPrimaryCount_sortAndFiltersSentenceCase() throws Exception {
        RoomBrowsingActivity a = launchWithThreeRooms();
        TextView count = a.findViewById(R.id.tvResultsCount);
        assertEquals("3 rooms", count.getText().toString());
        assertEquals("not red", app.getColor(R.color.velocity_text_primary), count.getCurrentTextColor());
        assertEquals("Sort: Recommended", text(a, R.id.btnSort));
        assertEquals("Filters", text(a, R.id.btnFilters));
        for (int id : new int[]{R.id.btnSort, R.id.btnFilters}) {
            MaterialButton b = a.findViewById(id);
            assertFalse(b.isAllCaps());
            assertEquals(0f, b.getLetterSpacing(), 0.0001f);
        }
    }

    @Test
    public void filtersAndSortCombine_countBadge_chips_andRemovingAChip() throws Exception {
        RoomBrowsingActivity a = launchWithThreeRooms();
        set(a, "selectedCapacity", 2); // 3-4 guests -> only the Suite
        invoke(a, "applyControlChange");
        assertEquals("1 room", text(a, R.id.tvResultsCount));
        assertEquals("Filters · 1", text(a, R.id.btnFilters));
        com.google.android.material.chip.ChipGroup chips = a.findViewById(R.id.chipsActiveFilters);
        assertEquals(1, chips.getChildCount());
        assertEquals("3–4 guests", ((TextView) chips.getChildAt(0)).getText().toString());

        set(a, "selectedCapacity", 0);
        set(a, "selectedMaxPrice", 3500f);
        set(a, "currentSortMode", "PriceHigh");
        invoke(a, "applyControlChange");
        List<Room> shown = get(a, "filteredRooms");
        assertEquals(2, shown.size());
        assertTrue("sorted high to low", shown.get(0).getPricePerNight() > shown.get(1).getPricePerNight());
        assertEquals("Sort: Price: High to Low", text(a, R.id.btnSort));
        assertEquals("Up to ₱3,500", ((TextView) ((com.google.android.material.chip.ChipGroup) a.findViewById(R.id.chipsActiveFilters)).getChildAt(0)).getText().toString());

        ((com.google.android.material.chip.Chip) ((com.google.android.material.chip.ChipGroup) a.findViewById(R.id.chipsActiveFilters)).getChildAt(0)).performCloseIconClick();
        idle();
        assertEquals("the chip's x removed the filter", "3 rooms", text(a, R.id.tvResultsCount));
        assertEquals(View.GONE, a.findViewById(R.id.layoutActiveFilters).getVisibility());
    }

    @Test
    public void emptyState_wordingFollowsDatesFiltersOrBoth_andSitsBelowTheResultsBar() throws Exception {
        RoomBrowsingActivity a = launchWithThreeRooms();
        // filters only
        set(a, "selectedCapacity", 3); // 5+ guests -> only the Family room; make it none
        set(a, "selectedRoomTypeFilter", "Suite");
        invoke(a, "applyControlChange");
        View empty = a.findViewById(R.id.noResultsView);
        assertEquals(View.VISIBLE, empty.getVisibility());
        assertEquals("No rooms match your filters", text(a, R.id.emptyTitle));
        assertEquals("Clear filters", text(a, R.id.btnEmptyAction));
        assertEquals(View.GONE, a.findViewById(R.id.btnEmptyAction2).getVisibility());

        // both
        setDates(a, 2, 4);
        invoke(a, "applyControlChange");
        assertEquals("No rooms match your dates and filters", text(a, R.id.emptyTitle));
        assertEquals("Clear filters", text(a, R.id.btnEmptyAction));
        assertEquals("Change dates", text(a, R.id.btnEmptyAction2));
        assertEquals(View.VISIBLE, a.findViewById(R.id.btnEmptyAction2).getVisibility());

        // dates only
        set(a, "selectedCapacity", 0);
        set(a, "selectedRoomTypeFilter", "All");
        answerNothing(a);
        assertEquals("No rooms for these dates", text(a, R.id.emptyTitle));
        assertEquals("Change dates", text(a, R.id.btnEmptyAction));

        for (int id : new int[]{R.id.btnEmptyAction, R.id.btnEmptyAction2}) {
            MaterialButton b = a.findViewById(id);
            assertEquals(0f, b.getLetterSpacing(), 0.0001f);
        }
        // the empty illustration is the bed, not the image-search icon
        assertNotNull(a.findViewById(R.id.emptyIcon));
    }

    private void answerNothing(RoomBrowsingActivity a) throws Exception {
        set(a, "allRooms", new java.util.ArrayList<Room>());
        invoke(a, "applyControlChange");
    }

    private void assertEmptyStateBelowTheResultsBar() throws Exception {
        RoomBrowsingActivity a = launchWithThreeRooms();
        set(a, "selectedCapacity", 3);
        set(a, "selectedRoomTypeFilter", "Suite");
        invoke(a, "applyControlChange");
        layOut(a, app);
        View root = a.getWindow().getDecorView();
        View bar = a.findViewById(R.id.stickyControlBar);
        View empty = a.findViewById(R.id.noResultsView);
        Rect barBox = LayoutHarness.boundsIn(root, bar);
        Rect emptyBox = LayoutHarness.boundsIn(root, empty);
        assertTrue("the empty view starts below the results bar (bar bottom " + barBox.bottom + ", empty top " + emptyBox.top + ")", emptyBox.top >= barBox.bottom - 1);
        assertFalse(LayoutHarness.overlaps(root, a.findViewById(R.id.tvResultsCount), a.findViewById(R.id.emptyIconContainer)));
        assertFalse(LayoutHarness.overlaps(root, a.findViewById(R.id.btnSort), a.findViewById(R.id.emptyIconContainer)));
        assertFalse(LayoutHarness.overlaps(root, a.findViewById(R.id.btnFilters), a.findViewById(R.id.emptyIconContainer)));
        assertEquals("no translation or offset", 0f, empty.getTranslationY(), 0.01f);
        androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams lp = (androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams) empty.getLayoutParams();
        assertNotNull(lp.getBehavior());
    }

    @Test
    public void emptyState_neverOverlapsTheResultsBar_light360() throws Exception {
        assertEmptyStateBelowTheResultsBar();
    }

    @Test
    @Config(sdk = 35, qualifiers = "w393dp-h851dp-night-xxhdpi", application = TestApplication.class)
    public void emptyState_neverOverlapsTheResultsBar_dark393() throws Exception {
        assertEmptyStateBelowTheResultsBar();
    }

    // ---- the Filters sheet ----

    private void assertSheetOpaqueAndReadable() throws Exception {
        RoomBrowsingActivity a = launchWithThreeRooms();
        Dialog dialog = openSheet(a);
        View sheet = dialog.findViewById(R.id.roomFilterSheet);
        assertTrue("an opaque colour, not a translucent one", sheet.getBackground() instanceof ColorDrawable);
        int color = ((ColorDrawable) sheet.getBackground()).getColor();
        assertEquals("fully opaque", 255, android.graphics.Color.alpha(color));
        assertEquals(app.getColor(R.color.velocity_surface_elevated), color);
        assertEquals("Filter rooms", ((TextView) dialog.findViewById(R.id.tvFilterTitle)).getText().toString());

        MaterialButtonToggleGroup group = dialog.findViewById(R.id.capacityChipGroup);
        int[] checked = {android.R.attr.state_checked, android.R.attr.state_enabled};
        int[] unchecked = {-android.R.attr.state_checked, android.R.attr.state_enabled};
        for (int id : new int[]{R.id.chipAnyCap, R.id.chip12, R.id.chip34, R.id.chip5plus}) {
            MaterialButton chip = group.findViewById(id);
            for (int[] state : new int[][]{checked, unchecked}) {
                int text = chip.getTextColors().getColorForState(state, 0);
                int bg = chip.getBackgroundTintList().getColorForState(state, 0);
                assertTrue("\"" + chip.getText() + "\" readable (" + (state == checked ? "selected" : "unselected") + "): text " + Integer.toHexString(text) + " on " + Integer.toHexString(bg),
                        androidx.core.graphics.ColorUtils.calculateContrast(text, bg) >= 4.5);
            }
            assertEquals("one line", 1, chip.getLineCount() == 0 ? 1 : chip.getLineCount());
        }
        assertEquals(View.VISIBLE, dialog.findViewById(R.id.btnCloseFilters).getVisibility());
    }

    @Test
    public void filterSheet_isOpaque_andItsGuestChipsAreReadable_light() throws Exception {
        assertSheetOpaqueAndReadable();
    }

    @Test
    @Config(sdk = 35, qualifiers = "w360dp-h800dp-night-xxhdpi", application = TestApplication.class)
    public void filterSheet_isOpaque_andItsGuestChipsAreReadable_dark() throws Exception {
        assertSheetOpaqueAndReadable();
    }

    @Test
    public void filterSheet_labelsTheTwoDropdowns_listsEveryOption_andTheSliderUsesTheRealPrices() throws Exception {
        RoomBrowsingActivity a = launchWithThreeRooms();
        Dialog dialog = openSheet(a);
        TextInputLayout typeLayout = dialog.findViewById(R.id.tilRoomTypeFilter);
        TextInputLayout statusLayout = dialog.findViewById(R.id.tilStatusFilter);
        assertEquals("Room type", typeLayout.getHint().toString());
        assertEquals("Availability", statusLayout.getHint().toString());
        AutoCompleteTextView types = dialog.findViewById(R.id.dropdownRoomType);
        assertEquals("All types", types.getText().toString());
        assertEquals("All types + the three real types", 4, types.getAdapter().getCount());
        AutoCompleteTextView status = dialog.findViewById(R.id.dropdownStatus);
        assertEquals(4, status.getAdapter().getCount());

        RangeSlider slider = dialog.findViewById(R.id.priceSlider);
        assertEquals("lowest room price rounded down", 1800f, slider.getValueFrom(), 0.01f);
        assertEquals("highest room price rounded up", 4000f, slider.getValueTo(), 0.01f);
        assertEquals("₱1,800 – ₱4,000", ((TextView) dialog.findViewById(R.id.tvPriceLabel)).getText().toString());
        assertEquals("live count on the button", "Show 3 rooms", ((TextView) dialog.findViewById(R.id.btnApplyFilters)).getText().toString());
    }

    @Test
    public void filterSheet_liveCountFollowsThePendingChoices_andApplyWritesThem() throws Exception {
        RoomBrowsingActivity a = launchWithThreeRooms();
        Dialog dialog = openSheet(a);
        dialog.findViewById(R.id.chip34).performClick();
        idle();
        assertEquals("Show 1 room", ((TextView) dialog.findViewById(R.id.btnApplyFilters)).getText().toString());
        assertEquals("nothing applied until Apply", "3 rooms", text(a, R.id.tvResultsCount));
        dialog.findViewById(R.id.btnApplyFilters).performClick();
        idle();
        assertEquals("1 room", text(a, R.id.tvResultsCount));
        assertEquals("Filters · 1", text(a, R.id.btnFilters));
    }

    @Test
    public void filterSheet_opensOnce_evenOnARapidDoubleTap() throws Exception {
        RoomBrowsingActivity a = launchWithThreeRooms();
        a.findViewById(R.id.btnFilters).performClick();
        a.findViewById(R.id.btnFilters).performClick();
        idle();
        int sheets = 0;
        for (Dialog d : ShadowDialog.getShownDialogs()) if (d.findViewById(R.id.roomFilterSheet) != null && d.isShowing()) sheets++;
        assertEquals(1, sheets);
    }

    @Test
    public void rotationWithTheSheetOpen_doesNotCrash_andKeepsTheFilters() throws Exception {
        ActivityController<RoomBrowsingActivity> c = launch();
        answerRooms(type(1, "Deluxe", "1850.00", 2, 5), type(2, "Suite", "3000.00", 4, 5));
        openSheet(c.get());
        set(c.get(), "selectedCapacity", 2);
        c.recreate();
        idle();
        assertNotNull(c.get());
        assertEquals("the guests filter survived", 2, (int) get(c.get(), "selectedCapacity"));
    }

    // ---- layout ----

    private void assertSearchAreaFits() throws Exception {
        RoomBrowsingActivity a = launchWithThreeRooms();
        setDates(a, 2, 4);
        set(a, "checkInDate", day(com.example.velocitysuites.CheckInWindow.today().plusDays(2)));
        ((TextView) a.findViewById(R.id.etCheckIn)).setText("Wed, Oct 21, 2026");
        ((TextView) a.findViewById(R.id.etCheckOut)).setText("Fri, Oct 23, 2026");
        layOut(a, app);
        View root = a.getWindow().getDecorView();
        String[] names = {"etCheckIn", "etCheckOut"};
        for (int id : new int[]{R.id.etCheckIn, R.id.etCheckOut}) {
            TextView v = a.findViewById(id);
            assertEquals("single-line value", 1, v.getLineCount());
            assertEquals("not ellipsized", 0, v.getLayout().getEllipsisCount(0));
        }
        for (int id : new int[]{R.id.checkInLayout, R.id.checkOutLayout}) {
            TextInputLayout l = a.findViewById(id);
            assertTrue("non-empty floating label", l.getHint() != null && l.getHint().length() > 0);
            TextView helper = l.findViewById(com.google.android.material.R.id.textinput_helper_text);
            assertNotNull(helper);
            assertEquals("helper on one line: " + helper.getText(), 1, helper.getLineCount());
            Rect box = LayoutHarness.boundsIn(root, l);
            assertTrue(box.right <= app.getResources().getDisplayMetrics().widthPixels);
        }
        assertEquals("one-line title", 1, ((TextView) a.findViewById(R.id.tvSearchHeader)).getLineCount());
        assertEquals("one-line description", 1, ((TextView) a.findViewById(R.id.screenDescription)).getLineCount());
        assertFalse("check-in / check-out do not overlap", LayoutHarness.overlaps(root, a.findViewById(R.id.checkInLayout), a.findViewById(R.id.checkOutLayout)));
        assertEquals("View availability on one line", 1, ((TextView) a.findViewById(R.id.btnSearchAvailability)).getLineCount());
        TextView count = a.findViewById(R.id.tvResultsCount);
        MaterialButton sort = a.findViewById(R.id.btnSort);
        MaterialButton filters = a.findViewById(R.id.btnFilters);
        assertEquals(1, count.getLineCount());
        assertEquals("sort on one line", 1, sort.getLineCount());
        assertEquals("filters on one line", 1, filters.getLineCount());
        assertFalse(LayoutHarness.overlaps(root, count, sort));
        assertFalse(LayoutHarness.overlaps(root, sort, filters));
        assertTrue(LayoutHarness.boundsIn(root, filters).right <= app.getResources().getDisplayMetrics().widthPixels);
        // the same border for both date fields when not focused
        Field f = TextInputLayout.class.getDeclaredField("defaultStrokeColor");
        f.setAccessible(true);
        assertEquals(f.getInt(a.findViewById(R.id.checkInLayout)), f.getInt(a.findViewById(R.id.checkOutLayout)));
        assertNotNull(names);
    }

    @Test
    public void searchAreaAndResultsBarFit_360_light() throws Exception {
        assertSearchAreaFits();
    }

    @Test
    @Config(sdk = 35, qualifiers = "w360dp-h800dp-night-xxhdpi", application = TestApplication.class)
    public void searchAreaAndResultsBarFit_360_dark() throws Exception {
        assertSearchAreaFits();
    }

    @Test
    @Config(sdk = 35, qualifiers = "w393dp-h851dp-xxhdpi", application = TestApplication.class)
    public void searchAreaAndResultsBarFit_393_light() throws Exception {
        assertSearchAreaFits();
    }

    @Test
    @Config(sdk = 35, qualifiers = "w393dp-h851dp-night-xxhdpi", application = TestApplication.class)
    public void searchAreaAndResultsBarFit_393_dark() throws Exception {
        assertSearchAreaFits();
    }

    // ---- errors ----

    @Test
    public void aNetworkErrorShowsTheInlineErrorWithRetry_notASpinnerForever() throws Exception {
        RoomBrowsingActivity a = launch().get();
        api.<RoomsResponse>last("getRoomsFresh").offline();
        idle();
        assertEquals(View.VISIBLE, a.findViewById(R.id.layoutRoomError).getVisibility());
        assertEquals(View.GONE, a.findViewById(R.id.loadingOverlay).getVisibility());
        assertEquals("Try again", ((TextView) a.findViewById(R.id.layoutRoomError).findViewById(R.id.btnEmptyAction)).getText().toString());
    }

    @Test
    public void aRoomWithMissingFields_doesNotCrashFilteringOrTheCard() throws Exception {
        RoomBrowsingActivity a = launch().get();
        RoomTypeDto odd = type(9, null, null, 0, 3);
        odd.description = null;
        odd.amenities = null;
        answerRooms(odd);
        RecyclerView rv = a.findViewById(R.id.rvRooms);
        assertEquals(1, rv.getAdapter().getItemCount());
        set(a, "selectedRoomTypeFilter", "Deluxe");
        invoke(a, "applyControlChange");
        assertEquals("0 rooms", text(a, R.id.tvResultsCount));
    }

    // ---- the compact room card, the results bar, the details screen ----

    private View firstRoomCard(RoomBrowsingActivity a) {
        RecyclerView rv = a.findViewById(R.id.rvRooms);
        androidx.recyclerview.widget.RecyclerView.ViewHolder h = null;
        for (int i = 0; i < rv.getAdapter().getItemCount() && h == null; i++) h = rv.findViewHolderForAdapterPosition(i);
        assertNotNull("a card is laid out", h);
        return h.itemView;
    }

    @Test
    public void roomCard_showsTheNameOnce_bigNameAndPrice_noDescriptionNoAmenityChips_andCompactActions() throws Exception {
        RoomBrowsingActivity a = launchWithThreeRooms();
        View card = firstRoomCard(a);
        TextView name = card.findViewById(R.id.roomName);
        TextView price = card.findViewById(R.id.roomPrice);
        assertEquals(20f, name.getTextSize() / app.getResources().getDisplayMetrics().scaledDensity, 0.6f);
        assertEquals(20f, price.getTextSize() / app.getResources().getDisplayMetrics().scaledDensity, 0.6f);
        assertTrue(price.getText().toString().contains("/ night"));
        // The name is the room type here: the type chip must not repeat it.
        assertEquals(View.GONE, card.findViewById(R.id.roomTypeText).getVisibility());
        assertEquals("no description view on the card", 0, app.getResources().getIdentifier("roomDescription", "id", app.getPackageName()));
        assertEquals("no amenity chips on the card", 0, app.getResources().getIdentifier("amenitiesChipGroup", "id", app.getPackageName()));
        assertEquals("Reserve", ((TextView) card.findViewById(R.id.btnReserveNow)).getText().toString());
        assertEquals("Book now", ((TextView) card.findViewById(R.id.btnQuickBook)).getText().toString());
        assertEquals("View details", ((TextView) card.findViewById(R.id.btnViewDetails)).getText().toString());
        assertEquals(0f, ((TextView) card.findViewById(R.id.btnQuickBook)).getLetterSpacing(), 0f);
        assertEquals("No refund", ((TextView) card.findViewById(R.id.tvNoRefundBadge)).getText().toString());
    }

    @Test
    public void roomCard_plusIsDisabledAtTheAvailableCount() throws Exception {
        RoomBrowsingActivity a = launch().get();
        answerRooms(type(3, "Family", "3999.00", 6, 1));
        View card = firstRoomCard(a);
        View plus = card.findViewById(R.id.btnRoomQtyPlus);
        assertTrue(plus.isEnabled());
        plus.performClick();
        idle();
        assertFalse("1 left: + is disabled at 1", firstRoomCard(a).findViewById(R.id.btnRoomQtyPlus).isEnabled());
    }

    @Test
    public void resultsBar_hasNoAvailableRoomsHeading_andTheDatesAreSummarisedOnce() throws Exception {
        RoomBrowsingActivity a = launchWithThreeRooms();
        setDates(a, 2, 3);
        RecyclerView rv = a.findViewById(R.id.rvRooms);
        for (int i = 0; i < rv.getAdapter().getItemCount(); i++) {
            androidx.recyclerview.widget.RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(i);
            if (h == null) continue;
            TextView header = h.itemView.findViewById(R.id.tvSectionHeader);
            assertNull("no AVAILABLE ROOMS heading", header);
        }
        TextView pinned = a.findViewById(R.id.tvPinnedDates);
        assertTrue(pinned.getText().toString(), pinned.getText().toString().matches(".+ → .+ · 1 night"));
        assertEquals("3 rooms", text(a, R.id.tvResultsCount));
        assertFalse(((TextView) a.findViewById(R.id.btnSort)).isAllCaps());
    }
}
