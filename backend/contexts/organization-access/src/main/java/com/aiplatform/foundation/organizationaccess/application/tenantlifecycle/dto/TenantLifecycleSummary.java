package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto;

import java.time.Instant;
import java.util.Objects;

/**
 * 平台治理工作区分页展示的最小、不可变租户生命周期投影。
 *
 * <p>管理员仅提供资格数量；不携带个人身份、成员明细、权限、凭据或租户正文。</p>
 */
public record TenantLifecycleSummary(
        long tenantId,
        String tenantCode,
        String displayName,
        String status,
        long version,
        Instant createdAt,
        Instant lastChangedAt,
        long administratorCount) {

    /**
     * 校验只读投影的标识、时间、版本与资格数量，不将统计值当成个人身份。
     *
     * @throws IllegalArgumentException 当标识、版本、时间顺序或资格数量非法时拒绝。
     */
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
        if (administratorCount < 0L) {
            throw new IllegalArgumentException("管理员资格数量不能为负数");
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
