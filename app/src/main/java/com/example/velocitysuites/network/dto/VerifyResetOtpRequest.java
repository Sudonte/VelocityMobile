package com.example.velocitysuites.network.dto;

/** POST /verify-reset-otp body - confirms the OTP sent for a Forgot Password flow, before the new password is submitted. */
public class VerifyResetOtpRequest {
    public String email;
    public String otp;

    public VerifyResetOtpRequest(String email, String otp) {
        this.email = email;
        this.otp = otp;
    }
}
