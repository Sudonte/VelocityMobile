package com.example.velocitysuites.network.dto;

public class ReactivateVerifyRequest {
    public String reactivation_token;
    public String otp;
    public String device_name;

    public ReactivateVerifyRequest(String reactivationToken, String otp, String deviceName) {
        this.reactivation_token = reactivationToken;
        this.otp = otp;
        this.device_name = deviceName;
    }
}
