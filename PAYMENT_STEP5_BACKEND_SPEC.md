# Backend changes needed to fully close out the Step 5 payment bug

Scope note: this Android project has no Laravel source anywhere on this machine
(no `composer.json`/`artisan`/`.php` files, no `backend`/`api`/`server` folder).
The app is a pure REST client of a live, already-deployed server at
`https://velocitysuites.com/api/`. Everything below is written for whoever owns
that Laravel repository - it cannot be applied from this project.

## 0. NEW: "Payment submission failed: We received an unexpected response..." — root cause confirmed, Android-side fix applied

This is a distinct bug from section 1's duplicate-reference issue, though it
compounds it. Confirmed by reading the actual code path, not by guessing:

- `RoomRepository.networkErrorMessage(Throwable t)` maps any
  `com.google.gson.JsonParseException` or `java.io.IOException` (that isn't
  one of the more specific timeout/DNS/SSL cases checked first) to
  `error_unexpected_response` ("We received an unexpected response from the
  server. Please try again.").
- Retrofit's `Call.enqueue()` routes a request into `onFailure(Throwable)`
  for **two** completely different situations it cannot itself tell apart:
  (a) the request never reached the server / no response came back at all,
  and (b) an HTTP response (including a real `200`/`201` success) **was**
  received, but converting its JSON body into the expected DTO
  (`DirectBookingResponseDto`, `PaymentSubmitResponse`) threw - e.g. the
  connection dropped after the server had already written and saved the
  booking/payment row but before the confirmation body finished streaming, or
  the server's JSON shape didn't exactly match a numeric/string field the DTO
  expects (`GsonResponseBodyConverter.convert()` lets any `RuntimeException`
  from Gson propagate straight into `onFailure`, never `onResponse`).
- In case (b) **the booking/payment was already saved server-side** - the
  guest is shown a scary generic error for a submission that actually
  succeeded, and a retry risks hitting the exact duplicate-reference
  rejection described in section 1.

**Android-side fix applied (`RoomRepository.java`):** `createDirectBooking()`
and `submitGcashPayment()` no longer report `onFailure` straight to the
guest. They now call a new `reconcileAfterFailure()` helper first, which
re-fetches `guest/bookings` + `guest/reservations` and checks whether a
booking now exists (scoped to the authenticated guest's own token) whose
payment history already contains the exact reference number that was just
submitted. If found, the submission is treated as the success it actually
was and the guest proceeds normally; only if genuinely not found does the
original "unexpected response" (or timeout/network) error get shown. This
also directly covers test cases Q/R (retry after timeout; server saves the
record but the client loses the connection before seeing the response).

**Also applied:** `ApiClient.java`'s `HttpLoggingInterceptor` now logs full
request/response bodies (`Level.BODY`) in debug builds only
(`BuildConfig.DEBUG`, newly enabled via `buildFeatures.buildConfig = true` in
`app/build.gradle.kts`) so the exact response JSON that trips a future parse
failure is visible in Logcat during development, per this task's explicit
"log the actual HTTP status code and response body" requirement. Release
builds are untouched (`Level.BASIC`, no bodies logged) - nothing sensitive is
newly exposed to guests or in production logs.

**What still needs backend-side confirmation**, since the exact malformed
field can't be identified without seeing a real failing response body (no
Laravel source on this machine, no device logs from an actual repro): check
whether any of `DirectBookingResponseDto`'s or `PaymentDto`'s numeric fields
(`id`, `guest_id`, `room_type_id`, `billing_id`, etc. - all declared as Java
primitive `long`/`int`, matching the API's documented shape) can ever be
serialized as a non-numeric string (e.g. an empty string `""` instead of
`null`) for a booking created through an edge-case path (multi-room-type
group, async job, etc.) - that specific shape would throw exactly the
`JsonSyntaxException` this bug reproduces. Also check for any middleware
(auth, CSRF, maintenance mode) that could return an HTML error page with a
`200` status on this route - Gson would fail identically on that body. The
debug logging above will surface the real body next time this reproduces.

## 1. Root cause actually confirmed on the client side

`PaymentActivity.submitPendingBookingGroups()` is the "New Booking" GCash Step-5
submit handler. It calls `POST guest/bookings` (`Api\BookingController::store()`,
per this app's own code comments) with `reference_number`/`gcash_number`/`receipt`
attached. The 20s OkHttp read timeout + a large receipt-image multipart upload
means the request can legitimately reach the server and be saved successfully
while the guest's device never sees the response (dropped connection, timeout,
backgrounded app, etc.). The guest then sees a generic failure, the reference
number field still holds the same value (nothing was cleared), and either the
guest or an automatic retry resubmits the identical request - which the
server correctly rejects via `reference_number`'s uniqueness rule, because it
now genuinely belongs to the payment row the first attempt already created.
This is a true "at-least-once delivery" problem, not a validation bug - the
unique constraint itself is doing exactly what it should.

The Android app now works around this by re-fetching `guest/bookings` +
`guest/reservations` after a duplicate-reference rejection and checking
whether any booking already belonging to the authenticated guest carries that
exact reference number in its payment history; if so it treats the submission
as a success instead of blocking the guest. That is a safe recovery (the list
is scoped to the guest's own token, so a match can only be their own prior
attempt), but it is reconciliation after the fact, not prevention - two
things still need to happen server-side:

## 2. Required: idempotent submission (prevents the double-charge risk entirely)

Add support for a client-supplied idempotency key on the payment-creating
endpoints (`POST guest/bookings`, `POST guest/reservations/{id}/payments`):

- Accept a header, e.g. `Idempotency-Key: <client-generated UUID>`, one value
  per logical Step-5 submission attempt (the Android client would generate
  this once when Step 5 is entered/whenever the reference number or receipt
  changes, and reuse it across retries of that same attempt).
- Store it on the `payments` row (new nullable, unique `idempotency_key`
  column).
- On `POST`, if a payment already exists with that key: return the existing
  booking/payment (200/201 with the same body shape) instead of re-validating
  or re-inserting. Do this check *before* the `reference_number` uniqueness
  check, wrapped in the same DB transaction as the insert, to close the race
  window entirely (two near-simultaneous requests with the same key must not
  both pass a "does it exist yet" check and both insert).
- This is the standard fix for exactly the symptom described ("Step 5 submits
  the same reference twice") and removes the need for the client-side
  reconciliation workaround to be the only safety net.

## 3. Required: exclude the record itself from the uniqueness check on update

If the backend ever moves to a draft-payment-then-finalize model (a payment
row is written before Step 5's final submit, then updated rather than
recreated), the `reference_number` uniqueness validation on that update path
must exclude the row's own id, e.g. (Laravel validation rule):

```php
Rule::unique('payments', 'reference_number')->ignore($payment->id),
```

Today's `guest/bookings`/`guest/reservations/{id}/payments` endpoints appear
to create the payment atomically in one call (no separate draft step), so
this specific rule may not be needed as-is - but if a draft/pending payment
record is introduced later (e.g. to support pre-generating a Booking ID, see
below), this exclusion is mandatory or every finalize will look like a
self-duplicate.

## 4. Required: pre-created, permanent Booking ID before Step 5

Step 5 must display a real Booking ID before the guest submits payment, and
that exact same ID must stay attached to the same booking for its entire
lifecycle (create → pending verification → confirmed → completed) - never
regenerated, never replaced, never shown differently in two places.

**This is not a client-side mapping bug.** Before writing this spec I read
`PaymentActivity.java`/`RoomRepository.java`/`Booking.java` directly: for a
New Booking + GCash submission, `currentBooking` is `null` for the entire
Steps 1-5 lifecycle because `POST guest/bookings` (`RoomRepository#createDirectBooking()`)
is the ONLY call that creates the Booking row, and it does so atomically
together with the GCash payment - this is a deliberate rule stated in this
app's own code comments ("no Booking row exists yet... A Booking must never
be created before that succeeds"). There is no `booking_id` field being
dropped, mis-mapped, or lost in a `Bundle`/`Intent`/ViewModel - there is
simply no booking record anywhere (client or server) until that one call
succeeds. Confirm this against your own controller before assuming a mapping
fix will resolve it: if `Api\BookingController::store()` truly only inserts
the `bookings` row inside the same request that also validates/saves the
`payments` row, a pre-existing ID cannot exist earlier no matter what the
Android code does.

Closing this requires a genuinely new endpoint, called once the guest
finishes the booking wizard (Step 8 Confirm) and *before* the GCash portal
opens:

### New endpoint: `POST guest/bookings/draft`

Request (no payment fields at all - this call only reserves the booking):
```json
{
  "room_type_id": 12,
  "rooms_requested": 1,
  "check_in": "2026-09-14",
  "check_out": "2026-09-16",
  "adults": 2,
  "children": 0,
  "guest_first_name": "Juan",
  "guest_last_name": "Dela Cruz",
  "id_card_type": "None"
}
```

Response:
```json
{
  "id": 125,
  "booking_id": "VS-BKG-20260912-000001",
  "status": "PAYMENT_IN_PROGRESS"
}
```

Server-side, inside one DB transaction:
1. Insert the `bookings` row (status `PAYMENT_IN_PROGRESS` /
   `PENDING_PAYMENT_SUBMISSION` - pick one canonical name and use it
   everywhere below).
2. Generate `booking_id` (format `VS-BKG-YYYYMMDD-NNNNNN`, `NNNNNN` a
   database-safe daily sequence - e.g. `SELECT ... FOR UPDATE` on a per-day
   counter row, or an auto-increment `booking_number` column combined with
   the creation date - never a value computed only in PHP from `count()`,
   which races under concurrent inserts).
3. Persist `booking_id` on that same row (new `booking_id` varchar/unique
   column on `bookings`), commit.

Field naming: keep the wire format `booking_id` (snake_case, matching every
other field this API already returns) and let the Android `BookingDto`/
`DirectBookingResponseDto`/`ApiMapper` map it to a `publicBookingId` field on
the domain `Booking` object - the existing numeric `id` keeps being used for
every API path/relationship call (`cancelPayment`, `voidPayment`,
`uploadIdCard`, etc.), `booking_id` is guest-facing display only. Do not
repurpose the numeric `id` field's wire name for this - that will break the
Android app's existing `Booking.getId()` usage everywhere else.

### Existing submit endpoint becomes a finalize, not a create

`POST guest/bookings/{id}/payment` (new, or repurpose the existing
`guest/bookings` POST to accept an existing `id` and skip the room/dates
fields when present) attaches the GCash payment to the draft created above:

```json
{
  "gcash_mobile_number": "9594646465",
  "gcash_reference_number": "4i2o283848483939",
  "payment_type": "partial",
  "amount_paid": 1000.00
}
```

Server-side:
1. Load booking `id`, confirm it belongs to the authenticated guest and is
   still in `PAYMENT_IN_PROGRESS` (reject with a clear error if it's already
   been paid/finalized - see section 6, "already finalized" guard).
2. Validate `gcash_reference_number` uniqueness **excluding this booking's
   own not-yet-inserted payment** - since the payment row is being inserted
   for the first time here (not updated), a plain unique-on-insert check is
   correct as long as this booking never gets a second `POST` to this same
   endpoint (guard with the "already finalized" check in step 1, not with an
   `ignore()`). If a retry/idempotency-key hits this endpoint twice for the
   same draft (see section 2), that must short-circuit to "already finalized,
   return existing payment" before reaching the uniqueness check at all.
3. Insert the `payments` row, set `payment_status = pending_verification`.
4. Update `bookings.status = PENDING_PAYMENT_VERIFICATION`.
5. Commit. Return the same `id`/`booking_id` back, unchanged, plus the new
   payment's fields.

### Status constants (pick canonical names, use identically everywhere)

```
PAYMENT_IN_PROGRESS          -- draft created, guest still filling GCash form
PENDING_PAYMENT_VERIFICATION -- guest submitted, awaiting receptionist review
CONFIRMED                    -- receptionist verified the payment
COMPLETED / CANCELLED / ...  -- existing downstream statuses, unaffected
```
Payment status, separately: `pending_verification` → `verified` (full) /
`partially_paid` (partial) - this already exists and the Android client
already displays it as-is (see section 5).

### Immutability

`booking_id` is written once, at draft-creation, inside the same transaction
that inserts the row - never updated by any later endpoint (payment submit,
verify, cancel, no-show, completion). Enforce this in code (no
`UPDATE bookings SET booking_id = ...` anywhere past creation) rather than
relying on convention.

Until this exists, the Android app now shows "Will be generated after
submission" instead of a bare "N/A" for the Booking ID row on Step 5, and
displays the real id everywhere else the moment it comes back from
`POST guest/bookings`'s response (success dialog, Booking Details, the
receptionist dashboard already reads it from the same field). That is the
correct behavior for the *current* one-call API - it is not a substitute for
the draft endpoint above, which is the only way to make Step 5 show a real ID
*before* submission as required.

## 6. Android-side follow-up, once the draft endpoint above ships

This is scoped out of the current Android changes because there is nothing
real to call yet - wiring the wizard to a guessed request/response shape
would either fail against production or silently drift from whatever the
backend actually ships. Once `POST guest/bookings/draft` exists, the Android
change is:

- `Step8ReviewPaymentFragment`'s Confirm button (Booking mode) calls the new
  draft endpoint instead of just staging `PendingBookingPayload` and jumping
  straight to `PaymentActivity` - store the returned `id`/`booking_id` in
  `BookingWizardState`/a new `PendingBookingPayload` field.
- `PaymentActivity` opens already carrying a real booking id (like the
  existing "Pay Now" `BOOKING_ID` extra path already does) - `populateGcashReviewStep()`'s
  fallback string becomes dead code for this flow (kept for the genuine edge
  case where the draft lookup fails - see below).
- `submitPendingBookingGroups()` is replaced by a single call to the new
  finalize endpoint (`POST guest/bookings/{id}/payment`) - no more per-room-
  type-group looping with the same GCash reference sent multiple times (that
  loop only existed because `createDirectBooking()` bundled room creation and
  payment into the same call; once room selection happens at draft time and
  payment finalization is a separate, single call per booking, the multi-
  room-type reference-reuse issue in the current code disappears entirely).
- If the draft call itself fails (network/server error) before Step 5 ever
  opens, block payment submission and show "Unable to generate your Booking
  ID. Please try again." with a Retry action, per the original task's error-
  handling requirement - do not let the guest reach Step 5 without a real ID
  once this endpoint exists.

## 7. Acceptance test to run once sections 2-6 ship

Create a booking, note the generated `booking_id` (e.g.
`VS-BKG-20260912-000015`), and verify the exact same string appears in: the
draft-creation response, Step 5 of `payment.xml`, the payment-submission
request/response, the success dialog, Bookings > All Booking, Booking
Details, the receptionist Bookings module, receptionist payment
verification, and any related notification. It must never read `N/A`, never
change to a different value (e.g. `...-000016`) after payment, and no
duplicate booking/payment/ID may be created by a retried submission.

## 8. Status transitions (verify only - appears already correct)

Nothing in the client forces `Confirmed`/`Verified` immediately after
submission - `PaymentActivity` only ever displays whatever
`payment_verification_status`/booking `status` the server returns
(`status_pending_verification_*` strings, `renderPaymentVerificationStatus()`).
Please confirm server-side that `POST guest/bookings` (GCash) sets the new
payment to a pending-verification state and the booking to a
pending-payment-verification state, and that only the receptionist's
Verify action flips them to Confirmed/Verified - the Android side already
assumes and displays whatever you send here, nothing further to change on
that end once confirmed.
