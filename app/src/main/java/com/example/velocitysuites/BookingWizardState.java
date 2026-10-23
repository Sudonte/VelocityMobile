package com.example.velocitysuites;

import android.net.Uri;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
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
public class BookingWizardState implements Serializable {

    private static final long serialVersionUID = 2L;

    public enum Mode { BOOKING, RESERVATION }

    /** Who checks in: nothing chosen yet (Next stays blocked), the account owner, or someone else. */
    public enum Who { NONE, OWNER, OTHER }

    public final Mode mode;

    /** The most stays one booking or reservation may hold (each is its own booking/reservation, tied together as a group). */
    public static final int MAX_STAYS = 4;

    /**
     * One stay: its own dates, rooms and paid amenities. The fields below ({@link #checkIn}, {@link #checkOut}, {@link #selectedRooms},
     * {@link #selectedAmenities}) are the WORKING COPY of the stay being edited right now ("Stay 1" until the guest adds or opens another
     * one); {@link #commitActive()} saves it into {@link #stays} and {@link #activate(int)} loads another. With a single stay nothing
     * else changes, so one stay works exactly as it always did.
     */
    public static final class Stay implements Serializable {
        private static final long serialVersionUID = 1L;

        public Calendar checkIn;
        public Calendar checkOut;
        public final List<Room> rooms = new ArrayList<>();
        /** What this stay's paid amenities add up to per amenity (quantities summed over its rooms): what is priced and sent to the server. */
        public final List<AddOnAmenity> amenities = new ArrayList<>();
        /** The guest tapped "Confirm this stay" for exactly these dates and rooms. Any change reopens the stay and clears this. */
        public boolean confirmed;
        /** Amenities chosen per room of this stay: room key ("typeId#n") to (amenity id to quantity). {@link #amenities} is always derived from it. */
        public final Map<String, Map<String, Integer>> roomAmenityQty = new LinkedHashMap<>();
        /** The catalog entries behind {@link #roomAmenityQty}, by id (price, name and limits as the guest saw them). */
        public final Map<String, AddOnAmenity> amenityCatalog = new LinkedHashMap<>();

        /** The key of the "Any room" group: amenities that belong to the stay as a whole (older rows with no room recorded). */
        public static final String ANY_ROOM = "*";

        /** True when some amenity of this stay is still in the "Any room" group. */
        public boolean hasAnyRoomAmenities() {
            Map<String, Integer> any = roomAmenityQty.get(ANY_ROOM);
            return any != null && !any.isEmpty();
        }

        /** The "Any room" group as a slot (no room, number 0), for screens that list it beside the real rooms. */
        public Slot anyRoomSlot() {
            return new Slot(ANY_ROOM, null, 0);
        }

        /** One room of the stay with its key and label: "Deluxe (room 1)", "Deluxe (room 2)", "Executive (room 1)". */
        public static final class Slot implements Serializable {
            public final String key;
            public final Room room;
            public final int number;

            Slot(String key, Room room, int number) {
                this.key = key;
                this.room = room;
                this.number = number;
            }
        }

        /** The rooms one by one, in selection order, numbered within their type. */
        public List<Slot> roomSlots() {
            List<Slot> slots = new ArrayList<>();
            Map<String, Integer> seen = new LinkedHashMap<>();
            for (Room r : rooms) {
                int n = seen.containsKey(r.getId()) ? seen.get(r.getId()) + 1 : 1;
                seen.put(r.getId(), n);
                slots.add(new Slot(r.getId() + "#" + n, r, n));
            }
            return slots;
        }

        public int amenityQty(String roomKey, String amenityId) {
            Map<String, Integer> forRoom = roomAmenityQty.get(roomKey);
            Integer q = forRoom != null ? forRoom.get(amenityId) : null;
            return q != null ? q : 0;
        }

        /** Sets one room's quantity of one amenity (0 removes it) and re-derives {@link #amenities}. */
        public void setAmenityQty(String roomKey, AddOnAmenity amenity, int qty) {
            amenityCatalog.put(amenity.getId(), amenity);
            Map<String, Integer> forRoom = roomAmenityQty.get(roomKey);
            if (qty <= 0) {
                if (forRoom != null) {
                    forRoom.remove(amenity.getId());
                    if (forRoom.isEmpty()) roomAmenityQty.remove(roomKey);
                }
            } else {
                if (forRoom == null) {
                    forRoom = new LinkedHashMap<>();
                    roomAmenityQty.put(roomKey, forRoom);
                }
                forRoom.put(amenity.getId(), qty);
            }
            rebuildAmenities();
        }

