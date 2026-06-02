package com.demo.versioning.response;

import com.demo.versioning.version.ApiVersion;
import com.demo.versioning.version.VersionManifest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Order(2)
@RequiredArgsConstructor
public class FlattenBillingTransformer implements ResponseTransformer {

    private final VersionManifest versionManifest;

    @Override
    public Map<String, Object> transform(Map<String, Object> response, ApiVersion targetVersion) {
        if (!versionManifest.isGateActive(targetVersion, "flat_billing")) {
            return response;
        }

        Object raw = response.get("billingDetails");
        if (!(raw instanceof Map<?, ?> billing)) {
            return response;
        }

        response.put("billing_name",    billing.get("name"));
        response.put("billing_email",   billing.get("email"));
        response.put("billing_address", billing.get("addressLine1"));
        response.put("billing_city",    billing.get("city"));
        response.put("billing_country", billing.get("country"));
        response.remove("billingDetails");

        return response;
    }
}
