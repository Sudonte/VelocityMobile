package com.example.velocitysuites;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import retrofit2.Response;

/**
 * Field-error-first parsing of a Laravel-shaped {"message":..., "errors":{"field":["msg"]}}
 * error body, shared by RegistrationActivity and ProfileManagementActivity so a specific
 * field message (e.g. "This mobile number is already registered to another account.") is
 * shown instead of the generic top-level "message" - mirrors RoomRepository's own private
 * firstFieldError()/errorMessage() convention, duplicated here as a small standalone utility
 * rather than reused directly: that method is private, and its owning errorMessage() also
 * triggers a 401/419 session-expiry redirect that would be wrong to pull into pre-auth
 * registration.
 */
public final class ApiErrorParser {

    private ApiErrorParser() {
    }

    public static String extractMessage(String jsonBody, String fallback) {
        if (jsonBody == null) return fallback;
        try {
            JsonObject json = JsonParser.parseString(jsonBody).getAsJsonObject();
            String fieldError = firstFieldError(json);
            if (fieldError != null && !fieldError.trim().isEmpty()) return fieldError;
            if (json.has("message") && !json.get("message").isJsonNull()) {
                String msg = json.get("message").getAsString();
                if (!msg.trim().isEmpty()) return msg;
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    public static String extractMessage(Response<?> response, String fallback) {
        if (response == null || response.errorBody() == null) return fallback;
        try {
            return extractMessage(response.errorBody().string(), fallback);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String firstFieldError(JsonObject json) {
        if (!json.has("errors") || !json.get("errors").isJsonObject()) return null;
        JsonObject errors = json.getAsJsonObject("errors");
        for (String key : errors.keySet()) {
            JsonElement value = errors.get(key);
            if (value.isJsonArray()) {
                JsonArray array = value.getAsJsonArray();
                if (array.size() > 0) return array.get(0).getAsString();
            } else if (value.isJsonPrimitive()) {
                return value.getAsString();
            }
        }
        return null;
    }
}
