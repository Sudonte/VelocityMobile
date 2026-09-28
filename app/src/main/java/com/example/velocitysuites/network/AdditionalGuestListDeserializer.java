package com.example.velocitysuites.network;

import com.example.velocitysuites.network.dto.AdditionalGuestDto;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Defense-in-depth for List&lt;AdditionalGuestDto&gt; fields (ReservationDto#additional_guest_details,
 * DirectBookingResponseDto#additional_guest_details). Without this, Gson's default collection
 * adapter requires a JSON ARRAY and throws if it ever sees a JSON OBJECT instead - which is
 * exactly what a PHP array with out-of-0..n-1-order keys serializes as (json_encode()'s list
 * detection needs the keys in that EXACT order, not just that value set - see
 * BookingController::store()/DirectBookingService::create()'s array_values() fix on the backend,
 * added after this exact shape was confirmed live in production data and had silently broken
 * the ENTIRE combined bookings+reservations refresh - both endpoints share one Retrofit call
 * pair, so one malformed record failed the whole list, not just itself).
 * <p>
 * The backend fix (array_values() before every write, plus a one-time repair of the rows already
 * affected) means a NEW malformed response should never happen again - this exists so an older
 * cached response, a not-yet-repaired legacy row this session's scan didn't catch, or any future
 * regression of the same class degrades to "recovered, in whatever order the object's keys
 * happened to be" instead of failing the entire list with zero diagnostic.
 */
public final class AdditionalGuestListDeserializer implements JsonDeserializer<List<AdditionalGuestDto>> {

    @Override
    public List<AdditionalGuestDto> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        if (json == null || json.isJsonNull()) {
            return null;
        }

        if (json.isJsonArray()) {
            List<AdditionalGuestDto> result = new ArrayList<>();
            for (JsonElement element : json.getAsJsonArray()) {
                result.add(context.deserialize(element, AdditionalGuestDto.class));
            }
            return result;
        }

        if (json.isJsonObject()) {
            // Malformed shape (see class doc) - recover by reading the object's VALUES,
            // ignoring its (out-of-order, otherwise-meaningless) keys entirely.
            DiagnosticLog.w("additionalGuestListDeserializer.recoveredFromObject",
                    "keys=" + ((JsonObject) json).keySet());
            List<AdditionalGuestDto> result = new ArrayList<>();
            for (JsonElement value : ((JsonObject) json).entrySet().stream().map(java.util.Map.Entry::getValue)
                    .collect(java.util.stream.Collectors.toList())) {
                try {
                    result.add(context.deserialize(value, AdditionalGuestDto.class));
                } catch (JsonParseException ignored) {
                    // A genuinely unrecoverable entry - skip it rather than fail the whole list.
                }
            }
            return result;
        }

        // Any other unexpected shape (e.g. a bare string/number) - never crash the whole
        // response over one cosmetic field; empty is the same safe default ApiMapper already
        // treats a missing/null additional_guest_details as.
        DiagnosticLog.w("additionalGuestListDeserializer.unexpectedShape", "json=" + json);
        return new ArrayList<>();
    }
}
