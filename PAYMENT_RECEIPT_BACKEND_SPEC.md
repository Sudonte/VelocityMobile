# Official payment receipt - backend + web Receptionist spec

Scope note: same constraint as the other `*_BACKEND_SPEC.md` files in this
repo - no Laravel source, and no web Receptionist portal source, exist on
this machine. Written for whoever owns those repositories.

## Update: seventh report, a *different* record this time (238/255) - same failure shape ("49" instead of "6068"); stopped ever showing a raw partial reference on any screen

A seventh report came in with a fresh screenshot - this time `Payment
Reference: 238` / `Booking ID: 255`, `GCash Mobile #: Unavailable`,
`GCash Reference #: 49` plus the old incomplete-reference hint. This is a
**different underlying record** from every prior update in this file (235/165)
- so the "it's always the same stale test row" explanation no longer applies
on its own; this is either a second bad row, or evidence the underlying
problem is systemic rather than one-off. Re-checked the whole path again
end to end (Step 3 input cap/validation in `PaymentActivity`, the single
`RoomRepository.submitGcashPayment()` submission path, `ApiService`'s
`@Part("reference_number")`/`@Part("gcash_number")` multipart fields,
`PaymentDto`'s `String` fields, `ApiMapper`'s direct assignments, `Booking`'s
`String` properties) - confirmed, again, there is still no `substring`/
`takeLast`/`parseInt`/int-cast anywhere in this app's source touching either
field, and the one client bug this file already knows about
(`PaymentTransaction.getReferenceNumber()`) was fixed several updates ago and
re-verified still fixed.

Two things this pass did differently from prior updates, since re-tracing the
same clean path a fourth time wasn't going to find anything new:

1. **Stopped treating "short/wrong-length" as displayable.** Every prior
   update's fix still put the raw partial digits on screen (grouped as raw
   digits, with a warning line underneath) - which is exactly what let `49`
   render at all. Per this report's own explicit rule ("Legacy Payment
   Records" section: *"Do NOT invent the missing digits... otherwise display
   an appropriate message"*), the value cell itself should never hold a
   partial digit string, full stop - a guest has no way to tell `49` apart
   from a real (if odd) value just by looking at it. `GcashReferenceFormatter`
   gained `formatOrFallback(value, missingMessage, incompleteMessage)`: exactly
   13 digits -> grouped value; anything else -> one of two explicit messages
   (`receipt_gcash_value_missing` = "Unavailable" for no value at all,
   the new `receipt_gcash_reference_legacy_incomplete` = "Full GCash reference
   number is unavailable for this older payment record." for a present-but-
   short value), never the digits themselves. Replaces the old
   `formatIfComplete()` (removed - no longer has any caller) everywhere a
   GCash reference is shown: `PaymentReceiptActivity`, `BookingDetailsActivity`,
   `TransactionDetailsActivity`. The now-redundant separate warning line
   (`tvRowHint` in `item_receipt_row.xml`, `setRowHint()` in
   `PaymentReceiptActivity`) was removed too - the fallback message already
   says what the hint used to say, so keeping both was duplicating the same
   information.
2. **Sharpened the root-cause theory, since "always the same stale row" no
   longer fits.** `GCash Mobile #` reads `"Unavailable"` on *every single*
   record reported across all seven updates in this file, with no exception
   ever observed - that's the pattern worth escalating hardest: a column that
   is **always** empty across unrelated rows looks a lot less like scattered
   bad legacy data and a lot more like the write path never lands a value in
   it for *any* row (submission endpoint not persisting `gcash_number` at all,
   a `fillable`/mass-assignment guard silently dropping it, or a `NULL`
   default with no code path that ever sets it). The reference number is
   harder to explain this way (a fully-dropped write would leave it `NULL`,
   not a real 2-4 digit value - see the "third report" update below, which
   already ruled out a wrong-JSON-key theory the same way) - but it's now
   worth checking whether `reference_number` and `gcash_number` are written
   in the *same* backend code path for that payment row, since a bug that
   drops/truncates one plausibly drops/truncates the other too. Recommend the
   backend owner check, on the actual `payments` table: has **any** GCash
   payment row, ever, had a correctly-stored 13-digit `reference_number` and
   non-empty `gcash_number` at the same time? If the honest answer is "no,
   never, not once" even for very recent submissions, that's no longer
   explainable as old pre-migration test data - it points at a live bug in
   whatever endpoint/controller persists these two columns today, not
   history.

