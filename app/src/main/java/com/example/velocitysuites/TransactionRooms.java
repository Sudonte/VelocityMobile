package com.example.velocitysuites;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Your rooms": every room of a transaction with the amenities requested FOR THAT ROOM. Pure data (the one shared model behind
 * {@link TransactionRoomsView}, so the Next Stay details, Booking Details, Transaction History and the Upcoming sheet cannot drift).
 *
 * <p><b>Amounts always come from the bill.</b> The stay bill says what was approved and what it costs; the other sources
 * ({@link AmenityRoomSources}: the requests API, a room on the bill line, this phone's saved submissions, the approval
 * notifications) only say WHICH ROOM a line belongs to. Each source line is matched against what the bill still has unassigned, so a
 * line two sources both know is placed once and never counted twice, and whatever no source can place goes under "Other amenities".
 *
 * <p>Only approved amenities are counted. Requests still waiting and ones the front desk turned down are listed under their room
 * but are never part of any total.
 */
final class TransactionRooms {

    /** One room: its number once assigned at check-in, else only its type. */
    static final class RoomItem {
        @Nullable final String number;
        final String type;

        RoomItem(@Nullable String number, String type) {
            this.number = AmenityRecord.normalizeRoom(number);
            this.type = type == null ? "" : type.trim();
        }
    }

    /** One amenity (or several requests of it added up). */
    static final class AmenityItem {
        final String name;
        final int quantity;
        final double subtotal;

        AmenityItem(String name, int quantity, double subtotal) {
            this.name = name;
            this.quantity = quantity;
            this.subtotal = subtotal;
        }
    }

    /** An amenity line for grouping; {@code roomNumber} is null when no source links it to a room. */
    static final class Line {
        final String name;
        final int quantity;
        final double subtotal;
        @Nullable final String roomNumber;

        Line(String name, int quantity, double subtotal, @Nullable String roomNumber) {
            this.name = name == null ? "" : name.trim();
            this.quantity = Math.max(1, quantity);
            this.subtotal = subtotal;
            this.roomNumber = AmenityRecord.normalizeRoom(roomNumber);
        }
    }

    /** One room card (or the "Other amenities" card, whose {@code room} is null). */
    static final class Card {
        @Nullable final RoomItem room;
        final List<AmenityItem> approved = new ArrayList<>();
        final List<AmenityItem> waiting = new ArrayList<>();
        final List<AmenityItem> rejected = new ArrayList<>();
        double subtotal;

        Card(@Nullable RoomItem room) {
            this.room = room;
        }

        boolean isEmpty() {
            return approved.isEmpty() && waiting.isEmpty() && rejected.isEmpty();
        }
    }

    /** The sources that placed at least one line under a room (for diagnostics and tests). */
    enum Source { API, BILL, LOCAL, NOTIFICATION }

    /** One card per room, ordered by room number. */
    final List<Card> cards;
    /** Approved / waiting / rejected lines no source could place in a room; null when there are none. */
    @Nullable final Card other;
    final double amenitiesTotal;
    final List<Source> linkedBy;

    private TransactionRooms(List<Card> cards, @Nullable Card other, double total, List<Source> linkedBy) {
        this.cards = cards;
        this.other = other;
        this.amenitiesTotal = total;
        this.linkedBy = linkedBy;
    }

    /** The room cards plus the "Other amenities" card, when there is one. */
    List<RoomItem> rooms() {
        List<RoomItem> out = new ArrayList<>();
        for (Card c : cards) out.add(c.room);
        return out;
    }

    @Nullable
    Card cardFor(@Nullable String roomNumber) {
        String key = AmenityRecord.roomKey(roomNumber);
        if (key.isEmpty()) return null;
        for (Card c : cards) if (c.room != null && key.equals(AmenityRecord.roomKey(c.room.number))) return c;
        return null;
    }

    boolean hasAnyApproved() {
        for (Card c : cards) if (!c.approved.isEmpty()) return true;
        return other != null && !other.approved.isEmpty();
    }

    // ---- grouping

    /** One item per amenity name (case-insensitive), quantities and amounts added up. */
    static List<AmenityItem> grouped(List<Line> lines) {
        Map<String, double[]> sums = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        for (Line l : lines) {
            String key = l.name.toLowerCase(Locale.ROOT);
            double[] sum = sums.get(key);
            if (sum == null) {
                sum = new double[2];
                sums.put(key, sum);
                names.put(key, l.name);
            }
            sum[0] += l.quantity;
            sum[1] += l.subtotal;
        }
        List<AmenityItem> out = new ArrayList<>();
        for (Map.Entry<String, double[]> e : sums.entrySet()) out.add(new AmenityItem(names.get(e.getKey()), (int) Math.round(e.getValue()[0]), e.getValue()[1]));
        return out;
    }

    // ---- building it from a transaction

    @NonNull
    static TransactionRooms of(@NonNull Booking b) {
        return of(b, AmenityRoomSources.api(b), AmenityRoomSources.local(b), AmenityRoomSources.fromNotifications(b));
    }

