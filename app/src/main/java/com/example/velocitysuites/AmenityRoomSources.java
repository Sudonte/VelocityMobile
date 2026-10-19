package com.example.velocitysuites;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.velocitysuites.network.dto.AdditionalAmenityInfoDto;
import com.example.velocitysuites.network.dto.AdditionalAmenityRequestDto;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where "which room is this amenity for?" comes from, in the order they are trusted:
 * <ol>
 *   <li><b>(a) the amenity-requests API</b> - {@code GET guest/bookings/{id}/additional-amenities} lists the guest's own requests with
 *       room, item, quantity, price and status (pending / approved / rejected); cached here when a screen loads it;</li>
 *   <li><b>(b) the stay bill</b> - an approved line that carries a {@code room_number} (see {@link StayInfo.AmenityLine});</li>
 *   <li><b>(c) this phone's own submissions</b> - what the guest picked on the request screen, saved when the request is sent
 *       (works only on the phone that made it);</li>
 *   <li><b>(d) amenity notifications</b> - the "(Room 1-001)" the server writes into "Amenity Request Approved".</li>
 * </ol>
 * Sources only ever LINK a line to a room; {@link TransactionRooms} takes the amounts from the bill, so a line seen by two sources is
 * still counted once.
 */
final class AmenityRoomSources {

    private static final String PREFS = "amenity_room_records";
    private static final long FRESH_MS = 15_000L;
    private static final Gson GSON = new Gson();

    private static final Map<String, List<AmenityRecord>> API = new ConcurrentHashMap<>();
    private static final Map<String, Long> API_AT = new ConcurrentHashMap<>();
    private static final Map<String, List<AmenityRecord>> LOCAL = new ConcurrentHashMap<>();
    private static volatile List<Notification> notifications = Collections.emptyList();
    private static volatile boolean localLoaded;

    private AmenityRoomSources() {
    }

    /** One key per transaction ("B567" for a booking, "R567" for a reservation). */
    @NonNull
    static String key(@NonNull Booking b) {
        return (b.isDirectBooking() ? "B" : "R") + b.getId();
    }

    // ---- (a) the amenity-requests API

    /** Views showing this transaction's rooms, redrawn when its request list arrives (a view that left the screen is dropped). */
    private static final Map<String, List<Runnable>> LISTENERS = new ConcurrentHashMap<>();

