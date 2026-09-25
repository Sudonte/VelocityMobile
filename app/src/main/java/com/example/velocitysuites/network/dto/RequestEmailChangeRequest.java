package com.example.velocitysuites.network.dto;

/** POST guest/profile/email/request - see Api\ProfileController::requestEmailChange(). */
public class RequestEmailChangeRequest {
    public String new_email;
    public String current_password;

    public RequestEmailChangeRequest(String newEmail, String currentPassword) {
        this.new_email = newEmail;
        this.current_password = currentPassword;
    }
}
