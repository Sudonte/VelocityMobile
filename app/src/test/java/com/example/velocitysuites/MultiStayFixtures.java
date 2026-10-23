package com.example.velocitysuites;

import com.example.velocitysuites.network.ApiMapper;
import com.example.velocitysuites.network.dto.DirectBookingResponseDto;
import com.example.velocitysuites.network.dto.ReservationDto;
import com.google.gson.Gson;

/**
 * The two real transactions from the device: a booking and a reservation, each of TWO one-night stays (Oct 26 and Oct 27, span Oct 26 to 28),
 * each stay with a Deluxe (3,500) and an Executive (5,800). Rooms are 18,600 for the two stays; the booking has 300 of amenities
 * (Grand total 18,900), the reservation 415 (Grand total 19,015) with a PWD 20% discount that is only an expectation until the ID is checked.
 */
public final class MultiStayFixtures {

    private MultiStayFixtures() {
    }

    private static final String STAYS = "\"stays\":["
            + "{\"id\":10,\"position\":1,\"check_in\":\"2026-10-26\",\"check_out\":\"2026-10-27\",\"nights\":1,\"state\":\"upcoming\","
            + "\"rooms\":[{\"room_type_id\":\"1\",\"room_type\":\"Deluxe\",\"quantity\":1,\"price_per_night\":3500,\"nights\":1,\"subtotal\":3500,\"assigned_room_numbers\":[]},"
            + "{\"room_type_id\":\"8\",\"room_type\":\"Executive\",\"quantity\":1,\"price_per_night\":5800,\"nights\":1,\"subtotal\":5800,\"assigned_room_numbers\":[]}],"
            + "\"amenities\":[%s],\"rooms_subtotal\":9300,\"amenities_subtotal\":%s,\"subtotal\":%s},"
            + "{\"id\":11,\"position\":2,\"check_in\":\"2026-10-27\",\"check_out\":\"2026-10-28\",\"nights\":1,\"state\":\"upcoming\","
            + "\"rooms\":[{\"room_type_id\":\"1\",\"room_type\":\"Deluxe\",\"quantity\":1,\"price_per_night\":3500,\"nights\":1,\"subtotal\":3500,\"assigned_room_numbers\":[]},"
            + "{\"room_type_id\":\"8\",\"room_type\":\"Executive\",\"quantity\":1,\"price_per_night\":5800,\"nights\":1,\"subtotal\":5800,\"assigned_room_numbers\":[]}],"
            + "\"amenities\":[],\"rooms_subtotal\":9300,\"amenities_subtotal\":0,\"subtotal\":9300}]";

    private static final String ROOM_LINES = "\"room_lines\":["
            + "{\"room_type_id\":\"1\",\"room_type\":\"Deluxe\",\"quantity\":1,\"price_per_night\":3500,\"nights\":1,\"subtotal\":3500,\"assigned_room_numbers\":[]},"
            + "{\"room_type_id\":\"8\",\"room_type\":\"Executive\",\"quantity\":1,\"price_per_night\":5800,\"nights\":1,\"subtotal\":5800,\"assigned_room_numbers\":[]},"
            + "{\"room_type_id\":\"1\",\"room_type\":\"Deluxe\",\"quantity\":1,\"price_per_night\":3500,\"nights\":1,\"subtotal\":3500,\"assigned_room_numbers\":[]},"
            + "{\"room_type_id\":\"8\",\"room_type\":\"Executive\",\"quantity\":1,\"price_per_night\":5800,\"nights\":1,\"subtotal\":5800,\"assigned_room_numbers\":[]}]";

    /** Booking: Room charges 18,600 + Amenities 300 = Grand total 18,900. */
    public static Booking booking() {
        return ApiMapper.toBooking(bookingDto());
    }

    public static DirectBookingResponseDto bookingDto() {
        String amenity = "{\"amenity_id\":\"15\",\"amenity_name\":\"Additional Towel\",\"quantity\":2,\"unit_price\":50,\"subtotal\":100},"
                + "{\"amenity_id\":\"17\",\"amenity_name\":\"Additional Blanket\",\"quantity\":2,\"unit_price\":100,\"subtotal\":200}";
        String json = "{\"id\":585,\"guest_id\":1,\"room_type_id\":1,\"rooms_requested\":4,"
                + "\"check_in\":\"2026-10-26T00:00:00.000000Z\",\"check_out\":\"2026-10-28T00:00:00.000000Z\",\"adults\":1,\"children\":2,"
                + "\"number_of_guests\":3,\"booking_status\":\"ACTIVE_BOOKING\",\"payment_method\":\"gcash\",\"total_amount_due\":18900.0,"
                + "\"amenities\":[" + amenity + "]," + ROOM_LINES + ","
                + String.format(STAYS, amenity, "300", "9600") + "}";
        return new Gson().fromJson(json, DirectBookingResponseDto.class);
    }

    /** Reservation #636-like: Room charges 18,600 + Amenities 415 = Grand total 19,015; PWD 20% (3,803) pending the ID check. */
    public static Booking reservation(String discountStatus) {
        return ApiMapper.toBooking(reservationDto(discountStatus));
    }

    public static ReservationDto reservationDto(String discountStatus) {
        String amenity = "{\"amenity_id\":\"15\",\"amenity_name\":\"Additional Towel\",\"quantity\":1,\"unit_price\":50,\"subtotal\":50},"
                + "{\"amenity_id\":\"17\",\"amenity_name\":\"Additional Blanket\",\"quantity\":1,\"unit_price\":100,\"subtotal\":100},"
                + "{\"amenity_id\":\"25\",\"amenity_name\":\"Test1\",\"quantity\":1,\"unit_price\":250,\"subtotal\":250},"
                + "{\"amenity_id\":\"26\",\"amenity_name\":\"Soap\",\"quantity\":1,\"unit_price\":15,\"subtotal\":15}";
        String json = "{\"id\":636,\"guest_id\":1,\"room_type_id\":1,\"rooms_requested\":4,"
                + "\"check_in\":\"2026-10-26\",\"check_out\":\"2026-10-28\",\"adults\":1,\"children\":0,\"number_of_guests\":1,"
                + "\"status\":\"AWAITING_GCASH_PAYMENT\",\"payment_method\":\"gcash\",\"total_amount_due\":19015.0,"
                + "\"id_card_type\":\"PWD\",\"discount_verification_status\":\"" + discountStatus + "\","
                + "\"room_type\":{\"id\":1,\"name\":\"Deluxe\",\"rate\":\"3500.00\"},"
                + "\"discount_preview\":{\"nights\":2,\"room_charge\":18600,\"add_ons\":415,\"discount\":3803,\"total\":15212},"
                + "\"amenities\":[" + amenity + "]," + ROOM_LINES + ","
                + String.format(STAYS, amenity, "415", "9715") + "}";
        return new Gson().fromJson(json, ReservationDto.class);
    }
}
