package com.example.velocitysuites.network.dto;

/** POST /reset-password body - the final step of the Forgot Password flow, after the OTP has already been verified. */
public class ResetPasswordRequest {
    public String email;
    public String otp;
    public String password;
    public String password_confirmation;

    public ResetPasswordRequest(String email, String otp, String password, String passwordConfirmation) {
        this.email = email;
        this.otp = otp;
        this.password = password;
        this.password_confirmation = passwordConfirmation;
    }
}
