package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.R;
import com.example.velocitysuites.Room;
import com.example.velocitysuites.RoomAdapter;
import com.example.velocitysuites.RoomAmenity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * The room card is the main thing on the landing page (and on Room Browsing), so it has to hold up on a
 * 320dp phone and with a large system font. The compact layout puts a 140dp photo beside the details, which
 * is fine on a normal phone and unreadable on a small one; those get the roomy arrangement (photo on top,
 * details below, every action a full-width row). Checked on the real card, real theme, real measure pass.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class RoomCardLayoutTest {

    private static Context context(int widthDp, float fontScale) {
        Context app = ApplicationProvider.getApplicationContext();
        Configuration config = new Configuration(app.getResources().getConfiguration());
        config.fontScale = fontScale;
        config.screenWidthDp = widthDp;
        return new ContextThemeWrapper(app.createConfigurationContext(config), R.style.Theme_VelocitySuites);
    }

    private static Room room(String id, int availableCount) {
        List<RoomAmenity> amenities = new ArrayList<>();
        amenities.add(new RoomAmenity("Wi-Fi", "General", "", RoomAmenity.PRICING_COMPLIMENTARY, "0"));
        amenities.add(new RoomAmenity("Air Conditioning", "General", "", RoomAmenity.PRICING_COMPLIMENTARY, "0"));
        Room room = new Room(id, "Deluxe Room with City View", "Deluxe", 2, 1800.0, "About", 0, availableCount > 0,
                amenities, "1 Queen Bed + 1 Sofa Bed", "24 sqm", "");
        room.setAvailableCount(availableCount);
        return room;
    }

    /** One bound card, laid out the way a list row of this phone's width would be. */
    private static View card(Room room, int widthDp, float fontScale) {
        Context ctx = context(widthDp, fontScale);
        FrameLayout parent = LayoutHarness.parentOfWidth(ctx, widthDp);
        RoomAdapter adapter = new RoomAdapter(Collections.singletonList(room), new RoomAdapter.OnRoomClickListener() {
            @Override public void onRoomClick(Room r) { }
            @Override public void onViewDetailsClick(Room r) { }
            @Override public void onBookNowClick(Room r) { }
        }, false, new HashMap<>());
        RecyclerView.ViewHolder holder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(0));
        adapter.onBindViewHolder(holder, 0);
        LayoutHarness.layoutAtWidth(holder.itemView, ctx, widthDp);
        return holder.itemView;
    }

    private static View find(View card, int id) {
        View v = card.findViewById(id);
        assertNotNull("card is missing " + card.getResources().getResourceEntryName(id), v);
        return v;
    }

    private static Rect bounds(View card, int id) {
        return LayoutHarness.boundsIn(card, find(card, id));
    }

    // ---- compact vs roomy ----

    @Test
    public void aNormalPhoneWithANormalFont_keepsTheCompactSideBySideCard() {
        View card = card(room("1", 5), 360, 1.0f);

        Rect image = bounds(card, R.id.roomImageCard);
        Rect info = bounds(card, R.id.roomInfoColumn);
        assertTrue("details sit to the right of the photo", info.left >= image.right);
        assertTrue("and level with it, not below it", info.top < image.bottom);
        Rect reserve = bounds(card, R.id.btnReserveNow);
        Rect book = bounds(card, R.id.btnQuickBook);
        assertEquals("Reserve and Book share a row", reserve.top, book.top);
        assertTrue(book.left >= reserve.right);
        assertTrue("View Details is the row below", bounds(card, R.id.btnViewDetails).top >= reserve.bottom);
    }

    @Test
    public void aLargeFont_getsTheRoomyCard() {
        for (float scale : new float[]{1.3f, 1.5f, 2.0f}) {
            View card = card(room("1", 5), 360, scale);
            assertRoomy(card, "font scale " + scale);
        }
    }

    @Test
    public void aNarrowPhone_getsTheRoomyCardEvenWithANormalFont() {
        assertRoomy(card(room("1", 5), 320, 1.0f), "320dp");
        assertRoomy(card(room("1", 5), 339, 1.0f), "339dp");
    }

    @Test
    public void theBoundaries_areExactlyWhereTheyAreDocumented() {
        // narrow: below 340dp; large: from 1.3x
        assertTrue(RoomAdapter.needsRoomyLayout(config(339, 1.0f)));
        assertFalse(RoomAdapter.needsRoomyLayout(config(340, 1.0f)));
        assertFalse(RoomAdapter.needsRoomyLayout(config(360, 1.29f)));
        assertTrue(RoomAdapter.needsRoomyLayout(config(360, 1.3f)));
        assertTrue(RoomAdapter.needsRoomyLayout(config(411, 2.0f)));
        assertFalse("an unknown width is not treated as narrow", RoomAdapter.needsRoomyLayout(config(0, 1.0f)));
    }

    private static Configuration config(int widthDp, float fontScale) {
        Configuration c = new Configuration();
        c.screenWidthDp = widthDp;
        c.fontScale = fontScale;
        return c;
    }

    private static void assertRoomy(View card, String when) {
        Rect topRow = bounds(card, R.id.roomCardTopRow);
        Rect image = bounds(card, R.id.roomImageCard);
        Rect info = bounds(card, R.id.roomInfoColumn);
        assertTrue(when + ": photo spans the card (" + image.width() + " of " + topRow.width() + ")", image.width() >= topRow.width() - 2);
        assertTrue(when + ": details are below the photo", info.top >= image.bottom);
        assertTrue(when + ": details use the full width (" + info.width() + " of " + topRow.width() + ")", info.width() >= topRow.width() - 2);

        Rect reserve = bounds(card, R.id.btnReserveNow);
        Rect book = bounds(card, R.id.btnQuickBook);
        Rect details = bounds(card, R.id.btnViewDetails);
        assertTrue(when + ": Reserve is above Book", book.top >= reserve.bottom);
        assertTrue(when + ": Book is above View Details", details.top >= book.bottom);
        assertEquals(when + ": all three actions have the same width", reserve.width(), book.width());
        assertEquals(when + ": all three actions have the same width", book.width(), details.width());
        assertTrue(when + ": and that is the full width of the card", reserve.width() >= topRow.width() - 2);
    }

    // ---- touch targets and text ----

    @Test
    public void everyActionIsAtLeast48dpTall_inEveryArrangement() {
        for (int[] setup : new int[][]{{360, 10}, {320, 10}, {360, 20}}) {
            float scale = setup[1] / 10f;
            View card = card(room("1", 5), setup[0], scale);
            float density = card.getResources().getDisplayMetrics().density;
            int min = Math.round(48 * density);
            for (int id : new int[]{R.id.btnReserveNow, R.id.btnQuickBook, R.id.btnViewDetails}) {
                View v = find(card, id);
                assertTrue(card.getResources().getResourceEntryName(id) + " is " + v.getHeight() + "px tall at " + setup[0] + "dp x" + scale
                        + ", needs " + min, v.getHeight() >= min);
            }
            // The - and + buttons are 40dp squares inside a row that is itself at least 48dp tall.
            assertTrue("the quantity row is at least 48dp tall", find(card, R.id.cardRoomQtyStepper).getHeight() >= min);
            assertTrue("the + button is at least 40dp", find(card, R.id.btnRoomQtyPlus).getHeight() >= Math.round(40 * density));
            assertTrue("the - button is at least 40dp", find(card, R.id.btnRoomQtyMinus).getHeight() >= Math.round(40 * density));
        }
    }

    @Test
    public void everyTextInTheCardIsAtLeast12sp_exceptTheTagOnThePhoto() {
        View card = card(room("1", 1), 360, 1.0f);
        List<String> small = new ArrayList<>();
        collectSmallText(card, card.getResources().getDisplayMetrics().density, small);
        assertTrue("text under 12sp: " + small, small.isEmpty());
    }

    private static void collectSmallText(View v, float density, List<String> out) {
        if (v instanceof TextView && v.getId() != R.id.roomStatusBadge) {
            TextView t = (TextView) v;
            float sp = t.getTextSize() / density;
            if (t.getText() != null && t.getText().length() > 0 && sp < 12f && v.getVisibility() == View.VISIBLE) {
                out.add(t.getText() + " (" + sp + "sp)");
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collectSmallText(g.getChildAt(i), density, out);
        }
    }

    // ---- the availability tag ----

    private static double luminance(int rgb) {
        double[] c = {((rgb >> 16) & 0xFF) / 255.0, ((rgb >> 8) & 0xFF) / 255.0, (rgb & 0xFF) / 255.0};
        for (int i = 0; i < 3; i++) c[i] = c[i] <= 0.03928 ? c[i] / 12.92 : Math.pow((c[i] + 0.055) / 1.055, 2.4);
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
    }

    private static double contrast(int a, int b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static void assertTagReadable(Room room, String label) {
        View card = card(room, 360, 1.0f);
        TextView tag = (TextView) find(card, R.id.roomStatusBadge);
        assertTrue(label + ": the tag has a shape fill", tag.getBackground() instanceof GradientDrawable);
        int fill = ((GradientDrawable) tag.getBackground()).getColor().getDefaultColor();
        int text = tag.getCurrentTextColor();
        double ratio = contrast(text | 0xFF000000, fill | 0xFF000000);
        assertTrue(String.format("%s: tag text %06X on fill %06X is %.2f:1, below 4.5:1", label, text & 0xFFFFFF, fill & 0xFFFFFF, ratio), ratio >= 4.5);
    }

    @Test
    public void theAvailabilityTagIsReadable_forEveryAvailabilityState() {
        assertTagReadable(room("1", 5), "available");   // 3 or more left
        assertTagReadable(room("2", 2), "limited");     // 1-2 left
        assertTagReadable(room("3", 0), "unavailable"); // none left
    }
}
