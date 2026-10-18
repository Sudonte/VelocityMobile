package com.example.velocitysuites;

/**
 * Single source of truth for a notification category's icon, tint and chip label - used by the Notifications screen's
 * cards and Show list, the Dashboard's notification rows, the bell popup and Transaction History's Booking/Reservation
 * references, so a category reads as the exact same icon everywhere it appears in the app.
 * <p>
 * Pure static data, no Context needed (mirrors NotificationStatusResolver's own pattern) - callers resolve the actual
 * color/drawable via Context#getColor()/setImageResource() themselves.
 */
public final class NotificationCategoryPresenter {

    private NotificationCategoryPresenter() {
    }

    public static final class Result {
        public final int iconRes;
        public final int bgColorRes;
        public final int fgColorRes;
        /** Short human label for the card's category chip - "Check-in", not the internal "CheckIn" key. */
        public final int labelRes;

        Result(int iconRes, int bgColorRes, int fgColorRes, int labelRes) {
            this.iconRes = iconRes;
            this.bgColorRes = bgColorRes;
            this.fgColorRes = fgColorRes;
            this.labelRes = labelRes;
        }
    }

    /**
     * type is one of the Notification.TYPE_* constants - anything else (including null) falls back to the generic System
     * look. ONE look for every category: a round soft-red circle with the category's red icon, so no screen mixes pale and
     * solid circles.
     */
    public static Result resolve(String type) {
        if (Notification.TYPE_PAYMENT.equals(type)) return of(R.drawable.ic_payment_card, R.string.notif_category_payment);
        if (Notification.TYPE_BOOKING.equals(type)) return of(R.drawable.ic_booking, R.string.notif_category_booking);
        if (Notification.TYPE_RESERVATION.equals(type)) return of(R.drawable.ic_reservation, R.string.notif_category_reservation);
        if (Notification.TYPE_CHECK_IN.equals(type)) return of(R.drawable.ic_clock, R.string.notif_category_check_in);
        if (Notification.TYPE_PROMOTION.equals(type)) return of(R.drawable.ic_star, R.string.notif_category_promotion);
        if (Notification.TYPE_SMS.equals(type)) return of(R.drawable.ic_info, R.string.notif_category_sms);
        if (Notification.TYPE_ANNOUNCEMENT.equals(type)) return of(R.drawable.ic_info, R.string.notif_category_announcement);
        // TYPE_SYSTEM and anything unrecognized.
        return of(R.drawable.ic_notifications, R.string.notif_category_system);
    }

    /**
     * The look of one notification. The backend has no separate "amenity" category (an amenity update arrives as a Booking
     * or Check-in notification), so an amenity notification is recognised by its own wording - "Amenity Request Approved",
     * "You Can Now Request Additional Amenities" - and drawn with the amenity icon and an "Amenities" chip. This is
     * presentation only: the Show filter still files it under its real category.
     */
    public static Result resolve(Notification n) {
        if (n != null && isAmenity(n)) return of(R.drawable.ic_amenity_concierge, R.string.notif_category_amenity);
        return resolve(n == null ? null : n.getType());
    }

    /** True for a transaction notification that is about additional amenities. */
    public static boolean isAmenity(Notification n) {
        if (!NotificationPrimaryActionResolver.isTransactionCategory(n.getType())) return false;
        String title = n.getTitle() == null ? "" : n.getTitle().toLowerCase(java.util.Locale.US);
        return title.contains("amenit");
    }

    private static Result of(int iconRes, int labelRes) {
        return new Result(iconRes, R.color.velocity_red_soft, R.color.velocity_red_primary, labelRes);
    }

    /** Convenience for Transaction History's Booking/Reservation type chip - not a notification at all, but the exact same two categories, resolved by the same isHasBooking() flag the rest of the app already keys off of. */
    public static Result resolveForBooking(boolean isHasBooking) {
        return resolve(isHasBooking ? Notification.TYPE_BOOKING : Notification.TYPE_RESERVATION);
    }
}
