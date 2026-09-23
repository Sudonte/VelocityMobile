# Room availability / cancellation - backend spec

Scope note: same constraint as the other `*_BACKEND_SPEC.md` files in this
repo - no Laravel source on this machine. Written for whoever owns that
repository.

## 0. Good news: the client-side logic this task worried about is already correct

Before writing this, I read `RoomRepository.findOverlappingBooking()`,
`RoomRepository.isAvailableForDates()`, `BookingAndReservationActivity`'s
double-booking guard (the code that runs right before a guest submits a new
booking/reservation), and its `showCancelDialog()`/`cancelAnyBooking()` flow.
All of the client-owned pieces of this task are already implemented
correctly:

- **Status exclusion is already correct.** `findOverlappingBooking()`
  excludes `"Cancelled"`, `"Checked-Out"`, and `"Rejected"` - and per
  `ApiMapper.displayStatus()`'s own doc comment, those three strings are
  the *entire* set of terminal/inactive statuses this app's normalized
  vocabulary has (backend enums `CANCELLED_BOOKING`, `CANCELLED_RESERVATION`,
  `REJECTED_RESERVATION`, `COMPLETED_BOOKING` are already collapsed down to
  exactly these three by the mapper before anything else sees them). There
  is no `CANCELLED_BY_GUEST`/`CANCELLED_BY_RECEPTIONIST`/`CANCELLED_REJECTED`
  distinction reaching the mobile client, so there was nothing missing to
  add on the exclusion list.
- **The overlap formula is already correct.** `newIn < existingOut &&
  existingIn < newOut` (half-open ranges) - functionally identical to this
  task's `requestedCheckIn < existingCheckOut AND requestedCheckOut >
  existingCheckIn`, and already handles the "checkout day == next
  check-in day is fine" case correctly.
- **The duplicate-date guard already ignores cancelled/rejected records
  correctly.** `BookingAndReservationActivity`'s submit handler calls
  `repository.findOverlappingBooking(...)` (not a naive "does any
  transaction exist for these dates" check) - there is no second, less
  correct duplicate-check path elsewhere in the app; I searched for one.
- **Cancellation already refreshes both the bookings cache and room
  availability.** `showCancelDialog()`'s success callback calls
  `refreshMyBookings()` (re-fetches `guest/bookings` + `guest/reservations`,
  moving the cancelled record into the Cancelled list) and *also*
  `refreshRoomsForSelectedDates()`/`repository.refreshRooms()` (re-fetches
  room availability) - both already happen automatically, no app
  restart/logout needed.
- **Cancellation eligibility is already gated correctly.** Staff-verified
  transactions are refused outright (no confirm dialog); partially-paid
  bookings get an explicit non-refundable-deposit warning before
  confirming - matching this task's "eligible for cancellation" framing.

## 1. Fixed in this pass (Android-side)

`showCancelDialog()`'s success message was a single hardcoded Toast -
`"Reservation cancelled successfully."` - shown even when a *Booking* (not
a Reservation) was cancelled, and it didn't mention that the dates were
released. Replaced with a proper confirmation dialog, correctly picking
Booking vs. Reservation title/copy, and stating explicitly that the room
and dates are now reusable (per this task's requested wording). The old
unused `cancel_success` string was removed rather than left dead.

## 2. What's left, and only fixable in the Laravel repo

The Android app deliberately never tries to count cross-guest room
inventory itself - see `RoomRepository`'s own code comment: "Cross-guest
date conflicts are the backend's call (a reservation requests a room TYPE;
staff assign an actual room at confirmation)". The client only judges the
*signed-in guest's own* overlapping stays; everything else relies on
`room.isAvailable()`, a flag the backend computes and returns. That means
this task's actual `SUM(room_quantity) ... WHERE status NOT IN (...)
AND check_in_date < ? AND check_out_date > ?` query, and the
submit-time re-check for concurrent bookings racing for the last room, both
have to be implemented/verified server-side - there is no equivalent code
in this repository to inspect or fix.

Use the **exact same three-way collapsed status set** the mobile app
already relies on (see section 0) when excluding inactive rows from that
query, so the two systems can't drift:

```
Booking-side inactive:      CANCELLED_BOOKING
Reservation-side inactive:  CANCELLED_RESERVATION, REJECTED_RESERVATION
```

Everything else (`ACTIVE_BOOKING`, `CHECKED_IN`, `AWAITING_CASH_CONFIRMATION`,
`AWAITING_GCASH_PAYMENT`, `TO_BE_CONVERTED`, `CONVERTED_TO_BOOKING`, etc.)
should count toward active room inventory. `COMPLETED_BOOKING` (mapped to
"Checked-Out" client-side) is already excluded from the client's own
duplicate guard too, on the reasoning that a stay that has already
concluded can't still be occupying a future date range - apply the same
exclusion server-side for consistency.

## 3. Update (dated Step 2 fetch, and the exact error contract the client already surfaces verbatim)

Following up after swapping the wizard's Dates/Room Selection order (Dates is
now step 1, Room Selection step 2 - see `BookingWizardActivity`,
`Step1RoomSelectionFragment`, `Step2DatesFragment`):

- **Closed a real client-side gap, not a backend one.** Room Selection used
  to call `GET rooms` with no `check_in`/`check_out` filters at all (it used
  to run *before* Dates, so it had none to send) - meaning `available_count`
  it displayed was never actually date-range-aware in practice, regardless of
  how correct `RoomAvailabilityService` is server-side. Now that Dates runs
  first, `Step1RoomSelectionFragment` always calls `refreshRooms(checkIn,
  checkOut, callback)`, so the existing `check_in`/`check_out` query params
  finally get used for their intended purpose on this screen. No new
  endpoint or param needed - this was purely an Android-side ordering bug.
- **Response shape**: the client only consumes `available_count` (already
  the correct post-subtraction number per section 0/2's rules) and
  `is_fully_booked`. It has no use for `total_inventory`/`occupied_quantity`
  breakdown fields today - `RoomTypeDto` doesn't carry them and no screen
  displays them. Not requesting a schema change here; mentioning it only in
  case the admin/staff side of the same endpoint already wants that
  breakdown for its own reasons.
- **The client already surfaces your `message` field verbatim** - worth
  knowing precisely because it means the exact guest-facing wording for a
  race-lost create request is a backend copy decision, not a client one.
  `RoomRepository#errorMessage(Response)` extracts
  `body["message"]` from any non-2xx response on `POST guest/bookings` /
  `POST guest/reservations` and passes it straight through to
  `RepositoryCallback#onError(String)`, which `Step8ReviewPaymentFragment`
  then shows in a plain `Toast` with no rewriting. So: when the atomic
  inventory check on those two create endpoints (section 2) rejects a
  request because another guest took the last room first, return
  `{"message": "The selected room is no longer available for your chosen
  dates. Please select another room."}` (or equivalent guest-appropriate
  copy) on a 4xx - that string is exactly what the guest will see. The
  Android side has been updated to also navigate the guest back to Room
  Selection automatically whenever this call fails outright, so no
  additional "please go back" instruction needs to be embedded in the
  message itself.

## 4. Acceptance tests 4-6 (cross-guest inventory) can't be verified from here

Tests 1, 2, 3, and 5 (a guest's own cancelled record never blocks them
again) are already covered by the client-side logic confirmed correct in
section 0 - no server access needed to verify those with confidence. Tests
4 and 6 (a second guest correctly blocked/allowed based on real inventory
math) depend entirely on the backend query in section 2 and can't be
exercised or confirmed without that repository.
