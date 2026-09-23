package com.example.velocitysuites.network.dto;

/** One photo in a Room Type's gallery, plus which individual room it was taken in - see ApiMapper#toImageUrls()/toImageLabels(). */
public class RoomGalleryImageDto {
    public String url;
    /** e.g. "Room 302" - shown alongside the photo so guests know which actual unit it depicts. */
    public String room_label;
}