    /**
     * @param api           the requests the amenity-requests API listed (source a), or null when it was not asked / did not answer
     * @param local         what this phone submitted (source c)
     * @param notifications approved amenity notifications of this transaction (source d)
     */
    @NonNull
    static TransactionRooms of(@NonNull Booking b, @Nullable List<AmenityRecord> api, @NonNull List<AmenityRecord> local,
                               @NonNull List<AmenityRecord> notifications) {
        List<RoomItem> rooms = roomsOf(b);
        StayInfo stay = b.getStay();

        // What the bill says is approved (the amounts), and the requests' states.
        List<AmenityRecord> bill = new ArrayList<>();
        List<AmenityRecord> waiting = new ArrayList<>();
        List<AmenityRecord> rejected = new ArrayList<>();
        if (stay != null) {
            for (StayInfo.AmenityLine a : stay.approvedAmenities) {
                bill.add(new AmenityRecord(0, a.name, a.quantity, a.subtotal, a.roomNumber, null, AmenityRecord.Status.APPROVED));
            }
            if (api == null) {
                for (StayInfo.RequestedAmenity r : stay.requestedAmenities) {
                    (r.rejected ? rejected : waiting).add(new AmenityRecord(0, r.name, r.quantity, r.subtotal, r.roomNumber, null,
                            r.rejected ? AmenityRecord.Status.REJECTED : AmenityRecord.Status.PENDING));
                }
            }
        } else {
            for (BookingAmenity a : b.getAmenities()) {
                bill.add(new AmenityRecord(0, a.getAmenityName(), a.getQuantity(), a.getSubtotal(), null, null, AmenityRecord.Status.APPROVED));
            }
        }
        List<AmenityRecord> apiApproved = new ArrayList<>();
        if (api != null) {
            for (AmenityRecord r : api) {
                if (r.status == AmenityRecord.Status.APPROVED) apiApproved.add(r);
                else if (r.status == AmenityRecord.Status.PENDING) waiting.add(r);
                else rejected.add(r);
            }
        }

        // Approved lines with the room they were placed in, per source.
        List<Line> placed = new ArrayList<>();
        List<Line> unplaced = new ArrayList<>();
        List<Source> linkedBy = new ArrayList<>();
        if (stay != null) {
            // Bill present: it is the truth. Each source only names rooms for what the bill still has left.
            Map<String, double[]> left = new LinkedHashMap<>();
            Map<String, String> names = new LinkedHashMap<>();
            for (AmenityRecord r : bill) {
                double[] c = left.computeIfAbsent(r.nameKey(), k -> new double[2]);
                names.putIfAbsent(r.nameKey(), r.name);
                c[0] += r.quantity;
                c[1] += r.subtotal;
            }
            place(apiApproved, Source.API, left, names, placed, linkedBy);
            place(bill, Source.BILL, left, names, placed, linkedBy);
            place(local, Source.LOCAL, left, names, placed, linkedBy);
            place(notifications, Source.NOTIFICATION, left, names, placed, linkedBy);
            for (Map.Entry<String, double[]> e : left.entrySet()) {
                if (e.getValue()[0] > 0.5) unplaced.add(new Line(names.get(e.getKey()), (int) Math.round(e.getValue()[0]), Math.max(0, e.getValue()[1]), null));
            }
        } else {
            // No stay bill yet: only what the amenity-requests API says is approved can be shown.
            for (AmenityRecord r : apiApproved) {
                Line line = new Line(r.name, r.quantity, Math.max(0, r.subtotal), r.room);
                (line.roomNumber != null ? placed : unplaced).add(line);
                if (line.roomNumber != null && !linkedBy.contains(Source.API)) linkedBy.add(Source.API);
            }
            for (AmenityRecord r : bill) unplaced.add(new Line(r.name, r.quantity, r.subtotal, null));
        }

        return assemble(rooms, placed, unplaced, waiting, rejected, linkedBy);
    }

    /** Names a room for as much of each item as the bill still has unassigned; the amounts are the bill's own. */
    private static void place(List<AmenityRecord> source, Source tag, Map<String, double[]> left, Map<String, String> names,
                              List<Line> placed, List<Source> linkedBy) {
        for (AmenityRecord r : source) {
            if (r.room == null) continue;
            double[] c = left.get(r.nameKey());
            if (c == null || c[0] < 0.5) continue;
            int take = (int) Math.min(r.quantity, Math.round(c[0]));
            double amount = c[1] * take / c[0];
            c[0] -= take;
            c[1] -= amount;
            placed.add(new Line(names.get(r.nameKey()), take, amount, r.room));
            if (!linkedBy.contains(tag)) linkedBy.add(tag);
        }
    }

