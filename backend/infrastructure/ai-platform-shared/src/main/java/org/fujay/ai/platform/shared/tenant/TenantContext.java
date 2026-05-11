package org.fujay.ai.platform.shared.tenant;

import java.util.function.Supplier;
import org.fujay.ai.platform.shared.error.CommonErrorCode;
import org.fujay.ai.platform.shared.error.PlatformException;

public final class TenantContext {

    private static final ThreadLocal<TenantId> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static TenantId requiredTenantId() {
        TenantId tenantId = CURRENT.get();
        if (tenantId == null) {
            throw new PlatformException(CommonErrorCode.FORBIDDEN);
        }
        return tenantId;
    }

    public static <T> T callWith(TenantId tenantId, Supplier<T> supplier) {
        TenantId previous = CURRENT.get();
        CURRENT.set(tenantId);
        try {
            return supplier.get();
        } finally {
            restore(previous);
        }
    }

    public static void runWith(TenantId tenantId, Runnable runnable) {
        TenantId previous = CURRENT.get();
        CURRENT.set(tenantId);
        try {
            runnable.run();
        } finally {
            restore(previous);
        }
    }

    private static void restore(TenantId previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }
}
