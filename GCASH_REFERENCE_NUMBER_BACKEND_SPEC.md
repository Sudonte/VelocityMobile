# Backend validation needed for the GCash Reference Number (Step 3 of 5)

Scope note: same as `PAYMENT_STEP5_BACKEND_SPEC.md` - this Android project has
no Laravel/backend source anywhere on this machine. The app is a pure REST
client of a live, already-deployed server at `https://velocitysuites.com/api/`.
Everything below is written for whoever owns that server-side repository; it
cannot be applied from here.

## Client-side contract (already correct, for reference)

`PaymentActivity`'s Step 3 field (`etGcashReferenceNumber`) only ever sends
the **raw 13 numeric digits** as the `reference_number` field - display-only
grouping spaces (`GcashReferenceFormatter`, "XXX XXXX XXXXXX") are stripped
before submission (`gcashReferenceDigitsOnly()`), on both request paths:

- `POST guest/reservations/{id}/payments` (multipart `reference_number` part,
  `RoomRepository.submitGcashPayment()`)
- `POST guest/bookings` (multipart `reference_number` part,
  `RoomRepository.createDirectBooking()`)

Every Android-side model that carries this value (`PaymentDto.reference_number`,
`Booking.PaymentRecord.referenceNumber`, `PaymentRequest.reference_number`) is
typed as `String`, never a numeric type - so no digit can ever be dropped by
integer overflow/leading-zero truncation on this side.

## What still needs confirming/enforcing server-side

The Android form now enforces "exactly 13 digits, numeric only" client-side,
but that is a UX convenience, not a security boundary - a request can always
be replayed outside the app. Please confirm/add on every endpoint that
accepts `reference_number` (`guest/bookings`, `guest/reservations/{id}/payments`):

1. **Required.** Reject a missing/empty `reference_number` with a clear 422,
   not a generic 500 or a silently-accepted null.
2. **Format.** Reject any value that isn't exactly 13 characters, all `0-9`,
   after the server strips any stray non-digit characters itself (defense in
   depth - do not assume the client always sends a clean value). Example
   Laravel rule: `['required', 'regex:/^\d{13}$/']` applied to the value
   *after* stripping non-digits server-side, or reject outright if the raw
   incoming value contains anything but digits (the Android client never
   sends spaces/hyphens, so a request that does should be treated as
   suspicious rather than silently sanitized).
3. **Storage type.** Store as `VARCHAR(13)` (or similar string column), never
   an integer/bigint column - this is an identifier, not a value used in
   arithmetic, and a numeric column would silently truncate a leading `0`
   (GCash reference numbers can start with `0`).
4. **Uniqueness.** Confirm the existing uniqueness constraint on
   `reference_number` (already observed client-side via the 409/validation
   error `PaymentActivity.isDuplicateReferenceError()` reconciles against) is
   scoped correctly and returns a distinguishable error code/message so the
   client can keep showing "This GCash reference number has already been
   submitted" specifically, rather than a generic validation failure.

None of this requires a schema change if the column is already
`VARCHAR`/`string` - it's a request to confirm the validation rule (item 2)
actually rejects malformed input rather than relying on the Android app to
be the only thing enforcing it.
