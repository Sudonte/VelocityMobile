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
    /** Null for the room type's standard option; the option key ("r4200c4") for an "other" option, whose id is "<typeId>:<optionKey>". */
    private String optionKey;
    /** The type's other options (rooms with their own price or capacity), each a Room of its own; empty for an option or a type without any. */
    private List<Room> otherOptions = java.util.Collections.emptyList();
    private int availableCount;
    /** The promos running for this room type today (empty when none). */
    private List<PromoOffer> promos = java.util.Collections.emptyList();

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
    public List<PromoOffer> getPromos() { return promos; }
    public String getOptionKey() { return optionKey; }
    public boolean isOtherOption() { return optionKey != null; }
    public List<Room> getOtherOptions() { return otherOptions; }

    /** Some "other" option of this room type has a free room for the dates. */
    public boolean hasFreeOtherOption() {
        if (otherOptions == null) return false;
        for (Room o : otherOptions) if (o.isAvailable() && o.getAvailableCount() > 0) return true;
        return false;
    }

    /** The standard rooms are full but another option is free: the type is still bookable, just not as its standard option. */
    public boolean isOnlyOtherOptionAvailable() {
        return !isAvailable() && hasFreeOtherOption();
    }

    /** Anything of this room type can be booked for the dates: the standard option or one of the others. */
    public boolean isAvailableInAnyOption() {
        return isAvailable() || hasFreeOtherOption();
    }
    public void setOptionKey(String optionKey) { this.optionKey = optionKey; }
    public void setOtherOptions(List<Room> otherOptions) { this.otherOptions = otherOptions != null ? otherOptions : java.util.Collections.<Room>emptyList(); }

    /** The room type id the API knows (a Room's id is "12", or "12:r4200c4" for one of its other options). */
    public long apiTypeId() {
        int i = id.indexOf(':');
        return Long.parseLong(i < 0 ? id : id.substring(0, i));
    }

    /** The list with each type's other options listed right after it (what the wizard offers and what availability checks look through). */
    public static List<Room> withOptions(List<Room> rooms) {
        List<Room> out = new java.util.ArrayList<>();
        if (rooms == null) return out;
        for (Room r : rooms) {
            out.add(r);
            if (r.getOtherOptions() != null) out.addAll(r.getOtherOptions());
        }
        return out;
    }

    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls != null ? imageUrls : java.util.Collections.emptyList(); }
    public void setImageLabels(List<String> imageLabels) { this.imageLabels = imageLabels != null ? imageLabels : java.util.Collections.emptyList(); }
    public void setRoomTypeId(long roomTypeId) { this.roomTypeId = roomTypeId; }
    public void setAvailableCount(int availableCount) { this.availableCount = availableCount; }
    public void setPromos(List<PromoOffer> promos) { this.promos = promos != null ? promos : java.util.Collections.<PromoOffer>emptyList(); }
}
