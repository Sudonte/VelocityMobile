# GCash payment → reservation-to-booking conversion → receptionist verification - backend spec

Scope note: same constraint as the other `*_BACKEND_SPEC.md` files in this
repo - no Laravel source, and no web Receptionist portal source, exist on
this machine. Written for whoever owns those repositories.

## Important: don't rename the existing status vocabulary to match this task's example names

The task's suggested statuses (`PENDING_PAYMENT`, `PAYMENT_SUBMITTED`,
`PAID_PENDING_VERIFICATION`, etc.) are illustrative, not literal existing
column values - the Android client's own code comments (`ApiMapper`'s
docblock, confirmed in earlier work this session) already reference the
*actual* backend enum vocabulary in use: `AWAITING_CASH_CONFIRMATION`,
`AWAITING_GCASH_PAYMENT`, `TO_BE_CONVERTED`, `REJECTED_RESERVATION`,
`CANCELLED_RESERVATION`, `CONVERTED_TO_BOOKING` (reservation side) and
`ACTIVE_BOOKING`, `CHECKED_IN`, `COMPLETED_BOOKING`, `CANCELLED_BOOKING`
(booking side), collapsed client-side into `Pending`/`Confirmed`/
`Checked-In`/`Checked-Out`/`Cancelled`/`Rejected`. Please map this task's
*semantics* onto whatever your real enum already is rather than introducing
a second, parallel status vocabulary that would then need reconciling with
the first - the acceptance criteria below are written against the
semantics, not the literal string names.

## Good news: the core rule this task cares about most is already built

Before writing this, I traced `PaymentActivity.finalizePayment()` and its
own code comments, which reference a server-side
`ReservationWorkflowService::tryAutoConvert()` and
`ReservationWorkflowService::recordDepositPayment()`. Based on that evidence
(the client explicitly checks `Booking.isHasBooking()` post-payment rather
than assuming conversion happened, and its comments describe exactly this
mechanism), the backend already appears to:

1. Auto-convert a Reservation into a Booking **synchronously when a GCash
   payment is submitted** - not at verification time. This actually already
   matches this task's section 2 (conversion happens at submission, not
   verification).
2. Never auto-convert a Cash reservation this way (cash has no online
   proof-of-payment step to trigger it - a Cash "Pay Later" reservation
   stays a Reservation until a receptionist explicitly records the cash
   payment in person).
3. Never set the booking to a client-visible "Confirmed" state purely from
   this conversion - the Android app already only ever shows "Confirmed" via
   `TransactionCategorizer`/`PaymentStatusResolver` once the *payment* itself
   (not just the conversion) is staff-verified (`isStaffVerified()`).

**Please confirm this is genuinely how it behaves today** (I inferred it
from client-side comments and status-mapping code, not by reading the
service itself, which isn't in this repo) - if true, sections 1, 2, 3, 4,
11, 12, 20, and 21 of this task are already satisfied server-side and need
no change. If conversion actually happens later (e.g., only at verification,
contrary to what the client-side comments assume), the Android app's own
navigation logic (see below) already degrades gracefully either way, since
it branches on the real `isHasBooking()` value rather than assuming timing -
but the *business* answer to "does Bookings show this before verification"
depends entirely on which of the two your service actually does.

## Fixed on the Android side this pass

`PaymentActivity`'s "GCash Pay Later" reservation-creation flow (create the
reservation with no payment yet) was inconsistently routing to the generic
Dashboard afterward, while the parallel Cash-Pay-Later flow already
correctly routed to Reservations - All Reservations. Fixed to reuse the same
already-correct `finalizePayment()` routing (which itself already correctly
distinguishes "just converted to a real paid Booking" from "still an
unconverted Reservation" using the server-refreshed `isHasBooking()`, not a
guess). Also tightened the "Paid - Pending Verification"-equivalent status
label wording (`status_payment_verification_label`) to read closer to this
task's requested phrasing.

Everything else this task asks for on the guest-facing side - never showing
"Confirmed" before verification, the success dialog naming the real Booking
ID, landing on Bookings → All Bookings with the new item scrolled-to and
highlighted, and re-syncing on `onResume()`/pull-to-refresh/return-to-screen
- was already implemented and verified correct in earlier work this session
(`RoomRepository`'s shared `BookingsChangedListener` pub-sub +
`onResume()`'s `refreshMyBookings()` already satisfy this task's section 16
"if the app doesn't support real-time updates, refresh on resume/pull-to-
refresh" fallback - there's no WebSocket/Firebase listener in this app, and
adding one is a larger architectural change out of scope for this pass).

## What still needs the backend/receptionist portal specifically

### 1. The receptionist Verify/Reject actions and their UI (task section 6-10)

Entirely a separate, not-present-here web codebase. On **Verify and
Confirm**: validate reference number/receipt/amount presence, record
`verified_by` + `verified_at`, set payment status to verified/paid (partial
or full per the stored `payment_type`), and only *then* flip the
booking/reservation's client-visible status to something
`ApiMapper.displayStatus()` maps to `"Confirmed"` - already exactly how the
Android side interprets it, so no client change is needed once this exists.
On **Reject Payment**: require a reason, store it in whatever field
`Booking.getRejectionReason()`/`getTransactionRejectionReason()` already
read from (confirmed existing, already displayed client-side), and do not
touch the booking/payment status toward Confirmed.

### 2. Server-side status-transition guarding (task section 18-19)

Confirm the receptionist-only verification endpoint is the *only* write path
that can move a payment to "verified"/a booking to "Confirmed" - the
guest-facing payment-submission endpoint
(`RoomRepository.submitGcashPayment()` calls into whatever
`POST guest/reservations/{id}/payments` already is) must only ever be able
to reach a "submitted, awaiting verification" state, never further. This is
a request to confirm/enforce it authoritatively server-side, not an Android
change - the client has no way to request "Confirmed" today (confirmed by
reading every call site that touches `paymentMethod`/`referenceNumber`/etc.
this session; none of them send a status field at all, only payment
details), so there's nothing to lock down further on this side.

### 3. `verified_by` exposure (also needed for `PaymentReceiptActivity`, see `PAYMENT_RECEIPT_BACKEND_SPEC.md`)

Same field gap already flagged in that spec - once the API returns who
verified a payment, both the receipt screen and (if wanted) a Booking
Details row can show it; no other Android change needed beyond binding it.

### 4. Duplicate GCash reference prevention (task section 14)

Client-side, the guest's own reference-number reuse is already checked
against their own cached transactions
(`PaymentActivity.findBookingByReference()`, confirmed existing from earlier
work this session) with the exact message this task requests. That's
necessarily partial (same caveat as every other client-side check in this
project) - the backend must independently reject a reference number already
used on *any* guest's verified or pending payment, not just the currently
signed-in guest's own cache, and return a message the client's existing
generic error-surfacing path (`RoomRepository#errorMessage()`, which already
forwards a `message` field verbatim - confirmed in earlier work this
session) can show as-is.

### 5. Booking ID format (task section 13)

If not already the case, generate `BKG-YYYY-######` (or your existing
equivalent format) server-side with a real uniqueness guarantee (sequence/
auto-increment, not a timestamp or random value) - purely a database/
backend concern, the Android side only ever displays whatever id string the
API returns and never generates one itself.
