package com.example.velocitysuites.network.dto;

import java.util.ArrayList;
import java.util.List;

public class PaginatedResponse<T> {
    public int current_page;
    public List<T> data = new ArrayList<>();
    public int last_page;
    public int total;
    /** Notifications-only (Api\NotificationController::index()) - the guest's TRUE total unread count, not derived from `data`/`total` above (which only ever reflect this one page). Stays 0 (its Java default) on every other PaginatedResponse<T> use (bookings/reservations), which don't populate this key at all - harmless, never read there. */
    public int unread_count;
}
