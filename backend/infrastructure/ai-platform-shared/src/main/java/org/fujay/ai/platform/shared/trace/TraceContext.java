package org.fujay.ai.platform.shared.trace;

import java.util.UUID;

public final class TraceContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TraceContext() {
    }

    public static String currentTraceId() {
        String traceId = CURRENT.get();
        if (traceId == null || traceId.isBlank()) {
            return "trace-" + UUID.randomUUID();
        }
        return traceId;
    }

    public static void setTraceId(String traceId) {
        CURRENT.set(traceId);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
