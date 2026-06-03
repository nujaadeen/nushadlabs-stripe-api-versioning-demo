package com.demo.versioning;

import com.demo.versioning.core.BillingDetails;
import com.demo.versioning.core.PaymentResponse;
import com.demo.versioning.transformer.ResponseTransformerChain;
import com.demo.versioning.transformer.V2022To2020ResponseTransformer;
import com.demo.versioning.transformer.V2024To2022ResponseTransformer;
import com.demo.versioning.version.ApiVersion;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.demo.versioning.version.ApiVersion.*;
import static org.assertj.core.api.Assertions.assertThat;

class ResponseTransformerChainTest {

    private ResponseTransformerChain chain;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

        // Wire in reverse order to verify sortTransformers() corrects it.
        chain = new ResponseTransformerChain(
                List.of(new V2022To2020ResponseTransformer(), new V2024To2022ResponseTransformer()),
                mapper
        );
        chain.sortTransformers();
    }

    private PaymentResponse sampleResponse() {
        return PaymentResponse.builder()
                .paymentId("pay_001")
                .amount(new BigDecimal("49.99"))
                .currency("USD")
                .status("succeeded")
                .createdAt(Instant.parse("2024-05-01T12:00:00Z"))
                .billingDetails(new BillingDetails("Dana Lee", "dana@example.com", "5 River Ln", "Portland", "US"))
                .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void target2020_bothTransformersRun() {
        Map<String, Object> result = chain.transform(sampleResponse(), V_2020_01_01);

        // V2024→2022 then V2022→2020 both applied
        assertThat(result).doesNotContainKey("billingDetails");
        assertThat(result).doesNotContainKey("billing");
        assertThat(result)
                .containsEntry("billing_name",    "Dana Lee")
                .containsEntry("billing_email",   "dana@example.com")
                .containsEntry("billing_address", "5 River Ln");

        assertThat(result).doesNotContainKey("paymentId");
        assertThat(result).containsEntry("payment_id", "pay_001");

        assertThat(result).doesNotContainKey("amount");
        assertThat(result).containsEntry("amount_cents", 4999L);

        assertThat(result).doesNotContainKey("currency");
    }

    @Test
    @SuppressWarnings("unchecked")
    void target2022_onlyV2024To2022Runs() {
        Map<String, Object> result = chain.transform(sampleResponse(), V_2022_06_15);

        // V2024→2022 applied: billingDetails → billing
        assertThat(result).doesNotContainKey("billingDetails");
        assertThat(result).containsKey("billing");
        Map<String, Object> billing = (Map<String, Object>) result.get("billing");
        assertThat(billing)
                .containsEntry("name",    "Dana Lee")
                .containsEntry("email",   "dana@example.com")
                .containsEntry("address", "5 River Ln");

        // V2022→2020 skipped: paymentId and amount untouched, currency present
        assertThat(result).containsEntry("paymentId", "pay_001");
        assertThat(result).doesNotContainKey("payment_id");
        assertThat(result).containsKey("amount");
        assertThat(result).doesNotContainKey("amount_cents");
        assertThat(result).containsEntry("currency", "USD");
    }

    @Test
    void target2024_noTransformersRun() {
        Map<String, Object> result = chain.transform(sampleResponse(), V_2024_03_10);

        // No transformation: modern shape preserved
        assertThat(result).containsKey("billingDetails");
        assertThat(result).doesNotContainKey("billing");
        assertThat(result).containsEntry("paymentId", "pay_001");
        assertThat(result).containsKey("amount");
        assertThat(result).doesNotContainKey("amount_cents");
        assertThat(result).containsEntry("currency", "USD");
    }
}