    private static TransactionRooms assemble(List<RoomItem> rooms, List<Line> placed, List<Line> unplaced, List<AmenityRecord> waiting,
                                             List<AmenityRecord> rejected, List<Source> linkedBy) {
        Map<String, Card> byKey = new LinkedHashMap<>();
        List<Card> cards = new ArrayList<>();
        for (RoomItem room : rooms) {
            Card card = new Card(room);
            cards.add(card);
            if (room.number != null) byKey.putIfAbsent(AmenityRecord.roomKey(room.number), card);
        }
        Card other = new Card(null);
        double total = 0;

        Map<String, List<Line>> approvedByRoom = new LinkedHashMap<>();
        for (Line l : placed) approvedByRoom.computeIfAbsent(AmenityRecord.roomKey(l.roomNumber), k -> new ArrayList<>()).add(l);
        for (Map.Entry<String, List<Line>> e : approvedByRoom.entrySet()) {
            Card card = byKey.get(e.getKey());
            if (card == null) { // a source names a room the transaction does not list: believe the source
                card = new Card(new RoomItem(e.getValue().get(0).roomNumber, ""));
                byKey.put(e.getKey(), card);
                cards.add(card);
            }
            card.approved.addAll(grouped(e.getValue()));
        }
        if (!unplaced.isEmpty()) other.approved.addAll(grouped(unplaced));

        addStates(waiting, byKey, cards, other, true);
        addStates(rejected, byKey, cards, other, false);

        for (Card c : cards) {
            c.subtotal = sum(c.approved);
            total += c.subtotal;
        }
        other.subtotal = sum(other.approved);
        total += other.subtotal;
        Collections.sort(cards, BY_ROOM_NUMBER);
        return new TransactionRooms(Collections.unmodifiableList(cards), other.isEmpty() ? null : other, total, linkedBy);
    }

    private static void addStates(List<AmenityRecord> records, Map<String, Card> byKey, List<Card> cards, Card other, boolean waiting) {
        Map<Card, List<Line>> perCard = new LinkedHashMap<>();
        for (AmenityRecord r : records) {
            Card card = r.room == null ? other : byKey.get(AmenityRecord.roomKey(r.room));
            if (card == null) {
                card = new Card(new RoomItem(r.room, r.roomType));
                byKey.put(AmenityRecord.roomKey(r.room), card);
                cards.add(card);
            }
            perCard.computeIfAbsent(card, k -> new ArrayList<>()).add(new Line(r.name, r.quantity, Math.max(0, r.subtotal), null));
        }
        for (Map.Entry<Card, List<Line>> e : perCard.entrySet()) (waiting ? e.getKey().waiting : e.getKey().rejected).addAll(grouped(e.getValue()));
    }

    private static double sum(List<AmenityItem> items) {
        double s = 0;
        for (AmenityItem i : items) s += i.subtotal;
        return s;
    }

    /** Room numbers in reading order ("2-001" before "10-001", "602" after "1-001"); rooms without a number last, in their given order. */
    private static final Comparator<Card> BY_ROOM_NUMBER = (a, b) -> {
        String x = a.room == null ? null : a.room.number;
        String y = b.room == null ? null : b.room.number;
        if (x == null && y == null) return 0;
        if (x == null) return 1;
        if (y == null) return -1;
        return naturalCompare(x, y);
    };

    static int naturalCompare(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int si = i;
                int sj = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) i++;
                while (j < b.length() && Character.isDigit(b.charAt(j))) j++;
                String na = a.substring(si, i).replaceFirst("^0+(?=.)", "");
                String nb = b.substring(sj, j).replaceFirst("^0+(?=.)", "");
                if (na.length() != nb.length()) return na.length() - nb.length();
                int c = na.compareTo(nb);
                if (c != 0) return c;
            } else {
                int c = Character.compare(Character.toLowerCase(ca), Character.toLowerCase(cb));
                if (c != 0) return c;
                i++;
                j++;
            }
        }
        return (a.length() - i) - (b.length() - j);
    }

    private static List<RoomItem> roomsOf(Booking b) {
        List<RoomItem> rooms = new ArrayList<>();
        StayInfo stay = b.getStay();
        if (stay != null && !stay.rooms.isEmpty()) {
            for (StayInfo.RoomLine r : stay.rooms) rooms.add(new RoomItem(r.roomNumber, r.roomType));
        } else if (!b.getRooms().isEmpty()) {
            for (BookingRoom r : b.getRooms()) {
                List<String> assigned = r.getAssignedRoomNumbers();
                int count = Math.max(1, r.getQuantity());
                for (int i = 0; i < count; i++) {
                    rooms.add(new RoomItem(assigned != null && i < assigned.size() ? assigned.get(i) : null, r.getRoomTypeName()));
                }
            }
        } else {
            String type = b.getRoomType() != null && !b.getRoomType().trim().isEmpty() ? b.getRoomType() : b.getRoomName();
            int count = Math.max(1, b.getRoomsRequested());
            for (int i = 0; i < count; i++) rooms.add(new RoomItem(i == 0 ? b.getRoomNumber() : null, type));
        }
        return rooms;
    }
}
