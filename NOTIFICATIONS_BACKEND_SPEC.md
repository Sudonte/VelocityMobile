# Promotions/Announcements notification triggers - backend spec

Scope note: same constraint as `PAYMENT_STEP5_BACKEND_SPEC.md` - this project
is the Android client only. There is no Laravel source, no System
Administrator web app source, and no database schema anywhere on this
machine. Everything below is written for whoever owns those repos.

## 0. What's already built and working on the Android side (verified by reading the code, not assumed)

Before writing this, I read `NotificationActivity.java`, `NotificationAdapter.java`,
`NotificationDetailsActivity.java`, `NotificationHelper.java`,
`NotificationPollWorker.java`, `Notification.java`, `NotificationDto.java`,
`ApiMapper.toNotification()`, `BaseNavigationActivity`'s badge logic, and
`notification.xml`. The notification module is already a mature, working
system, not a blank slate:

- **List screen** (`notification.xml`/`NotificationActivity`): collapsing
  header, search box, filter chips (`All`/`Unread`/`Booking`/`Payment`/
  `Check-in`/`Promotions`/`System`), pull-to-refresh, a silent 30s
  foreground auto-poll, empty state, and a "Mark All as Read" action - all
  already in the white/red theme.
- **List item** (`NotificationAdapter`): per-type icon/color, an unread dot,
  stronger stroke/elevation + full opacity when unread vs. a flattened,
  faded card once read, and a status pill (`NotificationStatusResolver`) -
  this already satisfies the "unread must look visually different, NEW
  badge" requirement.
- **Detail screen** (`NotificationDetailsActivity`): full title/message/
  absolute Philippine-formatted timestamp, and - when the notification
  carries a `reference_id` for a Booking/Payment - an inline related-record
  panel plus a "View Transaction" button into `TransactionHistoryActivity`.
- **Push-style local alerts** (`NotificationHelper`): every fetch (manual
  refresh, 30s poll, or the background `NotificationPollWorker`) posts a
  real heads-up Android notification for any row not already alerted on
  this device (SharedPreferences-backed id dedup, capped at 300 entries),
  grouped under one summary if several arrive together, deep-linking on tap.
  This is a deliberate, working substitute for Firebase Cloud Messaging -
  the code comment says so explicitly ("this project doesn't have \[FCM\]
  set up"). **Do not introduce FCM/WebSocket/SSE** - it would duplicate
  working infrastructure the task itself says to reuse.
- **Unread badge**: `BaseNavigationActivity.updateNotificationBadge()`
  already counts unread rows and paints `guest_header.xml`'s
  `headerNotificationBadge` on every guest screen, with a "99+" overflow
  string and auto-hide at zero. Nothing to add here.
- **Read/unread persistence**: `PUT notifications/{id}/read` and
  `PUT notifications/read-all` already exist and are already wired
  (`RoomRepository.markNotificationAsRead`/`markAllNotificationsAsRead`).
- **Fetch**: `GET notifications?per_page=N` already exists and is already
  paginated/mapped.

None of the above needs to change for this task. Two real, small mapping
bugs were found and fixed directly in this pass (see section 1) - everything
past that is genuinely backend/admin-web work this repo can't touch.

## 1. Fixed in this pass (Android-side, already applied)

- `ApiMapper.toNotification()`'s `category` switch had no case for
  `"promotion"` or `"discount"` - both fell through to `TYPE_SYSTEM`, even
  though `Notification.TYPE_PROMOTION` and the `chipNotifPromotion` filter
  already exist and are fully wired in the adapter/UI. Added both cases,
  mapped to `TYPE_PROMOTION`. **This means: once the backend actually sends
  a notification row with `category: "promotion"` or `category: "discount"`,
  it will now correctly show up under the "Promotions" filter with the
  star icon** - before this fix it would have silently rendered as a
  generic System notification.
- `NotificationActivity.applyFilters()`'s "System" chip matched only
  `TYPE_SYSTEM`, never `TYPE_ANNOUNCEMENT` - meaning a published
  announcement (which already maps to `TYPE_ANNOUNCEMENT` correctly) would
  show under "All" but never under the "System" filter, since there's no
  separate "Announcements" chip. Fixed so the "System" chip now matches
  both types - this is what a guest tapping "System" actually expects
  ("System Announcements" is one category, not two).

Everything below this line is **not implementable from this repository** -
it requires the Laravel backend and the System Administrator web app.

## 2. Important: "Discount" and "Promotion" are NOT interchangeable in this codebase

Before implementing anything, read `Discount.java` and `Promotion.java`'s
own doc comments - they describe two structurally different, already-
existing concepts:

- **`Discount`** (`DiscountDto`: `id`, `name`, `discount_type`, `value`,
  `description` - no dates, no status field at all) is a **standing,
  non-expiring statutory category** (Senior Citizen/PWD/Student) that a
  *receptionist* applies manually per-transaction at billing time. It has
  no start/end date and no admin lifecycle today.
- **`Promotion`** (`PromotionDto`: `id`, `promo_name`, `description`,
  `image_url`, `start_date`, `end_date`, optional `room_type`, bundled
  `amenities`) is the **admin-managed, time-bound campaign** - this is
  already fetched from `Api\CatalogController::promotions()`, already has
  start/end dates, and is already described in code as centrally managed
  by "the System Administrator (web)".

The task's "Discount Module" description (admin creates/updates/
deactivates a time-bound offer like "Weekend Stay Discount", 20%, with a
start/end date and an ACTIVE/SCHEDULED/EXPIRED lifecycle) **matches the
existing `Promotion` entity, not the existing `Discount` entity.** Per the
task's own instruction ("reuse the existing database schema instead of
creating unnecessary duplicate tables"), the correct move is almost
certainly to add lifecycle/status fields to the existing `promotions` table
(and reuse it for both "Discount"- and "Promotion"-labeled campaigns from
the admin UI's point of view, if the two are meant to be user-facing
synonyms) rather than building a second, parallel table that duplicates
`promotions`' shape. Confirm this with whoever owns that schema before
adding a new `discounts` table - if the statutory `Discount` concept above
must stay untouched (likely, since receptionist billing already depends on
its current shape), the new time-bound, notifiable thing described in this
task should be modeled as an extension of `Promotion`, not of `Discount`.

