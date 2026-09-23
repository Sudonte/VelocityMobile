package com.example.velocitysuites;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.pdf.PdfDocument;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Read-only official payment receipt - the guest-facing counterpart to
 * BookingDetailsActivity's "Payment Receipt" action card, which is the only
 * place this Activity is ever launched from. Never editable (see the task's
 * "receipt immutability" requirement): every value here is read straight off
 * the Booking snapshot passed in, nothing is re-fetched or made interactive.
 *
 * Distinct from the guest's own uploaded GCash proof
 * (Booking#getReceiptUrl(), shown separately in BookingDetailsActivity as
 * "Receipt image") - this is the *official* Velocity Suites receipt, and per
 * this app's existing business rules it may only exist once
 * Booking#isStaffVerified() is true. That gate is re-checked here too (not
 * just at the launching button), so a stale Intent/back-stack re-entry can't
 * bypass it - though the real authoritative gate has to be server-side (see
 * PAYMENT_RECEIPT_BACKEND_SPEC.md), which this Android-only repo can't add.
 */
public class PaymentReceiptActivity extends AppCompatActivity {

    private static final String TAG = "PaymentReceiptActivity";
    private static final String EXTRA_BOOKING = "EXTRA_BOOKING";
    private static final String EXTRA_AUTO_DOWNLOAD = "EXTRA_AUTO_DOWNLOAD";

    private Booking booking;
    private View receiptCard;
    /**
     * Every sibling Booking/Reservation created together with this one as a
     * multi-room-type transaction (see BookingGroupState/BookingDetailsActivity's
     * identical field) - null if this transaction isn't grouped. The receipt is
     * launched from BookingDetailsActivity's action card, which only ever passes
     * this one anchor Booking - without resolving its siblings here too, the
     * receipt would silently show only one room type and one sibling's own
     * totals for a "Deluxe + Suite" transaction, disagreeing with the Booking
     * Details screen the guest just came from.
     */
    @Nullable
    private List<Booking> groupMembers;

    public static Intent newIntent(Context context, Booking booking, boolean autoDownload) {
        Intent intent = new Intent(context, PaymentReceiptActivity.class);
        intent.putExtra(EXTRA_BOOKING, booking);
        intent.putExtra(EXTRA_AUTO_DOWNLOAD, autoDownload);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_receipt);

