package com.example.velocitysuites;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.velocitysuites.network.dto.StayBillDto;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The server's itemized stay, ready to show: the booked dates, the nights billed and the total. The hotel has no refunds, so the
 * nights are always the BOOKED nights (an early check-out is still charged in full; a late check-out is a charge the front desk
 * adds). It carries the server's own figures (the same StayBill the front desk's check-out bill is built from) - nothing here is
 * calculated on the phone, so the guest's receipt, Booking Details and Payment History always agree with the receptionist's bill.
 */
public final class StayInfo implements Serializable {

    /** One physical room's line: rate x its own nights. */
    public static final class RoomLine implements Serializable {
        public final String roomNumber;
        public final String roomType;
        public final String roomTypeId;
        public final double rate;
        public final int nights;
        public final double subtotal;
        public final boolean checkedOut;
        @Nullable public final String checkedOutOn;

        RoomLine(String roomNumber, String roomType, String roomTypeId, double rate, int nights,
                 double subtotal, boolean checkedOut, @Nullable String checkedOutOn) {
            this.roomNumber = roomNumber;
            this.roomType = roomType;
            this.roomTypeId = roomTypeId;
            this.rate = rate;
            this.nights = nights;
            this.subtotal = subtotal;
            this.checkedOut = checkedOut;
            this.checkedOutOn = checkedOutOn;
        }
    }

    /** A label/value pair for a details or receipt row. */
    public static final class Row implements Serializable {
        public final String label;
        public final String value;

        Row(String label, String value) {
            this.label = label;
            this.value = value;
        }
    }

    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

    public final String checkIn;
    public final String scheduledCheckOut;
    public final int scheduledNights;
    /** The nights billed - the booked nights (an older settled bill may carry a different, recorded count). */
    public final int actualNights;
    public final double roomCharge;
    public final double discount;
    @Nullable public final String discountName;
    public final double total;
    public final List<RoomLine> rooms;
    /** The 3rd receipt's balance breakdown, when the stay was settled at check-in. */
    @Nullable public final BalanceBreakdown balanceBreakdown;
    /** "Name x qty" / amount, one per approved additional amenity. */
    private final List<String[]> additionalAmenities;
    /** The approved amenities with their amounts - they make up the remaining balance, payable at check-out. */
    public final List<AmenityLine> approvedAmenities;
    public final double approvedAmenitiesTotal;
    /** Requests still waiting for the front desk, and ones it turned down: shown apart, never counted. */
    public final List<RequestedAmenity> requestedAmenities;

    /** One approved amenity: name, quantity and what it adds to the balance. */
    public static final class AmenityLine implements Serializable {
        public final String name;
        public final int quantity;
        public final double subtotal;
        /** The room it was requested for, or null when the server does not link the line to a room. */
        @Nullable public final String roomNumber;

        AmenityLine(String name, int quantity, double subtotal, @Nullable String roomNumber) {
            this.name = name != null ? name : "";
            this.quantity = Math.max(1, quantity);
            this.subtotal = subtotal;
            this.roomNumber = roomNumber;
        }
    }

    /** One amenity request that is not on the bill. */
    public static final class RequestedAmenity implements Serializable {
        public final String name;
        public final int quantity;
        public final double subtotal;
        public final boolean rejected;
        @Nullable public final String reason;
        @Nullable public final String roomNumber;

        RequestedAmenity(String name, int quantity, double subtotal, boolean rejected, @Nullable String reason, @Nullable String roomNumber) {
            this.roomNumber = roomNumber;
            this.name = name != null ? name : "";
            this.quantity = Math.max(1, quantity);
            this.subtotal = subtotal;
            this.rejected = rejected;
            this.reason = reason;
        }
    }

