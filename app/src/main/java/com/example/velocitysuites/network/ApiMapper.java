package com.example.velocitysuites.network;

import com.example.velocitysuites.Booking;
import com.example.velocitysuites.BookingAmenity;
import com.example.velocitysuites.BookingRoom;
import com.example.velocitysuites.ReceiptDetail;
import com.example.velocitysuites.Room;
import com.example.velocitysuites.RoomAmenity;
import com.example.velocitysuites.TimeUtils;
import com.example.velocitysuites.network.dto.BookingAmenityDto;
import com.example.velocitysuites.network.dto.BookingRoomDto;
import com.example.velocitysuites.network.dto.DirectBookingResponseDto;
import com.example.velocitysuites.network.dto.NotificationDto;
import com.example.velocitysuites.network.dto.PaymentSummaryDto;
import com.example.velocitysuites.network.dto.PaymentTransactionDto;
import com.example.velocitysuites.network.dto.ReceiptDetailDto;
import com.example.velocitysuites.network.dto.ReceiptDetailResponse;
import com.example.velocitysuites.network.dto.ReceiptSummaryDto;
import com.example.velocitysuites.network.dto.ReservationDto;
import com.example.velocitysuites.network.dto.RoomAmenityDto;
import com.example.velocitysuites.network.dto.RoomDto;
import com.example.velocitysuites.network.dto.RoomTypeDto;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Converts backend JSON shapes (network.dto) into the app's existing
 * display models (Room, Booking, Notification), so the rest of the app
 * that was built against those models doesn't need to change.
 */
public final class ApiMapper {

    private static final SimpleDateFormat API_DATE = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private static final SimpleDateFormat DISPLAY_DATE = new SimpleDateFormat("MMM dd, yyyy", Locale.US);

    private ApiMapper() {}

    public static String toApiDate(java.util.Calendar calendar) {
        return API_DATE.format(calendar.getTime());
    }

    /**
     * Maps a room TYPE (guests browse/book by type, never an individual
     * room/unit - see RoomRepository/BookingAndReservationActivity).
     * Reuses the app's existing Room display model rather than
     * introducing a parallel one; getId() here is the room_type_id sent
     * back on booking, not an individual room's id.
     */
    public static Room toRoom(RoomTypeDto dto) {
        Room room = new Room(
                String.valueOf(dto.id),
                dto.name,
                dto.name,
                dto.capacity,
                dto.rateAsDouble(),
                dto.description != null ? dto.description : "",
                0,
                !dto.is_fully_booked,
                toRoomAmenities(dto.amenities),
                dto.bed_type != null ? dto.bed_type : "",
                dto.formattedRoomSize(),
                dto.policies != null ? dto.policies : ""
        );

        room.setRoomTypeId(dto.id);
        room.setAvailableCount(dto.available_count);
        if (dto.image_url != null && !dto.image_url.isEmpty()) {
            room.setImageUrl(dto.image_url);
        }
        room.setImageUrls(toImageUrls(dto.gallery));
        room.setImageLabels(toImageLabels(dto.gallery));

        return room;
    }

    private static List<String> toImageUrls(List<com.example.velocitysuites.network.dto.RoomGalleryImageDto> gallery) {
        if (gallery == null || gallery.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> urls = new ArrayList<>(gallery.size());
        for (com.example.velocitysuites.network.dto.RoomGalleryImageDto image : gallery) {
            urls.add(image.url);
        }
        return urls;
    }

    /** Index-aligned with toImageUrls() - which individual room each gallery photo came from (e.g. "Room 302"). */
    private static List<String> toImageLabels(List<com.example.velocitysuites.network.dto.RoomGalleryImageDto> gallery) {
        if (gallery == null || gallery.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> labels = new ArrayList<>(gallery.size());
        for (com.example.velocitysuites.network.dto.RoomGalleryImageDto image : gallery) {
            labels.add(image.room_label);
        }
        return labels;
    }

    private static List<RoomAmenity> toRoomAmenities(List<RoomAmenityDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return Collections.emptyList();
        }

        List<RoomAmenity> amenities = new ArrayList<>(dtos.size());
        for (RoomAmenityDto dto : dtos) {
            amenities.add(new RoomAmenity(dto.name, dto.category, dto.description, dto.pricing_type, dto.fee));
        }
        return amenities;
    }

    /**
     * Itemized room-type breakdown for a multi-room-type transaction - see
     * MULTI_ROOM_TRANSACTION_BACKEND_SPEC.md. CORRECTED 2026-09-18 after
     * connecting directly to the live backend via SSH: contrary to this
     * doc's and an earlier version of this comment's claims, NONE of
     * DirectBookingResponseDto#room_lines / ReservationDto#room_lines /
     * BillingDto#room_lines were actually being returned by any live
     * endpoint before this date - `booking_room_lines`/`reservation_room_lines`
     * had real data in the database (populated at creation time) but the
     * guest API's show()/index() methods never eager-loaded or serialized
     * them. Backend now fixed to expose them (see Booking/Reservation/
     * Billing::getRoomLinesAttribute()) - this method itself needed no
     * change beyond the field rename (dto.rooms -> dto.room_lines at each
     * call site) once that was live. Every caller must still treat
     * Booking#getRooms() as "may be empty" (a transaction predating this
     * feature, or created through a different channel that only ever sent a
     * single room_type_id) and fall back to the existing single
     * roomType/roomName fields in that case.
     */
    private static List<BookingRoom> toBookingRooms(List<BookingRoomDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return Collections.emptyList();
        }
        List<BookingRoom> rooms = new ArrayList<>(dtos.size());
        for (BookingRoomDto dto : dtos) {
            rooms.add(new BookingRoom(dto.room_type_id, dto.room_type, dto.quantity, dto.price_per_night, dto.nights, dto.subtotal, dto.assigned_room_numbers));
        }
        return rooms;
    }

