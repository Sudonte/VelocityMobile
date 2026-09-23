package com.example.velocitysuites;

import java.io.Serializable;
import java.util.List;

public class Room implements Serializable {
    private String id;
    private String name;
    private String type;
    private int capacity;
    private double pricePerNight;
    private String description;
    private int imageResId;
    private boolean isAvailable;
    private List<RoomAmenity> amenities;
    private String bedType;
    private String roomSize;
    private String policies;
    private String imageUrl;
    private List<String> imageUrls = java.util.Collections.emptyList();
    /** Index-aligned with imageUrls - which individual room each photo came from (e.g. "Room 302"), null entries allowed. */
    private List<String> imageLabels = java.util.Collections.emptyList();
    private long roomTypeId;
    private int availableCount;

    public Room(String id, String name, String type, int capacity, double pricePerNight, String description, int imageResId, boolean isAvailable, List<RoomAmenity> amenities, String bedType, String roomSize, String policies) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.capacity = capacity;
        this.pricePerNight = pricePerNight;
        this.description = description;
        this.imageResId = imageResId;
        this.isAvailable = isAvailable;
        this.amenities = amenities;
        this.bedType = bedType;
        this.roomSize = roomSize;
        this.policies = policies;
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getType() { return type; }
    public int getCapacity() { return capacity; }
    public double getPricePerNight() { return pricePerNight; }
    public String getDescription() { return description; }
    public int getImageResId() { return imageResId; }
    public boolean isAvailable() { return isAvailable; }
    public List<RoomAmenity> getAmenities() { return amenities; }
    public String getBedType() { return bedType; }
    public String getRoomSize() { return roomSize; }
    public String getPolicies() { return policies; }
    public String getImageUrl() { return imageUrl; }
    /** This room/type's full photo gallery (4-5 images, ordered) - empty when none are assigned yet. */
    public List<String> getImageUrls() { return imageUrls; }
    /** Index-aligned with getImageUrls() - the room label (e.g. "Room 302") for each photo. */
    public List<String> getImageLabels() { return imageLabels; }
    public long getRoomTypeId() { return roomTypeId; }
    public int getAvailableCount() { return availableCount; }

    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls != null ? imageUrls : java.util.Collections.emptyList(); }
    public void setImageLabels(List<String> imageLabels) { this.imageLabels = imageLabels != null ? imageLabels : java.util.Collections.emptyList(); }
    public void setRoomTypeId(long roomTypeId) { this.roomTypeId = roomTypeId; }
    public void setAvailableCount(int availableCount) { this.availableCount = availableCount; }
}
