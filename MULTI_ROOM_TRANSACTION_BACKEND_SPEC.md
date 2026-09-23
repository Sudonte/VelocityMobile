# Backend/database changes needed for true multi-room-type Booking/Reservation transactions

> **Update (2026-09-18): the read-path gap this document describes (sections
> 6/8, "GET endpoints must return the grouped shape") has been fixed and
> verified directly against the live backend via SSH - the earlier "STALE-COMMENT
> CORRECTION" note in `ApiMapper.toBookingRooms()` claiming this was already
> shipped was itself wrong (verified: no eager-load or serialization of this
> data existed on any guest-facing endpoint before this date, despite
> `booking_room_lines`/`reservation_room_lines` already having real production
> data going back to 2026-09-15 - the gap was purely in the API response layer,
> not the database). Fixed by adding `roomLines()` relations +
> `getRoomLinesAttribute()` accessors to `Booking`/`Reservation`/`Billing`
> (the last one delegating to its owning Booking), returned under a
> **`room_lines`** JSON key - deliberately not `rooms`, since Booking already
> has a real, unrelated `rooms()` relation (physical assigned Room units, read
> by `getTotalAmountDueAttribute()` via plain property access) that a
> same-named accessor would have silently broken. Each line now also carries
> `assigned_room_numbers` (grouped from that same physical-assignment relation
> by `room_type_id`), satisfying a related, separately-requested feature (exact
> per-room-type room numbers in the mobile app's Booking/Reservation Details).
> Android's DTOs (`ReservationDto`, `BillingDto`, `DirectBookingResponseDto`)
> were renamed from `rooms` to `room_lines` to match; `BookingRoomDto` gained
> the new `assigned_room_numbers` field. Verified end-to-end with a disposable
> multi-room-type booking (2 Deluxe + 1 Executive, 2 physically assigned) via
> the real HTTP API, and confirmed `getTotalAmountDueAttribute()`/the physical
> `rooms` relation are both completely unaffected. **Everything else in this
> document - the single-call creation endpoint, `booking_rooms`/`reservation_rooms`
> as new tables (the real schema already used `booking_room_lines`/
> `reservation_room_lines` for this, a naming mismatch this document didn't
> anticipate), atomic availability locking, idempotency keys - remains
> unimplemented.** This was a narrower, explicitly-scoped fix (expose
> already-existing line-item data for reading) - not the full architecture
> below, which is a separate, much larger undertaking.
>
> **Also found while investigating (not fixed, out of scope for this pass):**
> `room_types.id=5` has `name` literally set to a guest's full name
> ("Bryan Dela Cruz") instead of a real room type name - a pre-existing data-entry
> error in the master catalog, not something this session's changes touched or
> caused. Worth a receptionist/admin fixing directly in the Room Types module
> whenever convenient - not database-migration-worthy, just a wrong value in an
> existing row.

Scope note: same as the other `*_BACKEND_SPEC.md` files in this repo - no
Laravel/backend source, and no receptionist web portal source, exist on this
machine. The app is a pure REST client of a live, already-deployed server at
`https://velocitysuites.com/api/`. Everything below is written for whoever
owns that server-side repository; it cannot be applied from here. **Update
(2026-09-18): this "no backend access" scope note is now stale too - see
[[project-repo-scope]] in this session's memory: SSH access to the live
backend is available when explicitly authorized, and was used for the update
above.**

> **CRITICAL FINAL REQUIREMENT - read before implementing anything else in
> this app's booking/reservation flow.** One guest submission of a
> multi-room-type booking or reservation must produce **exactly one real
> Booking ID or Reservation ID at the database level** - one row in
> `bookings`/`reservations`, with every selected room and amenity as a child
> row referencing that same parent id, one real payment, one real status.
> This must be true at the Android, API, Laravel, and database layers, not
> merely in how the Android app *displays* the result. The Android app
> currently ships a **temporary, client-side-only compatibility layer**
> (`BookingGroupState`, described below) that visually presents N separate
> server-side records as if they were one transaction in the RecyclerView -
> this is a stopgap for today's one-room-type-per-request API limitation,
> explicitly **not** the accepted final architecture, and must be replaced by
> the single-call endpoint this document specifies. Do not consider this
> feature complete until the backend actually creates one parent row with
> child room/amenity rows, per sections 1-9b below.

## Why this document exists

A request came in asking the Android app to let a guest select multiple room
types and quantities (e.g. 2 Deluxe + 1 Executive + 2 Standard) that all
belong to **one** Booking ID or Reservation ID. Before touching any code, I
audited the existing implementation to see what's actually true today. Short
version: **the Android client already lets a guest select multiple room
types/quantities in one wizard pass** - that part of the ask is already done,
on this side. What it *cannot* do, because the API doesn't support it, is
turn that selection into a single backend transaction.

## Client-side contract (already correct/already built, for reference)

- `BookingWizardState.selectedRooms` is a `List<Room>` (one entry per
  physical room unit, so "2 Deluxe" is two list entries) - not a single
  `selectedRoomType` field. `selectedRoomsGroupedByType()` groups it back by
  `room.getId()` for display/submission.
- `Step1RoomSelectionFragment` (the wizard's room-selection step) already:
  - Lets the guest add multiple distinct room types to the same selection
    without deselecting a previous one (`addRoomsToSelection()`).
  - Tracks quantity per room type keyed by `room.getId()`
    (`currentQtyForId()`), never a single shared quantity variable - so
    RecyclerView/dialog recycling can't leak one room's count onto another's
    card.
  - Caps quantity at the room type's real `availableCount` for the selected
    dates, both when staging in the "Add Room" dialog and when using the
    +/- steppers on an already-selected room (`adjustRoomQuantity()`).
  - Re-validates the entire carried-over selection against fresh
    availability every time this step is (re-)shown -
    `reconcileSelectedRoomsWithAvailability()` - trimming or dropping
    anything that's no longer available and telling the guest why. This
    covers both "guest changed the dates and came back" and "another guest
    took the inventory in the meantime."
  - Sums capacity across every selected room, every type
    (`BookingWizardState.totalSelectedCapacity()`), not just the first one.
  - Blocks the wizard's Next button entirely with zero rooms selected
    (`validateBeforeNext()`), not just a toast.
- Submission (`PaymentActivity.submitPendingBookingGroups()` /
  `submitPendingReservationGroups()`) already groups the selection by room
  type and fires one `POST guest/bookings` / one Reservation-creation call
  **per distinct room type**, sequentially, accumulating results as it goes.
  It already:
  - Guards against duplicate submission from repeated taps
    (`isSubmittingPayment` flag, checked/set before the sequence starts).
  - Reconciles the ambiguous "did that request actually land or not"
    case for a duplicate-reference error (`reconcileDuplicateReference()`)
    before treating it as a genuine failure.
  - Reports a clear partial-failure message
    (`multi_type_partial_failure_message`) if a later group in the sequence
    fails after earlier ones already succeeded, rather than silently
    hiding it.
  - Deliberately does **not** try to roll back a Booking group that already
    succeeded (and was already paid for) just because a later group failed -
    a paid Booking represents a real, already-charged room, and "successfully
    created and paid" is a valid terminal state for it regardless of what
    happens to the rest of the batch. For Reservations (unpaid by default),
    a create-then-attach-payment failure on the payment-attach step
    deliberately keeps the Reservation as a normal unpaid Reservation rather
    than discarding it.
  - `BookingGroupState` (on-device only, `SharedPreferences`-backed) ties the
    resulting N separate Booking/Reservation IDs back together locally so
    the guest's UI can show "these rooms were booked together" - this is a
    client-only convenience with no server-side equivalent, and it's the
    literal reason this feature can't satisfy "must be one Booking ID" today.
    Verified: the group key is `"GRP-" + <the first created record's own
    server-assigned id>` (`BookingAndReservationActivity.onAllRoomsCreated()`),
    generated fresh once per successful submission batch from IDs the server
    just returned - never derived from guest identity, dates, or room type.
    Two separate submissions - even with identical guest/dates/room
    selection - always get different server-assigned IDs and therefore
    different group keys, so they can never be merged by this logic. This
    client-side grouping is explicitly *not* the final architecture (see the
    "CRITICAL FINAL REQUIREMENT" callout at the top of this document) - it's
    the temporary display layer over N real backend records, kept only until
    section 1's single-call endpoint exists.

None of the above needed fixing - it's already a careful, defensive
implementation given the one-room-type-per-call constraint it has to work
within. The actual gap is entirely server-side.

## The constraint that blocks "one Booking ID for the whole transaction"

`Api\BookingController::store()` and `Api\ReservationController::store()`
(as called by `RoomRepository.createDirectBooking()` /
`RoomRepository.createReservation()`) each accept exactly one
`room_type_id` + `rooms_requested` per request. There is no request shape
today that lets the Android client submit "2 Deluxe + 1 Executive + 2
Standard" as a single call, so it can never produce a single Booking ID for
a mixed-room-type selection no matter what the client does.

## What the backend needs to add

### 1. New request contract accepting multiple room and amenity lines

```json
POST /api/bookings   (and the equivalent Reservation-creation endpoint)
{
  "check_in": "2026-09-20",
  "check_out": "2026-09-22",
  "rooms": [
    { "room_type_id": 1, "quantity": 2, "selected_room_ids": [101, 102] },
    { "room_type_id": 2, "quantity": 1, "selected_room_ids": [205] },
    { "room_type_id": 3, "quantity": 2, "selected_room_ids": [310, 311] }
  ],
  "amenities": [
    { "amenity_id": 1, "quantity": 2 },
    { "amenity_id": 2, "quantity": 1 }
  ],
  "adults": 7,
  "children": 0,
  "guest_first_name": "...",
  "guest_last_name": "...",
  "payment_method": "GCash",
  "reference_number": "...",
  "gcash_number": "...",
  "amount_paid": 7000,
  "representative": {
    "first_name": "Juan",
    "middle_name": "Miguel",
    "last_name": "Dela Cruz"
  }
}
```

`POST /api/reservations` accepts the identical shape (rooms/amenities/
representative/etc.) and must create exactly one Reservation ID the same
way - everything in this document applies equally to both endpoints unless
stated otherwise; it isn't repeated twice just to keep this document shorter.

Note on `selected_room_ids`: the Android wizard already tracks selection as
one `Room` entry per physical unit (`BookingWizardState.selectedRooms`, see
"Client-side contract" below) rather than a bare per-type quantity, so specific
unit IDs are available to send at creation time if the backend wants to
reserve exact physical rooms up front. If the current single-room flow instead
defers physical-unit assignment to check-in (worth the backend team
confirming against `Api\BookingController`'s existing behavior), `quantity`
alone is sufficient and `selected_room_ids` can be ignored/omitted - it isn't
load-bearing for the rest of this document either way.

Note: the nested `representative` object above (`first_name`/`middle_name`/
`last_name`) is not a new concept - it's the same representative-guest
identity already sent today as flat `guest_first_name`/`guest_middle_name`/
`guest_last_name` fields (Android's `Booking.getRepresentativeName()`
already composes the full display name from exactly those three fields
client-side). Whether the request/response keeps them flat or nests them
under `representative` is the backend's call - just don't introduce a
second, separately-tracked "representative" identity distinct from the
existing guest_first_name/middle_name/last_name columns, since Android
already treats those as the single source of truth for this name.

The server must generate exactly **one** booking/reservation ID for this
whole request - the Android app must never call this endpoint once per room
type (that per-room-type looping is the *current* workaround, described
under "Client-side contract" above, that this endpoint is meant to replace).

The existing single-room fields (`room_type_id`, `rooms_requested`) should
stay supported for backward compatibility with any in-flight app version
still calling the old shape, rather than being a breaking change - accept
either `room_type_id`/`rooms_requested` **or** a `rooms` array, and treat a
single `room_type_id` as a `rooms` array of length 1 internally.

`POST /api/bookings`'s response (and `POST /api/reservations`'s, substituting
`reservation`/`reservation_id`/`reservation_status`) should return the full
created transaction so the app never has to re-fetch it to show a
confirmation screen:

```json
{
  "success": true,
  "booking": {
    "booking_id": "BK-20260913-001",
    "check_in": "2026-09-20",
    "check_out": "2026-09-22",
    "number_of_nights": 2,
    "representative_name": "Juan Miguel Dela Cruz",
    "rooms": [
      { "room_type_id": 1, "room_type_name": "Deluxe Room", "quantity": 2, "price_per_night": 3500, "nights": 2, "subtotal": 14000 },
      { "room_type_id": 2, "room_type_name": "Executive Room", "quantity": 1, "price_per_night": 5000, "nights": 2, "subtotal": 10000 },
      { "room_type_id": 3, "room_type_name": "Standard Room", "quantity": 2, "price_per_night": 2500, "nights": 2, "subtotal": 10000 }
    ],
    "amenities": [
      { "amenity_id": 1, "amenity_name": "Breakfast Package", "quantity": 2, "unit_price": 300, "subtotal": 600 },
      { "amenity_id": 2, "amenity_name": "Extra Bed", "quantity": 1, "unit_price": 400, "subtotal": 400 }
    ],
    "room_total": 34000,
    "amenities_total": 1000,
    "additional_guest_fee": 0,
    "grand_total": 35000,
    "amount_paid": 7000,
    "remaining_balance": 28000,
    "payment_method": "GCash",
    "payment_status": "Partial",
    "booking_status": "Confirmed"
  }
}
```

`GET guest/bookings`/`GET guest/bookings/{id}` (and the Reservation
equivalents) should return the inner `booking`/`reservation` object shape
above directly (no `success` wrapper needed there, matching how the existing
single-room endpoints already respond) - list responses return an array of
these, detail responses return one.

`total_rooms`/`total_room_types`/`total_amenity_items`/`total_amenity_types`
may be included too if convenient, but see section 3's note below on why
Android won't actually read them - the field names above (`room_type_id`,
`room_type_name`, `quantity`, `price_per_night`, `nights`, `subtotal` for
each room line; `amenity_id`, `amenity_name`, `quantity`, `unit_price`,
`subtotal` for each amenity line) are the ones that matter, since they map
directly onto `network/dto/BookingRoomDto`/`BookingAmenityDto` (already
built - see "Android-side implementation" below). A field rename on the
backend side would require a matching rename in those two DTOs.

### 2. New parent/child schema (do not use comma-separated text)

```
bookings
--------
id, user_id, check_in, check_out, number_of_nights, booking_status,
payment_method, payment_status, room_total, amenities_total,
additional_guest_fee, grand_total, amount_paid, remaining_balance,
representative_name, created_at, updated_at
  (existing single-room columns like room_type_id can stay for backward
  compatibility with historical rows, but new rows should populate the
  child tables below and treat this row as the transaction header only)

booking_rooms
-------------
id, booking_id (FK), room_type_id (FK), quantity, price_per_night,
number_of_nights, subtotal

booking_amenities
-----------------
id, booking_id (FK), amenity_id (FK), quantity, unit_price, subtotal

reservations              (mirror of bookings)
------------
id, user_id, check_in, check_out, number_of_nights, reservation_status,
payment_method, payment_status, room_total, amenities_total,
additional_guest_fee, grand_total, amount_paid, remaining_balance,
representative_name, created_at, updated_at

reservation_rooms        (mirror of booking_rooms)
------------------
id, reservation_id (FK), room_type_id (FK), quantity, price_per_night,
number_of_nights, subtotal

reservation_amenities    (mirror of booking_amenities)
----------------------
id, reservation_id (FK), amenity_id (FK), quantity, unit_price, subtotal
```

A migration should backfill `booking_rooms`/`reservation_rooms` for existing
rows from their current single `room_type_id`/`rooms_requested` columns, so
old and new transactions can be read through the same child-table query
everywhere downstream (receipts, transaction history, receptionist web)
without an `if (has child rows) ... else (read legacy columns)` branch
living forever. Existing rows with no itemized amenity data can leave
`booking_amenities`/`reservation_amenities` empty - the Android side already
falls back to the existing single `amenity_charge` dollar total when the
itemized list is empty (see "Android-side implementation" below), so this
isn't a blocking requirement for the migration.

### 2b. Eloquent model relationships

```php
class Booking extends Model
{
    public function rooms(): HasMany
    {
        return $this->hasMany(BookingRoom::class);
    }

    public function amenities(): HasMany
    {
        return $this->hasMany(BookingAmenity::class);
    }
}

class BookingRoom extends Model
{
    public function booking(): BelongsTo
    {
        return $this->belongsTo(Booking::class);
    }
}

class BookingAmenity extends Model
{
    public function booking(): BelongsTo
    {
        return $this->belongsTo(Booking::class);
    }
}

// Reservation / ReservationRoom / ReservationAmenity mirror the three
// relationships above exactly, substituting Reservation for Booking.
```

### 3. Server-side authoritative calculation - never trust client prices

On receiving a multi-room request:

1. Look up each `room_type_id`'s current price from the database - never
   accept a client-submitted price for the room total.
2. Compute `number_of_nights` server-side from `check_in`/`check_out`.
3. Compute each room line's `subtotal = price_per_night * quantity * nights`.
4. `room_total = SUM(room line subtotals)`.
5. Look up each `amenity_id`'s current price server-side; compute each
   amenity line's `subtotal = unit_price * quantity`.
6. `amenities_total = SUM(amenity line subtotals)`.
7. `grand_total = room_total + amenities_total + additional_guest_fee`.
8. Persist all of the above on the `bookings`/`reservations` row and its
   child rows - this is the one value every downstream screen (Billing
   Summary, Payment, Payment Receipt, Transaction History, notifications,
   receptionist web) should read, never a value recomputed independently on
   each screen (see "Client-side contract" above - the Android side already
   expects one trusted total to read, not to recompute).

Do **not** additionally transmit `total_rooms`/`total_room_types`/
`total_amenity_items`/`total_amenity_types` as separate stored fields - the
Android side derives these from the `rooms`/`amenities` arrays themselves
(`Booking.getTotalRoomCount()` etc.), so there's no separate count that could
ever drift out of sync with the actual line items. If it's more convenient
for the backend to include them in the response as a courtesy (matching the
example in section 10 below), that's fine too - Android will simply never
read them, since the derived value is authoritative by construction.

### 4. Atomic creation with real availability locking

```
BEGIN DATABASE TRANSACTION
  1.  Validate the authenticated user.
  2.  Validate check_in/check_out (check_out after check_in, respects the
      existing minimum-lead-time rule already enforced for the single-room
      path today).
  3.  Validate every requested room_type_id exists.
  4.  Lock/check room inventory for each room_type_id over
      [check_in, check_out) - overlap rule:
        existing.check_in < requested.check_out
        AND existing.check_out > requested.check_in
      excluding Cancelled/Rejected bookings and reservations (see section 5).
      Use row-level locking (e.g. SELECT ... FOR UPDATE) or an equivalent
      inventory-safe mechanism - see the race-condition note below for why
      this must happen inside the same transaction as the insert, not before it.
  5.  Validate requested quantity <= currently available, per room type.
      If any room type fails this, ROLLBACK the whole transaction and return
      a structured error naming which room type and the real remaining
      quantity (the Android client already has a specific string,
      error_room_no_longer_available_for_dates, ready to show this) - do NOT
      create a transaction containing only the room types that did pass.
  6.  Validate every requested amenity_id exists.
  7.  Retrieve authoritative room_type/amenity prices from the database -
      never a client-submitted price.
  8.  Calculate room_total/amenities_total/grand_total per section 3.
  9.  CREATE exactly one bookings/reservations row for the whole transaction.
  10. CREATE one booking_rooms/reservation_rooms row per requested room line.
  11. CREATE one booking_amenities/reservation_amenities row per requested
      amenity line.
  12. SAVE room_total/amenities_total/additional_guest_fee/grand_total on the
      parent row.
  13. Create the payment/billing relationship as applicable (GCash reference/
      mobile number + amount_paid for a Pay Now flow, or a pending/unpaid
      state for a Cash/Pay Later Reservation) - same as the existing
      single-room path already does, just against the one parent row above
      instead of per room type.
COMMIT
```

Equivalent Laravel shape (illustrative - fill in this project's actual
availability-locking/pricing calls):

```php
return DB::transaction(function () use ($request, $user) {
    foreach ($request->rooms as $line) {
        $available = RoomType::where('id', $line['room_type_id'])
            ->lockForUpdate()
            ->firstOrFail()
            ->availableCount($request->check_in, $request->check_out);

        if ($available < $line['quantity']) {
            throw new RoomNotAvailableException($line['room_type_id'], $available);
            // Throwing inside the closure aborts the whole DB::transaction()
            // automatically - no explicit rollback call needed, and no
            // booking/reservation row or child row from this request is left
            // behind, matching the "no partial room set" rule above.
        }
    }

    $booking = Booking::create([
        'user_id' => $user->id,
        'check_in' => $request->check_in,
        'check_out' => $request->check_out,
        // ...guest/representative/payment_method fields...
    ]);

    $roomTotal = 0;
    foreach ($request->rooms as $line) {
        $roomType = RoomType::findOrFail($line['room_type_id']);
        $subtotal = $roomType->price_per_night * $line['quantity'] * $nights;
        $booking->rooms()->create([
            'room_type_id' => $roomType->id,
            'quantity' => $line['quantity'],
            'price_per_night' => $roomType->price_per_night,
            'number_of_nights' => $nights,
            'subtotal' => $subtotal,
        ]);
        $roomTotal += $subtotal;
    }

    $amenitiesTotal = 0;
    foreach ($request->amenities ?? [] as $line) {
        $amenity = Amenity::findOrFail($line['amenity_id']);
        $subtotal = $amenity->price * $line['quantity'];
        $booking->amenities()->create([
            'amenity_id' => $amenity->id,
            'quantity' => $line['quantity'],
            'unit_price' => $amenity->price,
            'subtotal' => $subtotal,
        ]);
        $amenitiesTotal += $subtotal;
    }

    $booking->update([
        'room_total' => $roomTotal,
        'amenities_total' => $amenitiesTotal,
        'grand_total' => $roomTotal + $amenitiesTotal,
    ]);

    return $booking->load('rooms', 'amenities');
});
```

If ANY step fails, ROLLBACK the entire transaction - no partial transaction,
no partial room set. Example: Deluxe ×2 is available but Executive ×1 just
became unavailable - the correct result is that NO booking is created at
all, not a booking containing only the Deluxe rooms. This is the piece that
actually delivers "one Booking ID, all-or-nothing" - the Android client
cannot simulate this itself no matter how it's written, since true atomicity
requires a database transaction the client has no access to.

**Race-condition note:** availability must be re-checked *inside* the same
database transaction that performs the insert, with real locking (row locks
or an equivalent construct the existing database engine supports) - never
"check availability, release the connection, insert separately," since
another guest's request could take the same rooms in the gap between those
two steps. This applies per room type, independently, within the one
transaction - if the guest requested 3 room types, all 3 must be
locked/re-checked before any of the 3 child rows are inserted.

### 5. Cancelled/Rejected transactions must free their rooms

Confirm the availability query already excludes Cancelled and Rejected
bookings/reservations from the "occupied" count for a date range (this is
presumably already true for the existing single-room path - please confirm
it also applies correctly once availability is computed against the new
child-table quantities rather than a single `rooms_requested` column).

### 6. Downstream consumers that need to read the new shape

Once `booking_rooms`/`booking_amenities` (and their reservation equivalents)
exist, these need to read from them instead of a single room type per
transaction:

- The booking/reservation detail response (`ReservationDto`/`BookingDto`
  equivalents) - return `rooms: [...]` and `amenities: [...]` arrays
  alongside the existing aggregate fields, matching the shapes in section 10
  below - `ApiMapper` on the Android side already maps these onto
  `Booking.getRooms()`/`Booking.getAmenities()` (see "Android-side
  implementation" below - this part is already built and waiting).
- Payment receipt generation, if generated server-side.
- The receptionist web portal - verification, check-in, check-out screens
  all currently assume one room type per transaction and need to show/act on
  the full room and amenity list.
- Notifications generated on booking/reservation/payment events - the
  message text should list all room types/quantities, not just one.

### 7. Reservation-to-booking conversion

`ReservationWorkflowService`'s conversion logic must copy every
`reservation_rooms` and `reservation_amenities` row to `booking_rooms`/
`booking_amenities` on conversion, not just the first one - along with the
existing dates/guest info/payment fields/GCash information it presumably
already copies correctly for the single-room case today. Nothing on the
Android side needs to change for this - it already reads whatever `rooms`/
`amenities` arrays the response for the *converted* Booking contains, same
as any other transaction.

### 8. GET endpoints must return the same grouped shape

- `GET guest/bookings` (list) - each element must include `rooms: []` and
  `amenities: []`, not just a single `room_type_id`/`room_name`/`room_price`.
- `GET guest/bookings/{id}` (detail) - the same shape as the creation
  response's `booking` object in section 1, plus whatever guest-identity
  fields the existing single-room response already returns (guest account
  name, email, etc. - this document only concerns the room/amenity/billing
  structure, not fields that are already correct today).
- `GET guest/reservations` / `GET guest/reservations/{id}` - identical
  requirement, substituting `reservation`/`reservation_status`.

Android needs nothing new here beyond what's already built - `ApiMapper`
already reads `rooms`/`amenities` off exactly this shape (see "Android-side
implementation" below).

### 9. Receptionist web portal

The receptionist-facing web application (separate repository, not
inspectable from here) must be updated to operate on the same parent
transaction + child room/amenity records, not one room type at a time:

- Booking/Reservation detail view must list every `booking_rooms`/
  `reservation_rooms` row (e.g. "Deluxe ×2 / Executive ×1 / Standard ×2")
  and every amenity row under the one transaction, not show/require
  selecting a single room type.
- Payment verification, confirmation, check-in, check-out, cancellation,
  rejection, and reservation-to-booking conversion must all act on the
  parent `booking_id`/`reservation_id` and cascade to every child row -
  e.g. cancelling releases every room line's inventory, not just one.
- If the receptionist portal currently reads a flat single-room-per-record
  API response, it needs the same `rooms`/`amenities` arrays this document
  specifies for the guest-facing API - ideally the same underlying
  endpoint/serializer, so the two clients can never disagree about what a
  transaction contains.

### 9b. Duplicate-submission / idempotency protection

The new single-call endpoint replaces a client-side loop that today submits
one request per room type (see "Client-side contract" below) - collapsing to
one call *reduces* the number of places a duplicate can be created, but the
one remaining call still needs its own protection, since none of these are
new risks specific to this feature:

- **Double tap**: guest taps "Confirm Booking" twice before the button
  disables/the first response returns. Android already guards this
  client-side (`PaymentActivity`'s `isSubmittingPayment` flag, checked/set
  before submission starts - see "Client-side contract"), but a client-side
  guard alone is not sufficient; the server must not rely on it.
- **API retry / network retry**: the request reaches the server and creates
  the booking, but the response is lost in transit (dropped connection,
  timeout) - Android's Retrofit layer or an underlying OS/network stack may
  retry the same logical request, and the guest may also manually retry
  after seeing what looks like a failure.
- **Idempotency key**: have the Android client generate one client-side UUID
  per *submission attempt* (not per room, not per screen load - one per tap
  of the final Confirm button) and send it as an `idempotency_key` field on
  the create request. On the server, store this key against the created
  `bookings`/`reservations` row (a unique index on the column is enough to
  enforce it) and, if a request arrives with a key that already exists,
  return the *original* created booking/reservation instead of creating a
  second one - same response shape, `success: true`, no error. This makes
  retries of the exact same submission attempt safe by construction, without
  the server needing to guess "is this a duplicate?" from check-in/check-out/
  room selection matching (which, as this document's own "Separate
  Submission Test" requirement below establishes, must **never** be used to
  merge two genuinely separate, intentional submissions - only an explicit
  key the client itself generated once per attempt is a safe signal).
- This is unrelated to `BookingGroupState` (a purely client-side, on-device
  display convenience - see "Client-side contract") and unrelated to the
  availability row-locking in section 4 above (which prevents two *different*
  guests from overbooking the same room, not one guest's request from being
  processed twice) - all three are independent safeguards for different
  failure modes and none substitutes for another.

### 10. Required tests

At minimum, verify:

1. One room type × one room (existing behavior, must still work).
2. One room type × multiple rooms.
3. Multiple room types × multiple quantities, one transaction.
4. Multiple room types, zero amenities - `amenities_total` = 0, `grand_total`
   = `room_total` exactly (no null/undefined amenities fields).
5. Multiple room types + one amenity.
6. Multiple room types + multiple amenities.
7–11. GCash 20% / 30% / 40% / 50% / Full - each computed from the complete
   `grand_total`, never from one room's subtotal.
12. Cash - same grouped rooms/amenities/totals as GCash, just a different
    `payment_method`.
13. Requested quantity exceeds availability - request rejected, no rows
    created for any room type in the request.
14. A room type becomes unavailable between availability check and commit
    (simulate concurrent booking) - request rejected atomically, no partial
    transaction.
15. Reservation → Booking conversion carries every room/amenity row over.
16. Cancelling a grouped transaction releases every room line's inventory.
17. Rejecting a grouped transaction releases every room line's inventory.
18. `GET` Booking Details returns every room row for a grouped transaction.
19. `GET` Reservation Details returns every room row for a grouped
    transaction.
20. The Payment Receipt data source returns the complete grouped
    transaction (rooms, amenities, totals, GCash info, representative name)
    for a grouped transaction, not just the first room.
21. An existing single-room transaction created before this change still
    loads correctly through every endpoint above (backward compatibility).
22. Submitting the same request twice with the same `idempotency_key`
    (double tap, client retry) creates exactly one booking/reservation, and
    the second response returns the first one's data rather than erroring.
23. Two genuinely separate submissions (two different `idempotency_key`
    values) with identical guest, dates, and room types still create two
    independent booking/reservation IDs - same-looking input must never be
    treated as a duplicate of a different, intentional submission.

## Android-side implementation (already done, ready and waiting)

The following is **already built and shipped** in this repo, dormant until
the backend contract above exists. None of it required guessing at field
names beyond what's specified here, and none of it changes what the app
does today (every new field/branch is empty/unreached on every live
response, so behavior is unchanged until the backend actually returns data
in this shape):

- `BookingRoom`/`BookingAmenity` (plain data classes: type/name, quantity,
  price, subtotal) and their DTO counterparts `network/dto/BookingRoomDto`/
  `BookingAmenityDto`, matching section 1's JSON exactly.
- `Booking.getRooms()`/`getAmenities()` - empty `List` by default, populated
  by `ApiMapper` from `dto.rooms`/`dto.amenities` (pre-conversion) or
  `dto.booking.billing.rooms`/`.amenities` (post-conversion) the moment
  either is non-null. `getTotalRoomCount()`/`getTotalRoomTypeCount()`/
  `getTotalAmenityItemCount()`/`getTotalAmenityTypeCount()` are derived from
  those lists, never separately stored - see section 3's note on why.
- **The list screen and Booking Details already render the real structure
  in preference to the legacy client-side grouping**, and the two code
  paths are deliberately kept separate rather than merged into one
  "best guess" path:
  - `BookingAndReservationActivity.BookingsAdapter` checks
    `booking.getRooms().isEmpty()` first; if false, it renders the true
    single-record summary (`buildTrueMultiRoomSummaryText()`) using the
    real room/amenity arrays and the record's own already-correct
    `grand_total` - no summing needed, since there's only one record. Only
    when that's empty does it fall back to the existing `BookingGroupState`
    legacy path (`buildGroupSummaryText()`, summing across the N sibling
    records the current single-room-per-call workaround produces). A
    transaction created through the new endpoint is never tagged by
    `BookingGroupState` in the first place (it's one call, not N), so these
    two paths never fire for the same transaction - no explicit "which one
    wins" logic was even needed beyond checking `getRooms()` first.
  - `BookingDetailsActivity` follows the identical priority order in both
    `buildRoomInfoSection()` (itemized rooms) and `buildPaymentSummarySection()`
    (itemized amenities, room/amenity charge totals): real `getRooms()`/
    `getAmenities()` first, legacy group-member reconstruction second, plain
    single-room fields last.
- `BookingGroupState` and the per-room-type sequential submission loop in
  `PaymentActivity` (`submitPendingBookingGroups()`/
  `submitPendingReservationGroups()`) are **untouched** - switching
  submission itself to a single call is the one remaining Android change,
  deliberately not made yet (see "What begins working automatically" below
  for why).

### What still needs an Android change once the endpoint ships

- `PaymentActivity`'s sequential per-group submission loop collapses into a
  single call that sends the whole `rooms`/`amenities` array from
  `BookingWizardState.selectedRoomsGroupedByType()` (already-existing
  grouping logic, just sent once instead of driving N sequential requests).
  This is the *only* remaining Android change, and it's deliberately not
  done yet: pointing it at a `rooms`-array endpoint that doesn't exist on
  the live server today would break every real booking/reservation
  submission in production immediately. This has to be the last change,
  made once the endpoint is confirmed live.
- `BookingGroupState` can stay in the codebase indefinitely rather than
  being deleted - it's still correct for any already-existing grouped
  transaction created before this ships, and the priority-order check above
  already means it's simply never consulted for a new true-multi-room
  transaction.

## Related gap found while auditing: the "Modify Reservation" flow

`Step8ReviewPaymentFragment.saveEditedReservation()` (the one-time "Modify"
edit of an already-created, not-yet-converted Reservation) collapses the
whole selection down to `state.selectedRooms.get(0)` plus a total count
across every type, because `RoomRepository.updateReservationFull()` calls
into the same single-room `update()` endpoint the rest of this document is
about. This is the same constraint, not a separate client bug - if the
guest's edited selection only ever contains the reservation's original room
type (the normal case today, since it could only ever have been created
with one type), this is harmless. If Modify is ever extended to let a guest
add a second room type to an existing reservation, this code would silently
mislabel the total quantity under the first type's name - worth keeping in
mind if/when an `update()` equivalent of the multi-room `rooms` array above
gets built.
