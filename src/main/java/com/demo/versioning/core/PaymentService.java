package com.demo.versioning.core;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentService {

    public PaymentResponse process(PaymentRequest request) {
        return PaymentResponse.builder()
                .paymentId(UUID.randomUUID().toString())
                .amount(request.amount())
                .currency(request.currency())
                .billingDetails(request.billingDetails())
                .status("succeeded")
                .createdAt(Instant.now())
                .build();
    }
}
