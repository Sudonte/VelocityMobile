package com.example.velocitysuites;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a notification says: a clear title and a short description of three or four sentences, written in the app from the
 * facts the notification carries (item, quantity, room, amount, the linked Booking / Reservation and its dates, status) -
 * never invented. The server's own wording is only read, never shown, when a template applies: for the known message
 * patterns the item, quantity, room and amount are parsed out of it ("Your request for 1 x Additional Pillow (Room 1-001)
 * (₱50.00) was approved and added to your bill."). When a notification does not match a template, or a template cannot
 * be filled from what is known, the server's title and message are shown unchanged.
 *
 * <p>Templates exist for: amenity requests approved / rejected; Booking and Reservation confirmed / rejected / cancelled;
 * payment received / verified / rejected; check-in and check-out; promotions and discounts; system announcements.
 */
final class NotificationText {

    /** What to show: {@link #templated} is false when it is the server's own text. */
    static final class Content {
        final String title;
        final String description;
        final boolean templated;

        Content(String title, String description, boolean templated) {
            this.title = title;
            this.description = description;
            this.templated = templated;
        }
    }

    private static final Pattern AMENITY = Pattern.compile(
            "(?is)request\\s+for\\s+(\\d+)\\s*[x×]\\s*(.+?)\\s*(?:\\(\\s*room\\s+([^)]+?)\\s*\\))?\\s*(?:\\(\\s*₱\\s*([\\d,]+(?:\\.\\d{1,2})?)\\s*\\))?\\s+was\\s+(approved|rejected|declined|denied|not\\s+approved)");
    private static final Pattern AMOUNT = Pattern.compile("₱\\s*([\\d,]+(?:\\.\\d{1,2})?)");
    private static final Pattern REASON = Pattern.compile("(?is)(?:reason|note|remarks?)\\s*[:\\-]\\s*(.+?)\\s*$");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private NotificationText() {
    }

    /** The content of one notification, using the loaded transaction it links to (if any) for dates and rooms. */
    @NonNull
    static Content of(@NonNull Context context, @NonNull Notification n) {
        Booking linked = null;
        String label = null;
        try {
            String ref = n.getReferenceId();
            if (NotificationPrimaryActionResolver.canViewTransaction(n.getType(), ref)) {
                linked = TransactionNavigator.pick(RoomRepository.getInstance(context).getAllBookings(), ref, null, n.getType(), n.getReceiptNumber());
                label = NotificationRowBinder.linkedLabel(context, n);
            }
        } catch (RuntimeException e) {
            // A transaction that cannot be looked up is simply not mentioned.
        }
        return build(context, n, linked, label);
    }

    @NonNull
    static Content build(@NonNull Context c, @NonNull Notification n, @Nullable Booking linked, @Nullable String label) {
        String title = nz(n.getTitle());
        String message = nz(n.getMessage());
        Content original = new Content(n.getTitle() == null ? "" : n.getTitle(), n.getMessage() == null ? "" : n.getMessage(), false);
        try {
            Content templated = template(c, n, title, message, linked, label);
            return templated != null ? templated : original;
        } catch (RuntimeException e) {
            return original; // anything unexpected in the data: show what the server sent
        }
    }

    @Nullable
    private static Content template(Context c, Notification n, String title, String message, @Nullable Booking linked, @Nullable String label) {
        String type = n.getType() == null ? "" : n.getType();
        String t = title.toLowerCase(Locale.US);
        String m = message.toLowerCase(Locale.US);
        boolean reservation = Notification.TYPE_RESERVATION.equals(type) || (linked != null && !linked.isHasBooking());
        String noun = c.getString(reservation ? R.string.nt_noun_reservation : R.string.nt_noun_booking);
        String who = label != null ? label : c.getString(R.string.nt_your_noun_format, noun);
        String dates = linked != null ? StayCardText.dates(linked) : "";
        String rooms = linked != null ? TransactionRow.roomTextOf(linked).replace(" • ", ", ") : "";
        String reason = reasonIn(message);
        String amount = amountIn(message);

        if (NotificationPrimaryActionResolver.isTransactionCategory(type) && t.contains("amenit")) {
            return amenity(c, message, t, m, label);
        }
        if (Notification.TYPE_PAYMENT.equals(type)) {
            String of = amount != null ? c.getString(R.string.nt_of_amount_format, amount) : "";
            String forWho = label != null ? c.getString(R.string.nt_for_label_format, label) : "";
            List<String> s = new ArrayList<>();
            if (t.contains("verified") || m.contains("was verified")) {
                s.add(c.getString(R.string.nt_payment_verified_s1, of, forWho));
                s.add(c.getString(R.string.nt_payment_verified_s2));
                s.add(c.getString(R.string.nt_payment_verified_s3));
                s.add(c.getString(R.string.nt_open_to_see_due_format, noun));
                return new Content(c.getString(R.string.nt_payment_verified_title), join(s), true);
            }
            if (has(t, "reject", "declin", "fail", "invalid") || has(m, "was rejected", "was declined")) {
                s.add(c.getString(R.string.nt_payment_rejected_s1, of, forWho));
                if (reason != null) s.add(c.getString(R.string.nt_reason_format, reason));
                s.add(c.getString(R.string.nt_payment_rejected_s2));
                s.add(c.getString(R.string.nt_open_to_see_due_format, noun));
                return new Content(c.getString(R.string.nt_payment_rejected_title), join(s), true);
            }
            if (has(t, "received", "pending", "awaiting", "validation") || m.contains("received")) {
                s.add(c.getString(R.string.nt_payment_received_s1, of, forWho));
                if (has(t, "pending", "awaiting", "validation") || has(m, "pending", "awaiting", "validat")) {
                    s.add(c.getString(R.string.nt_payment_received_waiting));
                }
                s.add(c.getString(R.string.nt_payment_received_s2));
                s.add(c.getString(R.string.nt_payment_received_s3));
                return new Content(c.getString(R.string.nt_payment_received_title), join(s), true);
            }
            return null;
        }
        if (Notification.TYPE_BOOKING.equals(type) || Notification.TYPE_RESERVATION.equals(type)) {
            List<String> s = new ArrayList<>();
            if (t.contains("confirm")) {
                s.add(c.getString(R.string.nt_confirmed_s1, cap(who)));
                if (!dates.isEmpty()) {
                    s.add(rooms.isEmpty() ? c.getString(R.string.nt_stay_on_format, dates) : c.getString(R.string.nt_stay_in_on_format, rooms, dates));
                }
                s.add(c.getString(R.string.nt_checkin_times_format, hhmm(StayCalendarModel.CHECK_IN_TIME), hhmm(StayCalendarModel.CHECK_OUT_TIME)));
                s.add(c.getString(R.string.nt_open_details_format, noun));
                return new Content(c.getString(R.string.nt_confirmed_title_format, label != null ? label : cap(c.getString(R.string.nt_your_noun_format, noun))), join(s), true);
            }
            boolean rejected = has(t, "reject", "declin");
            boolean cancelled = !rejected && t.contains("cancel");
            if (rejected || cancelled) {
                String verb = c.getString(rejected ? R.string.nt_verb_rejected : R.string.nt_verb_cancelled);
                s.add(dates.isEmpty() ? c.getString(R.string.nt_closed_s1_format, cap(who), verb)
                        : c.getString(R.string.nt_closed_s1_dates_format, cap(who), dates, verb));
                if (reason != null) s.add(c.getString(R.string.nt_reason_format, reason));
                s.add(c.getString(R.string.nt_closed_s2));
                s.add(c.getString(R.string.nt_open_details_format, noun));
                return new Content(c.getString(R.string.nt_closed_title_format, label != null ? label : cap(c.getString(R.string.nt_your_noun_format, noun)), verb), join(s), true);
            }
            return null;
        }
        if (Notification.TYPE_CHECK_IN.equals(type)) {
            String to = label != null ? c.getString(R.string.nt_on_label_format, label) : "";
            List<String> s = new ArrayList<>();
            if (has(t, "check-out", "checkout", "checked out", "checked-out", "check out")) {
                s.add(c.getString(R.string.nt_checkout_s1, to));
                s.add(c.getString(R.string.nt_checkout_s2));
                s.add(c.getString(R.string.nt_checkout_s3));
                return new Content(c.getString(R.string.nt_checkout_title), join(s), true);
            }
            if (has(t, "check-in", "checkin", "checked in", "checked-in", "check in")) {
                s.add(c.getString(R.string.nt_checkin_s1, to));
                if (!rooms.isEmpty()) s.add(c.getString(R.string.nt_your_room_format, rooms));
                LocalDate out = linked != null ? PaymentDates.parseDate(linked.getCheckOutDate()) : null;
                if (out != null) s.add(c.getString(R.string.nt_checkout_on_format, hhmm(StayCalendarModel.CHECK_OUT_TIME), DAY.format(out)));
                s.add(c.getString(R.string.nt_checkin_amenities));
                return new Content(c.getString(R.string.nt_checkin_title), join(s), true);
            }
            return null;
        }
        if (Notification.TYPE_PROMOTION.equals(type)) {
            if (title.isEmpty() && message.isEmpty()) return null;
            List<String> s = new ArrayList<>();
            if (!message.isEmpty()) s.add(sentence(message));
            for (String line : n.getCatalogDetails()) {
                int bar = line.indexOf('|');
                if (bar <= 0 || bar == line.length() - 1) continue;
                String key = line.substring(0, bar).trim();
                String value = line.substring(bar + 1).trim();
                if ("Valid".equalsIgnoreCase(key)) s.add(c.getString(R.string.nt_promo_valid_format, value));
                else if ("Room type".equalsIgnoreCase(key)) s.add(c.getString(R.string.nt_promo_room_format, value));
                else if ("Includes".equalsIgnoreCase(key)) s.add(c.getString(R.string.nt_promo_includes_format, value));
                else if ("Discount".equalsIgnoreCase(key)) s.add(c.getString(R.string.nt_promo_discount_format, value));
            }
            s.add(c.getString(R.string.nt_promo_browse));
            return new Content(title, join(s), true);
        }
        if (Notification.TYPE_ANNOUNCEMENT.equals(type) || Notification.TYPE_SYSTEM.equals(type)) {
            if (message.isEmpty()) return null;
            List<String> s = new ArrayList<>();
            s.add(sentence(message));
            s.add(c.getString(R.string.nt_system_from));
            String published = n.getPublishedAt() == null ? "" : n.getPublishedAt().trim();
            if (!published.isEmpty()) s.add(c.getString(R.string.nt_system_published_format, published));
            return new Content(title, join(s), true);
        }
        return null;
    }

    @Nullable
    private static Content amenity(Context c, String message, String t, String m, @Nullable String label) {
        Matcher match = AMENITY.matcher(message);
        if (!match.find()) return null;
        int quantity = Integer.parseInt(match.group(1));
        String item = match.group(2).trim();
        String room = match.group(3) == null ? null : match.group(3).trim();
        String amount = match.group(4) == null ? null : money(match.group(4));
        String verdict = match.group(5).toLowerCase(Locale.US);
        boolean approved = verdict.equals("approved");
        if (item.isEmpty()) return null;
        String forRoom = room != null ? c.getString(R.string.nt_for_room_format, room) : "";
        String onLabel = label != null ? c.getString(R.string.nt_on_label_format, label) : "";
        List<String> s = new ArrayList<>();
        if (approved) {
            s.add(c.getString(R.string.nt_amenity_approved_s1, quantity, item, forRoom, onLabel));
            s.add(amount != null ? c.getString(R.string.nt_amenity_added_amount_format, amount) : c.getString(R.string.nt_amenity_added));
            s.add(c.getString(R.string.nt_amenity_settle));
            if (label != null) s.add(c.getString(R.string.nt_amenity_open_total));
            return new Content(c.getString(R.string.nt_amenity_approved_title_format, item), join(s), true);
        }
        s.add(c.getString(R.string.nt_amenity_rejected_s1, quantity, item, forRoom, onLabel));
        String reason = reasonIn(message);
        if (reason != null) s.add(c.getString(R.string.nt_reason_format, reason));
        s.add(c.getString(R.string.nt_amenity_not_added));
        s.add(c.getString(R.string.nt_amenity_request_again));
        return new Content(c.getString(R.string.nt_amenity_rejected_title_format, item), join(s), true);
    }

    // ---- small helpers

    @NonNull
    private static String nz(@Nullable String s) {
        return s == null ? "" : s.trim();
    }

    private static boolean has(String text, String... words) {
        for (String w : words) if (text.contains(w)) return true;
        return false;
    }

    @Nullable
    private static String amountIn(String message) {
        Matcher match = AMOUNT.matcher(message);
        return match.find() ? money(match.group(1)) : null;
    }

    private static String money(String digits) {
        try {
            return MoneyFormat.format(Double.parseDouble(digits.replace(",", "")));
        } catch (NumberFormatException e) {
            return "₱" + digits;
        }
    }

    @Nullable
    private static String reasonIn(String message) {
        Matcher match = REASON.matcher(message);
        if (!match.find()) return null;
        String reason = match.group(1).trim();
        while (reason.endsWith(".")) reason = reason.substring(0, reason.length() - 1).trim();
        return reason.isEmpty() ? null : reason;
    }

    private static String hhmm(java.time.LocalTime time) {
        return time.format(CLOCK);
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** The text as one sentence that ends with a full stop (a server message may or may not have one). */
    private static String sentence(String text) {
        String s = text.trim();
        if (s.isEmpty()) return s;
        char last = s.charAt(s.length() - 1);
        return last == '.' || last == '!' || last == '?' ? s : s + ".";
    }

    private static String join(List<String> sentences) {
        StringBuilder out = new StringBuilder();
        for (String s : sentences) {
            if (s == null || s.trim().isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(s.trim());
        }
        return out.toString();
    }
}
