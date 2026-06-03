package com.demo.versioning;

import com.demo.versioning.transformer.V2022To2020ResponseTransformer;
import com.demo.versioning.version.ApiVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class V2022To2020ResponseTransformerTest {

    private V2022To2020ResponseTransformer transformer;
    private Map<String, Object> result;

    @BeforeEach
    void setUp() {
        transformer = new V2022To2020ResponseTransformer();

        Map<String, Object> billing = new LinkedHashMap<>();
        billing.put("name",    "Carol White");
        billing.put("email",   "carol@example.com");
        billing.put("address", "7 Pine Rd");

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("paymentId", "pay_abc");
        input.put("amount",    new BigDecimal("99.99"));
        input.put("currency",  "USD");
        input.put("status",    "succeeded");
        input.put("createdAt", Instant.parse("2022-07-01T08:00:00Z"));
        input.put("billing",   billing);

        result = transformer.transform(input);
    }

    @Test
    void versionRange() {
        assertThat(transformer.fromVersion()).isEqualTo(ApiVersion.V_2022_06_15);
        assertThat(transformer.toVersion()).isEqualTo(ApiVersion.V_2020_01_01);
    }

    @Test
    void nestedBillingKeyIsRemoved() {
        assertThat(result).doesNotContainKey("billing");
    }

    @Test
    void billingFieldsAreFlattenedToTopLevel() {
        assertThat(result)
                .containsEntry("billing_name",    "Carol White")
                .containsEntry("billing_email",   "carol@example.com")
                .containsEntry("billing_address", "7 Pine Rd");
    }

    @Test
    void paymentIdIsRenamed() {
        assertThat(result).doesNotContainKey("paymentId");
        assertThat(result).containsEntry("payment_id", "pay_abc");
    }

    @Test
    void amountIsConvertedToCents() {
        assertThat(result).doesNotContainKey("amount");
        assertThat(result).containsEntry("amount_cents", 9999L);
    }

    @Test
    void currencyIsRemoved() {
        assertThat(result).doesNotContainKey("currency");
    }

    @Test
    void otherFieldsPassThrough() {
        assertThat(result)
                .containsEntry("status",    "succeeded")
                .containsEntry("createdAt", Instant.parse("2022-07-01T08:00:00Z"));
    }
}
