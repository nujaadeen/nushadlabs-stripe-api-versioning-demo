package com.demo.versioning.transformer;

import com.demo.versioning.version.ApiVersion;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class V2022To2020ResponseTransformer implements ResponseTransformer {

    @Override
    public ApiVersion fromVersion() {
        return ApiVersion.V_2022_06_15;
    }

    @Override
    public ApiVersion toVersion() {
        return ApiVersion.V_2020_01_01;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> transform(Map<String, Object> body) {
        Map<String, Object> out = new LinkedHashMap<>(body);

        Object paymentId = out.remove("paymentId");
        if (paymentId != null) {
            out.put("payment_id", paymentId);
        }

        Object amount = out.remove("amount");
        if (amount != null) {
            long cents = ((BigDecimal) amount).multiply(BigDecimal.valueOf(100)).longValue();
            out.put("amount_cents", cents);
        }

        out.remove("currency");

        Map<String, Object> billing = (Map<String, Object>) out.remove("billing");
        if (billing == null) {
            billing = Map.of();
        }
        putIfPresent(out, "billing_name",    billing.get("name"));
        putIfPresent(out, "billing_email",   billing.get("email"));
        putIfPresent(out, "billing_address", billing.get("address"));

        return out;
    }

    private static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
