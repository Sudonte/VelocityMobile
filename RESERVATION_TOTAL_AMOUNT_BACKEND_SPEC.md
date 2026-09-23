# Reservation total amount undercounts amenities - root cause and backend spec

Scope note: same constraint as the other `*_BACKEND_SPEC.md` files in this
repo - no Laravel source on this machine. Written for whoever owns that
repository.

## 0. Root cause - confirmed, exact, 100% reproducible from the code alone

**File:** `app/src/main/java/com/example/velocitysuites/network/ApiMapper.java`,
`toBooking(ReservationDto dto)`, lines 142-145:

```java
} else {
    double nightlyRate = dto.room_type != null ? dto.room_type.rateAsDouble() : 0;
    long nights = nightsBetween(dto.check_in, dto.check_out);
    totalAmount = nightlyRate * Math.max(1, nights) * Math.max(1, dto.rooms_requested);
    ...
```

This `else` branch runs whenever `dto.booking == null` - i.e. **any reservation
that hasn't been converted into a Booking yet**, which includes every fresh
"Pending, no payment yet" reservation (exactly Reservation #280 in the bug
report: 2× Deluxe, ₱3,500/night, 1 night, ₱200 of add-on amenities,
`Pending`). For that state, `totalAmount` is computed as
`nightlyRate × nights × roomsRequested` = `3500 × 1 × 2` = **₱7,000** -
amenities are never added, because **`ReservationDto` has no field at all**
for an amenities total or an amenities list (confirmed by reading the full
class - `check-in`/`check-out`/`room_type`/`rooms_requested`/`payments`/
`discount_preview`/`booking`, nothing else). There is nothing wrong with the
Android arithmetic here - the bug is that the data needed to do the
arithmetic correctly was never in the response to begin with.

For comparison, the `if` branch just above (taken once `dto.booking != null`,
i.e. after conversion) reads `dto.booking.billing.totalAmountAsDouble()` - a
real `Billing` row that, per the existing app's own conventions, already
has amenities baked in by the time it's created. So the bug is specific to
the **pre-conversion, "Pending" reservation state** - which is exactly the
state screenshotted in the bug report (`Payment: Reservation - no payment
yet`).

## 1. Why this isn't a client-side calculation bug

The Step 8 Bill Summary (`Step8ReviewPaymentFragment`) computes the correct
₱7,200 from data it already has locally in the wizard
(`BookingWizardState.selectedAmenities` + selected rooms) - it never talks
to the server for this number. When "Confirm Reservation" is tapped, the
request sent to `POST guest/reservations`
(`RoomRepository#createReservation()` / `ReservationRequest`) already
includes the selected amenities correctly - `amenity_id` + `quantity` per
selection, confirmed by reading the exact request-building code. The
request is fine. The problem is entirely in what comes back afterward:
the `ReservationDto` response (and every subsequent
`GET guest/reservations` refresh) has no field carrying either the
amenities total or an amenities-inclusive grand total for a
not-yet-converted reservation, so the Android app has no correct number to
read even though it asked for one implicitly by sending the amenities in
the first place.

## 2. What already exists and proves the fix is straightforward

`GET guest/reservations/{id}/amenities/requestable` already exists
(`Api\AmenityRequestController::requestable()`) and already returns each
paid amenity's `price` and `original_quantity` for a given reservation id -
this is the exact data needed to compute the correct total, and it's
already used successfully elsewhere in this app
(`BillingSummaryActivity`) for the identical correction. This proves the
backend already computes/stores amenity pricing per reservation
somewhere - it just isn't surfaced on the main reservation object/list
response.

`ReservationDto.discount_preview` is also a precedent already in the
codebase: a small nested object added specifically to give the *pre-Billing-row*
reservation state an accurate financial preview it wouldn't
otherwise have. Amenities need the exact same treatment.

## 3. Required backend fix

Add either of these to the JSON already returned by
`GET guest/reservations`, `GET guest/reservations/{id}`, and the
`POST guest/reservations` creation response (all three must agree):

