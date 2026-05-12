package org.fujay.ai.platform.tenant.domain.aggregate;

import org.fujay.ai.platform.shared.ddd.AggregateRoot;

public record Tenant(Long id, String tenantCode, String tenantName, String status) implements AggregateRoot {

    private static final String STATUS_ENABLED = "ENABLED";

    public Tenant {
        requireId(id, "租户标识不能为空");
        requireNotBlank(tenantCode, "租户编码不能为空");
        requireNotBlank(tenantName, "租户名称不能为空");
        status = normalizeStatus(status);
        tenantCode = tenantCode.trim();
        tenantName = tenantName.trim();
    }

    public boolean enabled() {
        return STATUS_ENABLED.equals(status);
    }

    public Tenant renameAndChangeStatus(String newTenantName, String newStatus) {
        return new Tenant(id, tenantCode, newTenantName, newStatus);
    }

    public Tenant changeStatus(String newStatus) {
        return new Tenant(id, tenantCode, tenantName, newStatus);
    }

    public static String normalizeStatus(String status) {
        requireNotBlank(status, "租户状态不能为空");
        String normalized = status.trim().toUpperCase();
        if (!STATUS_ENABLED.equals(normalized) && !"DISABLED".equals(normalized)) {
            throw new IllegalArgumentException("租户状态不合法");
        }
        return normalized;
    }

    private static void requireId(Long value, String message) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
