package com.demo.versioning;

import com.demo.versioning.transformer.V2024To2022ResponseTransformer;
import com.demo.versioning.version.ApiVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class V2024To2022ResponseTransformerTest {

    private V2024To2022ResponseTransformer transformer;
    private Map<String, Object> result;

    @BeforeEach
    void setUp() {
        transformer = new V2024To2022ResponseTransformer();

        Map<String, Object> bd = new LinkedHashMap<>();
        bd.put("name",         "Bob Smith");
        bd.put("email",        "bob@example.com");
        bd.put("addressLine1", "99 Oak Ave");
        bd.put("city",         "Shelbyville");
        bd.put("country",      "US");

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("paymentId",      "pay_xyz");
        input.put("amount",         new BigDecimal("75.00"));
        input.put("currency",       "USD");
        input.put("status",         "succeeded");
        input.put("createdAt",      Instant.parse("2024-04-01T10:00:00Z"));
        input.put("billingDetails", bd);

        result = transformer.transform(input);
    }

    @Test
    void versionRange() {
        assertThat(transformer.fromVersion()).isEqualTo(ApiVersion.V_2024_03_10);
        assertThat(transformer.toVersion()).isEqualTo(ApiVersion.V_2022_06_15);
    }

    @Test
    void billingDetailsKeyIsRemoved() {
        assertThat(result).doesNotContainKey("billingDetails");
    }

    @Test
    @SuppressWarnings("unchecked")
    void billingKeyIsPresentWithCorrectFields() {
        assertThat(result).containsKey("billing");
        Map<String, Object> billing = (Map<String, Object>) result.get("billing");
        assertThat(billing)
                .containsEntry("name",    "Bob Smith")
                .containsEntry("email",   "bob@example.com")
                .containsEntry("address", "99 Oak Ave");
    }

    @Test
    @SuppressWarnings("unchecked")
    void cityAndCountryAreNotPresentAnywhere() {
        assertThat(result).doesNotContainKeys("city", "country");
        Map<String, Object> billing = (Map<String, Object>) result.get("billing");
        assertThat(billing).doesNotContainKeys("city", "country");
    }

    @Test
    void passthroughFieldsAreUnchanged() {
        assertThat(result)
                .containsEntry("paymentId", "pay_xyz")
                .containsEntry("amount",    new BigDecimal("75.00"))
                .containsEntry("currency",  "USD")
                .containsEntry("status",    "succeeded");
    }
}