        booking = (Booking) getIntent().getSerializableExtra(EXTRA_BOOKING);
        if (booking == null || !booking.isStaffVerified() || booking.getAmountPaid() <= 0.009) {
            Toast.makeText(this, R.string.receipt_pending_desc, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        ImageButton btnBack = findViewById(R.id.btnReceiptBack);
        btnBack.setOnClickListener(v -> finish());
        receiptCard = findViewById(R.id.receiptCard);

        resolveGroupMembers();
        populateReceipt();

        MaterialButton btnDownload = findViewById(R.id.btnReceiptDownload);
        btnDownload.setOnClickListener(v -> downloadReceiptAsPdf());
        if (getIntent().getBooleanExtra(EXTRA_AUTO_DOWNLOAD, false)) {
            receiptCard.post(this::downloadReceiptAsPdf);
        }
    }

    /** Populates {@link #groupMembers} (this record included) if it belongs to a BookingGroupState group, else leaves it null - same logic as BookingDetailsActivity#resolveGroupMembers(). */
    private void resolveGroupMembers() {
        String groupRef = BookingGroupState.getGroupRef(this, booking.getId());
        if (groupRef == null) {
            groupMembers = null;
            return;
        }
        List<String> memberIds = BookingGroupState.getGroupMembers(this, booking.getId());
        List<Booking> allBookings = RoomRepository.getInstance(this).getBookings();
        List<Booking> resolved = new ArrayList<>();
        for (String memberId : memberIds) {
            for (Booking candidate : allBookings) {
                if (candidate.getId().equals(memberId)) {
                    resolved.add(candidate);
                    break;
                }
            }
        }
        groupMembers = resolved;
    }

    private void populateReceipt() {
        boolean gcash = "gcash".equalsIgnoreCase(booking.getPaymentMethod());

        // Grouped multi-room-type transaction (see resolveGroupMembers()) - every
        // amount below must be summed across every sibling, exactly mirroring
        // BookingDetailsActivity#buildPaymentSummarySection(), so this receipt can
        // never show a different Grand Total/Amount Paid than the Booking Details
        // screen it was launched from. Amenities are attached to exactly one
        // sibling at creation time (PaymentActivity#submitPendingReservationGroups()'s
        // index==0 rule), so summing every sibling's own amenityCharge picks that
        // one up correctly regardless of position. Adults/children/guests are
        // deliberately NOT summed here (see BookingDetailsActivity's identical
        // fix) - every sibling was created from the same whole-party
        // pendingWizardState.adults/children, so this one anchor record's own
        // value is already correct.
        double totalAmount;
        double amountPaid;
        double roomCharge;
        double amenityCharge;
        double additionalGuestFee;
        if (groupMembers != null && !groupMembers.isEmpty()) {
            BookingGroupAggregator.Totals totals = BookingGroupAggregator.sum(groupMembers);
            totalAmount = totals.totalAmount;
            amountPaid = totals.amountPaid;
            roomCharge = totals.roomCharge;
            amenityCharge = totals.amenityCharge;
            additionalGuestFee = totals.additionalGuestFee;
        } else {
            totalAmount = booking.getTotalAmount();
            amountPaid = booking.getAmountPaid();
            roomCharge = booking.getRoomCharge();
            amenityCharge = booking.getAmenityCharge();
            additionalGuestFee = booking.getAdditionalGuestFee();
        }
        double remainingBalance = Math.max(0, totalAmount - amountPaid);
        // paymentId is the system-generated internal payment id (Payment
        // Reference) - never to be confused with the guest-entered GCash
        // reference number bound to rowGcashReference below, which is a
        // completely separate value. This line previously repeated the
        // "Payment Reference" label a second time inside the value itself
        // (the left-hand label TextView right next to it already says
        // "Payment Reference"), rendering as the redundant "Payment
        // Reference   Payment Reference: 235" - now just "Ref: 235".
        String paymentId = booking.getLatestPaymentId();
        ((TextView) findViewById(R.id.tvReceiptReference)).setText(
                getString(R.string.receipt_reference_value_prefix) + " " + (paymentId != null ? paymentId : booking.getId()));

        // --- Guest Information ---
        // Guest Account Name = the authenticated account holder, not this specific
        // transaction's stay guest - same "userName"/"userEmail" SharedPreferences
        // keys DashboardActivity's own account summary already reads from (see
        // VelocityPrefs; kept in sync with the profile's first/middle/last fields
        // by both ProfileManagementActivity save paths). Representative Name below
        // is the separate, transaction-specific guest entered during creation and
        // may legitimately be someone else - each has its own distinct label so the
        // two are never confused for one another.
        android.content.SharedPreferences prefs = getSharedPreferences("VelocityPrefs", MODE_PRIVATE);
        bindRow(R.id.rowGuestName, getString(R.string.receipt_guest_account_name_label),
                prefs.getString("userName", null));
        bindRow(R.id.rowRepresentativeName, getString(R.string.details_label_representative_name), booking.getRepresentativeName());
        bindRow(R.id.rowGuestEmail, getString(R.string.receipt_guest_email_label),
                prefs.getString("userEmail", null));
        bindRow(R.id.rowGuestMobile, getString(R.string.mobile_label), prefs.getString("userMobile", null));

        // --- Booking Information ---
        bindRow(R.id.rowBookingId,
                getString(booking.isHasBooking() ? R.string.receipt_label_booking_id : R.string.receipt_label_reservation_id),
                booking.getId());
        // Itemized multi-room-type breakdown (Booking#getRooms()) only ever
        // populates once the backend ships the contract in
        // MULTI_ROOM_TRANSACTION_BACKEND_SPEC.md - empty on every
        // transaction today, so this falls back to the existing single Room
        // Type value below, which remains correct either way. The value
        // TextView already wraps rather than truncating (item_receipt_row.xml
        // has no maxLines/ellipsize), so a multi-line list is fully readable.
        bindRow(R.id.rowRoomType, getString(R.string.details_label_room_type), formatRoomTypeValue());
        // Only renders once a room is actually assigned at check-in - null/hidden
        // beforehand, never fabricated (Booking#getRoomNumber()).
        bindRow(R.id.rowRoomNumber, getString(R.string.details_label_room_number), booking.getRoomNumber());
        bindRow(R.id.rowCheckIn, getString(R.string.details_label_check_in), booking.getCheckInDate());
        bindRow(R.id.rowCheckOut, getString(R.string.details_label_check_out), booking.getCheckOutDate());
        bindRow(R.id.rowNights, getString(R.string.receipt_number_of_nights_label), computeNights(booking.getCheckInDate(), booking.getCheckOutDate()));
        bindRow(R.id.rowAdults, getString(R.string.details_label_adults), booking.getAdults() > 0 ? String.valueOf(booking.getAdults()) : null);
        bindRow(R.id.rowChildren, getString(R.string.details_label_children), booking.getChildren() > 0 ? String.valueOf(booking.getChildren()) : null);
        bindRow(R.id.rowTotalGuests, getString(R.string.details_label_total_guests), String.valueOf(booking.getGuests()));

        // --- Transaction Information ---
        boolean fullyPaid = remainingBalance <= 0.009;
        bindRow(R.id.rowPaymentMethod, getString(R.string.details_label_payment_method),
                getString(gcash ? R.string.payment_method_gcash : R.string.payment_method_cash));
        bindGcashMobileRow(gcash);
        bindGcashReferenceRow(gcash);
        // Percentage is the deposit/full-payment percentage chosen at the time of
        // the latest submitted payment (Booking#getSelectedPaymentPercentage()'s
        // own doc) - shown regardless of fullyPaid, so a Full Payment (100%)
        // transaction still shows "Payment Percentage: 100%" instead of the row
        // being hidden entirely (the old `!fullyPaid` gate here hid it for every
        // Full Payment, since that always leaves remainingBalance at 0).
        bindRow(R.id.rowPaymentType, getString(R.string.details_label_payment_type),
                getString(fullyPaid ? R.string.review_payment_type_full : R.string.review_payment_type_partial));
        // selectedPercentage is already a whole percentage value (20/30/40/50/100 -
        // see Reservation::selected_payment_percentage, set directly from
        // Api\PaymentController::store()'s $selectedPercentage, never a 0.20-1.00
        // fraction) - do NOT multiply by 100 again here (that was this screen's
        // own bug: 50 x 100 = "5000%"). BookingDetailsActivity#formatPercentage()
        // already renders this same field correctly, unmultiplied - kept
        // consistent with it here.
        Double selectedPercentage = booking.getSelectedPaymentPercentage();
        bindRow(R.id.rowPaymentPercentage, getString(R.string.receipt_payment_percentage_label),
                selectedPercentage != null ? PaymentPercentageUtil.formatApiPercentageForDisplay(selectedPercentage) : null);
        // This screen only ever renders once isStaffVerified() is true (see the
        // onCreate() gate above), so "Verified" is always an accurate, non-fabricated
        // value here - never shown for a pending/rejected payment.
        bindRow(R.id.rowPaymentStatus, getString(R.string.receipt_payment_status_label), getString(R.string.status_verified));
        bindRow(R.id.rowPaymentDate, getString(R.string.details_label_payment_date), booking.getPaymentDateOnly());
        bindRow(R.id.rowPaymentTime, getString(R.string.details_label_payment_time), booking.getPaymentTimeOnly());

        // --- Payment Summary ---
        TextView paymentTypeBadge = findViewById(R.id.tvPaymentTypeBadge);
        paymentTypeBadge.setText(fullyPaid ? R.string.receipt_payment_type_full : R.string.receipt_payment_type_partial);

        // Room Charges/Amenities/Additional Guest Fee only render when the backend's
        // Billing breakdown actually returned them (Booking#getRoomCharge() etc.) -
        // never fabricated for a still-pending Reservation or a direct Booking.
        bindRow(R.id.rowRoomCharge, getString(R.string.details_label_room_charge),
                roomCharge > 0.009 ? formatPrice(roomCharge) : null);
        bindRow(R.id.rowAmenityCharge, getString(R.string.details_label_amenity_charge),
                amenityCharge > 0.009 ? formatPrice(amenityCharge) : null);
        bindRow(R.id.rowAdditionalGuestFee, getString(R.string.details_label_additional_guest_fee),
                additionalGuestFee > 0.009 ? formatPrice(additionalGuestFee) : null);
        bindRow(R.id.rowTotalAmount, getString(R.string.details_label_total_amount), formatPrice(totalAmount));
        bindRow(R.id.rowAmountPaid, getString(R.string.details_label_amount_paid), formatPrice(amountPaid));
        bindRow(R.id.rowRemainingBalance, getString(R.string.details_label_remaining_balance),
                remainingBalance > 0.009 ? formatPrice(remainingBalance) : null);
        emphasizeRowValue(R.id.rowTotalAmount);
        emphasizeRowValue(R.id.rowAmountPaid);

        // --- Payment Verification Information ---
        // No "verified by staff name" field exists on this Android-side model yet
        // (see PAYMENT_RECEIPT_BACKEND_SPEC.md) - bindRow hides the row rather than
        // showing a fabricated name.
        bindRow(R.id.rowVerifiedBy, getString(R.string.receipt_verified_by_label), null);
        bindRow(R.id.rowVerifiedAt, getString(R.string.receipt_verified_at_label), booking.getPaymentVerifiedAtDisplay());

        ((TextView) findViewById(R.id.tvTotalAmountPaid)).setText(formatPrice(amountPaid));
    }

    private void bindRow(int includeId, String label, @Nullable String value) {
        View row = findViewById(includeId);
        if (row == null) return;
        if (value == null || value.trim().isEmpty()) {
            row.setVisibility(View.GONE);
            return;
        }
        ((TextView) row.findViewById(R.id.tvRowLabel)).setText(label);
        ((TextView) row.findViewById(R.id.tvRowValue)).setText(value);
    }

    /**
     * Bumps a row's value to bold, on top of item_receipt_row.xml's shared
     * (slightly reduced, see that layout) value text size - for the handful of
     * fields worth visually standing out (the two guest-entered GCash fields,
     * Total/Amount Paid) without giving every row its own bespoke layout. A
     * no-op if the row was hidden by bindRow() above (no value to emphasize).
     */
    private void emphasizeRowValue(int includeId) {
        View row = findViewById(includeId);
        if (row == null || row.getVisibility() != View.VISIBLE) return;
        TextView value = row.findViewById(R.id.tvRowValue);
        value.setTypeface(value.getTypeface(), android.graphics.Typeface.BOLD);
    }

    /**
     * The GCash mobile number entered in payment.xml Step 2
     * (Booking#getGcashNumber(), persisted with this exact payment - never
     * the account profile's own number, never another transaction's). Hidden
     * entirely only for a Cash payment, where it genuinely doesn't apply. For
     * a GCash payment, the row always stays visible: if the value is present
     * it's shown in full (never masked/truncated), and if it's missing from
     * the payment record the row still shows explicitly rather than silently
     * disappearing - a guest (or support agent looking at the same screen)
     * seeing no row at all can't tell "not applicable" apart from "data
     * didn't load", whereas an explicit "not on file" message makes clear
     * this is a data gap on a GCash transaction, not a rendering bug.
     */
    private void bindGcashMobileRow(boolean gcash) {
        if (!gcash) {
            bindRow(R.id.rowGcashMobileNumber, getString(R.string.receipt_gcash_mobile_label), null);
            return;
        }
        String raw = booking.getGcashNumber();
        boolean hasValue = raw != null && !raw.trim().isEmpty();
        if (!hasValue) {
            android.util.Log.w(TAG, "gcash_number missing for paymentId=" + booking.getLatestPaymentId()
                    + " bookingId=" + booking.getId() + " - API returned null/empty for a GCash payment");
        }
        bindRow(R.id.rowGcashMobileNumber, getString(R.string.receipt_gcash_mobile_label),
                hasValue ? formatGcashMobileNumber(raw) : getString(R.string.receipt_gcash_value_missing));
        if (hasValue) emphasizeRowValue(R.id.rowGcashMobileNumber);
    }

    /**
     * The 13-digit GCash reference number entered in payment.xml Step 3
     * (Booking#getTransactionRef()). Same visibility rule as
     * bindGcashMobileRow() above - hidden only for Cash, otherwise always
     * shown. A stored value that isn't exactly 13 digits (a backend data
     * problem - PaymentActivity's own Step 3 input never lets a guest submit
     * anything but exactly 13) is NEVER shown as a bare digit string (a guest
     * reading "49" next to "GCash Reference #" can't tell that apart from a
     * real value) - it's replaced with an explicit fallback message instead,
     * via the shared GcashReferenceFormatter.formatOrFallback() also used by
     * BookingDetailsActivity/TransactionDetailsActivity so all three screens
     * agree on what "incomplete" looks like.
     */
    private void bindGcashReferenceRow(boolean gcash) {
        if (!gcash) {
            bindRow(R.id.rowGcashReference, getString(R.string.receipt_gcash_reference_number_label), null);
            return;
        }
        String raw = booking.getTransactionRef();
        String digits = GcashReferenceFormatter.digitsOnly(raw);
        boolean complete = digits.length() == 13;
        if (!complete) {
            android.util.Log.w(TAG, (digits.isEmpty()
                    ? "reference_number missing for paymentId=" + booking.getLatestPaymentId()
                    : "reference_number has " + digits.length() + " digits (expected 13) for paymentId=" + booking.getLatestPaymentId()
                    + " - value=\"" + digits + "\"")
                    + " bookingId=" + booking.getId() + " - API returned incomplete data for a GCash payment");
        }
        bindRow(R.id.rowGcashReference, getString(R.string.receipt_gcash_reference_number_label),
                GcashReferenceFormatter.formatOrFallback(raw,
                        getString(R.string.receipt_gcash_value_missing),
                        getString(R.string.receipt_gcash_reference_legacy_incomplete)));
        if (complete) emphasizeRowValue(R.id.rowGcashReference);
    }

    @Nullable
    private String computeNights(@Nullable String checkIn, @Nullable String checkOut) {
        Long nights = StayDateCalculator.nightsBetweenOrNull(checkIn, checkOut);
        return nights != null ? String.valueOf(nights) : null;
    }

    private String formatPrice(double value) {
        return String.format(Locale.US, "₱%,.2f", value);
    }

    /**
     * One line per room type (e.g. "Deluxe Room ×2\nExecutive Room ×1") - the
     * real itemized breakdown (Booking#getRooms()) once the backend returns
     * one (see MULTI_ROOM_TRANSACTION_BACKEND_SPEC.md, empty on every
     * transaction today), else today's actual multi-room-type case: each
     * BookingGroupState sibling IS one distinct room type (see
     * resolveGroupMembers()/BookingDetailsActivity's identical fallback) -
     * without this, a "Deluxe + Suite" transaction's receipt would silently
     * show only this one anchor sibling's single room type. Falls back to
     * the plain single Room Type value only when neither applies.
     */
    private String formatRoomTypeValue() {
        List<BookingRoom> rooms = booking.getRooms();
        if (!rooms.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (BookingRoom room : rooms) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(room.getRoomTypeName()).append(" ×").append(room.getQuantity());
            }
            return sb.toString();
        }
        if (groupMembers != null && !groupMembers.isEmpty()) {
            java.util.LinkedHashMap<String, Integer> counts = new java.util.LinkedHashMap<>();
            for (Booking member : groupMembers) {
                int quantity = Math.max(1, member.getRoomsRequested());
                counts.merge(member.getRoomType(), quantity, Integer::sum);
            }
            StringBuilder sb = new StringBuilder();
            for (java.util.Map.Entry<String, Integer> entry : counts.entrySet()) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(entry.getKey()).append(" ×").append(entry.getValue());
            }
            return sb.toString();
        }
        return booking.getRoomType();
    }

    /**
     * Displays the exact GCash mobile number entered in payment.xml Step 2
     * (Booking#getGcashNumber()) - grouped with spaces for readability only,
     * every digit stays visible, nothing is masked/starred/truncated. This
     * app's own Step 2 input (isValidGcashNumber(), "9\\d{9}") only ever
     * stores the raw 10 digits "9XXXXXXXXX" - the "+63 " the guest sees while
     * typing is a prefixText decoration on that input field, never part of
     * the stored value - so that canonical case is displayed in local form
     * ("0912 345 6789", 4-3-4 grouping with the leading zero restored) to
     * match what the guest actually typed. A raw value that was itself
     * already stored in international form (a "+63"/"63" country code, e.g.
     * from a future input path or legacy data) is displayed as "+63 912 345
     * 6789" instead, since that's the form it's actually in - this method
     * never converts between the two, only reformats a value into the shape
     * it already is. Any other unexpected length/shape falls back to the raw
     * value verbatim rather than mangling or hiding real (if unusual) data.
     */
    @Nullable
    private String formatGcashMobileNumber(@Nullable String raw) {
        return GcashReferenceFormatter.formatMobileNumber(raw);
    }

    /**
     * Renders the receipt card view itself into a single-page PDF and saves
     * it via MediaStore.Downloads (API 29+, matches this project's minSdk, so
     * no WRITE_EXTERNAL_STORAGE permission is needed). Preserves everything
     * visible on screen - logo, hotel info, receipt number, every section -
     * since it's a direct render of the same view, not a separately
     * maintained template that could drift out of sync with it.
     */
    private void downloadReceiptAsPdf() {
        String paymentId = booking.getLatestPaymentId() != null ? booking.getLatestPaymentId() : booking.getId();
        String fileName = "VelocitySuites_Receipt_" + paymentId + ".pdf";

        int width = receiptCard.getWidth();
        int height = receiptCard.getHeight();
        if (width <= 0 || height <= 0) {
            Toast.makeText(this, R.string.receipt_download_failed, Toast.LENGTH_LONG).show();
            return;
        }

        PdfDocument document = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(width, height, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();
        canvas.drawColor(android.graphics.Color.WHITE);
        receiptCard.draw(canvas);
        document.finishPage(page);

        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            }
            android.net.Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new java.io.IOException("MediaStore did not return a Uri");
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                document.writeTo(out);
            }
            Toast.makeText(this, R.string.receipt_download_success, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.receipt_download_failed, Toast.LENGTH_LONG).show();
        } finally {
            document.close();
        }
    }
}
