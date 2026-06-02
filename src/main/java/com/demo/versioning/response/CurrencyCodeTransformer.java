package com.demo.versioning.response;

import com.demo.versioning.version.ApiVersion;
import com.demo.versioning.version.VersionManifest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Order(1)
@RequiredArgsConstructor
public class CurrencyCodeTransformer implements ResponseTransformer {

    private final VersionManifest versionManifest;

    @Override
    public Map<String, Object> transform(Map<String, Object> response, ApiVersion targetVersion) {
        if (!versionManifest.isGateActive(targetVersion, "multi_currency")) {
            response.remove("currency");
        }
        return response;
    }
}
