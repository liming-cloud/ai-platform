package com.aiplatform.shared.observability;

import java.util.UUID;

public final class TraceContext {
    private static final ThreadLocal<String> CURRENT_TRACE_ID = new ThreadLocal<>();

    private TraceContext() {
    }

    public static String startTraceId() {
        String value = UUID.randomUUID().toString();
        CURRENT_TRACE_ID.set(value);
        return value;
    }

    public static String ensureTraceId(String traceId) {
        String value = normalizeOrGenerate(traceId);
        CURRENT_TRACE_ID.set(value);
        return value;
    }

    public static String currentTraceId() {
        String value = CURRENT_TRACE_ID.get();
        return value == null ? ensureTraceId(null) : value;
    }

    public static void clear() {
        CURRENT_TRACE_ID.remove();
    }

    private static String normalizeOrGenerate(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return traceId.trim();
    }
}
