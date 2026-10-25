package com.example.velocitysuites;

import androidx.annotation.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * What the backend's 401 from POST /login means. An unknown email and a wrong password get the SAME answer (code INVALID_CREDENTIALS), so the
 * sign-in screen cannot be used to find out which emails have an account; an older server's ACCOUNT_NOT_FOUND / WRONG_PASSWORD are treated
 * the same way. The only specific 401s come after a correct password: ACCOUNT_NOT_VERIFIED (a sign-up still waiting for its code).
 */
public final class LoginFailure {

    public enum Kind { INVALID_CREDENTIALS, ACCOUNT_NOT_VERIFIED, OTHER }

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
            if ("INVALID_CREDENTIALS".equals(code) || "ACCOUNT_NOT_FOUND".equals(code) || "WRONG_PASSWORD".equals(code)) return new LoginFailure(Kind.INVALID_CREDENTIALS, message);
            if ("ACCOUNT_NOT_VERIFIED".equals(code)) return new LoginFailure(Kind.ACCOUNT_NOT_VERIFIED, message);
            return new LoginFailure(Kind.OTHER, message);
        } catch (RuntimeException e) {
            return new LoginFailure(Kind.OTHER, null);
        }
    }
}
