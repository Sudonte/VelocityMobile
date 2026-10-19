package com.example.velocitysuites;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.velocitysuites.network.ApiMapper;
import com.example.velocitysuites.network.dto.AnnouncementDto;
import com.example.velocitysuites.network.dto.DiscountDto;
import com.example.velocitysuites.network.dto.PromotionDto;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * "Promotions &amp; discounts" and "System" notifications built IN THE APP from the two catalog endpoints it already calls for the
 * landing page (promotions and announcements). The notifications API itself never delivers these to a guest, so each active promotion
 * becomes a Promotion notification and each guest-audience announcement an Announcement ("System") notification, dated by its start /
 * published date, with a stable id ("promo-12" / "news-5") so the same item never appears twice.
 * <p>
 * Whether a guest has read one is kept on the phone (there is no server row to flip), per guest and per item id, in
 * {@link ReadStore}; it counts toward the unread badge like any other notification.
 */
final class CatalogNotifications {

    static final String PROMO_PREFIX = "promo-";
    static final String NEWS_PREFIX = "news-";
    static final String DISCOUNT_PREFIX = "disc-";
    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");
    private static final DateTimeFormatter RANGE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    private CatalogNotifications() {
    }

    static boolean isCatalogId(@Nullable String id) {
        return id != null && (id.startsWith(PROMO_PREFIX) || id.startsWith(NEWS_PREFIX) || id.startsWith(DISCOUNT_PREFIX));
    }

    /** Only promotions running right now: started (or no start date) and not yet past their last day (or no end date). */
    @NonNull
    static List<Notification> fromPromotions(@Nullable List<PromotionDto> dtos, long nowMillis, @NonNull ReadStore reads) {
        List<Notification> out = new ArrayList<>();
        if (dtos == null) return out;
        Set<String> seen = new HashSet<>();
        for (PromotionDto dto : dtos) {
            if (dto == null) continue;
            String id = PROMO_PREFIX + dto.id;
            if (!seen.add(id)) continue; // deduplicated by id
            LocalDate start = parseDate(dto.start_date);
            LocalDate end = parseDate(dto.end_date);
            long startMillis = start == null ? 0 : start.atStartOfDay(MANILA).toInstant().toEpochMilli();
            long endExclusive = end == null ? Long.MAX_VALUE : end.plusDays(1).atStartOfDay(MANILA).toInstant().toEpochMilli();
            if (nowMillis < startMillis || nowMillis >= endExclusive) continue;
            String title = dto.promo_name == null || dto.promo_name.trim().isEmpty() ? "Promotion" : dto.promo_name.trim();
            String message = dto.description == null ? "" : dto.description.trim();
            long created = startMillis > 0 ? startMillis : 1L;
            Notification n = new Notification(id, title, message, "", Notification.TYPE_PROMOTION, reads.isRead(id), null, null,
                    start == null ? "" : RANGE.format(start), null, null, created);
            List<String> lines = new ArrayList<>();
            if (start != null && end != null) lines.add("Valid|" + RANGE.format(start) + " – " + RANGE.format(end));
            else if (end != null) lines.add("Valid|Until " + RANGE.format(end));
            if (dto.room_type != null && dto.room_type.name != null) lines.add("Room type|" + dto.room_type.name);
            if (dto.amenities != null) {
                StringBuilder includes = new StringBuilder();
                for (PromotionDto.PromotionAmenityDto a : dto.amenities) {
                    if (a == null || a.amenity_name == null) continue;
                    if (includes.length() > 0) includes.append(", ");
                    includes.append(a.pivot != null && a.pivot.quantity > 1 ? a.pivot.quantity + "× " : "").append(a.amenity_name);
                }
                if (includes.length() > 0) lines.add("Includes|" + includes);
            }
            n.setCatalogDetails(lines);
            out.add(n);
        }
        return out;
    }

