package com.aiplatform.shared.kernel;

public final class TenantContext {
    private static final ThreadLocal<TenantId> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(TenantId tenantId) {
        CURRENT.set(tenantId);
    }

    public static TenantId current() {
        TenantId tenantId = CURRENT.get();
        if (tenantId == null) {
            throw new IllegalStateException("租户上下文未初始化");
        }
        return tenantId;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
