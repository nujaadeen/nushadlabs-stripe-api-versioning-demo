package com.demo.versioning;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentVersioningIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // -------------------------------------------------------------------------
    // Fixtures — each shaped for its version
    // -------------------------------------------------------------------------

    /** 2020-shaped request: flat snake_case fields, no currency. */
    private static final String BODY_2020 = """
            {
              "customer_id": "cust_abc123",
              "amount": 49.99,
              "billing_name": "Jane Doe",
              "billing_email": "jane@example.com",
              "billing_address": "123 Main St",
              "billing_city": "New York",
              "billing_country": "US"
            }
            """;

    /** 2022-shaped request: camelCase id, nested billing map. */
    private static final String BODY_2022 = """
            {
              "customerId": "cust_abc123",
              "amount": 49.99,
              "currency": "USD",
              "billing": {
                "name": "Jane Doe",
                "email": "jane@example.com",
                "address": "123 Main St"
              }
            }
            """;

    /** 2024-shaped request: camelCase id, nested billingDetails, currency. */
    private static final String BODY_2024 = """
            {
              "customerId": "cust_abc123",
              "amount": 49.99,
              "currency": "USD",
              "billingDetails": {
                "name": "Jane Doe",
                "email": "jane@example.com",
                "addressLine1": "123 Main St",
                "city": "New York",
                "country": "US"
              }
            }
            """;

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void whenNoVersionHeader_usesDefaultVersion() throws Exception {
        // Default is 2020-01-01: both gate and both transformers run.
        // Response: flat billing_*, payment_id, amount_cents, no currency.
        mockMvc.perform(post("/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_2020))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billing_name").value("Jane Doe"))
                .andExpect(jsonPath("$.billing_email").value("jane@example.com"))
                .andExpect(jsonPath("$.billing_address").value("123 Main St"))
                .andExpect(jsonPath("$.billingDetails").doesNotExist())
                .andExpect(jsonPath("$.billing").doesNotExist())
                .andExpect(jsonPath("$.payment_id").exists())
                .andExpect(jsonPath("$.paymentId").doesNotExist())
                .andExpect(jsonPath("$.amount_cents").value(4999))
                .andExpect(jsonPath("$.amount").doesNotExist())
                .andExpect(jsonPath("$.currency").doesNotExist());
    }

    @Test
    void whenVersion2020_responseIsFlattened() throws Exception {
        mockMvc.perform(post("/v1/payments")
                        .header("Stripe-Api-Version", "2020-01-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_2020))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billing_name").value("Jane Doe"))
                .andExpect(jsonPath("$.billing_email").value("jane@example.com"))
                .andExpect(jsonPath("$.billing_address").value("123 Main St"))
                .andExpect(jsonPath("$.billingDetails").doesNotExist())
                .andExpect(jsonPath("$.billing").doesNotExist())
                .andExpect(jsonPath("$.payment_id").exists())
                .andExpect(jsonPath("$.paymentId").doesNotExist())
                .andExpect(jsonPath("$.amount_cents").value(4999))
                .andExpect(jsonPath("$.amount").doesNotExist())
                .andExpect(jsonPath("$.currency").doesNotExist());
    }

    @Test
    void whenVersion2022_responseHasNestedBillingMap() throws Exception {
        // Only V2024To2022 transformer runs.
        // Response: nested billing map, paymentId (not renamed), amount as decimal, currency present.
        mockMvc.perform(post("/v1/payments")
                        .header("Stripe-Api-Version", "2022-06-15")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_2022))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billing.name").value("Jane Doe"))
                .andExpect(jsonPath("$.billing.email").value("jane@example.com"))
                .andExpect(jsonPath("$.billing.address").value("123 Main St"))
                .andExpect(jsonPath("$.billingDetails").doesNotExist())
                .andExpect(jsonPath("$.paymentId").exists())
                .andExpect(jsonPath("$.payment_id").doesNotExist())
                .andExpect(jsonPath("$.amount").exists())
                .andExpect(jsonPath("$.amount_cents").doesNotExist())
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void whenLatestVersion_responseIsModernSchema() throws Exception {
        // No transformers run. Response: full billingDetails, currency, paymentId.
        mockMvc.perform(post("/v1/payments")
                        .header("Stripe-Api-Version", "2024-03-10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_2024))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billingDetails.name").value("Jane Doe"))
                .andExpect(jsonPath("$.billingDetails.email").value("jane@example.com"))
                .andExpect(jsonPath("$.billingDetails.addressLine1").value("123 Main St"))
                .andExpect(jsonPath("$.billingDetails.city").value("New York"))
                .andExpect(jsonPath("$.billingDetails.country").value("US"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.paymentId").exists())
                .andExpect(jsonPath("$.payment_id").doesNotExist())
                .andExpect(jsonPath("$.amount").exists())
                .andExpect(jsonPath("$.amount_cents").doesNotExist());
    }

    @Test
    void whenInvalidVersionHeader_returns400() throws Exception {
        mockMvc.perform(post("/v1/payments")
                        .header("Stripe-Api-Version", "not-a-date")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_2024))
                .andExpect(status().isBadRequest());
    }

    @Test
    void whenUnknownFieldSentToNewVersion_isIgnored() throws Exception {
        // Unknown/legacy fields are silently ignored by the gate chain.
        String bodyWithUnknownField = """
                {
                  "customerId": "cust_abc123",
                  "amount": 49.99,
                  "currency": "USD",
                  "legacy_amount": 4999,
                  "billingDetails": {
                    "name": "Jane Doe",
                    "email": "jane@example.com",
                    "addressLine1": "123 Main St",
                    "city": "New York",
                    "country": "US"
                  }
                }
                """;
        mockMvc.perform(post("/v1/payments")
                        .header("Stripe-Api-Version", "2024-03-10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithUnknownField))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billingDetails.name").value("Jane Doe"));
    }
}