**Option A (preferred - minimal, mirrors `discount_preview`):**
```json
{
  "amenities_total": 200.00,
  "total_amount": 7200.00
}
```
`total_amount` should always be the complete, amenities-inclusive grand
total regardless of conversion state - `room_subtotal + amenities_total`
before conversion, the real `Billing.total_amount` after. This lets Android
drop its fallback formula entirely and just read one authoritative field,
exactly like the converted-Booking branch already does.

**Option B (more data, if useful elsewhere):** also return the
`amenities` array itself (id, name, price, quantity) on the reservation
response, the same shape `RequestableAmenityDto` already has, so the
mobile client (and any other consumer) can render a full breakdown without
a second request.

Either way, apply the exact calculation already specified informally by
this bug report and already used correctly in `Step8ReviewPaymentFragment`:

```
roomSubtotal      = roomRate × roomQuantity × numberOfNights
amenitiesSubtotal = Σ(amenityPrice × amenityQuantity)
grandTotal        = roomSubtotal + amenitiesSubtotal
```

## 4. Android-side fix already applied in this pass (partial mitigation)

Two screens that render a single already-known reservation were corrected
to call the existing `amenities/requestable` endpoint themselves and add
the result to `getTotalAmount()` when the reservation hasn't converted yet:

- `BillingSummaryActivity` (`updateBillingBreakdownAmounts()`) - previously
  assumed `getTotalAmount()` always already included amenities and
  subtracted them back out to find the "room-only" figure; that assumption
  was correct for a converted Booking but wrong for a pending Reservation,
  which silently produced an under-counted Grand Total identical to this
  bug. Now branches on `booking.isHasBooking()`.
- `BookingDetailsActivity` - previously read `booking.getTotalAmount()`
  directly with no correction at all (the "Reservation Details" screen in
  the bug report's third screenshot). Now fetches
  `amenities/requestable` once on load for a not-yet-converted reservation
  and corrects `booking.setTotalAmount(...)` in place before rendering, so
  every row derived from it (Total Amount, Remaining Balance) is correct
  with no further per-row changes.

## 5. What's NOT fixed yet, and why it has to happen server-side

- **The All Reservations list cards** (`TransactionAdapter`,
  `BookingAndReservationActivity`) still show the undercounted
  `booking.getTotalAmount()`. Fixing this the same way as sections above
  would mean firing one `amenities/requestable` request per row in a list
  that can hold dozens of reservations (N+1 network calls just to render a
  list) - the wrong trade-off. The correct fix is section 3: once the list
  endpoint itself returns the right `total_amount`, this screen is fixed
  automatically with zero Android changes, since it already just displays
  `booking.getTotalAmount()`.
- **Dashboard** ("Next Hotel Transaction", overview stats) and anywhere
  else that reads a `Booking` from `RoomRepository`'s shared cache without
  its own dedicated per-item screen: same reasoning, same fix location.
- **Pay Now / GCash payment percentage calculations** (`PaymentActivity`):
  these read whatever total the loaded `Booking` already carries, so they
  inherit the same undercount today and will be correct automatically the
  moment section 3 ships - no separate mobile fix needed there once the API
  is right.
- **Reservation-to-booking conversion**: once converted, the real `Billing`
  row becomes authoritative (the `if` branch in `toBooking()`, which is
  not part of this bug). Confirm server-side that whatever creates that
  `Billing` row at conversion time also correctly sums amenities into it -
  I can't verify that logic from here, but the failure mode would look
  identical to this bug if it also under-counts.

## 6. Acceptance test

Once section 3 ships: create a reservation with 2× Deluxe (₱3,500/night, 1
night) + the same three amenities from the bug report (₱50 + ₱100 + ₱50).
Confirm `GET guest/reservations` returns `total_amount: 7200.00` for that
row immediately after creation (not just after conversion), and that the
All Reservations card, Reservation Details, and any Pay Now amount all
read that same 7200 figure without needing the client-side workarounds in
section 4.
