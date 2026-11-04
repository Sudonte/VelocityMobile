package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.R;
import com.example.velocitysuites.Room;
import com.example.velocitysuites.RoomAdapter;
import com.example.velocitysuites.RoomAvailabilityReconciler;
import com.example.velocitysuites.network.ApiMapper;
import com.example.velocitysuites.network.dto.RoomOptionDto;
import com.example.velocitysuites.network.dto.RoomSelectionDto;
import com.example.velocitysuites.network.dto.RoomTypeDto;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Other Deluxe rooms": rooms of a type with their own price or capacity are options. The type's card shows the standard option; an
 * "Other Deluxe rooms >" row opens the others, each with its capacity, price, availability and its own stepper limited by its own rooms.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class RoomOptionsTest {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private static RoomTypeDto deluxeDto(boolean withOption) {
        RoomTypeDto dto = new RoomTypeDto();
        dto.id = 5;
        dto.name = "Deluxe";
        dto.capacity = 2;
        dto.rate = "3500.00";
        dto.available_count = 3;
        if (withOption) {
            RoomOptionDto o = new RoomOptionDto();
            o.option_key = "r4200c4";
            o.price = 4200;
            o.capacity = 4;
            o.available_count = 2;
            dto.other_options = Collections.singletonList(o);
        }
        return dto;
    }

    private static Context context() {
        return new ContextThemeWrapper((Context) ApplicationProvider.getApplicationContext(), R.style.Theme_VelocitySuites);
    }

    @Test
    public void theMapperMakesEachOtherOptionARoomOfItsOwn() {
        Room deluxe = ApiMapper.toRoom(deluxeDto(true));
        assertEquals("5", deluxe.getId());
        assertEquals(3500.0, deluxe.getPricePerNight(), 0.001);
        assertEquals(1, deluxe.getOtherOptions().size());
        Room other = deluxe.getOtherOptions().get(0);
        assertEquals("5:r4200c4", other.getId());
        assertEquals("r4200c4", other.getOptionKey());
        assertTrue(other.isOtherOption());
        assertEquals(4200.0, other.getPricePerNight(), 0.001);
        assertEquals(4, other.getCapacity());
        assertEquals(2, other.getAvailableCount());
        assertEquals("Deluxe (up to 4 guests)", other.getName());
        assertEquals(5L, other.getRoomTypeId());
        assertEquals("the API knows it by its room type id", 5L, other.apiTypeId());
    }

    @Test
    public void aTypeWithoutOtherRoomsHasNoOptions_andAnOlderServerIsFine() {
        assertTrue(ApiMapper.toRoom(deluxeDto(false)).getOtherOptions().isEmpty());
        assertNull(ApiMapper.toRoom(deluxeDto(false)).getOptionKey());
    }

    @Test
    public void theRequestNamesTheOption_andTheStandardOptionSendsNone() {
        Room deluxe = ApiMapper.toRoom(deluxeDto(true));
        Room other = deluxe.getOtherOptions().get(0);
        RoomSelectionDto std = new RoomSelectionDto(deluxe.apiTypeId(), 1, deluxe.getOptionKey());
        RoomSelectionDto opt = new RoomSelectionDto(other.apiTypeId(), 2, other.getOptionKey());
        assertEquals(5L, std.room_type_id);
        assertNull(std.option_key);
        assertEquals(5L, opt.room_type_id);
        assertEquals("r4200c4", opt.option_key);
        assertEquals(2, opt.quantity);
    }

    @Test
    public void availabilityLooksThroughTheOptions_andTrimsEachOptionToItsOwnRooms() {
        Room deluxe = ApiMapper.toRoom(deluxeDto(true));
        Room other = deluxe.getOtherOptions().get(0);
        List<Room> selected = new ArrayList<>();
        selected.add(deluxe);
        selected.add(other);
        selected.add(other);
        selected.add(other); // 3 of the "other" rooms, only 2 are free
        List<RoomAvailabilityReconciler.Shortfall> cut = RoomAvailabilityReconciler.reconcile(selected, Collections.singletonList(deluxe));
        assertEquals(1, cut.size());
        assertEquals("5:r4200c4", cut.get(0).room.getId());
        assertEquals(2, cut.get(0).available);
        assertEquals("the standard room and two others stay", 3, selected.size());
    }

    private static RoomAdapter adapter(Room deluxe, Map<String, Integer> cart, int[] changes) {
        return new RoomAdapter(Collections.singletonList(deluxe), new RoomAdapter.OnRoomClickListener() {
            @Override public void onRoomClick(Room r) { }
            @Override public void onViewDetailsClick(Room r) { }
            @Override public void onBookNowClick(Room r) { }
            @Override public void onRoomSelectionChanged() { changes[0]++; }
        }, false, cart);
    }

    private static RecyclerView.ViewHolder bind(RoomAdapter a, FrameLayout parent, int position) {
        RecyclerView.ViewHolder h = a.onCreateViewHolder(parent, a.getItemViewType(position));
        a.onBindViewHolder(h, position);
        return h;
    }

    @Test
    public void theCardGetsAnOtherRoomsRow_thatOpensTheOptionWithItsOwnStepper() {
        Room deluxe = ApiMapper.toRoom(deluxeDto(true));
        Map<String, Integer> cart = new LinkedHashMap<>();
        int[] changes = {0};
        RoomAdapter a = adapter(deluxe, cart, changes);
        FrameLayout parent = LayoutHarness.parentOfWidth(context(), 360);

        assertEquals("the card and the closed 'Other Deluxe rooms' row", 2, a.getItemCount());
        RecyclerView.ViewHolder toggle = bind(a, parent, 1);
        assertEquals("Other Deluxe rooms", ((TextView) toggle.itemView.findViewById(R.id.tvOtherToggle)).getText().toString());

        toggle.itemView.performClick();
        assertEquals("the option row appears", 3, a.getItemCount());
        RecyclerView.ViewHolder opt = bind(a, parent, 2);
        assertEquals("Up to 4 guests", ((TextView) opt.itemView.findViewById(R.id.tvOptCapacity)).getText().toString());
        assertTrue(((TextView) opt.itemView.findViewById(R.id.tvOptPrice)).getText().toString().contains("4,200.00"));
        assertEquals("2 rooms available", ((TextView) opt.itemView.findViewById(R.id.tvOptAvailability)).getText().toString());

        // its own stepper, limited by ITS rooms (2), not the standard 3
        View plus = opt.itemView.findViewById(R.id.btnOptPlus);
        plus.performClick();
        plus.performClick();
        plus.performClick();
        assertEquals(Integer.valueOf(2), cart.get("5:r4200c4"));
        assertFalse("the standard room is not in the cart", cart.containsKey("5"));
        assertEquals(2, changes[0]);

        toggle.itemView.performClick();
        assertEquals("closing hides the options again", 2, a.getItemCount());
    }

    @Test
    public void aTypeWithoutOtherRoomsHasNoExtraRow() {
        RoomAdapter a = adapter(ApiMapper.toRoom(deluxeDto(false)), new LinkedHashMap<>(), new int[]{0});
        assertEquals(1, a.getItemCount());
    }

    @Test
    public void anOptionAlreadyInTheCartStaysOpen() {
        Room deluxe = ApiMapper.toRoom(deluxeDto(true));
        Map<String, Integer> cart = new LinkedHashMap<>();
        cart.put("5:r4200c4", 1);
        assertEquals(3, adapter(deluxe, cart, new int[]{0}).getItemCount());
    }
}
