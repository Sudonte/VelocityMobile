package com.example.velocitysuites.network.dto;

/**
 * The literal top-level JSON envelope GET guest/receipts/{receiptNumber}
 * returns - {@code {"receipt": {...}}} (Api\ReceiptController::show()) - a
 * thin wrapper so Retrofit/Gson deserialize straight off the real response
 * shape instead of assuming the receipt object is the top-level body.
 */
public class ReceiptDetailResponse {
    public ReceiptDetailDto receipt;
}
