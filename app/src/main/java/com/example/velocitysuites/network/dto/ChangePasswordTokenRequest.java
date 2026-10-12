package com.example.velocitysuites.network.dto;

/** POST guest/password/change: the verified-OTP token plus the new password and its confirmation. */
public class ChangePasswordTokenRequest {
    public final String reset_token;
    public final String password;
    public final String password_confirmation;

    public ChangePasswordTokenRequest(String resetToken, String password, String confirmation) {
        this.reset_token = resetToken;
        this.password = password;
        this.password_confirmation = confirmation;
    }
}