## 3. Required: notification category/type values (contract with the Android client)

The Android client is already correct for these exact strings (see
`ApiMapper.toNotification()`), so the backend must emit `category` as one
of:

```
booking
payment
check_in | check_out | checkin_reminder
announcement
promotion   -- now correctly mapped (was silently dropped to "system" before this fix)
discount    -- same as above; both map to the guest-facing "Promotions" bucket
```

Anything else falls back to a generic "System" notification - safe, but
loses the dedicated icon/filter, so don't introduce a new category string
without also adding an Android-side case.

## 4. Required: notification-on-status-transition triggers

For both the promotion/campaign entity (see section 2) and announcements,
generate a `notifications` row (existing table - reuse it, see the
`NotificationDto` fields the client already parses: `id`, `user_id`,
`title`, `message`, `category`, `reference_id`, `target_audience`,
`is_read`, `created_at`) **only** on these transitions, and only after the
underlying business row's write has committed:

- Created + immediately active (or `SCHEDULED → ACTIVE` on its start date) → `category: promotion`, title/message per the task's examples.
- Significant guest-relevant update while active (amount/percentage, dates, eligible rooms, description) → same category, "... Updated" copy. Do not notify for internal-only field changes (e.g. an admin note field, if one exists).
- Manual deactivation while active → "... No Longer Available" / "... Ended" copy.
- Natural expiration (`end_date` reached) → "... Expired" copy, generated by a scheduled job, exactly once (see section 5).
- Announcement `DRAFT → PUBLISHED` → `category: announcement`. A `DRAFT` never generates a notification. An edit to an already-`DRAFT` announcement never generates one either.

Tie notification creation to the same DB transaction as the status write
(or a job enqueued only after that transaction commits) - per the task's
own instruction, a failed save must never produce a notification.

## 5. Required: idempotent/duplicate-safe notification generation

The task explicitly calls out double-firing as a risk (e.g. a cron-style
expiration check running more than once). Whatever job flips
`ACTIVE → EXPIRED` (or any other transition) must be safe to run
repeatedly without double-notifying - the standard approach: only create
the notification in the same statement/transaction as the status flip
itself (`UPDATE ... WHERE status = 'ACTIVE'` guards the flip to happen at
most once; only create notifications for rows the `UPDATE` actually
affected), rather than a separate "has this been notified yet" check that
can race. This mirrors the same "at-least-once delivery" class of problem
already solved for GCash reference numbers in
`PAYMENT_STEP5_BACKEND_SPEC.md` section 2 - same idempotency-key or
guarded-update principle applies here.

## 6. Required: recipient targeting

`target_audience` already exists on both the DTO and the `Notification`
model (a list of role strings) - reuse it rather than adding a parallel
mechanism. Default to the guest-facing role for public promotions/
announcements; exclude deleted/deactivated accounts at the query level
that selects recipients, not by filtering after the fact.

## 7. Acceptance test to run once sections 2-6 ship

Publish a promotion (or whatever the chosen entity ends up being per
section 2) from the admin web app, then confirm: a `notifications` row
appears with `category: "promotion"`, the guest's Android app shows it
under "All" and under the "Promotions" filter chip (star icon, red-dark
background) within one 30s poll cycle without needing to log out/in, a
heads-up system notification appears on the device, tapping either opens
the in-app detail screen, and re-running the same admin action (or the
expiration job) never produces a second identical row for the same event.
