# Permanent transaction deletion - backend spec

> **Update (2026-09-18): found and fixed the actual root cause of a
> Completed (Checked-Out) Booking's Delete Permanently failing - e.g. a real
> guest-reported case, Booking #334 (guest Bryan Dela Cruz, ×2 rooms,
> COMPLETED_BOOKING/"Checked-Out", grand total ₱10,150.00). This was never
> covered by the 2026-09-15 test matrix below, which only exercised (a) a
> genuinely direct Booking and (b) a converted-**then-cancelled**
> Reservation+Booking pair - never a converted-**then-completed** one.
>
> **Root cause (client-side, now fixed on Android):**
> `ApiMapper.toBooking(ReservationDto)` builds every converted transaction's
> `Booking` object keyed by the *reservation's* id (`getId()` - the
> intentional, documented "guests see everything keyed by the reservation id"
> convention). `BookingAndReservationActivity#confirmDeleteTransaction()` and
> `TransactionListActivity#confirmDelete()` used to route *any* non-direct
> transaction's delete through `DELETE guest/reservations/{id}` using that
> same id - including a converted transaction whose Booking had since reached
> `COMPLETED_BOOKING` ("Checked-Out"), which the client's own Delete
> Permanently button already (correctly) allows on the Booking tab. But per
> this document's own "Required server-side checks" section above, the
> reservations endpoint only ever accepts a still-**unconverted**
> reservation's own `CANCELLED_RESERVATION`/`REJECTED_RESERVATION` status -
> once converted, `reservation.status` stays `CONVERTED_TO_BOOKING`
> permanently (see `GCASH_BOOKING_STATUS_BACKEND_SPEC.md`'s enum list; nothing
> ever transitions it back), so that endpoint refuses the request with `409`
> every single time for this case, regardless of what the resulting Booking's
> own `booking_status` is. This is exactly the generic "This transaction
> cannot be deleted because of its current status or related data" the guest
> saw - not a bug in the confirmation dialog, the id being sent, or the
> booking's own eligibility, but the wrong *endpoint* being called for an
> otherwise-perfectly-eligible Completed transaction.
>
> (The 2026-09-15 "converted-then-cancelled" test passing via the reservations
> endpoint is not a contradiction - cancelling a converted booking is
> apparently the one post-conversion event that also flips the founding
> reservation's own status back to a terminal one, most likely so
> `GUEST_DATE_CONFLICT_BACKEND_SPEC.md`'s reservations-table-only conflict
> query doesn't keep treating a cancelled stay's dates as still blocking a new
> reservation. Completing a stay (checkout) has no such reason to touch the
> reservation row, so it never does, and `CONVERTED_TO_BOOKING` sticks
> forever - please confirm this asymmetry is intentional on your side too.)
>
> **Android-side fix applied:** `ApiMapper.toBooking(ReservationDto)` now
> also captures the nested Booking row's own id (`dto.booking.id`) into a new
> `Booking.nestedBookingRecordId` field (deliberately kept separate from the
> pre-existing `convertedBookingId`, which is historical-reservation-only and
> drives an unrelated detail-page display row - reusing it would have leaked
> that UI row onto every converted booking's details page). A new
> `Booking.getBookingEndpointDeleteId()` now centralizes the routing decision
> for both delete call sites: returns the direct booking's own id when
> `isDirectBooking()`, the nested id when converted **and** `COMPLETED`
> (Checked-Out), or `null` (meaning "use the reservations endpoint with
> `getId()` instead") for every other case - unchanged behavior for a plain
> unconverted Cancelled/Rejected reservation and for a converted-then-
> cancelled pair. `RoomRepository.deleteBookingPermanently()`'s local
> cache-eviction predicate was also updated to match on
> `getNestedBookingRecordId()` for this new case (it previously only matched
> `isDirectBooking()` rows, so the item would have stayed visible locally
> until the next full refresh even after a successful server-side delete).
>
> **Still needs backend verification - not fixable from the Android repo:**
> please confirm `Api\BookingController::destroy()` was actually built/tested
> to also handle a Booking whose `reservation_id` is **not** null, since the
> deployed code shown further below in this document explicitly says it
> "mirrors this for a genuinely direct Booking (`reservation_id` always
> null)" - i.e. it may have only ever been exercised for that case. Two
> things to check specifically: (1) it must delete/clean up the **parent**
> `reservations` row too (in the same transaction) whenever
> `$booking->reservation_id` is set, or a guest deleting a Completed
> converted booking via this new path will leave that reservation row - and
> its own now-dangling `CONVERTED_TO_BOOKING` status - permanently orphaned
> in the database; (2) the ownership check must key off `$booking->guest_id`
> directly (not assume it can only be reached through the reservation), which
> should already be true but is worth a direct confirmation given this path
> was apparently never exercised end-to-end before.
>
> **Update (2026-09-15, later still): implemented, deployed, and tested
> directly against the live server.** With the user's explicit authorization,
> connected via SSH to the Hostinger box hosting `velocitysuites.com` and
> found the real deployed Laravel app at
> `~/domains/velocitysuites.com/public_html/hotel_reservation` (a git
> checkout of `github.com/Sudonte/velocitysuites`, branch
> `fix/multi-room-direct-booking`). Added `Api\ReservationController::destroy()`
> and `Api\BookingController::destroy()` plus their
> `DELETE /guest/reservations/{reservation}` and
> `DELETE /guest/bookings/{booking}` routes, backed by a live
> `information_schema` audit of every FK touching `reservations`/`bookings`
> (recorded below) so the cascade/manual-delete split is exact, not guessed.
> Verified end-to-end with disposable test accounts/transactions created and
> torn down for this purpose only (never touched a real guest's data):
> ownership (403), auth (401), ineligible status (409), not-found-after-delete
> (404), successful delete of a plain cancelled reservation, of a
> converted-then-cancelled reservation+booking pair (confirms the
> `bookings.reservation_id` cascade), and of a genuinely direct booking -
> in every case confirming via direct DB query that the row and every
> owned child row (room/amenity line items, payments, amenity requests,
> notifications) were actually gone, receipt files were removed from
> storage, and sibling/unrelated records were untouched. Files changed:
> `routes/api.php`, `app/Http/Controllers/Api/ReservationController.php`,
> `app/Http/Controllers/Api/BookingController.php` - originals backed up to
> `~/db-backups/pre_delete_feature_<timestamp>/` on the server in addition
> to the existing git history, before any edit was made.
>
> **Update (2026-09-15, earlier): confirmed live and reproduced on
> device.** Tapping "Delete Permanently" on a Cancelled/Rejected Booking now
> round-trips to `https://velocitysuites.com/api/guest/bookings/{id}` with
> `DELETE`, and the live server responds `405 Method Not Allowed` with body
> `{"message": "The DELETE method is not supported for route ... Supported
> methods: GET, HEAD, POST."}` (a stock Laravel `MethodNotAllowedHttpException`
> message - this only fires when a route already exists for that URI under a
> *different* verb, e.g. an `apiResource(...)->except(['destroy'])`
> registration, or simply no `destroy`/`Route::delete(...)` entry was ever
> added). **This confirms the client-side implementation is correct end to
> end** (right id, right URL, right verb, auth header attached, error body
> parsed and logged) **and the only remaining gap is that the two routes
> below have not been added to the live server yet.** Nothing on the Android
> side can work around a route the server doesn't expose - this genuinely
> needs a deploy to `velocitysuites.com`'s backend. The Android app has
> separately been hardened so this specific failure mode no longer shows the
> guest a raw/truncated framework error string (see
> `RoomRepository.deleteErrorMessage()`) and so nothing is deleted
> client-side unless the server actually confirms success.
>
> **Update (2026-09-14): this document now describes a hard, permanent
> `DELETE` instead of the previous soft `hidden_at` hide.** The original
> "Delete" feature (see git history / the old version of this file) turned
> out to only ever soft-hide a row (`PUT guest/reservations/{id}/hide`,
> `PUT guest/bookings/{id}/hide`) - the record stayed in the database
> forever, just excluded from `GET guest/reservations`/`GET guest/bookings`.
> That does not satisfy "permanently delete" as now required: a guest must
> be able to make an eligible transaction and its own child records
> actually disappear from the database. The Android client
> (`ApiService.deleteReservationPermanently()`/`deleteBookingPermanently()`,
> `RoomRepository.deleteReservationPermanently()`/`deleteBookingPermanently()`,
> wired into `BookingAndReservationActivity#confirmDeleteTransaction()` and
> `TransactionListActivity#confirmDelete()`) has already been switched over
> to call a real `DELETE` endpoint instead of the old `PUT .../hide` ones.
> **The `PUT .../hide` routes/controller methods described in the old
> version of this document are no longer called by the app and can be
> removed once this new `DELETE` behavior is live** (or left in place
> unused - Android's choice not required either way).

Scope note: unlike the other `*_BACKEND_SPEC.md` files in this repo, this one
*was* implemented and deployed directly (see the update note above) - the
sections below now describe the actual live implementation, not a proposal.

## What the client now calls

```
DELETE guest/reservations/{id}   -> Api\ReservationController::destroy()
DELETE guest/bookings/{id}       -> Api\BookingController::destroy()
```

Both are behind the same auth guard as every other `guest/*` route. Success
response can be `200 {"message": "..."}` or `204 No Content` - the Android
side (`RoomRepository`) only checks `response.isSuccessful()`, it never
reads a body from this call, so either shape is fine.

## Required server-side checks, in order, for both endpoints

1. **Authenticated** - existing Sanctum/session guard already covers this.
2. **Exists** - 404 if the id doesn't exist in the respective table at all.
3. **Owned by the caller** - `reservation.guest_id`/`booking.guest_id` (or
   however this schema names the FK to the authenticated guest's account)
   must equal `auth()->id()`. If not, respond `403` (or `404` if this
   codebase prefers not to reveal that a differently-owned row exists -
   either is acceptable, just don't delete). **This check must never be
   skippable via the id alone** - a guest must not be able to delete
   another guest's transaction by editing the id in the request, even
   though the Android UI never lets them construct such a request itself.
4. **Eligible status** - reject anything else with `409` or `422`:
   - `DELETE guest/reservations/{id}`: only when the reservation's status
     is **Cancelled or Rejected** (`CANCELLED_RESERVATION` /
     `REJECTED_RESERVATION` in the enum vocabulary from
     `GCASH_BOOKING_STATUS_BACKEND_SPEC.md`). A reservation that has been
     staff-verified but not yet converted, or that has already converted to
     a Booking (`CONVERTED_TO_BOOKING`), must be refused - the Android
     client's own `TransactionCategorizer`/deletion gate already narrows to
     exactly this (see `BookingAndReservationActivity`'s `BookingsAdapter`
     bind logic), the server must enforce the same rule independently
     rather than trusting the client.
   - `DELETE guest/bookings/{id}`: only when `booking_status` is
     `CANCELLED_BOOKING` or `COMPLETED_BOOKING`. There is no separate
     "rejected" enum member on the Booking side - a rejected GCash payment
     (`ReservationWorkflowService::reconcileGcashBookingPayment()`) already
     lands the booking on `CANCELLED_BOOKING` with `rejection_reason` set,
     so `CANCELLED_BOOKING` alone covers both Cancelled and Rejected from
     the guest's perspective. `ACTIVE_BOOKING`/`CHECKED_IN` must be refused.
5. **Already deleted** - since this is now a real `DELETE` rather than an
   idempotent hide-flag, a replayed request naturally 404s once the first
   one succeeds (the row is gone). No special idempotency handling needed
   beyond the ordinary 404 from step 2.

## The real schema has no separate `receipts`/`additional_guests` tables

Earlier drafts of this document (and of `PAYMENT_RECEIPT_BACKEND_SPEC.md`/
`MULTI_ROOM_TRANSACTION_BACKEND_SPEC.md`) guessed at a `receipts` table and
an `additional_guests` child table. Neither exists on the live schema://
a receipt is just `payments.receipt_path` (a Storage `public`-disk path,
one column on the same Payment row - no separate table), and additional
guest details are `additional_guest_details`, a JSON column directly on
`reservations`/`bookings` (cast to `array` on the model) - deleting the
parent row removes them automatically, no child-table cleanup needed. The
real child tables are `reservation_room_lines`/`reservation_amenities`
(Reservation side) and `booking_room_lines`/`booking_amenity_lines`/
`booking_rooms` (Booking side, the last one being the physical room-
assignment pivot, set at check-in).

## Deletion must be permanent, transactional, and correctly scoped

Wrap the whole delete in a DB transaction so it's all-or-nothing - if any
child delete fails (e.g. an unexpected FK constraint), nothing is left
half-deleted. The exact FK delete rules on the live database (queried
directly via `information_schema.KEY_COLUMN_USAGE`/`REFERENTIAL_CONSTRAINTS`,
not guessed) are:

```
payments.reservation_id -> reservations.id     ON DELETE RESTRICT
payments.booking_id     -> bookings.id         ON DELETE RESTRICT
payments.billing_id     -> billings.id         ON DELETE CASCADE
amenity_requests.reservation_id -> reservations.id  ON DELETE SET NULL
amenity_requests.booking_id     -> bookings.id      ON DELETE RESTRICT
reservation_room_lines.reservation_id -> reservations.id  ON DELETE CASCADE
reservation_amenities.reservation_id  -> reservations.id  ON DELETE CASCADE
booking_room_lines.booking_id   -> bookings.id  ON DELETE CASCADE
booking_amenity_lines.booking_id -> bookings.id ON DELETE CASCADE
booking_rooms.booking_id        -> bookings.id  ON DELETE CASCADE
billings.booking_id             -> bookings.id  ON DELETE CASCADE
bookings.reservation_id         -> reservations.id ON DELETE CASCADE
notifications.reference_id -> (no FK - relaxed by the
  2026_08_23_153000_relax_notifications_reference_id_constraint migration,
  since reference_id can point at either table depending on `category`)
```

Practically: only `payments.*` and `amenity_requests.booking_id` are
`RESTRICT` and must be cleared by hand before the parent row can be
deleted at all. Everything else - `billings`, both `*_room_lines` tables,
`booking_amenity_lines`, `booking_rooms`, and (critically) `bookings`
itself via `reservation_id` - CASCADEs automatically the instant the
`reservations` row is deleted, which is also how a converted-then-
cancelled Reservation+Booking pair gets deleted together through a single
`DELETE guest/reservations/{id}` call. `amenity_requests.reservation_id`
is only `SET NULL` (not cascade, not restrict), so it's cleared explicitly
too, purely so a deleted transaction doesn't leave orphaned amenity-request
rows lying around.

Actual, deployed `Api\ReservationController::destroy()` body:

```php
$booking = $reservation->booking;
$billingId = $booking?->billing?->id;

// Gathered up front (before anything is deleted) so receipt files for
// EVERY payment shape - reservation_id, booking_id, or billing_id-
// reparented-at-checkout - can be removed too, since a DB-level cascade
// never runs application code (i.e. never deletes a Storage file).
$payments = Payment::where('reservation_id', $reservation->id)
    ->when($booking, fn ($q) => $q->orWhere('booking_id', $booking->id))
    ->when($billingId, fn ($q) => $q->orWhere('billing_id', $billingId))
    ->get();

DB::transaction(function () use ($reservation, $booking, $payments) {
    foreach ($payments as $payment) {
        if ($payment->receipt_path) {
            Storage::disk('public')->delete($payment->receipt_path);
        }
    }
    if ($booking) {
        AmenityRequest::where('booking_id', $booking->id)->delete(); // RESTRICT
    }
    Payment::where('reservation_id', $reservation->id)               // RESTRICT
        ->when($booking, fn ($q) => $q->orWhere('booking_id', $booking->id))
        ->delete();
    AmenityRequest::where('reservation_id', $reservation->id)->delete(); // SET NULL, cleared anyway
    Notification::where('category', 'reservation')->where('reference_id', $reservation->id)->delete();
    if ($booking) {
        Notification::where('category', 'booking')->where('reference_id', $booking->id)->delete();
    }
    if ($reservation->id_card_image_path) {
        Storage::disk('local')->delete($reservation->id_card_image_path);
    }
    $reservation->delete(); // cascades booking -> billing -> payments/*_lines, reservation_room_lines, reservation_amenities
});
```

`Api\BookingController::destroy()` mirrors this for a genuinely direct
Booking (`reservation_id` always null), with one difference: **`Booking`
uses Eloquent's `SoftDeletes` trait**, so it calls `$booking->forceDelete()`
instead of `->delete()` - a plain `delete()` there would only set
`deleted_at` (yet another soft-hide, not the hard delete this feature
requires) while leaving the row, and every one of its CASCADE children,
still physically in the database.

### Never touch shared/master data

Every delete above is scoped through a specific transaction's own
relationship (`$reservation->payments()`, not `Payment::where('guest_id', ...)`
or similar). This must never cascade into:

- Room type / room master records, amenity master records, hotel
  configuration, payment-method master data.
- The guest's user account or guest profile.
- Any other transaction belonging to the same guest.
- **Correction from an earlier draft of this section:** a converted
  Reservation+Booking pair is *deliberately* deleted together through
  `DELETE guest/reservations/{id}` alone (via the `bookings.reservation_id`
  CASCADE) - this is correct, not a bug, and mirrors `hide()`'s existing
  behavior exactly. The Android client only ever keys a converted
  transaction by its Reservation id (`ApiMapper.toBooking()`'s "guests see
  everything keyed by the reservation id even after conversion"
  convention - see `TRANSACTION_DELETE_BACKEND_SPEC.md`'s Android-side
  counterpart notes in `BookingAndReservationActivity`), so the Booking
  row it's paired with is never a "different guest's" or "different
  transaction's" data - it's the *same* transaction, just past the
  conversion point. What must never happen is a **direct** Booking
  (`reservation_id` already null) being reachable by anything other than
  `DELETE guest/bookings/{id}`, or a Reservation/Booking id resolving to
  a row this guest doesn't actually own (see the ownership check above) -
  those are the two real ways this could otherwise go wrong.

## What was actually tested (2026-09-15)

Against the live server, using disposable test accounts/transactions created
and fully cleaned up afterward (two throwaway Users/Guests, deleted at the
end - cascades removed everything else they owned):

- `DELETE` a Cancelled reservation (no booking) as its own owner -> `200`,
  reservation + room line + amenity + payment + amenity request +
  notification + receipt file all confirmed gone via direct DB/Storage
  query afterward.
- `DELETE` the same reservation again -> `404`.
- `DELETE` an active/ineligible reservation -> `409`, confirmed it still
  exists afterward.
- `DELETE` a reservation as a *different* guest's token -> `403`, confirmed
  it still exists afterward.
- `DELETE` with no bearer token -> `401`.
- `DELETE` a converted-then-cancelled Reservation+Booking pair via the
  reservation id -> `200`, confirmed both rows and every one of the
  Booking's own child rows (room/amenity line, payment, amenity request,
  notification, receipt file) gone.
- `DELETE` a genuinely direct Booking via `guest/bookings/{id}` -> `200`,
  confirmed the row (via `withTrashed()`, to specifically rule out it only
  being soft-deleted) and every child row/receipt file gone.
- A sibling Cancelled reservation and an ineligible reservation belonging
  to the *same* test guest, never targeted by any of the above, were
  confirmed still present and untouched throughout.

## Response codes summary

| Scenario                                   | Status |
|---------------------------------------------|--------|
| Success                                     | 200 or 204 |
| Not authenticated                           | 401 |
| Id doesn't exist                            | 404 |
| Exists but belongs to another guest         | 403 (or 404) |
| Exists, owned, but status not eligible      | 409 or 422 |
| Any other server error                      | 500 (Android shows the generic "couldn't delete" toast either way) |

The Android side surfaces every non-2xx response as
`getString(R.string.delete_transaction_failed_format, message)` via
`RoomRepository.RepositoryCallback#onError`, and explicitly does **not**
remove the item from the on-screen list unless the server actually
returned success - so a bug here fails safe (the guest just sees a "please
try again" toast, never a phantom deletion).
