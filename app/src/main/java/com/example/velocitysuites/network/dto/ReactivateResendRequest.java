package com.example.velocitysuites.network.dto;

public class ReactivateResendRequest {
    public String reactivation_token;

    public ReactivateResendRequest(String reactivationToken) {
        this.reactivation_token = reactivationToken;
    }
}
