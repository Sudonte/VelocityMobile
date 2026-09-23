package com.example.velocitysuites.network;

import com.example.velocitysuites.network.dto.AmenityDto;
import com.example.velocitysuites.network.dto.AmenityRequestDto;
import com.example.velocitysuites.network.dto.AmenityRequestSubmitRequest;
import com.example.velocitysuites.network.dto.AnnouncementDto;
import com.example.velocitysuites.network.dto.ApiMessage;
import com.example.velocitysuites.network.dto.PromotionDto;
import com.example.velocitysuites.network.dto.AuthResponse;
import com.example.velocitysuites.network.dto.DeactivateAccountRequest;
import com.example.velocitysuites.network.dto.DirectBookingResponseDto;
import com.example.velocitysuites.network.dto.DiscountDto;
import com.example.velocitysuites.network.dto.EmailRequest;
import com.example.velocitysuites.network.dto.LoginRequest;
import com.example.velocitysuites.network.dto.NotificationDto;
import com.example.velocitysuites.network.dto.PaginatedResponse;
import com.example.velocitysuites.network.dto.PaymentRequest;
import com.example.velocitysuites.network.dto.PaymentSubmitResponse;
import com.example.velocitysuites.network.dto.PaymentsResponse;
import com.example.velocitysuites.network.dto.ReceiptDetailResponse;
import com.example.velocitysuites.network.dto.ProfileResponse;
import com.example.velocitysuites.network.dto.ProfileUpdateRequest;
import com.example.velocitysuites.network.dto.ReactivateResendRequest;
import com.example.velocitysuites.network.dto.ReactivateVerifyRequest;
import com.example.velocitysuites.network.dto.RegisterRequest;
import com.example.velocitysuites.network.dto.RequestableAmenityDto;
import com.example.velocitysuites.network.dto.ReservationDto;
import com.example.velocitysuites.network.dto.ReservationRequest;
import com.example.velocitysuites.network.dto.ReservationUpdateRequest;
import com.example.velocitysuites.network.dto.ResetPasswordRequest;
import com.example.velocitysuites.network.dto.RoomDetailResponse;
import com.example.velocitysuites.network.dto.RoomsResponse;
import com.example.velocitysuites.network.dto.VerifyOtpRequest;
import com.example.velocitysuites.network.dto.VerifyResetOtpRequest;

import java.util.List;
import java.util.Map;

import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.PartMap;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;

public interface ApiService {

    @POST("register")
    Call<ApiMessage> register(@Body RegisterRequest request);

    @POST("verify-otp")
    Call<AuthResponse> verifyOtp(@Body VerifyOtpRequest request);

    @POST("resend-otp")
    Call<ApiMessage> resendOtp(@Body EmailRequest request);

    @POST("login")
    Call<AuthResponse> login(@Body LoginRequest request);

    @POST("logout")
    Call<ApiMessage> logout();

    @POST("forgot-password")
    Call<ApiMessage> forgotPassword(@Body EmailRequest request);

    @POST("verify-reset-otp")
    Call<ApiMessage> verifyResetOtp(@Body VerifyResetOtpRequest request);

    @POST("reset-password")
    Call<AuthResponse> resetPassword(@Body ResetPasswordRequest request);

    /** Rate-limited server-side (see AccountReactivationService) - an Android countdown alone is never trusted. */
    @POST("reactivate-resend")
    Call<ApiMessage> reactivateResend(@Body ReactivateResendRequest request);

    /** Only reachable after login() returns reactivation_required - never a raw user id/email, only the opaque reactivation_token it issued. */
    @POST("reactivate-verify")
    Call<AuthResponse> reactivateVerify(@Body ReactivateVerifyRequest request);

    @GET("rooms")
    Call<RoomsResponse> getRooms(@QueryMap Map<String, String> filters);

    @GET("rooms/{id}")
    Call<RoomDetailResponse> getRoom(@Path("id") String id);

    /**
     * Active-only, admin-managed amenity catalog (Admin\AmenityManagementController).
     * Optional pricing_type=paid|free filters to Additional/Paid or Free/Included
     * amenities only; omit to get everything (landing page usage).
     */
    @GET("amenities")
    Call<List<AmenityDto>> getAmenities(@Query("pricing_type") String pricingType);

    /** Published, guest-audience announcements (Admin\AnnouncementManagementController, same rows the web side and this guest's Notifications share). */
    @GET("announcements")
    Call<List<AnnouncementDto>> getAnnouncements();

    /** Active, currently-in-date-range promotions (Admin\PromotionManagementController, same rows/query the web Home page uses). */
    @GET("promotions")
    Call<List<PromotionDto>> getPromotions();

    /** Active standing discounts (Admin\DiscountManagementController, same rows/query the web Home page's "Promotions & Discounts" section uses). */
    @GET("discounts")
    Call<List<DiscountDto>> getDiscounts();

