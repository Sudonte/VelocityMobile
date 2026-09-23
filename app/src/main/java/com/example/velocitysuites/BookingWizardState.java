package com.example.velocitysuites;

import android.net.Uri;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory state shared across all 7 steps of BookingWizardActivity, owned
 * by the host Activity for its lifetime (not persisted/serialized between
 * steps - each step Fragment reads/writes it directly via
 * BookingWizardActivity#getState()). Deliberately a plain mutable holder,
 * not an AndroidX ViewModel - the wizard never needs to survive process
 * death/recreation beyond the normal Activity instance-state mechanisms.
 */
public class BookingWizardState {

    public enum Mode { BOOKING, RESERVATION }

    public final Mode mode;

    /** Flat list, duplicates ARE the per-type quantity - same convention as BookingAndReservationActivity#selectedRooms. */
    public final List<Room> selectedRooms = new ArrayList<>();

    public Calendar checkIn;
    public Calendar checkOut;

    public final List<AddOnAmenity> selectedAmenities = new ArrayList<>();

    public String guestFirstName;
    public String guestMiddleName;
    public String guestLastName;

    public int adults = 1;
    public int children = 0;
    public final List<BookingAndReservationActivity.AdditionalGuest> additionalGuests = new ArrayList<>();

    /** "None" | "Senior Citizen" | "PWD" */
    public String idCardType = "None";
    public Uri idCardImageUri;

    /** Booking mode: fixed GCash-only, chosen on payment.xml. Reservation mode (fresh, non-edit): chosen by the guest on Step7PaymentMethodFragment - see paymentMethodChosen. Edit mode: changed via Step8ReviewPaymentFragment's own chip picker. */
    public String paymentMethod = "cash";
    /** True once the guest has actively picked a card on Step7PaymentMethodFragment - lets that step's validateBeforeNext() require an explicit choice rather than silently accepting paymentMethod's "cash" default. */
    public boolean paymentMethodChosen = false;
    public String gcashNumber;
    public String referenceNumber;
    public Uri receiptUri;

    /** True once the guest has scrolled the Terms, Conditions, and Policy dialog to the bottom - gates cbTermsAgreement's enabled state (see Step8ReviewPaymentFragment#showHotelTermsDialog()). Lives here rather than as a fragment-local field so it survives Step 8 being recreated on every Back/Next through the wizard. */
    public boolean termsViewed = false;
    public boolean termsAccepted = false;

    public BookingWizardState(Mode mode) {
        this.mode = mode;
    }

    public boolean isBookingMode() {
        return mode == Mode.BOOKING;
    }

    /**
     * Nights = whole days between checkIn and checkOut, floored at 1 - the
     * one shared formula every step/screen that prices a stay must use
     * (previously reimplemented separately in Step2DatesFragment,
     * Step8ReviewPaymentFragment and PaymentActivity's pending-wizard summary
     * - all three now delegate here so they can never drift from each other).
     */
    public long nights() {
        return StayDateCalculator.nightsBetween(checkIn, checkOut);
    }

    /** Sum of each selected room's capacity - the hard ceiling step 5's declared adults+children can't exceed. */
    public int totalSelectedCapacity() {
        int total = 0;
        for (Room r : selectedRooms) {
            total += r.getCapacity();
        }
        return total;
    }

    /**
     * A guest may stage more than one distinct room type in one transaction
     * (e.g. 2 Deluxe + 1 Suite) - same as the existing booking flow. Since
     * Api\BookingController::store() (like Api\ReservationController::store()
     * before it) only accepts a single room_type_id + rooms_requested per
     * call, step 7's submit logic must group selectedRooms by type and fire
     * one createDirectBooking()/createReservation() call per group - this
     * mirrors BookingAndReservationActivity#createRoomReservations()'s
     * existing per-room-group loop exactly.
     */
    public Map<String, List<Room>> selectedRoomsGroupedByType() {
        Map<String, List<Room>> grouped = new LinkedHashMap<>();
        for (Room r : selectedRooms) {
            grouped.computeIfAbsent(r.getId(), k -> new ArrayList<>()).add(r);
        }
        return grouped;
    }

    /**
     * Sum of every selected room type's (price/night x nights x quantity) -
     * the one shared room-charge formula every step/screen that prices a
     * stay must use (previously reimplemented separately as a private
     * method on Step8ReviewPaymentFragment; also now needed by
     * Step7PaymentMethodFragment's Payment Amount section, so it lives here
     * instead, same "single shared formula" convention as nights()).
     */
    public double roomsTotal() {
        double total = 0;
        for (List<Room> group : selectedRoomsGroupedByType().values()) {
            total += group.get(0).getPricePerNight() * nights() * group.size();
        }
        return total;
    }

    /** Sum of every selected amenity's subtotal - see roomsTotal()'s docblock for why this is shared here. */
    public double amenitiesTotal() {
        double total = 0;
        for (AddOnAmenity a : selectedAmenities) {
            total += a.getSubtotal();
        }
        return total;
    }
}
