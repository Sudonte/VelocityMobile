package com.example.velocitysuites.network.dto;

/** POST verify-reset-otp: on a correct code the server also returns the one-time token the in-app password change requires. */
public class ResetTokenResponse {
    public String message;
    public String reset_token;
    public int expires_in;
}
