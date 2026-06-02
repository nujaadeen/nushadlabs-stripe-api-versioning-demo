package com.demo.versioning.request;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Reads and caches the request body on construction so it can be consumed
 * multiple times (once by the interceptor, once by the handler).
 * Stores the body as a request attribute under {@link #BODY_ATTRIBUTE}.
 */
public class CachedBodyRequestWrapper extends HttpServletRequestWrapper {

    static final String BODY_ATTRIBUTE = "cachedRequestBody";

    private final byte[] cachedBody;

    public CachedBodyRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        byte[] body = request.getInputStream().readAllBytes();
        this.cachedBody = body;
        request.setAttribute(BODY_ATTRIBUTE, new String(body, StandardCharsets.UTF_8));
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream byteStream = new ByteArrayInputStream(cachedBody);
        return new ServletInputStream() {
            @Override public int read() { return byteStream.read(); }
            @Override public boolean isFinished() { return byteStream.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener listener) {}
        };
    }
}
