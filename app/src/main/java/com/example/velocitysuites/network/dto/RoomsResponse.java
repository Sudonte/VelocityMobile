package com.example.velocitysuites.network.dto;

/** GET /rooms response body - see Api\CatalogController::rooms() and RoomRepository#refreshRooms(). */
public class RoomsResponse {
    public PaginatedResponse<RoomTypeDto> room_types;
}