    /** Standing discounts (Senior Citizen, PWD ...) from the same Promotions &amp; Discounts module: only active ones inside their optional window. */
    @NonNull
    static List<Notification> fromDiscounts(@Nullable List<DiscountDto> dtos, long nowMillis, @NonNull ReadStore reads) {
        List<Notification> out = new ArrayList<>();
        if (dtos == null) return out;
        Set<String> seen = new HashSet<>();
        for (DiscountDto dto : dtos) {
            if (dto == null) continue;
            String id = DISCOUNT_PREFIX + dto.id;
            if (!seen.add(id)) continue;
            if (dto.status != null && !"active".equalsIgnoreCase(dto.status.trim())) continue;
            LocalDate start = parseDate(dto.start_date);
            LocalDate end = parseDate(dto.end_date);
            long startMillis = start == null ? 0 : start.atStartOfDay(MANILA).toInstant().toEpochMilli();
            long endExclusive = end == null ? Long.MAX_VALUE : end.plusDays(1).atStartOfDay(MANILA).toInstant().toEpochMilli();
            if (nowMillis < startMillis || nowMillis >= endExclusive) continue;
            String title = dto.name == null || dto.name.trim().isEmpty() ? "Discount" : dto.name.trim();
            String message = dto.description == null ? "" : dto.description.trim();
            long created = startMillis > 0 ? startMillis : Math.max(1L, ApiMapper.epochMillis(dto.created_at));
            Notification n = new Notification(id, title, message, "", Notification.TYPE_PROMOTION, reads.isRead(id), null, null,
                    start == null ? "" : RANGE.format(start), null, null, created);
            List<String> lines = new ArrayList<>();
            double value = dto.valueAsDouble();
            if (value > 0) {
                boolean percent = dto.discount_type != null && dto.discount_type.toLowerCase(Locale.US).startsWith("percent");
                lines.add("Discount|" + (percent ? trim(value) + "% off" : "\u20b1" + trim(value) + " off"));
            }
            if (start != null && end != null) lines.add("Valid|" + RANGE.format(start) + " \u2013 " + RANGE.format(end));
            else if (end != null) lines.add("Valid|Until " + RANGE.format(end));
            n.setCatalogDetails(lines);
            out.add(n);
        }
        return out;
    }

    private static String trim(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format(Locale.US, "%.2f", v);
    }

    /** Announcements for guests (no audience list means everyone). */
    @NonNull
    static List<Notification> fromAnnouncements(@Nullable List<AnnouncementDto> dtos, @NonNull ReadStore reads) {
        List<Notification> out = new ArrayList<>();
        if (dtos == null) return out;
        Set<String> seen = new HashSet<>();
        for (AnnouncementDto dto : dtos) {
            if (dto == null) continue;
            String id = NEWS_PREFIX + dto.id;
            if (!seen.add(id)) continue;
            if (dto.target_audience != null && !dto.target_audience.isEmpty() && !dto.target_audience.contains("guest")) continue;
            String title = dto.title == null || dto.title.trim().isEmpty() ? "Announcement" : dto.title.trim();
            String message = dto.content == null ? "" : dto.content.trim();
            long created = ApiMapper.epochMillis(dto.published_at);
            if (created <= 0) created = 1L;
            out.add(new Notification(id, title, message, "", Notification.TYPE_ANNOUNCEMENT, reads.isRead(id), null, dto.target_audience,
                    ApiMapper.absoluteDateTime(dto.published_at), null, null, created));
        }
        return out;
    }

    /**
     * The API's own notifications with the catalog ones merged in, newest first. A catalog item whose type and title the API already
     * delivered is left out (the backend may send its own copy one day - never two rows for one announcement).
     */
    @NonNull
    static List<Notification> merge(@NonNull List<Notification> fromApi, @NonNull List<Notification> catalog) {
        Set<String> apiKeys = new HashSet<>();
        for (Notification n : fromApi) apiKeys.add(key(n));
        List<Notification> all = new ArrayList<>(fromApi);
        for (Notification c : catalog) if (!apiKeys.contains(key(c))) all.add(c);
        // Stable: rows with the same instant (or none) keep their original order.
        java.util.Collections.sort(all, (a, b) -> Long.compare(b.getCreatedAtMillis(), a.getCreatedAtMillis()));
        return all;
    }

    private static String key(Notification n) {
        return (n.getType() == null ? "" : n.getType()) + "|" + (n.getTitle() == null ? "" : n.getTitle().trim().toLowerCase(Locale.US));
    }

    @Nullable
    private static LocalDate parseDate(@Nullable String iso) {
        if (iso == null || iso.trim().length() < 10) return null;
        try {
            return LocalDate.parse(iso.trim().substring(0, 10));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Per-guest, per-item read state for catalog notifications, kept in SharedPreferences. */
    static final class ReadStore {
        private static final String PREFS = "catalog_notification_reads";
        private final SharedPreferences prefs;
        private String guestKey = "0";

        ReadStore(@NonNull Context context) {
            prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            guestKey = String.valueOf(context.getApplicationContext().getSharedPreferences("VelocityPrefs", Context.MODE_PRIVATE).getLong("userId", 0L));
        }

        /** Re-reads which guest is signed in (a new login changes it). */
        void refreshGuest(@NonNull Context context) {
            guestKey = String.valueOf(context.getApplicationContext().getSharedPreferences("VelocityPrefs", Context.MODE_PRIVATE).getLong("userId", 0L));
        }

        private String keyFor(String id) {
            return guestKey + ":" + id;
        }

        boolean isRead(@NonNull String id) {
            return prefs.getBoolean(keyFor(id), false);
        }

        void setRead(@NonNull String id, boolean read) {
            prefs.edit().putBoolean(keyFor(id), read).apply();
        }
    }
}
