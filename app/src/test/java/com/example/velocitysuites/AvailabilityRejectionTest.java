package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Bodies are the real shape of Laravel's ValidationException as thrown by DirectBookingService::validateRoomTypeAvailability. */
public class AvailabilityRejectionTest {

    @Test
    public void fullyBooked_isRecognisedByItsErrorKey() {
        String body = "{\"message\":\"Deluxe is fully booked for these dates.\",\"errors\":{\"rooms_requested\":[\"Deluxe is fully booked for these dates.\"]}}";
        assertEquals("Deluxe is fully booked for these dates.", AvailabilityRejection.messageOf(422, body));
    }

    @Test
    public void notEnoughRooms_isRecognised() {
        String body = "{\"message\":\"x\",\"errors\":{\"rooms_requested\":[\"Not enough Deluxe rooms available for these dates (needs 3, only 1 free).\"]}}";
        assertEquals("Not enough Deluxe rooms available for these dates (needs 3, only 1 free).", AvailabilityRejection.messageOf(422, body));
    }

    @Test
    public void notOffered_isRecognisedByTheRoomTypeKey() {
        String body = "{\"message\":\"x\",\"errors\":{\"room_type_id\":[\"Suite is not currently offered.\"]}}";
        assertEquals("Suite is not currently offered.", AvailabilityRejection.messageOf(422, body));
    }

    @Test
    public void otherValidationErrors_areNotMistakenForIt() {
        assertNull(AvailabilityRejection.messageOf(422,
                "{\"message\":\"x\",\"errors\":{\"adults\":[\"Adults and children combined can't exceed the selected room capacity of 2.\"]}}"));
        assertNull(AvailabilityRejection.messageOf(422,
                "{\"message\":\"x\",\"errors\":{\"amount_paid\":[\"Must be more than 0.\"]}}"));
        assertNull(AvailabilityRejection.messageOf(422,
                "{\"message\":\"The amount paid must be greater than 0\"}"));
    }

    @Test
    public void otherStatusCodesAndNonJson_areNotMistakenForIt() {
        String body = "{\"errors\":{\"rooms_requested\":[\"fully booked\"]}}";
        assertNull(AvailabilityRejection.messageOf(500, body));
        assertNull(AvailabilityRejection.messageOf(409, body));
        assertNull(AvailabilityRejection.messageOf(422, "<html>oops</html>"));
        assertNull(AvailabilityRejection.messageOf(422, null));
        assertNull(AvailabilityRejection.messageOf(422, "{\"errors\":{\"rooms_requested\":[]}}"));
    }
}
