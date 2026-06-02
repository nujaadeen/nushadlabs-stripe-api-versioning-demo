package com.demo.versioning.response;

import com.demo.versioning.version.ApiVersion;
import com.demo.versioning.version.VersionManifest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
@Order(3)
@RequiredArgsConstructor
public class LegacyAmountTransformer implements ResponseTransformer {

    private final VersionManifest versionManifest;

    @Override
    public Map<String, Object> transform(Map<String, Object> response, ApiVersion targetVersion) {
        if (!versionManifest.isGateActive(targetVersion, "legacy_amount_field")) {
            return response;
        }

        Object raw = response.get("amount");
        if (raw instanceof BigDecimal amount) {
            response.put("legacy_amount", amount.multiply(BigDecimal.valueOf(100)).intValueExact());
        } else if (raw instanceof Number amount) {
            response.put("legacy_amount", (int) (amount.doubleValue() * 100));
        }

        return response;
    }
}
