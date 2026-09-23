package com.example.velocitysuites.network.dto;

public class ProfileResponse {
    public UserDto user;
    public GuestDto guest;
    /** Present on both GET /guest/profile and PUT /guest/profile - see ProfileUpdateState. */
    public ProfileUpdateState profile_update;
}
