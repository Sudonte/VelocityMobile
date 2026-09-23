package com.example.velocitysuites.network.dto;

/** The `users` table row embedded in AuthResponse/ProfileResponse - account-level fields only; guest-profile fields live on the nested GuestDto. */
public class UserDto {
    public long id;
    public String first_name;
    public String middle_name;
    public String last_name;
    public String full_name;
    public String email;
    /** e.g. "active" | "pending_deletion" - see LoginActivity's restore-account prompt. */
    public String account_status;
    /** Only set while account_status is "pending_deletion" - the deadline after which the pending deletion becomes permanent. */
    public String restore_deadline;
    /** Account creation timestamp (UTC ISO-8601), permanent - see TimeUtils#formatDateTime(). Null on older cached responses that predate this field, in which case the Profile screen simply hides the "Member Since" row. */
    public String created_at;
    /** Most recent account update timestamp (UTC ISO-8601) - distinct from created_at, which never changes. */
    public String updated_at;
    public GuestDto guest;
}
