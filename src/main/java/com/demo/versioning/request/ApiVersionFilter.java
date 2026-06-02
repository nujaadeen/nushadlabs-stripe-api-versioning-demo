package com.demo.versioning.request;

import com.demo.versioning.version.ApiVersion;
import com.demo.versioning.version.VersionContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.format.DateTimeParseException;

@Component
public class ApiVersionFilter extends OncePerRequestFilter {

    static final String VERSION_HEADER = "Stripe-Api-Version";

    @Value("${api.versioning.default-version}")
    private String defaultVersion;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String raw = request.getHeader(VERSION_HEADER);
        if (raw == null || raw.isBlank()) {
            raw = defaultVersion;
        }

        ApiVersion version;
        try {
            version = ApiVersion.fromString(raw);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid API version: '" + raw + "'. Expected one of the supported version dates.");
            return;
        }

        VersionContext.set(version);
        try {
            chain.doFilter(new CachedBodyRequestWrapper(request), response);
        } finally {
            VersionContext.clear();
        }
    }
}
