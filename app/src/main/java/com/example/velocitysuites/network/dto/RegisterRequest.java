package com.example.velocitysuites.network.dto;

/** POST /register body - see Api\AuthController::register(). */
public class RegisterRequest {
    public String first_name;
    public String middle_name;
    public String last_name;
    public String email;
    public String password;
    public String password_confirmation;
    public String mobile_number;
    public String gender;
    public String date_of_birth;
    public int age;

    // Address block - Philippines-only fields are left empty/unset for non-PH guests, matching
    // AddressHierarchyController's getStructuredValues()/getComposedAddress() contract.
    public String address;
    public String country;
    public String region;
    public String province;
    public String city;
    public String barangay;
    public String street;
    public String zip_code;
    public String timezone;

    // Email is currently the only OTP delivery channel the UI offers - see RegistrationActivity.
    public String otp_channel;
}
