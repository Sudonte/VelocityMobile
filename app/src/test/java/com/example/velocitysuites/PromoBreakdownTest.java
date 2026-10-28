package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.network.ApiMapper;
import com.example.velocitysuites.network.dto.PromoInfoDto;
import com.example.velocitysuites.network.dto.PromoOfferDto;
import com.example.velocitysuites.network.dto.RoomTypeDto;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * Promos in the app (1.68.0): a promo is a line of its own next to the ID discount, taken off the room rate of its room type, never
 * subtracted before the front desk verifies it; before that it is listed as "to be verified at check-in" with an estimated saving.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, application = com.example.velocitysuites.ui.TestApplication.class)
public class PromoBreakdownTest {

    private final Context c = ApplicationProvider.getApplicationContext();

    private static PromoOfferDto offer(long id, String title, Long typeId, String typeName, double saving, Double rateAfter, boolean applied) {
        PromoOfferDto d = new PromoOfferDto();
        d.id = id;
        d.title = title;
        d.description = "Stay more, save more.";
        d.discount_type = "percentage";
        d.discount_value = 10.0;
        d.label = "10% off the room rate";
        d.room_type_id = typeId;
        d.room_type_name = typeName;
        d.valid_until = "2026-12-31";
        d.has_money_effect = true;
        d.estimated_saving = saving;
        d.rate_after = rateAfter;
        d.applied = applied;
        d.amenities = Collections.emptyList();
        return d;
    }

    /** Deluxe x1 for 2 nights at 3,500 = 7,000 (the promo lowers the nightly rate to 3,150), 300 of amenities, 500 of the desk's charges. */
    private static Booking booking(PromoInfo info, double idDiscount, double grandTotal) {
        Booking b = new Booking("9", "1", "Deluxe", "Deluxe", "Nov 01, 2026", "Nov 03, 2026", 2, 7300, "Checked-In", "Oct 01, 2026");
        b.setHasBooking(true);
        b.setStatus("Checked-In");
        b.setRooms(Collections.singletonList(new BookingRoom("1", "Deluxe", 1, 3500, 2, 7000, Collections.emptyList(), info.promoDiscount > 0 ? 3150 : -1)));
        b.setAmenityCharge(300);
        b.setAdditionalGuestFee(500);
        b.setPromoInfo(info);
        Booking.PaymentSummary s = new Booking.PaymentSummary(grandTotal, 0, grandTotal, "PENDING", 0, false, idDiscount);
        b.setPaymentSummary(s);
        if (idDiscount > 0) {
            b.setDiscountVerificationStatus("approved");
            b.setIdCardType("PWD");
        }
        return b;
    }

