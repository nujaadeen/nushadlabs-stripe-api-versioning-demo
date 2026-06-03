package com.demo.versioning.transformer;

import com.demo.versioning.version.ApiVersion;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class V2024To2022ResponseTransformer implements ResponseTransformer {

    @Override
    public ApiVersion fromVersion() {
        return ApiVersion.V_2024_03_10;
    }

    @Override
    public ApiVersion toVersion() {
        return ApiVersion.V_2022_06_15;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> transform(Map<String, Object> body) {
        Map<String, Object> out = new LinkedHashMap<>(body);

        Map<String, Object> bd = (Map<String, Object>) out.remove("billingDetails");
        if (bd == null) {
            bd = Map.of();
        }

        Map<String, Object> billing = new LinkedHashMap<>();
        billing.put("name",    bd.getOrDefault("name",         ""));
        billing.put("email",   bd.getOrDefault("email",        ""));
        billing.put("address", bd.getOrDefault("addressLine1", ""));

        out.put("billing", billing);

        return out;
    }
}
