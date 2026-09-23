# Guest transaction date-conflict validation - backend spec

Scope note: same constraint as the other `*_BACKEND_SPEC.md` files in this
repo - no Laravel source on this machine. Written for whoever owns that
repository. Related to, but distinct from, `ROOM_AVAILABILITY_BACKEND_SPEC.md`
- see the "two different validations" note in section 3 below before reading
either as a substitute for the other.

## 0. Out of scope, on purpose: the `johnpaulombid.gmail.com` cleanup

The request to delete that one guest's Cancelled/Rejected Booking rows is a
one-off production **data** change against a live guest account, not a code
change. It has not been attempted here: this repository has no database
connection, no backend source, and no admin tooling to run it against, and a
change like this is exactly the kind of high-blast-radius, irreversible
action that needs to go through whoever actually administers that database -
not get done by an Android client code change. Two things worth flagging
explicitly:

- Do **not** implement this as an Android-side "if guest email == X, hide
  their cancelled bookings" special case even as a stopgap - hardcoding one
  real customer's email into shipped app code to alter what they see is a
  privacy/security anti-pattern, and it's also the literal thing section 17
  below (and the app's existing architecture) says not to do: the guest is
  identified by their authenticated session/token, never by an email the
  client asserts.
- If/when this is done directly against the database, it should follow the
  same soft-delete convention already established for `RoomRepository#
  hideBooking()` (a `hidden_at`-style column, not a hard `DELETE`) so the
  record survives for accounting/audit exactly like a guest-initiated delete
  would, scoped to `status IN ('CANCELLED','REJECTED') AND guest_id = ?` for
  that one account only.

## 1. What's already handled client-side (no backend action needed for these)

`Step2DatesFragment` (Step 1 of the Booking/Reservation wizard) now validates
the guest's own date range against their own other active transactions
before Next is enabled - `RoomRepository#findConflictingStayForGuest()`,
called from `validateBeforeNext()` and inline right after both dates are
picked. It already:

- Merges Bookings and Reservations (both already live in one cache, see
  `RoomRepository#refreshBookings()`) - a new Reservation is checked against
  existing Bookings and vice versa, per this task's section 4.
- Excludes `Cancelled`/`Checked-Out`/`Rejected` from blocking (this app's
  three-way collapsed terminal-status set - see
  `ROOM_AVAILABILITY_BACKEND_SPEC.md` section 0 for why there's nothing else
  to add to that list).
- Uses half-open-range real date comparison (`newIn < existingOut &&
  existingIn < newOut`), so a check-in on someone's checkout day is allowed
  - same formula already used by the room-type-scoped
  `findOverlappingBooking()`.
- Excludes the transaction currently being edited from its own conflict
  check on a Modify run (`BookingWizardActivity#getEditingReservationId()`
  passed through as `excludeBookingId`).
- Shows the two distinct messages from this task verbatim (exact duplicate
  vs. general overlap).

This is a courtesy, client-known-data-only check (same caveat as every other
client-side availability check in this app) - it can only see what's already
been fetched into this device's session. **The backend must independently
re-validate at creation time** (section 2) since it's the only party that
can see concurrent requests, other sessions, and can't be bypassed by a
modified/replayed request.

## 2. What still needs the backend (can't be verified or implemented from here)

Before creating a Booking or Reservation, re-run the same guest-level
conflict check server-side, scoped to the **authenticated guest's** id (never
an id/email the request body claims - section 3), across both tables:

```sql
SELECT id FROM bookings
WHERE guest_id = :authenticated_guest_id
  AND status NOT IN ('CANCELLED', 'REJECTED')
  AND check_in_date < :new_check_out
  AND check_out_date > :new_check_in
UNION
SELECT id FROM reservations
WHERE guest_id = :authenticated_guest_id
  AND status NOT IN ('CANCELLED_RESERVATION', 'REJECTED_RESERVATION')
  AND check_in_date < :new_check_out
  AND check_out_date > :new_check_in
```

(substitute your actual enum values - see `ROOM_AVAILABILITY_BACKEND_SPEC.md`
section 0/2 for the exact backend status vocabulary this app already assumes)

On a hit, reject with a distinguishable response the client can branch on -
`RoomRepository#errorMessage()` already forwards a `message` field straight
to the guest verbatim (confirmed in `ROOM_AVAILABILITY_BACKEND_SPEC.md`
section 3), so:

```json
{"success": false, "code": "DATE_CONFLICT", "message": "Your selected stay dates overlap with another active booking or reservation. Please select different dates."}
```

on a 422 is enough for the existing client error-handling path to surface it
correctly with no further Android change needed - `code` is for the
backend's/logs' own use, only `message` reaches the guest today.

## 3. Two different validations - don't merge them into one query

This task's own section 21 flags this and it's worth restating precisely
against this app's actual architecture:

- **Guest Transaction Date Validation** (this doc): can the *same guest*
  have two overlapping stays, regardless of room type or other guests. Scope
  is `WHERE guest_id = ?`.
- **Room Availability Validation** (`ROOM_AVAILABILITY_BACKEND_SPEC.md`):
  does a specific room *type* have inventory left for a date range,
  considering every guest. Scope is `WHERE room_type_id = ? AND check_in <
  ... `, no `guest_id` filter at all.

Two different guests picking the same dates must never trip the first check
against each other (scenario 12 in this task) - only room inventory
(the second check, already covered by the existing `RoomAvailabilityService`)
can legitimately block that. Keep these as two separate queries/services
server-side, same as the Android client now keeps
`findConflictingStayForGuest()` (this doc) and `findOverlappingBooking()`/
`isAvailableForDates()` (room-type-scoped) as two separate methods rather
than one merged check.

## 4. Reservation-to-booking conversion must exclude its own source row

When converting Reservation X into Booking Y, the conflict query in section 2
must exclude X's own id (`AND id != :source_reservation_id` on the
reservations half of the query) - otherwise the reservation being converted
would trip its own guest-level conflict check against the booking being
created from it. This conversion path isn't guest-initiated from anywhere in
this Android codebase (no local code calls it), so there's nothing to change
here beyond confirming the exclusion exists wherever that conversion is
actually implemented server-side.

## 5. Idempotency / duplicate submission

Android-side double-submit guards already exist and were verified in earlier
work on this project - `Step8ReviewPaymentFragment.submitting`,
`PaymentActivity.isSubmittingPayment`, and
`BookingAndReservationActivity.isSubmitting` all disable their respective
Confirm/Submit buttons for the duration of the in-flight request. That's a
UX guard, not a correctness guarantee against a genuine network retry
resending the same request after a response was lost in transit. If not
already in place, wrap the conflict-check-then-insert in section 2 in a
database transaction (`SELECT ... FOR UPDATE` or equivalent row locking on
the conflict-check query) so two near-simultaneous requests for the guest's
same dates can't both pass the check before either commits - this is the
same race-condition class already documented for room inventory in
`ROOM_AVAILABILITY_BACKEND_SPEC.md`, just keyed by guest instead of room type.

## 6. Security

Every query above is keyed by the guest id resolved from the authenticated
session/token (Sanctum or equivalent - whatever already gates every other
`guest/*` route in this API), never a guest id or email read from the
request body. This is already how every existing endpoint in `ApiService`
behaves (no request in this Android app ever sends its own guest id - it's
implicit from the auth header) - restating it here only because this task's
data-cleanup request (section 0) specifically called out an email address,
and that pattern must not leak into how conflict validation resolves "which
guest".