    /**
     * per_page defaults to 15 server-side (Api\ReservationController::index()) - explicitly
     * requesting a high cap here so the dashboard/Transaction History always see this guest's
     * complete history in one page instead of silently only ever showing their 15 most recent.
     */
    @GET("guest/reservations")
    Call<PaginatedResponse<ReservationDto>> getReservations(@Query("per_page") int perPage);

    @POST("guest/reservations")
    Call<ReservationDto> createReservation(@Body ReservationRequest request);

    @GET("guest/reservations/{id}")
    Call<ReservationDto> getReservation(@Path("id") String id);

    @PUT("guest/reservations/{id}")
    Call<ReservationDto> updateReservation(@Path("id") String id, @Body ReservationUpdateRequest request);

    @PUT("guest/reservations/{id}/cancel")
    Call<ReservationDto> cancelReservation(@Path("id") String id);

    /** One-time Cash -> GCash payment-method switch; DB-enforced, never callable twice for the same reservation. */
    @PUT("guest/reservations/{id}/switch-to-gcash")
    Call<ReservationDto> switchReservationToGcash(@Path("id") String id);

    /** One-time GCash -> Cash payment-method switch; mirrors switch-to-gcash, same DB-enforced one-time lock. */
    @PUT("guest/reservations/{id}/switch-to-cash")
    Call<ReservationDto> switchReservationToCash(@Path("id") String id);

    /**
     * Real, permanent, non-recoverable deletion of this guest's own
     * Reservation row (and its owned child rows - reservation_rooms,
     * reservation_amenities, additional guests, exclusively-owned
     * payments/receipts, transaction-scoped notifications) - not a soft
     * hide. Only Cancelled/Rejected transactions are eligible; the server
     * must independently re-validate ownership + status rather than
     * trusting the client (see TRANSACTION_PERMANENT_DELETE_BACKEND_SPEC.md).
     * Must never cascade into a linked, still-valid converted Booking row.
     */
    @DELETE("guest/reservations/{id}")
    Call<ApiMessage> deleteReservationPermanently(@Path("id") String id);

    @POST("guest/reservations/{id}/payments")
    Call<PaymentSubmitResponse> submitPayment(@Path("id") String id, @Body PaymentRequest request);

    /** GCash-only: same endpoint as submitPayment, multipart so the receipt image and registered GCash number reach the server for real. */
    @Multipart
    @POST("guest/reservations/{id}/payments")
    Call<PaymentSubmitResponse> submitGcashPayment(@Path("id") String id,
                                                     @Part("payment_method") okhttp3.RequestBody paymentMethod,
                                                     @Part("payment_type") okhttp3.RequestBody paymentType,
                                                     @Part("reference_number") okhttp3.RequestBody referenceNumber,
                                                     @Part("amount_paid") okhttp3.RequestBody amountPaid,
                                                     @Part("gcash_number") okhttp3.RequestBody gcashNumber,
                                                     @Part("selected_payment_percentage") okhttp3.RequestBody selectedPaymentPercentage,
                                                     @Part MultipartBody.Part receipt);

    // ---- Direct Booking ("New Booking") - a genuinely independent
    // transaction, never derived from a Reservation. See Api\BookingController
    // on the server; contrast with the createReservation()/updateReservation()
    // family above, which remains the unchanged "New Reservation" path.

    @GET("guest/bookings")
    Call<PaginatedResponse<DirectBookingResponseDto>> getDirectBookings(@Query("per_page") int perPage);

    /**
     * Payment is submitted as part of this same request (fields + optional
     * idCardImage/receipt file parts) - no Booking row exists on the server
     * until this call succeeds, matching the "pay first, booking created only
     * after" requirement. Either file part may be null (Retrofit omits a null
     * @Part from the multipart body); id_card_image is only required
     * server-side when id_card_type is Senior Citizen/PWD, receipt only when
     * payment_method is gcash.
     */
    @Multipart
    @POST("guest/bookings")
    Call<DirectBookingResponseDto> createDirectBooking(@PartMap Map<String, okhttp3.RequestBody> fields,
                                                         @Part MultipartBody.Part idCardImage,
                                                         @Part MultipartBody.Part receipt);

    /** A direct Booking has no parent Reservation row, so it needs its own cancel endpoint - cancelReservation() below only ever resolves against the reservations table. */
    @PUT("guest/bookings/{id}/cancel")
    Call<DirectBookingResponseDto> cancelDirectBooking(@Path("id") String id);

