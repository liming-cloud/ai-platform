package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto;

import java.time.Instant;
import java.util.Objects;

/**
 * 平台治理工作区分页展示的最小、不可变租户生命周期投影。
 *
 * <p>该投影不属于生命周期聚合，不携带初始管理员、成员、权限、凭据或租户正文。</p>
 */
public record TenantLifecycleSummary(
        long tenantId,
        String tenantCode,
        String displayName,
        String status,
        long version,
        Instant createdAt,
        Instant lastChangedAt) {

    public TenantLifecycleSummary {
        if (tenantId <= 0L) {
            throw new IllegalArgumentException("租户标识必须为正数");
        }
        tenantCode = requireText(tenantCode, "租户编码不能为空");
        displayName = requireText(displayName, "租户显示名称不能为空");
        status = requireText(status, "租户生命周期状态不能为空");
        if (version < 0L) {
            throw new IllegalArgumentException("租户生命周期修订不能为负数");
        }
        createdAt = Objects.requireNonNull(createdAt, "租户创建时间不能为空");
        lastChangedAt = Objects.requireNonNull(lastChangedAt, "租户最后变更时间不能为空");
        if (lastChangedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("租户最后变更时间不能早于创建时间");
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
