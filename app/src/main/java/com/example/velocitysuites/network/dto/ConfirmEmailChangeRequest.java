package com.example.velocitysuites.network.dto;

/** POST guest/profile/email/confirm - see Api\ProfileController::confirmEmailChange(). */
public class ConfirmEmailChangeRequest {
    public String otp;

    public ConfirmEmailChangeRequest(String otp) {
        this.otp = otp;
    }
}
