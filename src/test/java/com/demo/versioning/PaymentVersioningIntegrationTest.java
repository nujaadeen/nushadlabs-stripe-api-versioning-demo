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
    // Fixtures
    // -------------------------------------------------------------------------

    private static final String VALID_BODY = """
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

    private static final String BODY_WITH_LEGACY_AMOUNT = """
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

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void whenNoVersionHeader_usesDefaultVersion() throws Exception {
        // Default is 2020-01-01 → gates: flat_billing, legacy_amount_field
        // Billing must be flattened; no nested billingDetails key
        mockMvc.perform(post("/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billing_name").value("Jane Doe"))
                .andExpect(jsonPath("$.billing_email").value("jane@example.com"))
                .andExpect(jsonPath("$.billing_address").value("123 Main St"))
                .andExpect(jsonPath("$.billing_city").value("New York"))
                .andExpect(jsonPath("$.billing_country").value("US"))
                .andExpect(jsonPath("$.billingDetails").doesNotExist());
    }

    @Test
    void whenVersion2020_responseIsFlattened() throws Exception {
        // Gates: flat_billing + legacy_amount_field; no multi_currency
        // Billing is flat, legacy_amount is injected (49.99 * 100 = 4999), currency is stripped
        mockMvc.perform(post("/v1/payments")
                        .header("Stripe-Api-Version", "2020-01-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billing_name").value("Jane Doe"))
                .andExpect(jsonPath("$.billing_email").value("jane@example.com"))
                .andExpect(jsonPath("$.billingDetails").doesNotExist())
                .andExpect(jsonPath("$.legacy_amount").value(4999))
                .andExpect(jsonPath("$.currency").doesNotExist());
    }

    @Test
    void whenVersion2022_responseHasNestedBillingPreview() throws Exception {
        // Gates: flat_billing + nested_billing_preview; no legacy_amount_field, no multi_currency
        // Billing is still flat (flat_billing active), but no legacy_amount added, no currency
        mockMvc.perform(post("/v1/payments")
                        .header("Stripe-Api-Version", "2022-06-15")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billing_name").value("Jane Doe"))
                .andExpect(jsonPath("$.billing_email").value("jane@example.com"))
                .andExpect(jsonPath("$.billingDetails").doesNotExist())
                .andExpect(jsonPath("$.legacy_amount").doesNotExist())
                .andExpect(jsonPath("$.currency").doesNotExist());
    }

    @Test
    void whenLatestVersion_responseIsModernSchema() throws Exception {
        // Gates: nested_billing + multi_currency; no flat_billing, no legacy_amount_field
        // Billing stays as nested object, currency is preserved, no legacy_amount
        mockMvc.perform(post("/v1/payments")
                        .header("Stripe-Api-Version", "2024-03-10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billingDetails.name").value("Jane Doe"))
                .andExpect(jsonPath("$.billingDetails.email").value("jane@example.com"))
                .andExpect(jsonPath("$.billingDetails.addressLine1").value("123 Main St"))
                .andExpect(jsonPath("$.billingDetails.city").value("New York"))
                .andExpect(jsonPath("$.billingDetails.country").value("US"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.legacy_amount").doesNotExist());
    }

    @Test
    void whenInvalidVersionHeader_returns400() throws Exception {
        mockMvc.perform(post("/v1/payments")
                        .header("Stripe-Api-Version", "not-a-date")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void whenLegacyAmountParamSentToNewVersion_returns400() throws Exception {
        // 2024-03-10 does not have the legacy_amount_field gate; sending the field is rejected
        mockMvc.perform(post("/v1/payments")
                        .header("Stripe-Api-Version", "2024-03-10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_WITH_LEGACY_AMOUNT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("legacy_amount field is not supported in your API version"));
    }
}
