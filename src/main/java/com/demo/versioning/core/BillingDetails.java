package com.demo.versioning.core;

public record BillingDetails(
        String name,
        String email,
        String addressLine1,
        String city,
        String country
) {}
