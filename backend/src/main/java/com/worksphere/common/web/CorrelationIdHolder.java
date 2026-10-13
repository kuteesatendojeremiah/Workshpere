package com.worksphere.common.web;

/** Request-scoped correlation id, set by {@link CorrelationIdFilter} at the start of every request. */
public final class CorrelationIdHolder {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private CorrelationIdHolder() {
    }

    public static String get() {
        return CURRENT.get();
    }

    static void set(String requestId) {
        CURRENT.set(requestId);
    }

    static void clear() {
        CURRENT.remove();
    }
}
