package com.example.velocitysuites.network.dto;

public class AuthResponse {
    public String token;
    public UserDto user;

    // Populated instead of token/user when login() detects a deactivated
    // account (see Api\AuthController::login()'s ACCOUNT_DEACTIVATED branch)
    // - the server never authenticates a deactivated account before OTP
    // verification succeeds, so these are the only fields set in that case.
    public String code;
    public Boolean reactivation_required;
    public String masked_email;
    public String reactivation_token;
    public String message;
}
