# Terms, Conditions, and Policy acceptance - backend spec

Scope note: same constraint as the other `*_BACKEND_SPEC.md` files in this
repo - no Laravel source on this machine. Written for whoever owns that
repository.

## What's now enforced client-side

`Step8ReviewPaymentFragment` (the final review/confirm step of the Booking
and Reservation creation wizard) now gates its Confirm button behind two
sequential guest actions, both stored on `BookingWizardState` (survives Back/
Next across the wizard, not just a fragment-local flag):

1. `termsViewed` - only flips true once the guest opens the new "View Terms,
   Conditions, and Policy" dialog (`showHotelTermsDialog()`, reusing the same
   full-screen scrollable dialog and scroll-to-bottom-marks-viewed
   interaction `RegistrationActivity` already uses for its own account
   Terms) and scrolls it to the end. Opening and immediately dismissing it
   does not count.
2. `termsAccepted` - the existing agreement checkbox, now disabled until (1)
   is true.

The Confirm/Save button only enables once both are true (plus, for a fresh
non-edit run, the wizard's own per-step validation - dates, room, guest
info - has already gated getting this far at all).

## What still needs the backend

This client-side gate proves the guest *saw* the checkbox and confirm
button behave correctly in this one app session - it is not durable proof
of acceptance, and the task's own requirement ("the backend must store
evidence that the Terms were accepted for the specific transaction ...
do not rely only on the mobile application's temporary Boolean value") can
only be satisfied server-side. I have **not** added `terms_accepted`/
`terms_version` fields to the existing `createReservation()`/
`createDirectBooking()` request bodies in `RoomRepository`/`ReservationRequest`
to send this today, because:

- I can't confirm from this repo whether the live endpoints
  (`POST guest/reservations`, `POST guest/bookings`) silently ignore unknown
  fields (safe) or reject them under strict/whitelisted validation
  (would break every booking/reservation creation in production the moment
  this shipped) - sending a field the backend doesn't expect is not a safe
  guess to make blind.
- Even if accepted, the value has nowhere durable to land until the backend
  has matching columns - sending it today would silently go nowhere.

Once the backend is ready to receive it, this is a small, mechanical
addition on both sides:

- **Backend**: add `terms_accepted` (boolean), `terms_accepted_at`
  (timestamp), `terms_version` (string, e.g. `"1.0"` - bump this whenever the
  policy text in `hotel_terms_policy_body_general`/`_gcash_addendum`/
  `_cash_addendum` changes materially) to both the `reservations` and
  `bookings` tables (or wherever each transaction's own row lives), and
  accept `terms_accepted` + `terms_version` on `POST guest/reservations` and
  `POST guest/bookings`. Reject the request (422) if `terms_accepted` isn't
  explicitly `true` on those two creation endpoints specifically - this is
  the actual authoritative enforcement point; the Android-side gate is UX
  only, exactly like every other client-side validation already documented
  across this project's other `*_BACKEND_SPEC.md` files.
- **Android** (once the above is confirmed live): add
  `request.terms_accepted = true; request.terms_version = "1.0";` to
  `ReservationRequest` inside `RoomRepository#createReservation()`, and the
  equivalent multipart fields to `createDirectBooking()` - both call sites
  already have `getState().termsAccepted` available (guaranteed `true` by
  this point, since `onConfirmClicked()` re-checks
  `termsViewed && termsAccepted` before either is ever called) to source the
  value from, with `"1.0"` (or whatever the backend's current
  `terms_version` is) as a shared constant next to it.
