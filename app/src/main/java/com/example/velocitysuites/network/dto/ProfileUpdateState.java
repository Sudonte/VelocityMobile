package com.example.velocitysuites.network.dto;

/**
 * Server-computed Personal Information / Contact & Address 30-day update
 * cooldown state - see Api\ProfileController::profileUpdateState(). The
 * server is the sole authority on this (never the device clock); Android
 * only ever displays what this DTO says, it never independently derives
 * eligibility from a locally-cached timestamp.
 */
public class ProfileUpdateState {
    public boolean can_update;
    /** ISO-8601, null if the guest has never completed a profile-info update. */
    public String last_updated_at;
    /** ISO-8601, null once editable again. */
    public String next_update_at;
    public int days_remaining;
}
