package com.aiplatform.knowledge.domain.maintenance;

import com.aiplatform.shared.kernel.TenantId;
import java.util.Objects;

/** 当前对象清理 claim 的租户边界与 fence。 */
public record DocumentObjectCleanupOwnership(
        TenantId tenantId,
        String cleanupId,
        String leaseOwner,
        String leaseToken,
        long fenceVersion
) {
    public DocumentObjectCleanupOwnership {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        cleanupId = requireText(cleanupId, "清理任务 ID");
        leaseOwner = requireText(leaseOwner, "清理 worker ID");
        leaseToken = requireText(leaseToken, "清理 lease token");
        if (fenceVersion < 1) {
            throw new IllegalArgumentException("清理 fence version 必须大于 0");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
