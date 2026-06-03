package com.demo.versioning;

import com.demo.versioning.core.PaymentRequest;
import com.demo.versioning.core.PaymentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentVersioningIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @SpyBean
    private PaymentService paymentService;

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

    /** 2022-shaped request: camelCase id, nested billing map, currency. */
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

    @Test
    void whenVersion2020_bothGatesRun_bothTransformersRun() throws Exception {
        mockMvc.perform(post("/v1/payments")
                        .header("Stripe-Api-Version", "2020-01-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_2020))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment_id").exists())
                .andExpect(jsonPath("$.amount_cents").value(4999))
                .andExpect(jsonPath("$.billing_name").value("Jane Doe"))
                .andExpect(jsonPath("$.billing_email").value("jane@example.com"))
                .andExpect(jsonPath("$.billing_address").value("123 Main St"))
                .andExpect(jsonPath("$.billingDetails").doesNotExist())
                .andExpect(jsonPath("$.billing").doesNotExist())
                .andExpect(jsonPath("$.currency").doesNotExist());
    }

    @Test
    void whenVersion2022_onlySecondGateRuns_onlyFirstTransformerRuns() throws Exception {
        mockMvc.perform(post("/v1/payments")
                        .header("Stripe-Api-Version", "2022-06-15")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_2022))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").exists())
                .andExpect(jsonPath("$.amount").exists())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.billing.name").value("Jane Doe"))
                .andExpect(jsonPath("$.billing.email").value("jane@example.com"))
                .andExpect(jsonPath("$.billing.address").value("123 Main St"))
                .andExpect(jsonPath("$.billingDetails").doesNotExist())
                .andExpect(jsonPath("$.payment_id").doesNotExist())
                .andExpect(jsonPath("$.amount_cents").doesNotExist());
    }

    @Test
    void whenVersion2024_noGatesRun_noTransformersRun() throws Exception {
        mockMvc.perform(post("/v1/payments")
                        .header("Stripe-Api-Version", "2024-03-10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_2024))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").exists())
                .andExpect(jsonPath("$.amount").exists())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.billingDetails.name").value("Jane Doe"))
                .andExpect(jsonPath("$.billingDetails.email").value("jane@example.com"))
                .andExpect(jsonPath("$.billingDetails.addressLine1").value("123 Main St"))
                .andExpect(jsonPath("$.billingDetails.city").value("New York"))
                .andExpect(jsonPath("$.billingDetails.country").value("US"))
                .andExpect(jsonPath("$.billing").doesNotExist())
                .andExpect(jsonPath("$.payment_id").doesNotExist())
                .andExpect(jsonPath("$.amount_cents").doesNotExist());
    }

    @Test
    void whenNoVersionHeader_defaultsTo2020AndFullChainRuns() throws Exception {
        mockMvc.perform(post("/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_2020))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment_id").exists())
                .andExpect(jsonPath("$.amount_cents").value(4999))
                .andExpect(jsonPath("$.billing_name").value("Jane Doe"))
                .andExpect(jsonPath("$.billing_email").value("jane@example.com"))
                .andExpect(jsonPath("$.billing_address").value("123 Main St"))
                .andExpect(jsonPath("$.billingDetails").doesNotExist())
                .andExpect(jsonPath("$.billing").doesNotExist())
                .andExpect(jsonPath("$.currency").doesNotExist());
    }

    @Test
    void whenInvalidVersionHeader_returns400() throws Exception {
        mockMvc.perform(post("/v1/payments")
                        .header("Stripe-Api-Version", "not-a-date")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_2024))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_API_VERSION"))
                .andExpect(jsonPath("$.error.version").value("unknown"));
    }

    @Test
    void coreAlwaysReceivesModernSchema() throws Exception {
        mockMvc.perform(post("/v1/payments")
                        .header("Stripe-Api-Version", "2020-01-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_2020))
                .andExpect(status().isOk());

        ArgumentCaptor<PaymentRequest> captor = ArgumentCaptor.forClass(PaymentRequest.class);
        verify(paymentService).process(captor.capture());
        PaymentRequest captured = captor.getValue();

        assertThat(captured.customerId()).isEqualTo("cust_abc123");
        assertThat(captured.billingDetails()).isNotNull();
        assertThat(captured.billingDetails().name()).isEqualTo("Jane Doe");
    }
}