Re-verified `:app:compileDebugJavaWithJavac` and `:app:assembleDebug` both
build clean after these changes (only the pre-existing, unrelated "deprecated
API" note). To be clear about what this update does and doesn't do: it
guarantees `49`/`6068`/any other partial value can never again render as if
it were a real reference on any of the three screens that show one - it does
not and cannot recover what the guest actually typed for payment 238, because
that was never returned by the API to begin with.

## Update: sixth report, same record (235/165) - reconfirmed backend-only; found and fixed a real per-payment data-isolation bug while re-auditing

A sixth report came in, again showing `GCash Mobile #: Not on file for this
payment` and `GCash Reference #: 6068` for Booking ID 165 / Payment
Reference 235 - the same database row every update above already tracks.
The screenshot's mobile-number fallback text ("Not on file for this
payment") is still the pre-fix string, confirming (again) that the
screenshot is from a build that predates the fixes already in this repo's
source - current `strings.xml` has read `"Unavailable"` since several
updates ago. Re-verified the full chain end to end once more (Step 2/3
capture and validation, multipart submission field names, `PaymentDto`'s
`String` fields, `ApiMapper`'s direct assignments, `Booking`'s `String`
properties, `PaymentReceiptActivity`'s binding/formatting) and found nothing
new to add to the "606 8" update's root-cause checklist - it's still exactly
that record's stored data, not a client bug, and the fastest path to
confirming the fix is still a fresh end-to-end GCash payment rather than
re-inspecting record 235 a seventh time.

While re-auditing, this pass did find and fix one genuine Android-side bug,
unrelated to record 235/165 itself but squarely inside the "make sure
Payment A never shows Payment B's GCash details" requirement other updates
in this file already flag as important:

- **`PaymentTransaction.getReferenceNumber()` was inverted.** It returned
  `null` (which callers then resolved by falling back to
  `Booking.getTransactionRef()`, the *booking's overall latest payment*)
  whenever a real itemized `Booking.PaymentRecord` was attached to the row,
  even though `PaymentRecord.referenceNumber` already held that exact
  payment's own correct reference number (populated from `PaymentDto
  .reference_number` in `ApiMapper`, per-payment, since early in this file's
  history). Net effect: a booking with two or more GCash payments would show
  the *latest* payment's reference number on every one of its payment rows
  in `TransactionDetailsActivity`, not each row's own - the exact
  "Payment A displays Payment B's GCash details" bug this file's "correct
  payment-specific database relationship" section warns about, just for
  multiple payments on one booking rather than across different bookings.
  Fixed to return `record.referenceNumber` when a record is present, falling
  back to the booking-level value only for the synthetic summary row
  (`record == null`) that already never had its own itemized record to read.
- **`Booking.PaymentRecord` had no `gcashNumber` field**, even though
  `PaymentDto.gcash_number` is already returned per-payment by the API - so
  there was no way for a per-payment view to show a payment-specific mobile
  number at all, only the booking-level latest. Added `gcashNumber` to
  `PaymentRecord` (new 6-arg constructor, old 5-arg one preserved and
  delegates with `null`), wired both `ApiMapper.toBooking()` construction
  sites to pass `p.gcash_number`, and added `PaymentTransaction
  .getGcashNumber()` mirroring the fixed `getReferenceNumber()` rule.
- Extracted the receipt's mobile-number/reference formatting
  (`formatGcashMobileNumber()`/`formatReceiptGcashReference()`) into two new
  reusable static methods on the existing shared `GcashReferenceFormatter`
  (`formatMobileNumber()`, `formatIfComplete()`) - `PaymentReceiptActivity`
  now just delegates to them, and `BookingDetailsActivity`/
  `TransactionDetailsActivity` use the exact same two methods, so all three
  screens format a GCash value identically instead of each having (or
  needing) their own copy of this logic.
- **Replaced the generic "Transaction Ref." row with "GCash Reference #"
  (plus a new "GCash Mobile #" row) on `BookingDetailsActivity`'s Payment tab
  and `TransactionDetailsActivity`'s info section, for GCash payments only.**
  Cash payments on both screens keep the original "Transaction Ref." label
  and raw value completely unchanged - this was a straight `if/else` split
  on `"gcash".equalsIgnoreCase(paymentMethod)`, not a removal of the Cash
  path. The now-unused `details_label_gcash_number` string (the old "GCash
  Number" label `BookingDetailsActivity` no longer needs, since GCash Mobile
  # now uses the receipt's own label/formatting) was deleted rather than
  left dead.

Verified `:app:compileDebugJavaWithJavac` and `:app:assembleDebug` both
build clean after all of the above. None of this changes what value is
displayed when the underlying data is present and correct, and none of it
can retroactively fix record 235/165's stored data - that remains the
backend-only gap tracked in every update above.

## Update: fixed GCash mobile number vs. reference number confusion on the receipt

The receipt previously had a row labeled "GCash Number" that was actually
bound to `Booking.getTransactionRef()` (the reference number) - the real
GCash mobile number (`Booking.getGcashNumber()`) was never shown on the
receipt at all. Fixed by giving each its own row in both `PaymentReceiptActivity`
and `activity_payment_receipt.xml`: "GCash Number" (mobile, formatted
`+63 9XX XXX XXXX`) and "GCash Reference Number" (formatted via the existing
app-wide `GcashReferenceFormatter`, 3-4-6 grouping). The Booking Information
section's GCash Reference Number row now reads from the exact same formatted
local variable as the Payment Details section's row, so the two can never
disagree. Also added "Guest Account Name" and "Guest Email" rows (from the
authenticated account's own `VelocityPrefs` `userName`/`userEmail`, matching
`DashboardActivity`'s existing pattern) distinct from "Representative Name"
(the transaction-specific stay guest, which may legitimately be someone
else). This was purely an Android-side label/binding bug - both values were
already flowing correctly from the API into `Booking`, they just weren't
both being displayed, and one was mislabeled.

Two items below are genuinely backend-only and still open:

### 11. `gcash_mobile_number` and `gcash_reference_number` must be separate columns

The client already models these as two distinct fields
(`Booking.getGcashNumber()` / `Booking.getTransactionRef()`, mapped from
whatever two fields the payment submission endpoint returns) - confirm the
`payment_transactions` (or equivalent) table actually has two separate
columns for these server-side too, not one shared `gcash_info`/`reference`
column being reused for both purposes. If a single column is ever
serving double duty today, that's the kind of thing that would explain a
guest occasionally seeing one value where the other belongs - split it if so.

### 12. Server-side validation on GCash payment submission

Reject a GCash payment submission (`POST .../submit-payment` or equivalent)
if `gcash_mobile_number` isn't exactly 10 digits starting with `9`, or if
`gcash_reference_number` isn't exactly 13 digits, mirroring the client-side
`isValidGcashNumber()`/`isReferenceValid()` checks already enforced in
`PaymentActivity` - client-side validation is UX only and must not be the
only check. Also enforce uniqueness of `gcash_reference_number` per the
task's own requirement (a GCash reference number is issued once by GCash
itself and should never legitimately be submitted twice) - reject a
duplicate submission with a clear error rather than silently accepting a
second payment under the same reference.

## Update: GCash Reference #/GCash Mobile # moved into Transaction Information

Moved both GCash-specific rows out of Payment Information and into
Transaction Information (order: Booking ID, Transaction Reference,
Transaction Date, GCash Reference #, GCash Mobile #), and stopped showing
them in both places. This also resolved the earlier ambiguity about what
"Transaction Reference" means: it's now explicitly bound to the internal
payment id (`Booking.getLatestPaymentId()`, the same value shown in the
"Payment Reference" line under the header) rather than the GCash reference
number - the GCash reference has its own clearly-labeled "GCash Reference #"
row instead, so the two can no longer be confused with one another. Payment
Information now only carries Payment Method/Payment Status/Payment Date, per
the "no duplicate payment info" requirement.

## Update: "606 8" - the 13-digit GCash reference showing as 4 digits is a backend/database issue, not an Android bug

A guest reported the receipt showing `GCash Reference Number: 606 8` for a
transaction where they entered the full 13-digit reference `6068123456789`
in `payment.xml` Step 3. I traced every step of this value's journey through
the Android app end to end and found no truncation anywhere on this side:

1. **Capture** (`PaymentActivity.setupGcashNumberInput()`): the Step 3
   TextWatcher keeps the field as raw digits only, capped at
   `digits.length() > 13 ? digits.substring(0, 13) : digits` - a cap, never a
   forced cut below 13. `updateSubmitButtonState()` disables both the Step 3
   Next button and the final Submit button unless
   `gcashReferenceDigitsOnly().length() == 13` - the guest cannot advance
   past Step 3, let alone submit payment, with anything other than exactly
   13 digits in the field.
2. **Submission** (`PaymentActivity.handleGcashStep3Next()`/final submit path):
   `String typedReference = gcashReferenceDigitsOnly();` reads the same
   validated 13-digit value and passes it straight through
   `RoomRepository.submitGcashPayment()`/`submitPendingReservationGroups()`/
   `submitPendingBookingGroups()` as a plain `String` `RequestBody`
   (`reference_number` field) - no numeric parsing, no substring, at any
   point in this path.
3. **Response parsing** (`PaymentDto.reference_number`): declared `String`,
   not `int`/`long` - Gson maps the JSON value directly with no coercion
   that could lose leading digits or overflow.
4. **Mapping into the domain model** (`ApiMapper`, two call sites):
   `booking.setTransactionRef(latestPayment.reference_number)` - a direct
   assignment, no substring/truncation.
5. **Display** (`PaymentReceiptActivity`): `GcashReferenceFormatter.format()`
   only ever groups digits into "XXX XXXX XXXXXX" (3-4-6) - it never drops
   any. Formatting the 4 raw characters `"6068"` through this exact grouping
   produces `"606 8"` (`"606"` + a space inserted after the 3rd character +
   the remaining `"8"`) - **this is the exact string the guest saw**, which
   means `booking.getTransactionRef()` already contained only `"6068"`
   before the Android app ever touched it for display. I've also changed
   this screen (see below) to stop grouping a value that isn't exactly 13
   digits, specifically so a data problem like this one displays as an
   obviously-raw, unformatted value instead of looking like a plausible but
   wrong reference number.

Given all of the above, the 4-digit value has to already be what the
`payments` table (or whatever table backs `PaymentDto.reference_number`)
actually stored and returned for this specific payment row - the Android
client is provably not capable of producing this symptom from a
correctly-submitted 13-digit value. Please check, in order of likelihood:

- The `reference_number` (or equivalent) column's type/length - if it's a
  `VARCHAR(4)`/`CHAR(4)` or similar, the database itself would silently
  truncate on INSERT.
- Whether the payment submission endpoint reads the `reference_number`
  field from the correct multipart/form field name (`reference_number`,
  matching what `RoomRepository` sends) and writes the full received string,
  rather than e.g. a substring used for a different purpose (a short
  display code, a dedupe key, a log excerpt) that got wired to the wrong
  column.
- Whether the specific database row behind this transaction (Payment
  Reference 235 / Booking ID 165, per the guest's screenshot) is old
  test/seed data with an intentionally-short placeholder reference, rather
  than a real submission - worth confirming against this specific row
  directly.
- Whether the endpoint that serves this payment record back to the app
  (used to build the receipt) is querying/joining the correct payment row
  at all, versus an unrelated row that happens to have a 4-character value
  in whatever column it maps to `reference_number` in the response.

I can't inspect any of this myself - no Laravel/database source exists in
this repo (confirmed again this pass). I also hardened one adjacent risk
while investigating: `BillingDto.latestPayment()` and two equivalent
`ApiMapper` call sites previously picked "the last entry in the `payments`
array" as the most recent payment, trusting the API to always return the
list in creation order. They now explicitly select the entry with the
highest `id` (an auto-increment primary key) instead - a defensive fix for
the general class of bug this report resembles (the receipt reading a
different/wrong payment record's fields), even though in this specific case
the reference value itself being short, not merely a different real
reference, points at the stored value rather than record selection as the
proximate cause.

I also could not reproduce "GCash Mobile Number missing" as a client bug for
the same reason: `rowGcashMobileNumber` is already wired to
`booking.getGcashNumber()` (added in the previous pass), and `bindRow()`
only hides a row when its value is null/blank - so if that row wasn't
visible in the reported screenshot, `gcash_number` was null/empty in the
API response for that same payment row, which is the same category of gap
as the reference number above and should be checked alongside it.

## Update: receipt redesign, section reorganization, and two gaps not filled

Reorganized `activity_payment_receipt.xml` into clearer sections - Hotel
Information (header), Transaction Information (Booking/Reservation ID,
Transaction Reference, Transaction Date), Guest Information, Stay
Information (renamed from "Booking Information"), Billing Breakdown (Total
Amount, Payment Percentage when partial, Amount Paid, Remaining Balance),
Payment Information, and Payment Verification Information. `item_receipt_row.xml`
(the one row layout every section uses) was reworked so every row keeps a
strict label-left/value-right split: the label takes only its own natural
width and the value fills all remaining space, right-aligned
(`android:gravity="end"`/`textAlignment="viewEnd"`), wrapping onto a second
line for long values (full names, an email address, a 13-digit reference)
instead of the old rigid 50/50 split. Neither this row layout nor its
short-lived stacked-layout predecessor (removed) ever set `maxLines`/
`ellipsize` - values wrap in full rather than truncating with "...". Also
fixed a real staleness bug found while investigating Guest
Account Name correctness: `ProfileManagementActivity.fetchProfileFromServer()`
refreshed `userFirstName`/`userMiddleName`/`userLastName` from the server but
never recomputed the pre-combined `userName` key that Dashboard and this
receipt both read - so a name changed through another channel (e.g. a
receptionist-assisted edit) would show correctly in Profile but stay stale
everywhere reading `userName` until the guest next used this screen's own
Save button. Now both paths keep `userName` in sync.

Two items from this pass are backend-only gaps, not implemented (faking
either would mean showing information that was never actually collected):

- **"GCash Account Name"**: `payment.xml` only ever collects a GCash mobile
  number, reference number, and proof screenshot in Steps 2-3 - there is no
  "name on the GCash account" input anywhere in this app today, and
  `Booking`/the API response carry no such field. Defaulting it to Guest
  Account Name would actively misrepresent the transaction (a guest can and
  sometimes does pay via a family member's or companion's GCash account,
  which is a different name entirely) - the client won't fabricate this. If
  the business wants it displayed, add a GCash account name input to
  `payment.xml` Step 2 alongside the mobile number, persist it as its own
  column (`gcash_account_name`, separate from both `gcash_mobile_number` and
  the guest's own name fields - see section 11 below), and I'll wire a row
  for it.
- **Selected Rooms / Add-on Amenities line items**: neither `Booking.java`
  nor the mapped API response carries a per-transaction list of selected
  rooms (only a single `roomType` string) or line-itemized amenities (only
  the corrected aggregate total used elsewhere this session for the
  Grand Total fix - see the amenities-total-correction work earlier in this
  file's history). Showing an itemized "Selected Rooms"/"Add-on Amenities"
  section would mean inventing line items with no backing data. If this is
  wanted, the booking/reservation and payment endpoints would need to return
  a `line_items` (or `rooms[]` + `amenities[]`) array per transaction; the
  receipt already sums real per-payment amounts today and can render a real
  itemized list without any further Android-side redesign once that data
  exists.

## Update: consolidated every Android-side receipt entry point to one gate

Since this doc was first written, a second, older, **ungated** receipt path
was found and fixed: `TransactionDetailsActivity` (Transaction History's
per-payment detail screen) had its own "Download Receipt" button that called
a separate `ReceiptPdfGenerator` class directly, with no verification check
at all - a guest could download a receipt-shaped PDF for a still-pending or
even rejected payment. That button, and the unreferenced dead code in
`TransactionHistoryActivity` that generated it from a field that was never
actually populated, are both removed now; `TransactionDetailsActivity` shows
the same gated card/copy as `BookingDetailsActivity` and routes through the
same `PaymentReceiptActivity`. `ReceiptPdfGenerator.java` itself was deleted
- there is now exactly one place on the client that renders/exports a
receipt PDF, closing the "duplicated receipt-generation logic" this task
asked to find. `NotificationDetailsActivity` also now shows a "View Payment
Receipt" button (gated identically) alongside its existing "View
Transaction" action, when the notification's linked record already has a
verified payment - this was previously only a single generic action.
None of this required a backend change; it was purely inconsistent gating
across four different UI entry points to the same underlying data.

## Update: removed the "Transaction Reference" row, gave GCash Mobile # its place, and a UI pass on the whole receipt

This pass came from a design spec asking the receipt to stop showing
"Transaction Reference" (`Booking.getLatestPaymentId()`, the internal payment
id - already duplicated one line up, in the "Payment Reference" line under
the header) as a labeled row in Transaction Information, and put GCash
Mobile # there instead. Transaction Information now reads, top to bottom:
Booking ID, GCash Mobile #, Transaction Date, GCash Reference # - the
`rowTransactionReference` `<include>`, its `bindRow()` call, and the now-
unused `receipt_transaction_reference_label` string are all deleted, not
just hidden. This is purely a client-side label/layout change; the payment
id itself is still sent and used (as the receipt filename and the header's
"Payment Reference" line) - nothing server-facing changed.

While in there, two formatting fixes and a typography/spacing pass, all
scoped to `activity_payment_receipt.xml`/`item_receipt_row.xml`/
`PaymentReceiptActivity.java` (plus the shared `GcashReferenceFormatter`,
see below):

- **GCash Mobile # now displays in the form the guest actually entered,
  not always converted to a "+63 " international-looking value.** The old
  `formatGcashMobileNumber()` always emitted `"+63 " + grouped digits`
  regardless of input shape. Since this app's own Step 2 input
  (`isValidGcashNumber()`, `"9\\d{9}"`) only ever stores the raw 10 digits
  `9XXXXXXXXX` - the "+63 " the guest sees while typing is a `prefixText`
  decoration on the `TextInputLayout`, never part of the stored value - that
  canonical case is now shown in local form with the leading zero restored:
  `"0912 345 6789"` (4-3-4 grouping). A value that's itself already stored in
  international form (12 digits starting `63`, with or without a `+`) still
  renders as `"+63 912 345 6789"`. Every digit is still shown either way;
  this only changes which of the two equivalent forms is used, to match what
  the guest actually typed rather than a format this client was inventing.
- **`GcashReferenceFormatter` grouping changed from 3-4-6 (`"XXX XXXX
  XXXXXX"`) to 4-3-6 (`"XXXX XXX XXXXXX"`)** - e.g. `6068123456789` now
  renders `"6068 123 456789"` instead of `"606 8123 456789"`. This is the
  single shared formatter (see the "606 8" update above for why it exists),
  so the same grouping change also applies to `PaymentActivity`'s Step 5
  review screen and `TransactionDetailsActivity` - intentional, so the
  reference number is grouped identically everywhere it's shown. Still
  digit-preserving and idempotent; still only groups when a value is
  exactly 13 digits (see `formatReceiptGcashReference()`, unchanged) rather
  than dressing up a short/wrong value as if it were a valid reference.
- **Row typography flipped**: label 12sp/value 13sp is now label 13sp/value
  12sp (`item_receipt_row.xml`), so the label stays legible while the value
  - typically the longer of the two - gets a little more room to fit without
  wrapping. GCash Mobile #, GCash Reference #, Total Amount, and Amount Paid
  additionally get a bold value (`PaymentReceiptActivity.emphasizeRowValue()`,
  applied on top of `bindRow()`) to stand out from the rest. No `maxLines`/
  `ellipsize` was ever set on the value `TextView` (confirmed still true) -
  long values still wrap in full rather than truncating.
- **Section dividers + spacing**: a hairline divider (`@color/
  velocity_divider_hairline`, already existed for this purpose, just wasn't
  used here) now separates Guest/Stay/Billing/Payment/Verification
  Information from the section above it, and Total Amount Paid now sits in
  its own soft-red rounded panel (`@drawable/bg_receipt_total_panel`, new)
  instead of bare centered text, for a bit more visual weight at 28sp.

Verified `:app:assembleDebug` still builds clean end-to-end (AAPT2 resource
linking, javac, dexing) after all of the above.

None of this touches, resolves, or supersedes any of the backend-only gaps
listed below (`verified_by`, `receipt_number`, the `receipts` table, backend
authorization, the still-open "606 8" root cause investigation, etc.) - this
was a client-only label/layout/formatting pass on top of data this app was
already receiving correctly.

## Update: "606 8" recurrence confirmed as the same known-bad record, and a second field (`gcash_number`) now confirmed missing on it too

A follow-up report came in with a fresh screenshot of the receipt still
showing `GCash Reference # 6068` and, new this time, no `GCash Mobile #` row
at all. Before touching anything I re-checked: the screenshot's "Payment
Reference: 235" / "Booking ID 165" is the *exact same record* the "606 8"
update above already identified and asked you to check server-side - this
isn't a new occurrence, it's the same unfixed row being viewed again after
the Android-side work in the update directly above (which only reordered/
restyled rows and fixed display formatting - it never touched, and can't
touch, what value is actually stored for this payment).

I re-verified there's still no client-side explanation available:
`Booking implements Serializable` with no `transient` on `transactionRef`/
`gcashNumber` (`Booking.java`), so both survive the `Intent.putExtra()` used
to hand the object to `PaymentReceiptActivity` intact - not a serialization
bug. `ApiMapper` still does a direct `booking.setTransactionRef(latestPayment
.reference_number)` / `booking.setGcashNumber(latestPayment.gcash_number)`
at both call sites (the plain-Booking and the reservation-derived path), and
`PaymentDto.reference_number`/`gcash_number` are still declared `String`. So
for this record specifically: `reference_number` is actually 4 characters in
whatever's backing this payment row, and now apparently `gcash_number` is
null/empty too - both are storage/API-response gaps on this one row, not a
new client bug. Given the "606 8" update's own checklist (column length,
wrong form-field name written to the wrong column, stale seed/test data, or
the endpoint joining the wrong payment row) already covers exactly this
shape of problem, I'd start by just checking payment id 235 directly in the
database rather than re-deriving the checklist - if `gcash_number` is also
short/wrong/null on that specific row, the two symptoms likely share one
root cause (e.g. this row's payment was submitted through a path that never
wrote either GCash column, or the endpoint is joining an unrelated row for
both fields at once).

Since I can't fix stored data from here, I made the client surface this
honestly instead of quietly hiding half the section, which is what made "is
the mobile number missing or is this a UI bug?" hard to tell apart from the
screenshot alone:

- `PaymentReceiptActivity.bindGcashMobileRow()`/`bindGcashReferenceRow()`
  (replacing the old inline `formattedGcashMobile`/`formattedGcashReference`
  locals in `populateReceipt()`) now keep both rows **visible** for any GCash
  payment, never silently `GONE`, regardless of whether the underlying value
  is present - only a Cash payment hides them (where they genuinely don't
  apply). A present value is still shown in full, formatted, never masked;
  a missing one now shows `"Not on file for this payment"`
  (`receipt_gcash_value_missing`) instead of disappearing, so a blank-looking
  section reads as a known data gap rather than a broken screen.
- `bindGcashReferenceRow()` additionally shows a small red hint line under
  the value (`item_receipt_row.xml`'s new optional `tvRowHint`, wired via
  `PaymentReceiptActivity.setRowHint()`) whenever the stored reference is
  non-empty but not exactly 13 digits - `"This reference number appears
  incomplete in our records - please contact support."` under the raw `6068`
  in this exact case. The raw digits themselves are still shown completely
  unmodified (never grouped as if valid, never swapped for a placeholder) -
  this only adds a visible flag next to a value that was already being
  displayed honestly.
- Fixed an unrelated but real display bug spotted in the same screenshot:
  the header's "Payment Reference" line was rendering as the redundant
  "Payment Reference ... Payment Reference: 235" (`tvReceiptReference` was
  prefixing its value with the *label string* rather than a short "Ref:"
  prefix, while the label `TextView` right next to it already read "Payment
  Reference"). Now renders "Payment Reference ... Ref: 235" - same underlying
  `Booking#getLatestPaymentId()` value, no data change.
- Both the missing-value message and the incomplete-reference hint render
  in the downloaded PDF too, same as every other row - `downloadReceiptAsPdf()`
  still renders `receiptCard` directly, so there's no separate template that
  could drift out of sync with what's on screen.

Re-verified `:app:assembleDebug` builds clean after these changes. To be
clear about what this doesn't do: it doesn't recover the missing digits or
the missing mobile number - only the database has those - it makes their
absence visible and explicit instead of silently blank, on both the screen
and the download.

## Update: third report on the same record (235/165) - ruled out payment selection and JSON field-name mismatches too, recommend testing with a fresh payment instead

A third report came in, same screenshot in substance: Booking ID 165, Payment
Reference 235, `GCash Reference # 6068`, `GCash Mobile # Not on file for this
payment`, plus the incomplete-reference hint added in the previous update.
This is still the exact same database row already flagged twice above - not
a new occurrence. Since the request asked specifically to rule out payment
selection and JSON key mismatches, I checked both, on top of everything
already re-verified twice before:

- **Wrong payment selected?** No. `BillingDto.latestPayment()` and
  `ApiMapper.latestPaymentOf()` (the two "most recent payment" lookups feeding
  every entry point that can open `PaymentReceiptActivity`) both select by
  highest `id`, not array order - confirmed again this pass. `latestPayment.id`
  is also what populates `Booking.getLatestPaymentId()`, which is the same
  "235" the header's "Payment Reference" line shows in the screenshot - so the
  record being read for `reference_number`/`gcash_number` is provably the same
  record whose id the receipt displays, not some other payment picked by
  mistake.
- **Wrong JSON field name?** No. `PaymentDto.reference_number`/`gcash_number`
  have no `@SerializedName` override, so Gson matches them against the API
  response by exact key name - `reference_number` and `gcash_number`. If these
  were the wrong keys (e.g. the server actually sends
  `gcash_reference_number`/`gcash_mobile_number`), Gson wouldn't partially
  populate one and leave the other null - it would silently leave **both**
  fields at their Java default (`null`) with no error, because that's how Gson
  handles an unmatched key. That's not what the screenshot shows:
  `reference_number` has a real (if short) value, `"6068"`. A totally missing
  key can't produce a non-null partial string - only a real, if bad, stored
  value can. This is consistent with `reference_number`/`gcash_number` being
  the correct keys and this row's stored data being the actual problem, not
  the client reading the wrong field name.

At this point every layer between "guest submits Step 3" and "receipt renders
a TextView" has been independently re-verified three times over three
reports, all on this one record. I don't have a fourth place left to look on
the Android side, and I can't query the `payments` table myself. **The most
useful next step isn't re-inspecting this repo again - it's a fresh test
submission**: have a guest (or a test account) actually complete a new GCash
payment end to end (Step 2 mobile number, Step 3 the full 13-digit reference,
submit, get it staff-verified), then open that *new* payment's receipt.

- If the new payment's receipt shows the full mobile number and all 13
  reference digits correctly - which every trace above says it should, since
  nothing in the client alters or drops digits anywhere in the path - that
  confirms payment id 235/Booking 165 is simply bad historical data (most
  likely seeded/test data predating the `gcash_number` column, or a one-time
  submission-path bug that's already been fixed since), and this thread can
  close without further Android changes.
- If the *new* payment's receipt still shows a short reference or a missing
  mobile number, that's a live, reproducible backend bug worth escalating with
  the new payment's id - at that point it's worth checking the submission
  endpoint itself (does it actually write `gcash_mobile_number`/
  `reference_number` from the request body it receives, and does the column
  length allow the full value) rather than this already-exhausted read path.

## Update: missing-value fallback text changed to "Unavailable", plus debug logging

A fourth report on the same record (235/165) asked specifically for the
missing-value fallback text to read `"Unavailable"` rather than `"Not on file
for this payment"`, and for the missing-field case to be logged for
debugging. Both done - `receipt_gcash_value_missing` now reads
`"Unavailable"` (same trigger conditions as before: only shown when
`Booking.getGcashNumber()`/`getTransactionRef()` is actually null/empty for a
GCash payment, never for Cash). `PaymentReceiptActivity.bindGcashMobileRow()`/
`bindGcashReferenceRow()` now log a `Log.w` with the payment id, booking id,
and (for the reference field) the actual digit count/value whenever a GCash
payment's mobile number or reference is missing or isn't exactly 13 digits -
so this is visible in Logcat instead of only inferable from the UI. This is
still just a fallback for what the API actually returns; it doesn't change
what value is displayed when the data is present, and it still doesn't
recover the missing/short data itself - that remains the backend-only gap
described in the three updates above, all still tracking the same underlying
record. Re-verified `:app:assembleDebug` builds clean.

## Update: fifth report, same record (235/165) - reconfirmed backend-only, consolidated fix checklist for the backend team

A fifth report came in with a screenshot showing `GCash Mobile #: Not on file
for this payment` and `GCash Reference #: 6068` with the incomplete-reference
hint - Booking ID 165, Payment Reference 235. Same record as every prior
update above. Two things worth noting about this specific screenshot before
the checklist:

- Its mobile-number fallback text, `"Not on file for this payment"`, is the
  *pre-fix* string. The current source (`strings.xml`,
  `receipt_gcash_value_missing`) has read `"Unavailable"` since the "missing-
  value fallback text changed" update above - so this screenshot was taken
  from a build older than this repo's current `main`, not a regression.
- I re-audited the full chain end to end again this pass (`payment.xml` Step
  2/3 input/validation, `PaymentActivity.isValidGcashNumber()`/
  `gcashReferenceValidationError()`, `RoomRepository.submitGcashPayment()`'s
  multipart field names `gcash_number`/`reference_number`, `PaymentDto`'s two
  `String` fields, both `ApiMapper.setGcashNumber()`/`setTransactionRef()`
  call sites, `Booking`'s two `String` properties, and
  `PaymentReceiptActivity.bindGcashMobileRow()`/`bindGcashReferenceRow()`/
  `formatGcashMobileNumber()`/`formatReceiptGcashReference()`) and confirmed
  again: every one of these is already `String`-typed, none does a
  substring/truncation, `GcashReferenceFormatter` only ever groups an
  exactly-13-digit value, and no `maxLines`/`ellipsize` exists anywhere in
  `activity_payment_receipt.xml` or `item_receipt_row.xml`. There's nothing
  left on the Android side to change for this specific symptom - the same
  conclusion as every prior pass, now checked a fifth time.

This app also has **no backend or database source anywhere in this
repository** (confirmed again this pass - no `.php`, `.sql`, Room `@Entity`/
`@Dao`, or `SQLiteOpenHelper` files exist) - everything below has to be
applied in whatever separate repo owns the API/database.

### Consolidated fix checklist (supersedes needing to re-derive this from scratch)

**1. Column types** - `payments` (or `payment_transactions`):

```sql
gcash_mobile_number    VARCHAR(13)  NULL   -- "09171234567" or "+639171234567"
gcash_reference_number VARCHAR(13)  NULL   -- all 13 raw digits, no separators
```

Never `INT`/`BIGINT`/`CHAR(4)` for either column - besides the leading-zero
problem, a numeric column silently truncates or overflows a 13-digit value
depending on the engine/column width, which is the leading theory for how
record 235 ended up with a 4-character reference in the first place (see the
"606 8" update above for the full reasoning). If either column is currently
narrower than 13 chars or numeric, that alone would explain this whole
report chain.

**2. API response field names** - must match `PaymentDto` exactly (no
`@SerializedName` override exists client-side, so Gson matches literally):
`gcash_number` and `reference_number`. Do not rename these to
`gcash_mobile_number`/`gcash_reference_number` in the JSON response without
also updating `PaymentDto.java` - and do not confuse either with
`payment_reference`, `transaction_reference`, `booking_id`,
`reservation_id`, or `payment_id`, which are separate fields serving
different purposes on this receipt.

**3. Submission-endpoint mapping** - confirm `POST .../submit-payment` (or
equivalent) writes the multipart fields it receives - `gcash_number` and
`reference_number`, matching what `RoomRepository.submitGcashPayment()`
sends - into these exact two columns, not into a shared/reused column, and
not into a short display code or dedupe key that happens to share a column
with one of these two.

**4. Server-side validation on submission** (client-side checks are UX only
and must not be the only enforcement):
- `gcash_mobile_number` required, matches `^(09\d{9}|\+639\d{9})$`.
- `gcash_reference_number` required, exactly 13 numeric digits, no letters.
- `gcash_reference_number` unique across all payments (reject a duplicate
  submission with a clear error - a real GCash reference is issued once by
  GCash itself).

**5. Verify record 235/165 directly** - query this specific payment row and
check the raw stored `reference_number`/`gcash_number` values. If
`reference_number` is `"6068"` (4 chars) and/or `gcash_number` is
`NULL`/empty in the actual row, that's conclusive: this is bad/incomplete
historical data on one record, not a live bug. The fastest way to confirm
the fix end to end is a **fresh test GCash payment** (Step 2 mobile number,
Step 3 the full 13-digit reference, submit, staff-verify, then open that
new payment's receipt) rather than continuing to re-inspect record 235,
which no amount of Android-side or database-side fixing can retroactively
repair - the digits it's missing were never captured, or were captured and
then truncated in storage, and simply aren't recoverable from either side
today.

## What's already implemented, on both sides

Before writing this, I confirmed the app already has most of the *signal*
this feature needs, just not a dedicated receipt record:

- `Booking.isStaffVerified()` / `getPaymentVerificationStatus()` (raw string,
  e.g. `"pending_verification"`, `"rejected"`) / `isPaymentRejected()` /
  `isPaymentPendingVerification()` / `getPaymentVerifiedAtDisplay()` (a
  Manila-formatted timestamp) already exist and are already mapped from the
  server in `ApiMapper.toBooking()` - `PaymentStatusResolver` already uses
  these as the single source of truth for every payment-status pill shown
  across Dashboard, Transaction History, and Booking Details.
- The guest's own uploaded GCash proof (`Booking.getReceiptUrl()`) is already
  correctly labeled "Receipt image" in `BookingDetailsActivity`, separate
  from anything called an official receipt - this task's business rule #3
  ("a guest-uploaded screenshot is not the official receipt") was already
  true before this change.

## What I built on the Android side (already working, gated correctly today)

- `BookingDetailsActivity`'s Payment tab now shows a "Payment Receipt" card
  (`buildReceiptActionCard()`) with three states, driven entirely by the
  existing fields above:
  - **Verified** (`isStaffVerified() && amountPaid > 0`): "✓ Payment
    Verified" + View Receipt / Download Receipt buttons.
  - **Rejected** (`isPaymentRejected()`): "Receipt Not Available" + your
    exact rejection copy, no buttons.
  - **Pending** (payment submitted, not yet verified): "Receipt Not
    Available" + your exact pending copy, no buttons.
  - **No payment at all** (plain unpaid Reservation): no card shown at all
    - per business rule #6, a reservation with no monetary payment must not
      have a payment receipt.
- `PaymentReceiptActivity` - a new, read-only receipt screen
  (`activity_payment_receipt.xml`) with the Velocity Suites logo (reused
  `@drawable/velocity_suites_logo`, not a placeholder), hotel address/tagline
  (reused the existing `landing_hotel_address`/`welcome_tagline` strings),
  Guest/Booking/Payment/Verification sections, a bold Total Amount Paid,
  and a PARTIAL/FULL PAYMENT badge. Gated a second time at the top of
  `onCreate()` (`isStaffVerified()` + `amountPaid > 0`) so a stale
  Intent/back-stack re-entry can't bypass the launching button's own gate -
  this is still only a client-side courtesy check, not the authoritative
  one (see "Backend authorization" below).
- **Download Receipt** actually works today: renders the receipt card to a
  one-page PDF via Android's built-in `PdfDocument` (no new library
  dependency) and saves it through `MediaStore.Downloads` - this project's
  `minSdk` is already 29, so no runtime storage permission is needed.
  Filename: `VelocitySuites_Receipt_<paymentId>.pdf`.

## What's missing and can only be added server-side

### 1. A `verified_by` field (blocks one row on the receipt today)

`PaymentReceiptActivity`'s "Verified by" row is currently hidden because no
field carries the verifying receptionist's name/id to the client at all.
Add `payment_verified_by` (receptionist user id, or resolve to a display
name) to whatever endpoint response already carries
`payment_verification_status`/`payment_verified_at`, and I'll wire it into
the existing `bindRow(R.id.rowVerifiedBy, ...)` call - a one-line change
once the field exists.

### 2. Receipt number generation and uniqueness (task's own item 7/19)

There is currently no `receipt_number` field anywhere in this app's API
responses - `PaymentReceiptActivity` uses the existing payment id
(`Booking.getLatestPaymentId()`) as its "Payment Reference" instead, since
inventing a client-side `VS-OR-YYYYMMDD-XXXXXX`-formatted number would risk
colliding with or disagreeing with whatever the server later assigns, and
two devices viewing the same payment could show different fabricated
numbers. Once the backend generates and returns a real
`receipt_number` (unique, generated exactly once per verified payment - see
suggested table below), swap `PaymentReceiptActivity`'s reference line to
use it instead.

### 3. The `receipts` table and its relationship to payments

Recommended (from the task's own spec, adjusted to reuse this app's existing
naming - `bookings`/`reservations`, not a generic `hotel_transactions`
table, per the dual-table split `ApiMapper`/`ApiService` already assume):

```text
receipts
  id
  receipt_number        (unique, e.g. VS-OR-YYYYMMDD-XXXXXX)
  booking_id             (nullable - see note below)
  reservation_id         (nullable - see note below)
  payment_id
  guest_id
  payment_method
  payment_type           (PARTIAL | FULL)
  total_booking_amount
  amount_paid
  remaining_balance
  verified_by            (receptionist user id)
  verified_at
  generated_at
  file_path              (server-rendered PDF, if the backend also wants to
                          serve one - the Android client renders its own
                          from the same data today and doesn't require this)
  status                 (READY | GENERATED)
  created_at / updated_at
```

Both `booking_id` and `reservation_id` nullable because this app's own
`ApiMapper` already distinguishes a converted-Reservation-derived Booking
from a direct Booking (`isHasBooking()`/`isDirectBooking()`) - a receipt
should link to whichever one the verified payment actually belongs to, not
force a single shared foreign key.

One receipt per verified payment (enforce a unique constraint on
`payment_id`, not just `receipt_number`) - re-verifying (if that's ever
possible) must not silently generate a second receipt for the same payment.
For multiple partial payments (task item 20), each verified payment gets its
own row here, each with its own `receipt_number`; the client already sums
`amount_paid` across a booking's `getPaymentHistory()` records for display,
so no separate "total paid across all receipts" aggregation is needed on the
client once each payment's own receipt exists.

### 4. Backend authorization (task's own item 24 - this is the real gate)

Every client-side check described above (`isStaffVerified()` gating the
Android buttons/screen) is UX only, exactly like every other client-side
check already documented in this project's other `*_BACKEND_SPEC.md` files.
Add the authoritative check server-side on whatever endpoint would serve
receipt data/file (`GET guest/receipts/{id}` or similar, if this is
introduced): reject with 403 unless
`payment.verification_status == VERIFIED AND receipt.status == READY AND
receipt.guest_id == authenticated_guest_id`. A guest must never be able to
fetch another guest's receipt by changing an id in the URL, and must never
be able to fetch a receipt for a payment that isn't actually verified by
guessing/replaying a request, regardless of what the Android app itself
would currently show.

### 5. Web Receptionist portal (entirely separate codebase, not in this repo)

Per the task's item 5:
- A payment review screen listing Booking ID, guest/representative name,
  room(s), dates, guest count, payment method/type, amount required vs.
  submitted, GCash number/reference, uploaded proof, submission date/time,
  and current verification status.
- **Verify Payment** / **Reject Payment** / **View Payment Proof** actions.
- Verify Payment must show a confirm dialog with your exact copy ("Confirm
  Payment Verification... Once confirmed, an official Velocity Suites
  payment receipt will be generated...") before committing.
- On confirm: set `payment_verification_status = VERIFIED`,
  `payment_status = CONFIRMED`, `receipt_status = READY`,
  `payment_verified_by`, `payment_verified_at`; generate the receipt row
  (section 3) and its unique `receipt_number`.
- On reject: `payment_verification_status = REJECTED`,
  `receipt_status = NOT_AVAILABLE` (already correctly reflected client-side
  via `isPaymentRejected()` - no receipt card/screen ever renders for this
  case, confirmed above), plus a rejection reason surfaced through the
  existing `transactionRejectionReason`/`rejectionReason` fields
  `BookingDetailsActivity` already displays.

### 6. Notification on verification (task item 22)

Confirm a notification is created server-side when verification succeeds,
using this app's existing notification mechanism (`RoomRepository`'s
`notifications` list, already surfaced on Dashboard) rather than a new
channel - the Android side doesn't need any change to receive it once the
backend creates it through the same pipeline other notifications already
use; only the copy needs to match: "Your payment for Booking ID ... has
been successfully verified... Your official payment receipt is now
available."

### 7. Audit trail (task item 26)

`PAYMENT_VERIFIED` / `PAYMENT_REJECTED` / `RECEIPT_GENERATED` log entries
(receptionist id, guest id, booking/payment id, amount, timestamp) - purely
a backend/database concern, no Android-side action needed or possible from
here. Add `RECEIPT_VIEWED`/`RECEIPT_DOWNLOADED` too if you want visibility
into guest access, not just receptionist actions - the Android side has no
existing "I opened/downloaded this" ping to the server today, so that would
be a new (small) API call from `PaymentReceiptActivity` if wanted, not
something already happening silently.

### 8. Idempotent receipt generation

Guard the verification endpoint itself, not just the UI: if a receipt
already exists for a given `payment_id` (unique constraint per section 3),
return the existing receipt rather than creating a second one -
```pseudo
existing = Receipt.where(payment_id: paymentId).first()
if existing: return existing
else: receipt = createReceipt(...)
```
This matters specifically because the Android confirm-action UX pattern
already used elsewhere in this app for destructive/one-shot actions
(`Step8ReviewPaymentFragment.submitting`, `PaymentActivity.
isSubmittingPayment`, confirmed in earlier work this session) only prevents
a *double-tap in the same screen session* - it can't prevent two separate
requests (a slow network retry, or the receptionist reloading the page and
clicking Verify again) from both reaching the server. The idempotency has to
live in the endpoint, matching this task's own pseudocode in section 5.

### 9. Checkout module - same rule, same receipt row (or a new one, your call)

Whether a checkout's final-payment verification updates the *same* receipt
record (new `amount_paid`/`remaining_balance`, `remaining_balance` now 0) or
creates a second receipt row for the checkout payment specifically depends
on whether your business wants "one receipt per booking, always current" or
"one receipt per verified payment event" (this repo already supports the
multiple-partial-payments case per section 15/20 of the task with the
`payment_transactions` table, so a second receipt per payment is the more
consistent choice given that design). Either way: the Android client already
reads whatever `Booking.getAmountPaid()`/`getRemainingBalance()`/
`isStaffVerified()` currently return, and `PaymentReceiptActivity` shows
whatever those say - no additional Android change is needed for a checkout
receipt to display correctly, beyond the `verified_by` field gap already
flagged in section 1.

### 10. Secure receipt download endpoint (task section 25)

If a server-rendered file (not just the Android-side PDF render already
built) is wanted, the exact pseudocode this task provides is correct and
matches how every other authorization check in this app's existing
endpoints already works (session/token-derived guest id, never a client-
supplied one - see `GUEST_DATE_CONFLICT_BACKEND_SPEC.md` section 6 for the
same principle applied elsewhere). One addition: return 404 (not 403) when
the transaction doesn't belong to the requesting guest, not just when it
doesn't exist at all - returning 403 for "not yours" leaks that the id is
valid for *someone*, which a 404 doesn't.
