package com.demo.versioning.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
@AllArgsConstructor
public class PaymentResponse {

    private final String paymentId;
    private final BigDecimal amount;
    private final String currency;
    private final BillingDetails billingDetails;
    private final String status;
    private final Instant createdAt;
}
