package com.example.velocitysuites.network.dto;

import java.util.ArrayList;
import java.util.List;

public class PaymentsResponse {
    public PaginatedResponse<PaymentDto> payments;
    public List<BillingDto> pending_bills = new ArrayList<>();
}
