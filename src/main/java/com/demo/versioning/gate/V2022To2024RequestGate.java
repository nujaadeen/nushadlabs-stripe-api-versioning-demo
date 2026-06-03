package com.demo.versioning.gate;

import com.demo.versioning.version.ApiVersion;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class V2022To2024RequestGate implements RequestGate {

    @Override
    public ApiVersion fromVersion() {
        return ApiVersion.V_2022_06_15;
    }

    @Override
    public ApiVersion toVersion() {
        return ApiVersion.V_2024_03_10;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> open(Map<String, Object> body) {
        Map<String, Object> out = new LinkedHashMap<>(body);

        Map<String, Object> billing = (Map<String, Object>) out.remove("billing");
        if (billing == null) {
            billing = Map.of();
        }

        Map<String, Object> billingDetails = new LinkedHashMap<>();
        billingDetails.put("name",         billing.getOrDefault("name",    ""));
        billingDetails.put("email",        billing.getOrDefault("email",   ""));
        billingDetails.put("addressLine1", billing.getOrDefault("address", ""));
        billingDetails.put("city",         billing.getOrDefault("city",    ""));
        billingDetails.put("country",      billing.getOrDefault("country", ""));

        out.put("billingDetails", billingDetails);

        return out;
    }
}
