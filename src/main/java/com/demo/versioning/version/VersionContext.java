package com.demo.versioning.version;

public final class VersionContext {

    private static final ThreadLocal<ApiVersion> HOLDER = new ThreadLocal<>();

    private VersionContext() {}

    public static ApiVersion get() {
        return HOLDER.get();
    }

    public static void set(ApiVersion version) {
        HOLDER.set(version);
    }

    public static void clear() {
        HOLDER.remove();
    }
}