    /**
     * Real, permanent, non-recoverable deletion of this guest's own direct
     * Booking row (and its owned child rows - booking_rooms,
     * booking_amenities, additional guests, exclusively-owned
     * payments/receipts, transaction-scoped notifications) - not a soft
     * hide. A direct Booking has no parent Reservation row, so it needs its
     * own endpoint - deleteReservationPermanently() above only ever
     * resolves against the reservations table. Only Cancelled/Rejected/
     * Completed transactions are eligible; the server must independently
     * re-validate ownership + status (see
     * TRANSACTION_PERMANENT_DELETE_BACKEND_SPEC.md).
     */
    @DELETE("guest/bookings/{id}")
    Call<ApiMessage> deleteBookingPermanently(@Path("id") String id);

    /** Cancels a still-pending (not yet verified) payment and its parent reservation/booking. */
    @PUT("guest/payments/{id}/cancel")
    Call<ApiMessage> cancelPayment(@Path("id") String id);

    /** "Convert to Reservation": voids this in-progress payment attempt only, leaving the room held as a plain unpaid reservation. */
    @PUT("guest/payments/{id}/void")
    Call<ApiMessage> voidPayment(@Path("id") String id);

    @Multipart
    @POST("guest/reservations/{id}/id-card")
    Call<ApiMessage> uploadIdCard(@Path("id") String id, @Part MultipartBody.Part idCard);

    @GET("guest/reservations/{id}/id-card")
    Call<ResponseBody> getIdCard(@Path("id") String id);

    /** The Paid/Additional amenities originally selected for this reservation, with how much has already been requested since (Api\AmenityRequestController::requestable()). */
    @GET("guest/reservations/{id}/amenities/requestable")
    Call<List<RequestableAmenityDto>> getRequestableAmenities(@Path("id") String id);

    /** This reservation's own Additional Amenity Request history, most recent first. */
    @GET("guest/reservations/{id}/amenities/requests")
    Call<List<AmenityRequestDto>> getAmenityRequests(@Path("id") String id);

    /** Rejected (422) server-side unless amenity_id was already selected in this reservation's original booking - see ReservationAmenity. */
    @POST("guest/reservations/{id}/amenities/requests")
    Call<AmenityRequestDto> submitAmenityRequest(@Path("id") String id, @Body AmenityRequestSubmitRequest request);

    /**
     * Guest-level payment ledger across every transaction (Api\ProfileController::payments()) -
     * already declared here for a while but not yet wired into any
     * screen/repository method. Prefer a specific Booking/Reservation's own
     * payment_transactions (richer - includes running totals and receipt
     * linkage) for a single transaction's detail view; this is for a
     * guest-wide "all my payments" ledger, if/when one is built - see
     * RoomRepository#refreshGuestPayments().
     */
    @GET("guest/payments")
    Call<PaymentsResponse> getPayments();

    /**
     * Authorization-protected lookup by receipt_number (PR-.../FR-.../OR-...) -
     * Api\ReceiptController::show()/ReceiptService::findReceiptPayload()
     * (backend). A PURE LOOKUP: never generates/mints a missing receipt,
     * returns 404 for an unknown, not-yet-available, or not-owned-by-this-
     * guest receipt_number, indistinguishably (see PAYMENT_RECEIPT_HISTORY_BACKEND_SPEC.md
     * §19). Not yet deployed to production as of this Android integration
     * pass - do not call expecting it to exist until the backend branch
     * ships and the receipt_number migration has run.
     */
    @GET("guest/receipts/{receiptNumber}")
    Call<ReceiptDetailResponse> getReceipt(@Path("receiptNumber") String receiptNumber);

    @GET("guest/profile")
    Call<ProfileResponse> getProfile();

    @PUT("guest/profile")
    Call<ProfileResponse> updateProfile(@Body ProfileUpdateRequest request);

    @Multipart
    @POST("guest/profile/picture")
    Call<ProfileResponse> updateProfilePicture(@Part MultipartBody.Part profilePicture);

    // Password change is OTP-emailed only (reuses forgotPassword()/resetPassword()
    // below, same as the website) - there is no current-password-gated endpoint;
    // the server no longer exposes PUT guest/profile/password either.

    /** Replaces the old permanent-sounding deleteAccount() - reversible, see Api\ProfileController::deactivateAccount(). */
    @POST("guest/account/deactivate")
    Call<ApiMessage> deactivateAccount(@Body DeactivateAccountRequest request);

    /** Unrelated legacy mechanism (still used by the web guest portal's own Delete Account feature) - kept for any pre-existing pending-deletion account. */
    @POST("guest/account/restore")
    Call<ApiMessage> restoreAccount();

    /** per_page defaults to 20 server-side (Api\NotificationController::index()) - see getReservations(int) above for why a high explicit cap is requested here too. */
    @GET("notifications")
    Call<PaginatedResponse<NotificationDto>> getNotifications(@Query("per_page") int perPage);

    @PUT("notifications/{id}/read")
    Call<NotificationDto> markNotificationRead(@Path("id") String id);

    @PUT("notifications/read-all")
    Call<ApiMessage> markAllNotificationsRead();
}