    /** Itemized paid-amenity breakdown - see toBookingRooms()'s own doc, same "always empty today" rule. */
    private static List<BookingAmenity> toBookingAmenities(List<BookingAmenityDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return Collections.emptyList();
        }
        List<BookingAmenity> amenities = new ArrayList<>(dtos.size());
        for (BookingAmenityDto dto : dtos) {
            amenities.add(new BookingAmenity(dto.amenity_id, dto.amenity_name, dto.quantity, dto.unit_price, dto.subtotal));
        }
        return amenities;
    }

    public static Booking toBooking(ReservationDto dto) {
        // Room assignment happens at check-in, against the Booking, not the
        // Reservation - dto.room is always null under the current backend
        // (kept only for old/unconverted records), so the actual assigned
        // room (once there is one) lives at dto.booking.room instead.
        RoomDto assignedRoom = dto.booking != null ? dto.booking.room : null;
        String roomName = assignedRoom != null ? assignedRoom.room_name
                : (dto.room_type != null ? dto.room_type.name : "Room");
        String roomType = dto.room_type != null ? dto.room_type.name : "";
        String roomIdDisplay = assignedRoom != null ? assignedRoom.room_number : "Pending Assignment";
        // Distinct from roomIdDisplay above (which falls back to the "Pending
        // Assignment" placeholder for the internal roomId field, never shown
        // to a guest) - this is the guest-facing "Room Number" value, null
        // (hidden) rather than a placeholder string until a room is actually
        // assigned at check-in.
        String roomNumberValue = assignedRoom != null ? assignedRoom.room_number : null;

        String checkIn = reformatDate(dto.check_in);
        String checkOut = reformatDate(dto.check_out);
        String bookingDate = dto.booking != null ? reformatDateTime(dto.booking.confirmed_at) : "";

        double totalAmount;
        boolean totalIncludesAmenities;
        double amountPaid = 0;
        String billingStatus = null;
        boolean pendingVerification = false;
        com.example.velocitysuites.network.dto.PaymentDto latestPayment = null;
        List<com.example.velocitysuites.network.dto.PaymentDto> paymentDtos = null;
        double roomCharge = 0;
        double amenityCharge = 0;
        double additionalGuestFee = 0;
        // Post-conversion, the itemized breakdown lives under the Billing row
        // (dto.booking.billing.room_lines) - confirmed live 2026-09-18 (see
        // BillingDto#room_lines's own doc). Pre-conversion, the breakdown
        // lives directly on the Reservation (dto.room_lines) - see
        // ReservationDto#room_lines's own doc. See toBookingRooms()'s own doc
        // for the full picture across all three response shapes.
        List<BookingRoomDto> roomLineDtos;
        List<BookingAmenityDto> amenityLineDtos;
        if (dto.booking != null && dto.booking.billing != null) {
            totalAmount = dto.booking.billing.totalAmountAsDouble();
            totalIncludesAmenities = true;
            billingStatus = dto.booking.billing.billing_status;
            amountPaid = dto.booking.billing.amountPaidCompleted();
            pendingVerification = dto.booking.billing.hasPendingPayment();
            latestPayment = dto.booking.billing.latestPayment();
            paymentDtos = dto.booking.billing.payments;
            roomCharge = parseAmount(dto.booking.billing.room_charge);
            amenityCharge = parseAmount(dto.booking.billing.amenity_charge);
            additionalGuestFee = parseAmount(dto.booking.billing.additional_guest_fee);
            roomLineDtos = dto.booking.billing.room_lines;
            amenityLineDtos = dto.booking.billing.amenities;
            // Older cached responses may not carry the payments list -
            // fall back to the billing status like before.
            if (amountPaid == 0 && "paid".equalsIgnoreCase(billingStatus)) {
                amountPaid = totalAmount;
            }
        } else {
            // total_amount_due (Reservation::total_amount_due, backend) is
            // the authoritative grand total whenever the server sends one -
            // correctly sums every selected room type's subtotal plus every
            // selected amenity's subtotal, unlike the single-room-type
            // nightlyRate*nights*rooms_requested formula below, which only
            // ever priced the reservation's "primary" room type and silently
            // ignored any additional room type/amenity selected alongside it
            // (see MULTI_ROOM_TRANSACTION_BACKEND_SPEC.md). The formula stays
            // only as a fallback for an older cached response that predates
            // this field.
            double nightlyRate = dto.room_type != null ? dto.room_type.rateAsDouble() : 0;
            long nights = nightsBetween(dto.check_in, dto.check_out);
            totalAmount = dto.total_amount_due > 0
                    ? dto.total_amount_due
                    : nightlyRate * Math.max(1, nights) * Math.max(1, dto.rooms_requested);
            // total_amount_due already sums every room line's subtotal plus
            // every amenity's subtotal server-side (Reservation::total_amount_due) -
            // only the legacy per-room-only fallback formula still needs
            // RoomRepository#correctPendingReservationTotal()'s amenities top-up.
            totalIncludesAmenities = dto.total_amount_due > 0;
            roomLineDtos = dto.room_lines;
            amenityLineDtos = dto.amenities;

            // No Booking/Billing yet, but a deposit may already be sitting
            // against the Reservation itself (billing_id null until
            // conversion) - surface it the same way so "pending
            // verification" shows up before staff convert the reservation.
            if (dto.payments != null && !dto.payments.isEmpty()) {
                amountPaid = sumCompletedPayments(dto.payments);
                pendingVerification = hasPendingPayment(dto.payments);
                latestPayment = latestPaymentOf(dto.payments);
                paymentDtos = dto.payments;
            }
        }

        Booking booking = new Booking(
                String.valueOf(dto.id),
                assignedRoom != null ? String.valueOf(assignedRoom.id) : roomIdDisplay,
                roomName,
                roomType,
                checkIn,
                checkOut,
                dto.number_of_guests,
                totalAmount,
                displayStatus(dto.status, dto.booking != null ? dto.booking.booking_status : null),
                bookingDate
        );
        booking.setAmountPaid(amountPaid);
        booking.setTotalIncludesAmenities(totalIncludesAmenities);
        booking.setRoomNumber(roomNumberValue);
        booking.setRoomCharge(roomCharge);
        booking.setAmenityCharge(amenityCharge);
        booking.setAdditionalGuestFee(additionalGuestFee);
        booking.setRooms(toBookingRooms(roomLineDtos));
        booking.setAmenities(toBookingAmenities(amenityLineDtos));
        if (dto.discount_preview != null) {
            booking.setDiscountAmount(dto.discount_preview.discount);
        }
        booking.setIdCardType(dto.id_card_type != null ? dto.id_card_type : "None");
        booking.setHasBooking(dto.booking != null);
        booking.setRoomImageUrl(dto.room_type != null ? dto.room_type.image_url : null);
        booking.setRoomTypeId(String.valueOf(dto.room_type_id));
        booking.setRoomsRequested(Math.max(1, dto.rooms_requested));
        booking.setAdults(dto.adults);
        booking.setChildren(dto.children);
        booking.setGuestFirstName(dto.guest_first_name);
        booking.setGuestMiddleName(dto.guest_middle_name);
        booking.setGuestLastName(dto.guest_last_name);
        booking.setBillingStatus(billingStatus);
        booking.setPaymentPendingVerification(pendingVerification);
        String verifiedAt = dto.booking != null ? dto.booking.verified_at : dto.verified_at;
        booking.setStaffVerified(verifiedAt != null && !verifiedAt.isEmpty());
        booking.setHiddenAt(dto.booking != null ? dto.booking.hidden_at : dto.hidden_at);
        booking.setPaymentDeadline(dto.payment_deadline);
        booking.setSelectedPaymentPercentage(dto.selected_payment_percentage);
        booking.setRequiredPaymentAmount(dto.required_payment_amount);
        // A converted Booking's own rejection (Receptionist\BookingController
        // ::reject()) takes priority; otherwise fall back to the Reservation's
        // own (pre-conversion receptionist reject, or the automatic 48-hour
        // unpaid expiry - both set dto.rejection_reason).
        String bookingLevelReason = dto.booking != null ? dto.booking.rejection_reason : null;
        booking.setTransactionRejectionReason(
                bookingLevelReason != null && !bookingLevelReason.isEmpty() ? bookingLevelReason : dto.rejection_reason);
        // Same booking-takes-priority-over-reservation resolution as the
        // rejection reason above - see Booking::cancelled_at/Reservation::
        // cancelled_at (backend). Previously always null (no backend column
        // existed to send), which forced every cancellation-date display to
        // permanently show "Not Available" - see TransactionHistoryActivity/
        // TransactionListActivity/UpcomingTransactionsActivity.
        String bookingLevelCancelledAt = dto.booking != null ? dto.booking.cancelled_at : null;
        String cancelledAt = bookingLevelCancelledAt != null && !bookingLevelCancelledAt.isEmpty()
                ? bookingLevelCancelledAt : dto.cancelled_at;
        booking.setCancellationDate(cancelledAt != null && !cancelledAt.isEmpty() ? reformatDateTime(cancelledAt) : null);
        booking.setCancellationReason(booking.getTransactionRejectionReason());

        // Timestamp architecture pass: created_at is permanent (reservation-level,
        // never overwritten by a later conversion/update), while checked_in_at/
        // checked_out_at/completed_at only ever live on the converted Booking -
        // a plain unconverted Reservation has no check-in/out lifecycle of its own.
        booking.setCreatedAtDisplay(reformatDateTime(dto.created_at));
        booking.setCreatedAtMillis(epochMillis(dto.created_at));
        if (dto.booking != null) {
            booking.setCheckedInAtDisplay(reformatDateTime(dto.booking.checked_in_at));
            booking.setCheckedOutAtDisplay(reformatDateTime(dto.booking.checked_out_at));
            booking.setCompletedAtDisplay(reformatDateTime(dto.booking.completed_at));
        }
        // The Reservation's own payment_method (set once any payment attempt is
        // recorded - see ReservationWorkflowService::recordCashIntent()/
        // recordDepositPayment()) is the canonical source; the latest payment's own
        // payment_method below is only a fallback for older cached data that predates
        // this field being populated server-side.
        if (dto.payment_method != null && !dto.payment_method.isEmpty()) {
            booking.setPaymentMethod(dto.payment_method.toUpperCase(Locale.US));
        }
        booking.setPaymentMethodLocked(dto.payment_method_locked_at != null && !dto.payment_method_locked_at.isEmpty());
        booking.setEditedOnce(dto.edited_at != null && !dto.edited_at.isEmpty());
        if (latestPayment != null) {
            booking.setTransactionRef(latestPayment.reference_number);
            if (booking.getPaymentMethod() == null && latestPayment.payment_method != null) {
                booking.setPaymentMethod(latestPayment.payment_method.toUpperCase(Locale.US));
            }
            booking.setPaymentDate(reformatDateTime(latestPayment.payment_date));
            booking.setPaymentDateOnly(reformatDateOnly(latestPayment.payment_date));
            booking.setPaymentTimeOnly(reformatTimeOnly(latestPayment.payment_date));
            booking.setLatestPaymentId(String.valueOf(latestPayment.id));
            booking.setGcashNumber(latestPayment.gcash_number);
            booking.setReceiptUrl(latestPayment.receipt_url);
            booking.setPaymentVerificationStatus(latestPayment.verification_status);
            booking.setRejectionReason(latestPayment.rejection_reason);
            booking.setPaymentVerifiedAtDisplay(reformatDateTime(latestPayment.verified_at));
        }
        if (paymentDtos != null) {
            List<Booking.PaymentRecord> history = new ArrayList<>();
            for (com.example.velocitysuites.network.dto.PaymentDto p : paymentDtos) {
                history.add(new Booking.PaymentRecord(
                        p.amount_paid,
                        p.payment_method != null ? p.payment_method.toUpperCase(Locale.US) : null,
                        p.reference_number,
                        reformatDateTime(p.payment_date),
                        p.payment_status,
                        p.gcash_number));
            }
            booking.setPaymentHistory(history);
        }

        List<Booking.AdditionalGuest> guests = new ArrayList<>();
        // additional_guest_details is a nullable JSON column, not a
        // relationship collection - Laravel serializes an unset value as
        // JSON null (not []), and Gson happily overwrites the field's
        // ArrayList default with null when it sees that, so this must be
        // null-checked rather than assumed to be an empty list.
        if (dto.additional_guest_details != null) {
            for (com.example.velocitysuites.network.dto.AdditionalGuestDto g : dto.additional_guest_details) {
                guests.add(new Booking.AdditionalGuest(g.name, g.age, g.gender, g.relationship));
            }
        }
        booking.setAdditionalGuests(guests);

        // Attached only by Api\ReservationController::show() (not index()) -
        // null/empty on any other response, which Booking's own getters
        // already fall back gracefully for - see PaymentSummaryDto's own
        // doc and PAYMENT_RECEIPT_HISTORY_BACKEND_SPEC.md §11.
        booking.setPaymentSummary(toPaymentSummary(dto.payment_summary));
        booking.setPaymentTransactions(toPaymentTransactions(dto.payment_transactions));
        booking.setReceipts(toReceipts(dto.receipts));

        return booking;
    }

    /**
     * A "frozen", view-only snapshot of a Reservation that has since
     * converted into a Booking - lets the Reservation tab's Completed
     * Reservation List keep showing the original transaction as a
     * historical record even though the real, actionable Booking now lives
     * under the Booking tab (see RoomRepository#refreshBookings(), which
     * calls this alongside the normal toBooking(dto) call for every dto
     * where dto.booking != null, and BookingAndReservationActivity#
     * itemsForCurrentTab(), which merges this list in only for the
     * Reservation tab's Completed category).
     * <p>
     * Deliberately built by cloning toBooking(dto)'s already-correct
     * display fields rather than re-deriving them, so this can never drift
     * from what the real converted Booking shows. Two fields are
     * deliberately forced regardless of the real underlying state:
     * hasBooking=false (so it still reads as a Reservation, not a Booking)
     * and staffVerified=true (the only signal TransactionCategorizer needs
     * to bucket a non-Booking item as COMPLETED - see that class). id is
     * deliberately left as the plain dto.id (same value the real converted
     * Booking uses, matching this app's existing "guests see everything
     * keyed by the reservation id even after conversion" convention - see
     * project_architecture) rather than a suffixed variant, so ref-number
     * display (Ref No. X) stays clean. This is safe against by-id
     * collisions because this object never enters the shared `bookings`
     * cache at all (see RoomRepository#completedHistoricalReservations) -
     * it only ever appears in the one render path that explicitly asks for
     * it (Reservation tab + Completed category), which the real Booking
     * object can never simultaneously occupy (hasBooking=true items are
     * excluded from the Reservation tab entirely).
     * <p>
     * Deliberately NOT copied: paymentHistory (left empty - the real
     * payment records already show once, on the real converted Booking;
     * copying them here would double-count every payment in Transaction
     * History's per-payment flattening), latestPaymentId/rejectionReason/
     * cancellationDate/paymentDeadline/actualCheckIn/actualCheckOut (none
     * apply to a closed, successfully-converted record). Only ever called
     * for a dto that has a non-null dto.booking - callers must check that
     * first.
     */
    public static Booking toHistoricalReservation(ReservationDto dto) {
        Booking real = toBooking(dto);

        Booking historical = new Booking(
                String.valueOf(dto.id),
                real.getRoomId(),
                real.getRoomName(),
                real.getRoomType(),
                real.getCheckInDate(),
                real.getCheckOutDate(),
                real.getGuests(),
                real.getTotalAmount(),
                "Completed",
                real.getBookingDate());
        historical.setAmountPaid(real.getAmountPaid());
        historical.setDiscountAmount(real.getDiscountAmount());
        historical.setIdCardType(real.getIdCardType());
        // Not copying this was the root cause of a real, reproduced bug:
        // RoomRepository#correctPendingReservationTotal()'s skip guard is
        // `isHasBooking() || isTotalIncludesAmenities()` - hasBooking is
        // deliberately forced false right below (so this still reads as a
        // Reservation, not a Booking), and with totalIncludesAmenities left
        // at its default `false`, BOTH guard conditions failed, so that
        // "amenities top-up" (meant only for a genuinely pre-conversion,
        // room-only total) fired again on top of real.getTotalAmount(),
        // which was already amenities-inclusive - double-counting the
        // amenities total (e.g. an ₱8,650 grand total becoming ₱8,800, off
        // by exactly the ₱150 amenities total) on this screen and on the
        // Official Payment Receipt launched from it (same Booking object,
        // passed forward by Intent extra after being mutated in place).
        historical.setTotalIncludesAmenities(real.isTotalIncludesAmenities());
        historical.setHasBooking(false);
        historical.setDirectBooking(false);
        historical.setHistoricalReservation(true);
        historical.setStaffVerified(true);
        historical.setPaymentPendingVerification(false);
        historical.setRoomImageUrl(real.getRoomImageUrl());
        historical.setRoomTypeId(real.getRoomTypeId());
        historical.setRoomsRequested(real.getRoomsRequested());
        historical.setAdults(real.getAdults());
        historical.setChildren(real.getChildren());
        historical.setGuestFirstName(real.getGuestFirstName());
        historical.setGuestMiddleName(real.getGuestMiddleName());
        historical.setGuestLastName(real.getGuestLastName());
        historical.setBillingStatus(real.getBillingStatus());
        historical.setPaymentMethod(real.getPaymentMethod());
        historical.setPaymentMethodLocked(real.isPaymentMethodLocked());
        historical.setEditedOnce(real.isEditedOnce());
        historical.setTransactionRef(real.getTransactionRef());
        historical.setPaymentDate(real.getPaymentDate());
        historical.setPaymentDateOnly(real.getPaymentDateOnly());
        historical.setPaymentTimeOnly(real.getPaymentTimeOnly());
        historical.setGcashNumber(real.getGcashNumber());
        historical.setRoomNumber(real.getRoomNumber());
        historical.setRoomCharge(real.getRoomCharge());
        historical.setAmenityCharge(real.getAmenityCharge());
        historical.setAdditionalGuestFee(real.getAdditionalGuestFee());
        historical.setRooms(real.getRooms());
        historical.setAmenities(real.getAmenities());
        historical.setReceiptUrl(real.getReceiptUrl());
        historical.setPaymentVerificationStatus(real.getPaymentVerificationStatus());
        historical.setSelectedPaymentPercentage(real.getSelectedPaymentPercentage());
        historical.setRequiredPaymentAmount(real.getRequiredPaymentAmount());
        historical.setAdditionalGuests(real.getAdditionalGuests());
        // Unlike paymentHistory (deliberately not copied - see this method's
        // own doc, to avoid double-counting per-payment rows in Transaction
        // History's flattening), payment_summary/payment_transactions/receipts
        // are booking-level aggregates describing the SAME underlying
        // converted transaction, not per-item rows - safe, and desired, to
        // carry over so this historical entry's own Booking Details still
        // shows accurate figures (see PAYMENT_RECEIPT_HISTORY_BACKEND_SPEC.md
        // §7 - "converted Reservation -> Booking data must remain representable").
        historical.setPaymentSummary(real.getPaymentSummary());
        historical.setPaymentTransactions(real.getPaymentTransactions());
        historical.setReceipts(real.getReceipts());
        // The Completed Reservation list must still show both the original
        // reservation creation date and the conversion date (getBookingDate(),
        // already passed to the constructor above via confirmed_at).
        historical.setCreatedAtDisplay(real.getCreatedAtDisplay());
        historical.setCreatedAtMillis(real.getCreatedAtMillis());
        // The one genuinely distinct number worth cross-referencing here -
        // the real backend bookings.id (never otherwise shown to a guest,
        // who only ever sees the shared reservation id as "Ref No. X" - see
        // the id choice above) - so "Converted Booking: BOOK-Y" on this
        // historical record's detail screen actually points at something
        // new, not a restatement of its own ref number.
        historical.setConvertedBookingId(String.valueOf(dto.booking.id));
        return historical;
    }

    /**
     * A "New Booking" transaction created directly via Api\BookingController -
     * reservation_id is always null here, so unlike toBooking(ReservationDto)
     * above there's no reservation/pre-conversion branch to consider: this
     * record is created already confirmed, with its payment already attached.
     */
    public static Booking toBooking(DirectBookingResponseDto dto) {
        RoomDto assignedRoom = dto.room;
        String roomName = assignedRoom != null ? assignedRoom.room_name
                : (dto.room_type != null ? dto.room_type.name : "Room");
        String roomType = dto.room_type != null ? dto.room_type.name : "";
        String roomIdDisplay = assignedRoom != null ? assignedRoom.room_number : "Pending Assignment";

        String checkIn = reformatDate(dto.check_in);
        String checkOut = reformatDate(dto.check_out);
        String bookingDate = reformatDateTime(dto.confirmed_at);

        // The true grand total for the WHOLE transaction (every selected room
        // type's subtotal + every selected amenity's subtotal), computed and
        // persisted server-side - see Booking::getTotalAmountDueAttribute()
        // and DirectBookingResponseDto#total_amount_due's own doc. Reading
        // the latest payment's own amount_paid as if it WERE the total (the
        // old behavior here) is wrong the moment a booking is paid partially
        // - it would show only the amount being paid now, never the total
        // the guest actually owes for every room type/amenity they selected.
        double totalAmount = dto.total_amount_due;
        double amountPaid = dto.payments != null ? sumSubmittedPayments(dto.payments) : 0;
        com.example.velocitysuites.network.dto.PaymentDto latestPayment = null;
        boolean pendingVerification = false;
        if (dto.payments != null && !dto.payments.isEmpty()) {
            latestPayment = latestPaymentOf(dto.payments);
            pendingVerification = hasPendingPayment(dto.payments);
        }
        // A booking created before this multi-room-type contract shipped (or
        // before any payment is attached at all) has no total_amount_due to
        // read yet - fall back to the single most recent payment's own
        // amount_paid, the old behavior, rather than showing a bare ₱0.
        if (totalAmount <= 0.009 && latestPayment != null) {
            totalAmount = parseAmount(latestPayment.amount_paid);
        }

        Booking booking = new Booking(
                String.valueOf(dto.id),
                assignedRoom != null ? String.valueOf(assignedRoom.id) : roomIdDisplay,
                roomName,
                roomType,
                checkIn,
                checkOut,
                dto.number_of_guests,
                totalAmount,
                displayStatus(null, dto.booking_status),
                bookingDate
        );
        booking.setAmountPaid(amountPaid);
        // Previously never read here - every direct "New Booking" transaction's
        // Payment Percentage row silently stayed blank on Booking Details/the
        // Official Payment Receipt (booking.getSelectedPaymentPercentage()
        // defaulted to null), unlike the reservation-derived path below, which
        // already reads dto.selected_payment_percentage. Same field, same
        // already-a-whole-percentage-value contract (20/30/40/50/100) - never
        // multiply by 100 here.
        booking.setSelectedPaymentPercentage(dto.selected_payment_percentage);
        booking.setRequiredPaymentAmount(dto.required_payment_amount);
        booking.setRoomNumber(assignedRoom != null ? assignedRoom.room_number : null);
        booking.setRooms(toBookingRooms(dto.room_lines));
        booking.setAmenities(toBookingAmenities(dto.amenities));
        // Room Charges/Amenities Total breakdown for the Payment Summary
        // section - summed from the same itemized lines just set above, so
        // this can never drift from what getRooms()/getAmenities() shows.
        // Both stay 0 (the constructor default) for a booking created
        // before this multi-room-type contract shipped, same as the
        // reservation-derived path already does when no Billing exists yet.
        if (dto.room_lines != null) {
            double roomCharge = 0;
            for (BookingRoomDto line : dto.room_lines) roomCharge += line.subtotal;
            booking.setRoomCharge(roomCharge);
        }
        if (dto.amenities != null) {
            double amenityCharge = 0;
            for (BookingAmenityDto line : dto.amenities) amenityCharge += line.subtotal;
            booking.setAmenityCharge(amenityCharge);
        }
        booking.setIdCardType(dto.id_card_type != null ? dto.id_card_type : "None");
        // Representative name for this specific transaction - DirectBookingResponseDto
        // carries these same three fields as ReservationDto (see toBooking(ReservationDto)
        // above), but they were never read here, so every direct "New Booking" transaction
        // (Api\BookingController, no parent Reservation) lost its Representative Name and
        // Guest Information silently fell back to showing only Guest Account Name.
        booking.setGuestFirstName(dto.guest_first_name);
        booking.setGuestMiddleName(dto.guest_middle_name);
        booking.setGuestLastName(dto.guest_last_name);
        booking.setRoomImageUrl(dto.room_type != null ? dto.room_type.image_url : null);
        booking.setHasBooking(true);
        booking.setDirectBooking(true);
        booking.setPaymentPendingVerification(pendingVerification);
        booking.setStaffVerified(dto.verified_at != null && !dto.verified_at.isEmpty());
        booking.setHiddenAt(dto.hidden_at);
        booking.setTransactionRejectionReason(dto.rejection_reason);
        booking.setCancellationDate(dto.cancelled_at != null && !dto.cancelled_at.isEmpty() ? reformatDateTime(dto.cancelled_at) : null);
        booking.setCancellationReason(dto.rejection_reason);
        // A direct booking is created already confirmed - confirmed_at IS its creation instant.
        booking.setCreatedAtDisplay(reformatDateTime(dto.confirmed_at));
        booking.setCreatedAtMillis(epochMillis(dto.confirmed_at));
        booking.setCheckedInAtDisplay(reformatDateTime(dto.checked_in_at));
        booking.setCheckedOutAtDisplay(reformatDateTime(dto.checked_out_at));
        booking.setCompletedAtDisplay(reformatDateTime(dto.completed_at));
        if (dto.payment_method != null && !dto.payment_method.isEmpty()) {
            booking.setPaymentMethod(dto.payment_method.toUpperCase(Locale.US));
        }
        if (latestPayment != null) {
            booking.setTransactionRef(latestPayment.reference_number);
            booking.setPaymentDate(reformatDateTime(latestPayment.payment_date));
            booking.setPaymentDateOnly(reformatDateOnly(latestPayment.payment_date));
            booking.setPaymentTimeOnly(reformatTimeOnly(latestPayment.payment_date));
            booking.setLatestPaymentId(String.valueOf(latestPayment.id));
            booking.setGcashNumber(latestPayment.gcash_number);
            booking.setReceiptUrl(latestPayment.receipt_url);
            booking.setPaymentVerificationStatus(latestPayment.verification_status);
            booking.setRejectionReason(latestPayment.rejection_reason);
            booking.setPaymentVerifiedAtDisplay(reformatDateTime(latestPayment.verified_at));

            List<Booking.PaymentRecord> history = new ArrayList<>();
            for (com.example.velocitysuites.network.dto.PaymentDto p : dto.payments) {
                history.add(new Booking.PaymentRecord(
                        p.amount_paid,
                        p.payment_method != null ? p.payment_method.toUpperCase(Locale.US) : null,
                        p.reference_number,
                        reformatDateTime(p.payment_date),
                        p.payment_status,
                        p.gcash_number));
            }
            booking.setPaymentHistory(history);
        }

        List<Booking.AdditionalGuest> guests = new ArrayList<>();
        if (dto.additional_guest_details != null) {
            for (com.example.velocitysuites.network.dto.AdditionalGuestDto g : dto.additional_guest_details) {
                guests.add(new Booking.AdditionalGuest(g.name, g.age, g.gender, g.relationship));
            }
        }
        booking.setAdditionalGuests(guests);

        // Attached only by Api\BookingController::show() (not index()/store()) -
        // see the ReservationDto overload's identical wiring above.
        booking.setPaymentSummary(toPaymentSummary(dto.payment_summary));
        booking.setPaymentTransactions(toPaymentTransactions(dto.payment_transactions));
        booking.setReceipts(toReceipts(dto.receipts));

        return booking;
    }

    /**
     * Null-safe - a response that hasn't attached payment_summary yet
     * (older cached data, or a build predating this feature) must produce
     * a null Booking.PaymentSummary, never a synthesized default; see
     * Booking#hasAuthoritativePaymentSummary()'s fallback contract.
     */
    @androidx.annotation.Nullable
    private static Booking.PaymentSummary toPaymentSummary(@androidx.annotation.Nullable PaymentSummaryDto dto) {
        if (dto == null) return null;
        return new Booking.PaymentSummary(
                dto.grand_total,
                dto.total_amount_paid,
                dto.remaining_balance,
                dto.payment_status,
                dto.payment_percentage,
                dto.official_receipt_available
        );
    }

    /** Empty (never null) when the backend response didn't attach payment_transactions - see Booking#getPaymentTransactions()'s contract. */
    private static List<Booking.PaymentTransactionRecord> toPaymentTransactions(@androidx.annotation.Nullable List<PaymentTransactionDto> dtos) {
        if (dtos == null || dtos.isEmpty()) return Collections.emptyList();
        List<Booking.PaymentTransactionRecord> out = new ArrayList<>(dtos.size());
        for (PaymentTransactionDto dto : dtos) {
            out.add(new Booking.PaymentTransactionRecord(
                    dto.id, dto.payment_method, dto.payment_stage, dto.transaction_type,
                    dto.amount_paid, dto.payment_status, dto.verification_status,
                    dto.gcash_number, dto.gcash_reference_number, dto.reference_number,
                    dto.payment_percentage, dto.verified_by, dto.verified_at,
                    dto.rejection_reason, dto.payment_date,
                    dto.total_paid_after_transaction, dto.remaining_balance_after_transaction,
                    dto.receipt_type, dto.receipt_number
            ));
        }
        return out;
    }

    /** Empty (never null) when the backend response didn't attach receipts - see Booking#getReceipts()'s contract. */
    private static List<Booking.ReceiptSummary> toReceipts(@androidx.annotation.Nullable List<ReceiptSummaryDto> dtos) {
        if (dtos == null || dtos.isEmpty()) return Collections.emptyList();
        List<Booking.ReceiptSummary> out = new ArrayList<>(dtos.size());
        for (ReceiptSummaryDto dto : dtos) {
            out.add(new Booking.ReceiptSummary(
                    dto.receipt_number, dto.receipt_type, dto.status, dto.amount,
                    dto.payment_percentage, dto.issued_at
            ));
        }
        return out;
    }

    /**
     * GET guest/receipts/{receiptNumber} (Api\ReceiptController::show()) ->
     * this app's ReceiptDetail model. See ReceiptDetail's own doc for the
     * critical "display the snapshot exactly as returned" contract for a
     * PARTIAL_RECEIPT/FULL_PAYMENT_RECEIPT.
     */
    public static ReceiptDetail toReceiptDetail(ReceiptDetailResponse response) {
        ReceiptDetailDto dto = response.receipt;
        ReceiptDetail.AnchorPayment anchor = null;
        if (dto.anchor_payment != null) {
            anchor = new ReceiptDetail.AnchorPayment(
                    dto.anchor_payment.amount_paid,
                    dto.anchor_payment.payment_method,
                    dto.anchor_payment.payment_percentage,
                    dto.anchor_payment.gcash_number,
                    dto.anchor_payment.gcash_reference_number,
                    dto.anchor_payment.verified_at,
                    dto.anchor_payment.verified_by
            );
        }
        return new ReceiptDetail(
                dto.receipt_type,
                dto.receipt_number,
                String.valueOf(dto.booking_id),
                dto.reservation_id != null ? String.valueOf(dto.reservation_id) : null,
                dto.guest_account_name,
                dto.representative_name,
                dto.room_type,
                toBookingRooms(dto.room_lines),
                reformatDate(dto.check_in),
                reformatDate(dto.check_out),
                dto.number_of_nights,
                dto.assigned_room_numbers,
                toPaymentSummary(dto.payment_summary),
                toPaymentTransactions(dto.payment_transactions),
                anchor,
                reformatDateTime(dto.issued_at)
        );
    }

    private static double parseAmount(String s) {
        try {
            return s != null ? Double.parseDouble(s) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Reservation.status only covers the pre-booking lifecycle
     * (AWAITING_CASH_CONFIRMATION/AWAITING_GCASH_PAYMENT/TO_BE_CONVERTED/
     * REJECTED_RESERVATION/CANCELLED_RESERVATION/CONVERTED_TO_BOOKING - the
     * backend's literal reservation-to-booking-workflow vocabulary, see
     * App\Support\ReservationStatus server-side) - once converted, the
     * operational status (ACTIVE_BOOKING/CHECKED_IN/COMPLETED_BOOKING/
     * CANCELLED_BOOKING, App\Support\BookingStatus) lives on Booking
     * instead, so bookingStatus takes priority whenever a Booking exists.
     *
     * AWAITING_CASH_CONFIRMATION, AWAITING_GCASH_PAYMENT, and
     * TO_BE_CONVERTED all collapse to the single "Pending" label a guest
     * sees - that distinction is a staff-side workflow detail (mirrors the
     * receptionist's Reservation Module tabs), not something the app's own
     * "Pending"/"Confirmed"/"Checked-In"/"Checked-Out"/"Cancelled" status
     * model elsewhere (DashboardActivity, BookingAndReservationActivity,
     * TransactionAdapter) needs to distinguish - those all match on the
     * exact string "Pending".
     */
    public static String displayStatus(String reservationStatus, String bookingStatus) {
        if (bookingStatus != null) {
            switch (bookingStatus) {
                case "ACTIVE_BOOKING": return "Confirmed";
                case "CHECKED_IN": return "Checked-In";
                case "COMPLETED_BOOKING": return "Checked-Out";
                case "CANCELLED_BOOKING": return "Cancelled";
                default: return bookingStatus;
            }
        }

        if (reservationStatus == null) return "Pending";
        switch (reservationStatus) {
            case "AWAITING_CASH_CONFIRMATION":
            case "AWAITING_GCASH_PAYMENT":
            case "TO_BE_CONVERTED":
                return "Pending";
            case "REJECTED_RESERVATION": return "Rejected";
            case "CANCELLED_RESERVATION": return "Cancelled";
            case "CONVERTED_TO_BOOKING": return "Confirmed";
            default: return reservationStatus;
        }
    }

    /** Same rule as BillingDto.amountPaidCompleted(), for a plain payments list. */
    private static double sumCompletedPayments(List<com.example.velocitysuites.network.dto.PaymentDto> payments) {
        double sum = 0;
        for (com.example.velocitysuites.network.dto.PaymentDto p : payments) {
            if ("completed".equalsIgnoreCase(p.payment_status)) {
                try {
                    sum += p.amount_paid != null ? Double.parseDouble(p.amount_paid) : 0;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return sum;
    }

    /**
     * A direct Booking's own "Amount Paid/Submitted Payment" - unlike
     * sumCompletedPayments() above (which only counts staff-verified
     * amounts, the right rule for a reservation-derived Booking's Billing
     * row), a direct Booking's guest-facing Payment Summary must show what
     * the guest actually submitted the moment they submit it, money that
     * has already left their GCash account, even before a receptionist
     * verifies the receipt - "Pending Verification" describes the
     * verification step, not whether the guest paid. Excludes 'failed'/
     * 'rejected' payments, since those never actually counted.
     */
    private static double sumSubmittedPayments(List<com.example.velocitysuites.network.dto.PaymentDto> payments) {
        double sum = 0;
        for (com.example.velocitysuites.network.dto.PaymentDto p : payments) {
            if ("completed".equalsIgnoreCase(p.payment_status) || "pending".equalsIgnoreCase(p.payment_status)) {
                try {
                    sum += p.amount_paid != null ? Double.parseDouble(p.amount_paid) : 0;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return sum;
    }

    /** Same rule as BillingDto.hasPendingPayment(), for a plain payments list. */
    private static boolean hasPendingPayment(List<com.example.velocitysuites.network.dto.PaymentDto> payments) {
        for (com.example.velocitysuites.network.dto.PaymentDto p : payments) {
            if ("pending".equalsIgnoreCase(p.payment_status)) return true;
        }
        return false;
    }

    /**
     * Same rule as BillingDto.latestPayment(), for a plain payments list -
     * selects by highest `id` rather than trusting the array's arrival order,
     * so a Reservation-stage payment record (billing_id still null, pre-
     * conversion) can't show a stale/wrong payment's GCash number or
     * reference number just because the API returned the list out of order.
     */
    private static com.example.velocitysuites.network.dto.PaymentDto latestPaymentOf(
            List<com.example.velocitysuites.network.dto.PaymentDto> payments) {
        com.example.velocitysuites.network.dto.PaymentDto latest = payments.get(0);
        for (com.example.velocitysuites.network.dto.PaymentDto p : payments) {
            if (p.id > latest.id) latest = p;
        }
        return latest;
    }

    public static com.example.velocitysuites.Notification toNotification(NotificationDto dto) {
        String type = com.example.velocitysuites.Notification.TYPE_SYSTEM;
        if (dto.category != null) {
            switch (dto.category) {
                case "booking": type = com.example.velocitysuites.Notification.TYPE_BOOKING; break;
                case "payment": type = com.example.velocitysuites.Notification.TYPE_PAYMENT; break;
                case "check_in":
                case "check_out":
                case "checkin_reminder": type = com.example.velocitysuites.Notification.TYPE_CHECK_IN; break;
                case "announcement": type = com.example.velocitysuites.Notification.TYPE_ANNOUNCEMENT; break;
                // Discounts are guest-facing promotional offers same as Promotion rows -
                // both surface under the single "Promotions" filter/category on this
                // screen (see NotificationActivity's Filter by Status dropdown), matching
                // how the app already has no separate Discount vs Promotion UI distinction.
                case "promotion":
                case "discount": type = com.example.velocitysuites.Notification.TYPE_PROMOTION; break;
                default: type = com.example.velocitysuites.Notification.TYPE_SYSTEM;
            }
        }
        return new com.example.velocitysuites.Notification(
                String.valueOf(dto.id),
                dto.title,
                dto.message,
                relativeTime(dto.created_at),
                type,
                dto.is_read,
                dto.reference_id != null ? String.valueOf(dto.reference_id) : null,
                dto.target_audience,
                absoluteDateTime(dto.created_at)
        );
    }

    private static long nightsBetween(String checkIn, String checkOut) {
        try {
            Date in = API_DATE.parse(checkIn.length() > 10 ? checkIn.substring(0, 10) : checkIn);
            Date out = API_DATE.parse(checkOut.length() > 10 ? checkOut.substring(0, 10) : checkOut);
            return Math.max(1, (out.getTime() - in.getTime()) / (24 * 60 * 60 * 1000));
        } catch (ParseException | NullPointerException e) {
            return 1;
        }
    }

    /** Pure calendar date (check_in/check_out) - no timezone conversion, since these are stay dates, not instants. */
    private static String reformatDate(String apiDate) {
        if (apiDate == null) return "";
        String datePart = apiDate.length() > 10 ? apiDate.substring(0, 10) : apiDate;
        try {
            return DISPLAY_DATE.format(API_DATE.parse(datePart));
        } catch (ParseException e) {
            return apiDate;
        }
    }

    /**
     * A genuine instant (confirmed_at, payment_date, cancelled_at, etc.) -
     * converted from the backend's UTC storage into Asia/Manila and
     * formatted as "MMM d, yyyy • h:mm a" via TimeUtils, replacing this
     * class's old hand-rolled SimpleDateFormat parsing (which silently
     * dropped the time-of-day and never actually converted timezones - see
     * TimeUtils' class doc). Returns "" (not "N/A") for a null/empty input
     * so existing TextUtils.isEmpty()-guarded callers keep hiding the row.
     */
    private static String reformatDateTime(String apiTimestamp) {
        if (apiTimestamp == null || apiTimestamp.trim().isEmpty()) return "";
        String formatted = TimeUtils.formatDateTime(apiTimestamp);
        return "N/A".equals(formatted) ? "" : formatted;
    }

    /** Same instant-aware conversion as reformatDateTime(), but date-only - see Booking#getPaymentDateOnly(). */
    private static String reformatDateOnly(String apiTimestamp) {
        if (apiTimestamp == null || apiTimestamp.trim().isEmpty()) return "";
        String formatted = TimeUtils.formatDate(apiTimestamp);
        return "N/A".equals(formatted) ? "" : formatted;
    }

    /** Same instant-aware conversion as reformatDateTime(), but time-only - see Booking#getPaymentTimeOnly(). */
    private static String reformatTimeOnly(String apiTimestamp) {
        if (apiTimestamp == null || apiTimestamp.trim().isEmpty()) return "";
        String formatted = TimeUtils.formatTime(apiTimestamp);
        return "N/A".equals(formatted) ? "" : formatted;
    }

    /** Sortable epoch millis for a backend timestamp, or 0 when null/unparseable - see Booking#getCreatedAtMillis(). */
    private static long epochMillis(String apiTimestamp) {
        java.time.Instant instant = TimeUtils.parseInstant(apiTimestamp);
        return instant != null ? instant.toEpochMilli() : 0L;
    }

    private static String relativeTime(String isoTimestamp) {
        return TimeUtils.formatRelative(isoTimestamp);
    }

    /** "MMM d, yyyy • h:mm a" in Asia/Manila - used for notification/announcement absolute timestamps, distinct from the relative timestamp above. */
    private static String absoluteDateTime(String isoTimestamp) {
        if (isoTimestamp == null) return "";
        String formatted = TimeUtils.formatDateTime(isoTimestamp);
        return "N/A".equals(formatted) ? "" : formatted;
    }
}
