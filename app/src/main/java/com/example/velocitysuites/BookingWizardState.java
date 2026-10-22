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

    /** The most stays one booking or reservation may hold (each is its own booking/reservation, tied together as a group). */
    public static final int MAX_STAYS = 4;

    /**
     * One stay: its own dates, rooms and paid amenities. The fields below ({@link #checkIn}, {@link #checkOut}, {@link #selectedRooms},
     * {@link #selectedAmenities}) are the WORKING COPY of the stay being edited right now ("Stay 1" until the guest adds or opens another
     * one); {@link #commitActive()} saves it into {@link #stays} and {@link #activate(int)} loads another. With a single stay nothing
     * else changes, so one stay works exactly as it always did.
     */
    public static final class Stay {
        public Calendar checkIn;
        public Calendar checkOut;
        public final List<Room> rooms = new ArrayList<>();
        public final List<AddOnAmenity> amenities = new ArrayList<>();

        public long nights() {
            return StayDateCalculator.nightsBetween(checkIn, checkOut);
        }

        public boolean isComplete() {
            return checkIn != null && checkOut != null && checkOut.after(checkIn) && !rooms.isEmpty();
        }

        /** Rooms grouped by room type, in selection order: duplicates in {@link #rooms} are the quantity. */
        public Map<String, List<Room>> roomsGroupedByType() {
            Map<String, List<Room>> grouped = new LinkedHashMap<>();
            for (Room r : rooms) grouped.computeIfAbsent(r.getId(), k -> new ArrayList<>()).add(r);
            return grouped;
        }

        public double roomsTotal() {
            double total = 0;
            for (List<Room> group : roomsGroupedByType().values()) total += group.get(0).getPricePerNight() * nights() * group.size();
            return total;
        }

        public double amenitiesTotal() {
            double total = 0;
            for (AddOnAmenity a : amenities) total += a.getSubtotal();
            return total;
        }

        public double total() {
            return roomsTotal() + amenitiesTotal();
        }

        public int capacity() {
            int total = 0;
            for (Room r : rooms) total += r.getCapacity();
            return total;
        }
    }

    /** Every stay saved so far; the one at {@link #activeStay} is stale until {@link #commitActive()} (its live data is in the working-copy fields). */
    private final List<Stay> stays = new ArrayList<>();
    private int activeStay = 0;

    /** Saves the working copy into the active stay's slot. */
    public void commitActive() {
        while (stays.size() <= activeStay) stays.add(new Stay());
        Stay s = stays.get(activeStay);
        s.checkIn = checkIn;
        s.checkOut = checkOut;
        s.rooms.clear();
        s.rooms.addAll(selectedRooms);
        s.amenities.clear();
        s.amenities.addAll(selectedAmenities);
    }

    /** Makes stay {@code index} the one being edited (saving the current one first). */
    public void activate(int index) {
        commitActive();
        if (index < 0 || index >= stays.size()) return;
        activeStay = index;
        Stay s = stays.get(index);
        checkIn = s.checkIn;
        checkOut = s.checkOut;
        selectedRooms.clear();
        selectedRooms.addAll(s.rooms);
        selectedAmenities.clear();
        selectedAmenities.addAll(s.amenities);
    }

    /** Adds an empty "Stay N+1" and opens it. Returns false at the {@link #MAX_STAYS} limit. */
    public boolean addStay() {
        commitActive();
        if (stays.size() >= MAX_STAYS) return false;
        stays.add(new Stay());
        activate(stays.size() - 1);
        return true;
    }

    /** Removes stay {@code index} (never the last one) and opens a neighbour. */
    public void removeStay(int index) {
        commitActive();
        if (stays.size() <= 1 || index < 0 || index >= stays.size()) return;
        boolean removedActive = index == activeStay;
        stays.remove(index);
        if (removedActive) activeStay = Math.min(index, stays.size() - 1);
        else if (index < activeStay) activeStay--;
        Stay s = stays.get(activeStay);
        checkIn = s.checkIn;
        checkOut = s.checkOut;
        selectedRooms.clear();
        selectedRooms.addAll(s.rooms);
        selectedAmenities.clear();
        selectedAmenities.addAll(s.amenities);
    }

    public int stayCount() {
        return Math.max(1, stays.size());
    }

    public int activeStayIndex() {
        return activeStay;
    }

    /** All stays, with the one being edited taken from its live working copy (a snapshot: nothing is modified). */
    public List<Stay> allStays() {
        List<Stay> out = new ArrayList<>();
        int n = Math.max(1, stays.size());
        for (int i = 0; i < n; i++) {
            if (i == activeStay) {
                Stay live = new Stay();
                live.checkIn = checkIn;
                live.checkOut = checkOut;
                live.rooms.addAll(selectedRooms);
                live.amenities.addAll(selectedAmenities);
                out.add(live);
            } else {
                out.add(stays.get(i));
            }
        }
        return out;
    }

    public boolean isGroup() {
        return stayCount() > 1;
    }

    /** The whole transaction's price: every stay's rooms and paid amenities. */
    public double groupTotal() {
        double total = 0;
        for (Stay s : allStays()) total += s.total();
        return total;
    }

    /** Flat list, duplicates ARE the per-type quantity - same convention as BookingAndReservationActivity#selectedRooms. */
    public final List<Room> selectedRooms = new ArrayList<>();

    public Calendar checkIn;
    public Calendar checkOut;
    /** Edit mode only: the reservation's check-in before editing began, so an untouched check-in isn't re-judged against the current check-in window. */
    public Calendar originalCheckIn;
    /** Edit mode only: what the reservation cost before this edit and what has been paid against it - the fixed side of the old-vs-new summary. */
    public double editOldTotal;
    public double editAmountPaid;

    public final List<AddOnAmenity> selectedAmenities = new ArrayList<>();

    /** The optional representative the guest typed on Step 4 (all three "" = the account owner checks in). */
    public String repLast = "";
    public String repFirst = "";
    public String repMiddle = "";

    /** The representative block as typed. */
    public RepresentativeName.Parts representative() {
        return new RepresentativeName.Parts(repLast, repFirst, repMiddle);
    }
    public String guestFirstName;
    public String guestMiddleName;
    public String guestLastName;

    public int adults = 1;
    public int children = 0;
    public final List<BookingAndReservationActivity.AdditionalGuest> additionalGuests = new ArrayList<>();

    /** The discount name the guest claimed ("None" when no discount) - what the API's id_card_type carries. Always kept in sync with {@link #discount} via {@link #setDiscount}. */
    public String idCardType = "None";
    /** The admin-created discount the guest picked on the ID-verification step; null = no discount. */
    public Discount discount;
    public Uri idCardImageUri;
    /** Edit mode only: an ID image is already stored on the server for this reservation. It stays untouched unless the guest replaces it ({@link #idCardImageUri}) or removes it ({@link #removeIdCard}). */
    public boolean idCardOnFile;
    /** Edit mode only: the guest asked to delete the stored ID (only honored server-side after the update itself succeeds). */
    public boolean removeIdCard;

    /** Booking mode: fixed GCash-only, chosen on payment.xml. Reservation mode (fresh, non-edit): chosen by the guest on Step7PaymentMethodFragment - see paymentMethodChosen. Edit mode: changed via Step8ReviewPaymentFragment's own chip picker. */
    public String paymentMethod = "cash";
    /** True once the guest has actively picked a card on Step7PaymentMethodFragment - lets that step's validateBeforeNext() require an explicit choice rather than silently accepting paymentMethod's "cash" default. */
    public boolean paymentMethodChosen = false;
    public String gcashNumber;
    public String referenceNumber;
    public Uri receiptUri;

    /**
     * True when the chosen check-in is inside the advance-booking window (see {@link CheckInWindow}). A Modify keeps
     * the reservation's existing check-in untouched (it may legitimately be today/past by now), so there it is only
     * re-checked if the guest changed it. Re-evaluated on submit because the screen may have been left open past midnight.
     */
    public boolean isCheckInWithinWindow(boolean editMode) {
        if (checkIn == null) return false;
        boolean unchangedOnEdit = editMode && originalCheckIn != null
                && CheckInWindow.toLocalDate(originalCheckIn).equals(CheckInWindow.toLocalDate(checkIn));
        return unchangedOnEdit || CheckInWindow.isAllowed(checkIn);
    }

    /** True once the guest has scrolled the Terms, Conditions, and Policy dialog to the bottom - gates the agreement checkbox's enabled state (see TermsConsentView). Lives here rather than as a fragment-local field so it survives Step 8 being recreated on every Back/Next through the wizard. */
    public boolean termsViewed = false;
    public boolean termsAccepted = false;

    public BookingWizardState(Mode mode) {
        this.mode = mode;
    }

    /** Picks (or, with null, clears) the claimed discount, keeping the legacy idCardType string in step. Clearing a discount also drops any newly chosen ID image. */
    public void setDiscount(Discount chosen) {
        discount = chosen;
        idCardType = chosen != null ? chosen.getName() : "None";
        if (chosen == null) {
            idCardImageUri = null;
        } else {
            removeIdCard = false;
        }
    }

    /** The claimed discount's numeric id for the API, or null when none / not parseable. */
    public Long discountIdOrNull() {
        if (discount == null || discount.getId() == null) return null;
        try {
            return Long.parseLong(discount.getId());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** True when a claimed discount still lacks any ID: nothing stored on the server (or the stored one was removed) and no new image picked. */
    public boolean discountNeedsId() {
        return discount != null && idCardImageUri == null && (!idCardOnFile || removeIdCard);
    }

    /** Old total, new total (with the current selection), amount paid and the resulting balance due or excess - for the Edit Reservation review. */
    public EditTotals editTotals() {
        return EditTotals.of(editOldTotal, roomsTotal() + amenitiesTotal(), editAmountPaid);
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

    /**
     * Sum of each selected room's capacity - the hard ceiling step 5's declared adults+children can't exceed. With several stays the guests
     * stay the same, so the ceiling is the smallest stay's capacity.
     */
    public int totalSelectedCapacity() {
        int min = Integer.MAX_VALUE;
        for (Stay s : allStays()) {
            if (!s.rooms.isEmpty()) min = Math.min(min, s.capacity());
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    /** Max adults + children for the current room selection (see {@link GuestCapacity}). */
    public int maxGuests() {
        return GuestCapacity.effectiveMax(totalSelectedCapacity());
    }

    /**
     * Lowers adults/children to fit the current room selection (e.g. after the
     * guest went back and picked a smaller room). Returns true when anything changed.
     */
    public boolean clampGuestsToCapacity() {
        int[] fitted = GuestCapacity.clamp(adults, children, totalSelectedCapacity());
        boolean changed = fitted[0] != adults || fitted[1] != children;
        adults = fitted[0];
        children = fitted[1];
        return changed;
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
        for (Stay s : allStays()) total += s.roomsTotal();
        return total;
    }

    /** Sum of every selected amenity's subtotal - see roomsTotal()'s docblock for why this is shared here. */
    public double amenitiesTotal() {
        double total = 0;
        for (Stay s : allStays()) total += s.amenitiesTotal();
        return total;
    }
}
