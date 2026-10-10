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
 * The server's itemized stay, ready to show: scheduled vs actual check-out, nights (booked vs actual), the extra
 * nights' charge and the updated total. It carries the server's own figures (the same StayBill the front desk's
 * check-out bill is built from) - nothing here is calculated on the phone, so the guest's receipt, Booking Details and
 * Payment History always agree with the receptionist's bill.
 */
public final class StayInfo implements Serializable {

    /** One physical room's line: rate x its own nights. */
    public static final class RoomLine implements Serializable {
        public final String roomNumber;
        public final String roomType;
        public final String roomTypeId;
        public final double rate;
        public final int nights;
        public final int extraNights;
        public final double subtotal;
        public final boolean checkedOut;
        @Nullable public final String checkedOutOn;

        RoomLine(String roomNumber, String roomType, String roomTypeId, double rate, int nights, int extraNights,
                 double subtotal, boolean checkedOut, @Nullable String checkedOutOn) {
            this.roomNumber = roomNumber;
            this.roomType = roomType;
            this.roomTypeId = roomTypeId;
            this.rate = rate;
            this.nights = nights;
            this.extraNights = extraNights;
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
    public final String actualCheckOut;
    public final int scheduledNights;
    public final int actualNights;
    public final int extraNights;
    public final double roomCharge;
    public final double extraNightsCharge;
    public final double discount;
    @Nullable public final String discountName;
    public final double total;
    public final List<RoomLine> rooms;

    private StayInfo(StayBillDto d, List<RoomLine> rooms) {
        this.checkIn = d.check_in;
        this.scheduledCheckOut = d.scheduled_check_out;
        this.actualCheckOut = d.actual_check_out;
        this.scheduledNights = Math.max(1, d.scheduled_nights);
        this.actualNights = Math.max(1, d.actual_nights);
        this.extraNights = Math.max(0, d.extra_nights);
        this.roomCharge = d.room_charge;
        this.extraNightsCharge = d.extra_nights_charge;
        this.discount = d.discount;
        this.discountName = d.discount_name;
        this.total = d.total;
        this.rooms = Collections.unmodifiableList(rooms);
    }

    /** Null in, null out - a stay that is not in house yet has no itemized bill. */
    @Nullable
    public static StayInfo from(@Nullable StayBillDto dto) {
        if (dto == null || dto.check_in == null || dto.actual_check_out == null) return null;
        List<RoomLine> lines = new ArrayList<>();
        if (dto.rooms != null) {
            for (StayBillDto.Room r : dto.rooms) {
                if (r == null) continue;
                lines.add(new RoomLine(r.room_number, r.room_type, r.room_type_id, r.rate, Math.max(1, r.nights), r.extra_nights,
                        r.subtotal, "checked_out".equals(r.status), r.checked_out_on));
            }
        }
        return new StayInfo(dto, lines);
    }

    public boolean isLate() { return extraNights > 0; }

    public boolean isEarly() { return actualNights < scheduledNights; }

    public boolean differsFromBooked() { return isLate() || isEarly(); }

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
     * The stay lines for a receipt / details screen, in reading order: scheduled stay, actual check-out, nights
     * (original vs actual when they differ), the extra nights' charge and the updated total.
     */
    public List<Row> rows(@NonNull android.content.Context c) {
        List<Row> out = new ArrayList<>();
        out.add(new Row(c.getString(R.string.stay_scheduled_check_in), display(checkIn)));
        out.add(new Row(c.getString(R.string.stay_scheduled_check_out), display(scheduledCheckOut)));
        out.add(new Row(c.getString(R.string.stay_actual_check_out), display(actualCheckOut)));
        out.add(new Row(c.getString(R.string.receipt_number_of_nights_label), nightsText(c)));
        if (extraNights > 0) {
            out.add(new Row(c.getString(R.string.stay_extra_nights_charge, extraNights), MoneyFormat.format(extraNightsCharge)));
        }
        out.add(new Row(c.getString(R.string.stay_updated_total), MoneyFormat.format(total)));
        return out;
    }

    /** "1 night" / "5 nights (booked: 1)" - the actual nights, with the booked ones alongside when they differ. */
    public String nightsText(@NonNull android.content.Context c) {
        String actual = c.getResources().getQuantityString(R.plurals.receipt_nights_count, actualNights, actualNights);
        return differsFromBooked() ? c.getString(R.string.stay_nights_booked_format, actual, scheduledNights) : actual;
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
