package com.demo.versioning.gate;

import com.demo.versioning.version.ApiVersion;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class V2020To2022RequestGate implements RequestGate {

    @Override
    public ApiVersion fromVersion() {
        return ApiVersion.V_2020_01_01;
    }

    @Override
    public ApiVersion toVersion() {
        return ApiVersion.V_2022_06_15;
    }

    @Override
    public Map<String, Object> open(Map<String, Object> body) {
        Map<String, Object> out = new LinkedHashMap<>(body);

        Object customerId = out.remove("customer_id");
        if (customerId != null) {
            out.put("customerId", customerId);
        }

        Map<String, Object> billing = new LinkedHashMap<>();
        putIfPresent(billing, "name",    out.remove("billing_name"));
        putIfPresent(billing, "email",   out.remove("billing_email"));
        putIfPresent(billing, "address", out.remove("billing_address"));
        out.remove("billing_city");
        out.remove("billing_country");

        out.put("billing", billing);

        return out;
    }

    private static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
