package com.demo.versioning;

import com.demo.versioning.gate.RequestGateChain;
import com.demo.versioning.gate.V2020To2022RequestGate;
import com.demo.versioning.gate.V2022To2024RequestGate;
import com.demo.versioning.version.ApiVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.demo.versioning.version.ApiVersion.*;
import static org.assertj.core.api.Assertions.assertThat;

class RequestGateChainTest {

    private RequestGateChain chain;

    @BeforeEach
    void setUp() {
        // Wire in reverse order to verify the chain sorts them correctly.
        chain = new RequestGateChain(List.of(new V2022To2024RequestGate(), new V2020To2022RequestGate()));
        chain.sortGates();
    }

    /** 2020-shaped input: flat billing_* fields, snake_case customer_id. */
    private Map<String, Object> input2020() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("customer_id",     "cus_abc");
        m.put("amount",          new BigDecimal("25.00"));
        m.put("billing_name",    "Alice");
        m.put("billing_email",   "alice@example.com");
        m.put("billing_address", "1 Elm St");
        m.put("billing_city",    "Shelbyville");
        m.put("billing_country", "US");
        return m;
    }

    /** 2022-shaped input: nested billing map, camelCase customerId. */
    private Map<String, Object> input2022() {
        Map<String, Object> billing = new LinkedHashMap<>();
        billing.put("name",    "Alice");
        billing.put("email",   "alice@example.com");
        billing.put("address", "1 Elm St");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("customerId", "cus_abc");
        m.put("amount",     new BigDecimal("25.00"));
        m.put("billing",    billing);
        return m;
    }

    /** 2024-shaped input: billingDetails map already present. */
    private Map<String, Object> input2024() {
        Map<String, Object> bd = new LinkedHashMap<>();
        bd.put("name",         "Alice");
        bd.put("email",        "alice@example.com");
        bd.put("addressLine1", "1 Elm St");
        bd.put("city",         "");
        bd.put("country",      "");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("customerId",     "cus_abc");
        m.put("amount",         new BigDecimal("25.00"));
        m.put("billingDetails", bd);
        return m;
    }

    @Test
    @SuppressWarnings("unchecked")
    void caller2020_bothGatesRun() {
        Map<String, Object> result = chain.open(input2020(), V_2020_01_01);

        // First gate ran: customer_id → customerId, flat billing_* removed
        assertThat(result).doesNotContainKey("customer_id");
        assertThat(result).containsKey("customerId");

        // Second gate ran: billing → billingDetails
        assertThat(result).doesNotContainKey("billing");
        assertThat(result).containsKey("billingDetails");

        Map<String, Object> bd = (Map<String, Object>) result.get("billingDetails");
        assertThat(bd)
                .containsEntry("name",         "Alice")
                .containsEntry("email",        "alice@example.com")
                .containsEntry("addressLine1", "1 Elm St");
    }

    @Test
    @SuppressWarnings("unchecked")
    void caller2022_onlySecondGateRuns() {
        Map<String, Object> result = chain.open(input2022(), V_2022_06_15);

        // First gate skipped: customerId key must still be camelCase (unchanged)
        assertThat(result).doesNotContainKey("customer_id");
        assertThat(result).containsKey("customerId");

        // Second gate ran: billing → billingDetails
        assertThat(result).doesNotContainKey("billing");
        assertThat(result).containsKey("billingDetails");

        Map<String, Object> bd = (Map<String, Object>) result.get("billingDetails");
        assertThat(bd).containsEntry("addressLine1", "1 Elm St");
    }

    @Test
    void caller2024_noGatesRun() {
        Map<String, Object> input = input2024();
        Map<String, Object> result = chain.open(input, V_2024_03_10);

        assertThat(result).isEqualTo(input);
        assertThat(result).containsKey("billingDetails");
        assertThat(result).doesNotContainKey("billing");
    }
}
