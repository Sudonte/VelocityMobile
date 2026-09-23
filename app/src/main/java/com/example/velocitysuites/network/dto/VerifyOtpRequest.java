package com.example.velocitysuites.network.dto;

/** POST /verify-otp body - confirms the email OTP sent during registration. */
public class VerifyOtpRequest {
    public String email;
    public String otp;

    public VerifyOtpRequest(String email, String otp) {
        this.email = email;
        this.otp = otp;
    }
}
