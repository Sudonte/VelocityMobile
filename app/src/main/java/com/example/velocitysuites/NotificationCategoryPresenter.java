package com.example.velocitysuites;

/**
 * Single source of truth for a notification category's icon/background/
 * foreground colors - the exact same mapping NotificationAdapter and
 * NotificationDetailsActivity each independently duplicated (and could
 * silently drift apart) before this class existed. Also reused by the
 * "Filter by status" dropdown (each option shows its category's icon) and
 * by Transaction History's Booking/Reservation references, so a
 * category reads as the exact same color/icon everywhere it appears in
 * the app, per the task's own "one consistent color and icon per
 * category, used in the combo box, the notification cards, and
 * Transaction History" requirement.
 * <p>
 * Pure static data, no Context needed (mirrors NotificationStatusResolver's
 * own pattern) - callers resolve the actual color/drawable via
 * Context#getColor()/setImageResource() themselves.
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

    /** type is one of the Notification.TYPE_* constants - anything else (including null) falls back to the same generic System look the adapters already used as their default case. */
    public static Result resolve(String type) {
        if (Notification.TYPE_PAYMENT.equals(type)) {
            return new Result(R.drawable.ic_check_circle, R.color.velocity_green_primary, R.color.white, R.string.notif_category_payment);
        }
        if (Notification.TYPE_BOOKING.equals(type)) {
            return new Result(R.drawable.ic_booking, R.color.velocity_red_bg_start, R.color.velocity_red_primary, R.string.notif_category_booking);
        }
        if (Notification.TYPE_RESERVATION.equals(type)) {
            return new Result(R.drawable.ic_reservation, R.color.velocity_red_subtle, R.color.velocity_red_dark, R.string.notif_category_reservation);
        }
        if (Notification.TYPE_CHECK_IN.equals(type)) {
            return new Result(R.drawable.ic_clock, R.color.velocity_orange_primary, R.color.white, R.string.notif_category_check_in);
        }
        if (Notification.TYPE_PROMOTION.equals(type)) {
            return new Result(R.drawable.ic_star, R.color.velocity_red_dark, R.color.white, R.string.notif_category_promotion);
        }
        if (Notification.TYPE_SMS.equals(type)) {
            return new Result(R.drawable.ic_info, R.color.velocity_red_soft, R.color.velocity_red_dark, R.string.notif_category_sms);
        }
        if (Notification.TYPE_ANNOUNCEMENT.equals(type)) {
            return new Result(R.drawable.ic_info, R.color.velocity_blue_soft, R.color.velocity_blue_primary, R.string.notif_category_announcement);
        }
        // TYPE_SYSTEM and anything unrecognized.
        return new Result(R.drawable.ic_notifications, R.color.velocity_red_soft, R.color.velocity_red_primary, R.string.notif_category_system);
    }

    /** Convenience for Transaction History's Booking/Reservation type chip - not a notification at all, but the exact same two categories/colors, resolved by the same isHasBooking() flag the rest of the app already keys off of. */
    public static Result resolveForBooking(boolean isHasBooking) {
        return resolve(isHasBooking ? Notification.TYPE_BOOKING : Notification.TYPE_RESERVATION);
    }
}
