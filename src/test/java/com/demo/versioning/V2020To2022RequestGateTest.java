package com.demo.versioning;

import com.demo.versioning.gate.V2020To2022RequestGate;
import com.demo.versioning.version.ApiVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class V2020To2022RequestGateTest {

    private V2020To2022RequestGate gate;
    private Map<String, Object> result;

    @BeforeEach
    void setUp() {
        gate = new V2020To2022RequestGate();

        Map<String, Object> input = Map.of(
                "customer_id",      "cus_123",
                "amount",           new BigDecimal("49.99"),
                "billing_name",     "Jane Doe",
                "billing_email",    "jane@example.com",
                "billing_address",  "123 Main St",
                "billing_city",     "Springfield",
                "billing_country",  "US"
        );

        result = gate.open(input);
    }

    @Test
    void versionRange() {
        assertThat(gate.fromVersion()).isEqualTo(ApiVersion.V_2020_01_01);
        assertThat(gate.toVersion()).isEqualTo(ApiVersion.V_2022_06_15);
    }

    @Test
    void customerIdIsRenamed() {
        assertThat(result).doesNotContainKey("customer_id");
        assertThat(result).containsEntry("customerId", "cus_123");
    }

    @Test
    void amountIsUnchanged() {
        assertThat(result).containsEntry("amount", new BigDecimal("49.99"));
    }

    @Test
    void flatBillingFieldsAreRemoved() {
        assertThat(result).doesNotContainKeys("billing_name", "billing_email", "billing_address");
    }

    @Test
    void billingCityAndCountryAreDropped() {
        assertThat(result).doesNotContainKeys("billing_city", "billing_country");
    }

    @Test
    @SuppressWarnings("unchecked")
    void nestedBillingMapIsPresent() {
        assertThat(result).containsKey("billing");
        Map<String, Object> billing = (Map<String, Object>) result.get("billing");
        assertThat(billing)
                .containsEntry("name",    "Jane Doe")
                .containsEntry("email",   "jane@example.com")
                .containsEntry("address", "123 Main St");
    }
}
