package com.example.velocitysuites;

import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Recognises the server's "that room is no longer available" answer to creating a Booking.
 * <p>
 * The backend (Api\BookingController::store -> DirectBookingService::validateRoomTypeAvailability) throws a Laravel
 * ValidationException, i.e. HTTP 422 with {@code {"message": "...", "errors": {"rooms_requested": ["Deluxe is fully
 * booked for these dates."]}}} - or the {@code room_type_id} key when the type is not offered / has no rooms in
 * service. There is no dedicated code field, so the error KEY is what identifies it; every other 422 of that
 * endpoint (capacity, amount, discount, duplicate reference) uses a different key and must not be mistaken for it.
 */
public final class AvailabilityRejection {

    private AvailabilityRejection() {
    }

    /** The server's own sentence when {@code body} is an availability rejection with HTTP {@code code}, else null. */
    @Nullable
    public static String messageOf(int code, @Nullable String body) {
        if (code != 422 || body == null) return null;
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (!json.has("errors") || !json.get("errors").isJsonObject()) return null;
            JsonObject errors = json.getAsJsonObject("errors");
            for (String key : new String[]{"rooms_requested", "room_type_id"}) {
                String text = firstText(errors.get(key));
                if (text != null) return text;
            }
        } catch (Exception notJson) {
            // not a JSON object: not a rejection we can recognise
        }
        return null;
    }

    @Nullable
    private static String firstText(@Nullable JsonElement value) {
        if (value == null) return null;
        if (value.isJsonArray() && value.getAsJsonArray().size() > 0) value = value.getAsJsonArray().get(0);
        if (!value.isJsonPrimitive()) return null;
        String text = value.getAsString();
        return text.trim().isEmpty() ? null : text;
    }
}