    private static void texts(View v, List<String> out) {
        if (v instanceof TextView && ((TextView) v).getText().length() > 0) out.add(((TextView) v).getText().toString());
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) texts(((ViewGroup) v).getChildAt(i), out);
    }

    private String bill(Booking b) {
        LinearLayout host = new LinearLayout(c);
        PayingForBreakdown breakdown = PayingForBreakdown.forDetails(c, b);
        breakdown.renderRanges(c, host);
        breakdown.renderBill(c, host);
        List<String> t = new ArrayList<>();
        texts(host, t);
        return String.join(" | ", t);
    }

    @Test
    public void anAppliedPromo_isItsOwnLine_betweenTheRoomChargesAndTheAmenities_andTheDiscountSaysWhatItCovered() {
        PromoInfo applied = new PromoInfo(Collections.emptyList(), "Deluxe 10", 700);
        String t = bill(booking(applied, 1320, 5780));

        int rooms = t.indexOf(c.getString(R.string.review_rooms_total_label));
        int promo = t.indexOf("Promo applied: Deluxe 10");
        int amenities = t.indexOf(c.getString(R.string.bill_amenities));
        int discount = t.indexOf("PWD");
        int other = t.indexOf(c.getString(R.string.details_label_additional_guest_fee));
        assertTrue(t, rooms >= 0 && promo > rooms && amenities > promo && discount > amenities && other > discount);
        assertTrue(t, t.contains("−₱700.00"));
        assertTrue(t, t.contains("−₱1,320.00"));
        assertTrue("the ID discount says what it covered: " + t, t.contains("on rooms and amenities"));
        assertTrue("other charges are added at full price: " + t, t.contains("₱500.00"));
        assertTrue("the room line shows the rate before and after the promo: " + t, t.contains("₱3,500.00 → ₱3,150.00 per night (Promo)"));
    }

    @Test
    public void beforeTheDeskVerifiesIt_theAvailablePromosAreANoteWithAnEstimate_nothingIsSubtracted() {
        List<PromoOffer> offers = PromoOffer.listOf(java.util.Arrays.asList(
                offer(1, "Deluxe 10", 1L, "Deluxe", 700, 3150.0, false),
                offer(2, "Everything 5", null, null, 350, 3325.0, false)));
        String t = bill(booking(new PromoInfo(offers, null, 0), 0, 7800));

        assertTrue(t, t.contains(c.getString(R.string.promos_for_your_rooms)));
        assertTrue(t, t.contains("Deluxe 10") && t.contains("Everything 5"));
        assertTrue("an estimated saving, said as a note: " + t, t.contains("Estimated saving ₱700.00. Not subtracted until the front desk verifies it."));
        assertTrue(t, t.contains(c.getString(R.string.promo_to_be_verified_note)));
        assertFalse("no promo line is subtracted yet: " + t, t.contains("Promo applied"));
        assertFalse("the room line keeps its full rate: " + t, t.contains("per night (Promo)"));
    }

    @Test
    public void everyPromoForTheRoomTypesIsListed_andTheAppliedOneIsNotListedAgain() {
        List<PromoOffer> offers = PromoOffer.listOf(java.util.Arrays.asList(
                offer(1, "Applied One", 1L, "Deluxe", 700, 3150.0, true),
                offer(2, "Still Waiting", 1L, "Deluxe", 350, 3325.0, false)));
        PromoInfo info = new PromoInfo(offers, "Applied One", 700);
        assertEquals(1, info.toBeVerified().size());
        String t = bill(booking(info, 0, 6600));
        assertTrue(t, t.contains("Promo applied: Applied One"));
        assertTrue(t, t.contains("Still Waiting"));
    }

    // ---- the room list ----

    @Test
    public void aRoomTypeCarriesItsActivePromos_fromTheServersList() {
        RoomTypeDto dto = new RoomTypeDto();
        dto.id = 1;
        dto.name = "Deluxe";
        dto.rate = "3500.00";
        dto.promos = java.util.Arrays.asList(offer(1, "Deluxe 10", 1L, "Deluxe", 0, 3150.0, false), offer(2, "Weekend", 1L, "Deluxe", 0, 3325.0, false));
        Room room = ApiMapper.toRoom(dto);
        assertEquals(2, room.getPromos().size());
        assertEquals("Deluxe 10", room.getPromos().get(0).title);
        assertEquals(3150.0, room.getPromos().get(0).rateAfter, 0.001);
        assertEquals("10% off the room rate", room.getPromos().get(0).label);

        RoomTypeDto none = new RoomTypeDto();
        none.id = 2;
        none.name = "Standard";
        none.rate = "1500";
        assertTrue("no promo: no badge", ApiMapper.toRoom(none).getPromos().isEmpty());
    }

    @Test
    public void theWizardListsThePromosOfTheChosenRoomTypes_withTheSavingOnThoseRoomsAndNights() {
        RoomTypeDto dto = new RoomTypeDto();
        dto.id = 1;
        dto.name = "Deluxe";
        dto.rate = "3500";
        dto.promos = Collections.singletonList(offer(1, "Deluxe 10", 1L, "Deluxe", 0, 3150.0, false));
        Room deluxe = ApiMapper.toRoom(dto);
        RoomTypeDto plain = new RoomTypeDto();
        plain.id = 2;
        plain.name = "Standard";
        plain.rate = "1500";
        Room standard = ApiMapper.toRoom(plain);

        BookingWizardState state = new BookingWizardState(BookingWizardState.Mode.BOOKING);
        Calendar in = Calendar.getInstance();
        in.set(2026, Calendar.NOVEMBER, 1, 0, 0, 0);
        Calendar out = (Calendar) in.clone();
        out.add(Calendar.DAY_OF_MONTH, 2);
        state.checkIn = in;
        state.checkOut = out;
        state.selectedRooms.add(deluxe);
        state.selectedRooms.add(deluxe);
        state.selectedRooms.add(standard);
        state.commitActive();

        List<PromoOffer> promos = PayingForBreakdown.promosFor(state);
        assertEquals(1, promos.size());
        assertEquals("Deluxe 10", promos.get(0).title);
        assertEquals("350 off x 2 rooms x 2 nights", 1400.0, promos.get(0).estimatedSaving, 0.001);

        List<ReviewSheet.Row> rows = WizardReview.promoRows(c, state);
        StringBuilder all = new StringBuilder();
        for (ReviewSheet.Row r : rows) all.append(r.label).append('|').append(r.amount).append('\n');
        assertTrue(all.toString(), all.toString().contains(c.getString(R.string.promos_for_your_rooms)));
        assertTrue(all.toString(), all.toString().contains("Estimated saving ₱1,400.00"));
        assertEquals("the Grand total stays whole", 3500.0 * 2 * 2 + 1500.0 * 2, state.roomsTotal(), 0.001);
    }

    // ---- Amount to pay ----

    @Test
    public void amountToPay_beforeCheckIn_isDueAtCheckOut_withAnOptionalNoteToPayAtCheckIn() {
        Booking b = new Booking("7", "1", "Deluxe", "Deluxe", "Nov 01, 2026", "Nov 02, 2026", 2, 6000, "Confirmed", "Oct 01, 2026");
        b.setHasBooking(true);
        b.setStatus("Confirmed");
        b.setPaymentTransactions(new ArrayList<>());
        b.setPaymentSummary(new Booking.PaymentSummary(6000, 0, 6000, "PENDING", 0, false, 0));
        MoneySummary s = MoneySummary.of(c, b);
        assertEquals("₱6,000.00 · at check-out", s.amountToPay);
        assertEquals("Optional: pay at check-in", c.getString(R.string.money_optional_check_in_note));
    }
}