    private StayInfo(StayBillDto d, List<RoomLine> rooms) {
        List<String[]> extras = new ArrayList<>();
        List<AmenityLine> approved = new ArrayList<>();
        if (d.additional_amenities != null) {
            for (StayBillDto.Amenity a : d.additional_amenities) {
                if (a == null) continue;
                extras.add(new String[]{a.amenity_name + " × " + a.quantity, MoneyFormat.format(a.subtotal)});
                approved.add(new AmenityLine(a.amenity_name, a.quantity, a.subtotal, a.room_number));
            }
        }
        this.additionalAmenities = Collections.unmodifiableList(extras);
        this.approvedAmenities = Collections.unmodifiableList(approved);
        this.approvedAmenitiesTotal = d.additional_amenities_total;
        List<RequestedAmenity> requested = new ArrayList<>();
        if (d.requested_amenities != null) {
            for (StayBillDto.RequestedAmenity r : d.requested_amenities) {
                if (r != null) requested.add(new RequestedAmenity(r.amenity_name, r.quantity, r.subtotal, "rejected".equals(r.status), r.reason, r.room_number));
            }
        }
        this.requestedAmenities = Collections.unmodifiableList(requested);
        this.checkIn = d.check_in;
        this.scheduledCheckOut = d.scheduled_check_out;
        this.scheduledNights = Math.max(1, d.scheduled_nights);
        this.actualNights = Math.max(1, d.actual_nights);
        this.roomCharge = d.room_charge;
        this.discount = d.discount;
        this.discountName = d.discount_name;
        this.total = d.total;
        this.rooms = Collections.unmodifiableList(rooms);
        this.balanceBreakdown = BalanceBreakdown.from(d.receipt_breakdown);
    }

    /** Null in, null out - a stay that is not in house yet has no itemized bill. */
    @Nullable
    public static StayInfo from(@Nullable StayBillDto dto) {
        if (dto == null || dto.check_in == null || dto.scheduled_check_out == null) return null;
        List<RoomLine> lines = new ArrayList<>();
        if (dto.rooms != null) {
            for (StayBillDto.Room r : dto.rooms) {
                if (r == null) continue;
                lines.add(new RoomLine(r.room_number, r.room_type, r.room_type_id, r.rate, Math.max(1, r.nights),
                        r.subtotal, "checked_out".equals(r.status), r.checked_out_on));
            }
        }
        return new StayInfo(dto, lines);
    }

    /**
     * The per-room-TYPE lines for the existing room blocks: rooms of the same type, rate and nights are one line
     * (quantity N), each room type with its own nights - exactly what the front desk billed.
     */
    public List<BookingRoom> asBookingRooms() {
        Map<String, List<RoomLine>> groups = new LinkedHashMap<>();
        for (RoomLine r : rooms) {
            String key = r.roomTypeId + "|" + r.roomType + "|" + r.rate + "|" + r.nights;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }
        List<BookingRoom> out = new ArrayList<>();
        for (List<RoomLine> g : groups.values()) {
            RoomLine first = g.get(0);
            double subtotal = 0;
            List<String> numbers = new ArrayList<>();
            for (RoomLine r : g) {
                subtotal += r.subtotal;
                if (r.roomNumber != null && !r.roomNumber.isEmpty()) numbers.add(r.roomNumber);
            }
            out.add(new BookingRoom(first.roomTypeId, first.roomType, g.size(), first.rate, first.nights, subtotal, numbers));
        }
        return out;
    }

    /**
     * The stay lines for a receipt / details screen, in reading order: the booked check-in and check-out, the nights billed,
     * the approved additional amenities and the total.
     */
    public List<Row> rows(@NonNull android.content.Context c) {
        List<Row> out = new ArrayList<>();
        out.add(new Row(c.getString(R.string.stay_scheduled_check_in), display(checkIn)));
        out.add(new Row(c.getString(R.string.stay_scheduled_check_out), display(scheduledCheckOut)));
        out.add(new Row(c.getString(R.string.receipt_number_of_nights_label), nightsText(c)));
        for (String[] extra : additionalAmenities) {
            out.add(new Row(c.getString(R.string.stay_additional_amenity_label, extra[0]), extra[1]));
        }
        out.add(new Row(c.getString(R.string.stay_total), MoneyFormat.format(total)));
        return out;
    }

    /** "1 night" / "5 nights" - the nights billed. */
    public String nightsText(@NonNull android.content.Context c) {
        return c.getResources().getQuantityString(R.plurals.receipt_nights_count, actualNights, actualNights);
    }

    static String display(@Nullable String isoDate) {
        if (isoDate == null || isoDate.isEmpty()) return "—";
        try {
            return LocalDate.parse(isoDate.length() > 10 ? isoDate.substring(0, 10) : isoDate).format(DISPLAY);
        } catch (DateTimeParseException e) {
            return isoDate;
        }
    }
}
