package com.example.velocitysuites.network.dto;

public class ProfileUpdateRequest {
    public String first_name;
    public String last_name;
    public String middle_name;
    // Email is deliberately NOT here - the server ignores it on this endpoint
    // now anyway, but the client's own intent must be correct too. Changing
    // email goes through RequestEmailChangeRequest/ConfirmEmailChangeRequest
    // (see ProfileManagementActivity's email-change OTP flow) instead.
    public String mobile_number;
    public String gender;
    public String date_of_birth;
    public String address;
    public String country;
    public String region;
    public String province;
    public String city;
    public String barangay;
    public String street;
    public String zip_code;
}
