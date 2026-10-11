package com.example.velocitysuites.network.dto;

import java.util.List;

/** Whether the guest may request more amenities on this stay (and why not), plus the requests so far. */
public class AdditionalAmenityInfoDto {
    public boolean can_request;
    public String reason;
    public List<AdditionalAmenityRequestDto> requests;
    public double total_approved;
}
