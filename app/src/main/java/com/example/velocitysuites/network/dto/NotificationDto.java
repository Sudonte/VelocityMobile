package com.example.velocitysuites.network.dto;

import java.util.List;

public class NotificationDto {
    public long id;
    public long user_id;
    public String title;
    public String message;
    public String category;
    public Long reference_id;
    public List<String> target_audience;
    /** Nullable - only a payment-verification/checkout-completion notification carries one (see PAYMENT_RECEIPT_HISTORY_BACKEND_SPEC.md Phase 5 §1). Always null for a notification created before this metadata existed. */
    public String receipt_number;
    /** Nullable - PARTIAL_RECEIPT/FULL_PAYMENT_RECEIPT/OFFICIAL_RECEIPT, always paired with a non-null receipt_number. */
    public String receipt_type;
    public boolean is_read;
    public String created_at;
}
