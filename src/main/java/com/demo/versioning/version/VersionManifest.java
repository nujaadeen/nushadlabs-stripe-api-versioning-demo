package com.demo.versioning.version;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

@Component
public class VersionManifest {

    private final Map<ApiVersion, Set<String>> gates = new EnumMap<>(ApiVersion.class);

    public VersionManifest() {
        gates.put(ApiVersion.V_2020_01_01, Set.of("flat_billing", "legacy_amount_field"));
        gates.put(ApiVersion.V_2022_06_15, Set.of("flat_billing", "nested_billing_preview"));
        gates.put(ApiVersion.V_2024_03_10, Set.of("nested_billing", "multi_currency"));
    }

    public boolean isGateActive(ApiVersion version, String gate) {
        Set<String> activeGates = gates.get(version);
        return activeGates != null && activeGates.contains(gate);
    }
}
