package com.example.velocitysuites.network.dto;

public class GuestDto {
    /** Only present when the response eager-loads guest.user (e.g. a direct Booking's own guest relation) - not populated on every endpoint that reuses this DTO. */
    public UserDto user;
    public Integer age;
    public String gender;
    public String date_of_birth;
    public String mobile_number;
    public String address;
    public String country;
    public String region;
    public String province;
    public String city;
    public String barangay;
    public String street;
    public String zip_code;
    public String profile_picture;
    public String profile_picture_url;
}