    static void listen(@NonNull Booking b, @NonNull Runnable onChanged) {
        LISTENERS.computeIfAbsent(key(b), k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(onChanged);
    }

    /** Stores the server's request list and redraws every view that shows this transaction's rooms. */
    static void publish(@NonNull Booking b, @Nullable AdditionalAmenityInfoDto info) {
        putApi(b, info);
        List<Runnable> listeners = LISTENERS.get(key(b));
        if (listeners == null) return;
        for (Runnable r : listeners) r.run();
    }

    /** Called by a listener whose view is gone. */
    static void unlisten(@NonNull Booking b, @NonNull Runnable onChanged) {
        List<Runnable> listeners = LISTENERS.get(key(b));
        if (listeners != null) listeners.remove(onChanged);
    }

    static void putApi(@NonNull Booking b, @Nullable AdditionalAmenityInfoDto info) {
        if (info == null) return;
        List<AmenityRecord> out = new ArrayList<>();
        if (info.requests != null) {
            for (AdditionalAmenityRequestDto r : info.requests) {
                if (r == null) continue;
                out.add(new AmenityRecord(r.id, r.name, r.quantity, r.subtotal > 0 ? r.subtotal : r.unit_price * Math.max(1, r.quantity),
                        r.room_number, r.room_type, statusOf(r.status)));
            }
        }
        API.put(key(b), out);
        API_AT.put(key(b), System.currentTimeMillis());
    }

    @NonNull
    private static AmenityRecord.Status statusOf(@Nullable String status) {
        String s = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
        if (s.equals("approved")) return AmenityRecord.Status.APPROVED;
        if (s.equals("rejected") || s.equals("declined")) return AmenityRecord.Status.REJECTED;
        return AmenityRecord.Status.PENDING;
    }

    /** The requests the server listed for this transaction, or null when it has not been asked (or the server could not answer). */
    @Nullable
    static List<AmenityRecord> api(@NonNull Booking b) {
        return API.get(key(b));
    }

    // ---- (c) what this phone submitted

    /** Saves what the guest picked on the request screen (item, quantity, price, chosen room) so approved lines can be matched later. */
    static void remember(@NonNull Context context, @NonNull Booking b, @NonNull List<AmenityRecord> submitted) {
        if (submitted.isEmpty()) return;
        load(context);
        List<AmenityRecord> all = new ArrayList<>(LOCAL.getOrDefault(key(b), Collections.emptyList()));
        all.addAll(submitted);
        LOCAL.put(key(b), all);
        prefs(context).edit().putString(key(b), GSON.toJson(all)).apply();
    }

    @NonNull
    static List<AmenityRecord> local(@NonNull Booking b) {
        List<AmenityRecord> list = LOCAL.get(key(b));
        return list == null ? Collections.emptyList() : list;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void load(Context context) {
        if (localLoaded) return;
        localLoaded = true;
        try {
            for (Map.Entry<String, ?> e : prefs(context).getAll().entrySet()) {
                if (!(e.getValue() instanceof String)) continue;
                List<AmenityRecord> list = GSON.fromJson((String) e.getValue(), new TypeToken<List<AmenityRecord>>() { }.getType());
                if (list != null) LOCAL.putIfAbsent(e.getKey(), list);
            }
        } catch (RuntimeException ignored) {
            // an unreadable saved record is simply not used
        }
    }

    // ---- (d) amenity notifications

    @NonNull
    static List<AmenityRecord> fromNotifications(@NonNull Booking b) {
        List<AmenityRecord> out = new ArrayList<>();
        String id = String.valueOf(b.getId());
        for (Notification n : notifications) {
            if (n == null || n.getReferenceId() == null || !id.equals(n.getReferenceId())) continue;
            AmenityRecord r = NotificationText.amenityRecordIn(n.getMessage());
            if (r != null && r.status == AmenityRecord.Status.APPROVED) out.add(r);
        }
        return out;
    }

    // ---- loading

    /** Brings the local records and the notifications into memory (cheap; call before building the view). */
    static void attach(@NonNull Context context) {
        load(context);
        try {
            notifications = new ArrayList<>(RoomRepository.getInstance(context).getNotifications());
        } catch (RuntimeException ignored) {
            // no notifications loaded yet
        }
    }

    /** Whether this transaction can have amenity requests at all (a stay that is, or was, in house). */
    static boolean mayHaveRequests(@NonNull Booking b) {
        if (!b.isHasBooking()) return false;
        String s = b.getStatus() == null ? "" : b.getStatus();
        return b.getStay() != null || "Checked-In".equalsIgnoreCase(s) || "Checked-Out".equalsIgnoreCase(s) || "Completed".equalsIgnoreCase(s);
    }

    /**
     * Asks the server for this transaction's amenity requests (source a) unless they were fetched a moment ago, then calls
     * the views listening to this transaction are redrawn. A server that cannot answer leaves the other sources in charge.
     */
    static void fetch(@NonNull Context context, @NonNull Booking b) {
        attach(context);
        if (!mayHaveRequests(b)) return;
        Long at = API_AT.get(key(b));
        if (at != null && System.currentTimeMillis() - at < FRESH_MS) return;
        try {
            RoomRepository.getInstance(context).fetchAdditionalAmenities(b.isDirectBooking(), b.getId(),
                    new RoomRepository.RepositoryCallback<AdditionalAmenityInfoDto>() {
                        @Override
                        public void onSuccess(AdditionalAmenityInfoDto info) {
                            publish(b, info);
                        }

                        @Override
                        public void onError(String message) {
                            // keep whatever the other sources know
                        }
                    });
        } catch (RuntimeException ignored) {
            // no network layer (or none that can answer): the other sources stay in charge
        }
    }

    /** Forgets everything (sign-out, and between tests). */
    static void clear(@Nullable Context context) {
        API.clear();
        API_AT.clear();
        LISTENERS.clear();
        LOCAL.clear();
        notifications = Collections.emptyList();
        localLoaded = false;
        if (context != null) prefs(context).edit().clear().apply();
    }
}