        /** Drops choices for rooms that are no longer part of the stay (the rooms changed after amenities were picked). */
        public void dropAmenitiesOfMissingRooms() {
            List<String> keys = new ArrayList<>();
            keys.add(ANY_ROOM);
            for (Slot s : roomSlots()) keys.add(s.key);
            if (roomAmenityQty.keySet().retainAll(keys)) rebuildAmenities();
        }

        /** Total of one amenity over the rooms of this stay. */
        public int amenityTotalQty(String amenityId) {
            int total = 0;
            for (Map<String, Integer> forRoom : roomAmenityQty.values()) {
                Integer q = forRoom.get(amenityId);
                if (q != null) total += q;
            }
            return total;
        }

        /** What one room's amenities cost. */
        public double roomAmenitiesTotal(String roomKey) {
            double total = 0;
            Map<String, Integer> forRoom = roomAmenityQty.get(roomKey);
            if (forRoom == null) return 0;
            for (Map.Entry<String, Integer> e : forRoom.entrySet()) {
                AddOnAmenity a = amenityCatalog.get(e.getKey());
                if (a != null) total += a.getPrice() * e.getValue();
            }
            return total;
        }

        private void rebuildAmenities() {
            amenities.clear();
            for (AddOnAmenity proto : amenityCatalog.values()) {
                int qty = amenityTotalQty(proto.getId());
                if (qty <= 0) continue;
                AddOnAmenity sum = new AddOnAmenity(proto.getId(), proto.getName(), proto.getCategory(), proto.getDescription(), proto.getPrice(), proto.getMaxQuantity());
                sum.setQuantity(qty);
                amenities.add(sum);
            }
        }

        /** Seeds the per-room choices from a plain per-stay list (a Modify): everything goes to the first room. */
        public void seedFromAggregate(List<AddOnAmenity> list) {
            roomAmenityQty.clear();
            amenityCatalog.clear();
            // a plain per-stay list has no room: it stays in the "Any room" group until the guest moves it to one
            for (AddOnAmenity a : list) setAmenityQty(ANY_ROOM, a, a.getQuantity());
        }

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
    /** The working copy's "confirmed" flag (see {@link Stay#confirmed}). */
    public boolean activeConfirmed;

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
        s.confirmed = activeConfirmed;
    }

    /** The stays themselves (not copies), after saving the working copy: what the amenities step edits per room. */
    public List<Stay> committedStays() {
        commitActive();
        return stays;
    }

    /** After the amenities step changed a stay: refresh the working copy of the active stay from it. */
    public void reloadActiveAmenities() {
        if (activeStay >= stays.size()) return;
        selectedAmenities.clear();
        selectedAmenities.addAll(stays.get(activeStay).amenities);
    }

    /** "Confirm this stay": the dates and rooms on screen are final until the guest reopens the stay. */
    public void confirmActive() {
        activeConfirmed = true;
        commitActive();
    }

    /** Edit on a stay: it is open again and must be confirmed again. */
    public void reopenActive() {
        activeConfirmed = false;
        commitActive();
    }

