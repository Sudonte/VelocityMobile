package com.example.velocitysuites;

import androidx.annotation.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * What the backend's 401 from POST /login means. Api\AuthController::login() answers an unknown email with
 * code ACCOUNT_NOT_FOUND and a known email with a bad password with code WRONG_PASSWORD, so the app can put the
 * message under the right field. An older server (or any other 401) is OTHER and keeps the generic handling.
 */
public final class LoginFailure {

    public enum Kind { ACCOUNT_NOT_FOUND, WRONG_PASSWORD, OTHER }

    public final Kind kind;
    /** The server's own message, or null when the body had none. */
    @Nullable
    public final String message;

    private LoginFailure(Kind kind, @Nullable String message) {
        this.kind = kind;
        this.message = message;
    }

    public static LoginFailure parse(@Nullable String errorBody) {
        if (errorBody == null || errorBody.trim().isEmpty()) return new LoginFailure(Kind.OTHER, null);
        try {
            JsonObject json = JsonParser.parseString(errorBody).getAsJsonObject();
            String code = json.has("code") && !json.get("code").isJsonNull() ? json.get("code").getAsString() : "";
            String message = json.has("message") && !json.get("message").isJsonNull() ? json.get("message").getAsString() : null;
            if ("ACCOUNT_NOT_FOUND".equals(code)) return new LoginFailure(Kind.ACCOUNT_NOT_FOUND, message);
            if ("WRONG_PASSWORD".equals(code)) return new LoginFailure(Kind.WRONG_PASSWORD, message);
            return new LoginFailure(Kind.OTHER, message);
        } catch (RuntimeException e) {
            return new LoginFailure(Kind.OTHER, null);
        }
    }
}
