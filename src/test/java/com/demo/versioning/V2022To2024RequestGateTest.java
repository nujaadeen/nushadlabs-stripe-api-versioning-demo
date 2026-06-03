package com.demo.versioning;

import com.demo.versioning.gate.V2022To2024RequestGate;
import com.demo.versioning.version.ApiVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class V2022To2024RequestGateTest {

    private V2022To2024RequestGate gate;
    private Map<String, Object> result;

    @BeforeEach
    void setUp() {
        gate = new V2022To2024RequestGate();

        Map<String, Object> billing = new LinkedHashMap<>();
        billing.put("name",    "Jane Doe");
        billing.put("email",   "jane@example.com");
        billing.put("address", "123 Main St");

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("customerId", "cus_123");
        input.put("amount",     new BigDecimal("49.99"));
        input.put("billing",    billing);

        result = gate.open(input);
    }

    @Test
    void versionRange() {
        assertThat(gate.fromVersion()).isEqualTo(ApiVersion.V_2022_06_15);
        assertThat(gate.toVersion()).isEqualTo(ApiVersion.V_2024_03_10);
    }

    @Test
    void oldBillingKeyIsRemoved() {
        assertThat(result).doesNotContainKey("billing");
    }

    @Test
    @SuppressWarnings("unchecked")
    void billingDetailsIsMapped() {
        assertThat(result).containsKey("billingDetails");
        Map<String, Object> bd = (Map<String, Object>) result.get("billingDetails");
        assertThat(bd)
                .containsEntry("name",         "Jane Doe")
                .containsEntry("email",        "jane@example.com")
                .containsEntry("addressLine1", "123 Main St");
    }

    @Test
    @SuppressWarnings("unchecked")
    void cityAndCountryDefaultToEmptyString() {
        Map<String, Object> bd = (Map<String, Object>) result.get("billingDetails");
        assertThat(bd)
                .containsEntry("city",    "")
                .containsEntry("country", "");
    }

    @Test
    void passthroughFieldsAreUnchanged() {
        assertThat(result)
                .containsEntry("customerId", "cus_123")
                .containsEntry("amount",     new BigDecimal("49.99"));
    }
}