    /** Every stay confirmed (and complete) - what lets the guest leave Dates and rooms. */
    public boolean allStaysConfirmed() {
        for (Stay s : allStays()) if (!s.confirmed || !s.isComplete()) return false;
        return true;
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
        activeConfirmed = s.confirmed;
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
        activeConfirmed = s.confirmed;
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
                live.confirmed = activeConfirmed;
                if (i < stays.size()) {
                    live.roomAmenityQty.putAll(stays.get(i).roomAmenityQty);
                    live.amenityCatalog.putAll(stays.get(i).amenityCatalog);
                }
                out.add(live);
            } else {
                out.add(stays.get(i));
            }
        }
        return out;
    }

    /**
     * The booking or reservation goes out as stays[] (one request per transaction, amenities named by room) rather than the flat
     * single-stay form: whenever there are several stays, or any amenity was chosen for a particular room.
     */
    public boolean sendsAsStays() {
        if (isGroup()) return true;
        for (Stay s : allStays()) if (!s.roomAmenityQty.isEmpty()) return true;
        return false;
    }

    /** More than one stay in this booking or reservation. */
    public boolean isGroup() {
        return stayCount() > 1;
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    /**
     * The index of ANOTHER stay with the very same check-in AND check-out day as stay {@code index}, or -1. Two stays of one booking
     * may overlap or touch, but not be identical (the guest adds rooms to that stay instead) - the server refuses it too.
     */
    public int duplicateOf(int index) {
        List<Stay> all = allStays();
        if (index < 0 || index >= all.size()) return -1;
        Stay me = all.get(index);
        if (me.checkIn == null || me.checkOut == null) return -1;
        for (int j = 0; j < all.size(); j++) {
            Stay other = all.get(j);
            if (j == index || other.checkIn == null || other.checkOut == null) continue;
            if (sameDay(me.checkIn, other.checkIn) && sameDay(me.checkOut, other.checkOut)) return j;
        }
        return -1;
    }

    /** True when any stay repeats another stay's exact dates. */
    public boolean hasDuplicateStay() {
        int n = stayCount();
        for (int i = 0; i < n; i++) if (duplicateOf(i) >= 0) return true;
        return false;
    }

    /** True when every stay has valid dates and at least one room, and no two stays have identical dates - what lets the guest move on from Step 1. */
    public boolean staysReadyToContinue() {
        for (Stay s : allStays()) if (!s.isComplete() || !s.confirmed) return false;
        return !hasDuplicateStay();
    }

    /**
     * Rooms of one type the OTHER stays already use on the busiest night of the stay being edited: stays may overlap, so their rooms add up
     * on the nights they share, and the server's availability for these dates does not know about them yet.
     */
    public int otherStaysPeak(String roomTypeId) {
        if (checkIn == null || checkOut == null || roomTypeId == null) return 0;
        List<Stay> all = allStays();
        int peak = 0;
        Calendar night = (Calendar) checkIn.clone();
        while (night.before(checkOut)) {
            int used = 0;
            for (int i = 0; i < all.size(); i++) {
                if (i == activeStay) continue;
                Stay other = all.get(i);
                if (other.checkIn == null || other.checkOut == null) continue;
                if (night.before(other.checkIn) || !night.before(other.checkOut)) continue; // not one of that stay's nights
                for (Room r : other.rooms) if (roomTypeId.equals(r.getId())) used++;
            }
            peak = Math.max(peak, used);
            night.add(Calendar.DAY_OF_YEAR, 1);
        }
        return peak;
    }

    /** Guests the rooms of the stay being edited can hold. */
    public int activeStayCapacity() {
        int total = 0;
        for (Room r : selectedRooms) total += r.getCapacity();
        return total;
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

    /** Step 3's choice: neither is selected until the guest picks (so Next is blocked with "Choose who will check in"). */
    public Who who = Who.NONE;

    /** The representative the guest typed when {@link #who} is OTHER (all three "" with OWNER = the account owner checks in). */
    public String repLast = "";
    /** What the guest typed for "Someone else" while Owner (me) is chosen: kept so switching back does not lose it. */
    public String otherLast = "";
    public String otherFirst = "";
    public String otherMiddle = "";
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
    public transient Uri idCardImageUri;
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
    public transient Uri receiptUri;

    /** Uris are not serializable: they travel as text. */
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeObject(idCardImageUri != null ? idCardImageUri.toString() : null);
        out.writeObject(receiptUri != null ? receiptUri.toString() : null);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        String id = (String) in.readObject();
        String receipt = (String) in.readObject();
        idCardImageUri = id != null ? Uri.parse(id) : null;
        receiptUri = receipt != null ? Uri.parse(receipt) : null;
    }

    /** Whether the guest has chosen anything yet - what "Discard this booking?" protects. */
    public boolean hasUnsavedChoices() {
        if (checkIn != null || checkOut != null || !selectedRooms.isEmpty() || !selectedAmenities.isEmpty()) return true;
        if (stays.size() > 1 || who != Who.NONE || discount != null || paymentMethodChosen) return true;
        for (Stay s : stays) if (s.checkIn != null || !s.rooms.isEmpty()) return true;
        return !repLast.isEmpty() || !repFirst.isEmpty() || !repMiddle.isEmpty() || !additionalGuests.isEmpty();
    }

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
