package com.demo.versioning.core;

import java.math.BigDecimal;

public record PaymentRequest(
        String customerId,
        BigDecimal amount,
        String currency,
        BillingDetails billingDetails
) {}
