package com.demo.versioning.request;

import com.demo.versioning.version.VersionContext;
import com.demo.versioning.version.VersionManifest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class RequestCompatibilityInterceptor implements HandlerInterceptor {

    private static final String LEGACY_AMOUNT_PARAM = "legacy_amount";
    private static final String LEGACY_AMOUNT_GATE  = "legacy_amount_field";

    private final VersionManifest versionManifest;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {

        var version = VersionContext.get();
        log.info("API request: method={} uri={} version={}", request.getMethod(), request.getRequestURI(), version);

        boolean legacyAmountPresent = request.getParameter(LEGACY_AMOUNT_PARAM) != null
                || isJsonBodyWithLegacyAmount(request);

        if (legacyAmountPresent && !versionManifest.isGateActive(version, LEGACY_AMOUNT_GATE)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                    {"error": "legacy_amount field is not supported in your API version"}
                    """);
            return false;
        }

        return true;
    }

    /**
     * Peeks at a cached request body (set by CachedBodyRequestWrapper) for the
     * legacy_amount key without re-consuming the stream.
     */
    private boolean isJsonBodyWithLegacyAmount(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType == null || !contentType.contains(MediaType.APPLICATION_JSON_VALUE)) {
            return false;
        }
        Object cached = request.getAttribute(CachedBodyRequestWrapper.BODY_ATTRIBUTE);
        if (cached instanceof String body) {
            return body.contains("\"" + LEGACY_AMOUNT_PARAM + "\"");
        }
        return false;
    }
}
