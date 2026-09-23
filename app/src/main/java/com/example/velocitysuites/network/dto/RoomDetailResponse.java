package com.example.velocitysuites.network.dto;

/** GET /rooms/{id} response body - single Room Type detail, mirroring RoomsResponse's "room_types" wrapper convention. */
public class RoomDetailResponse {
    public RoomTypeDto room_type;
}
