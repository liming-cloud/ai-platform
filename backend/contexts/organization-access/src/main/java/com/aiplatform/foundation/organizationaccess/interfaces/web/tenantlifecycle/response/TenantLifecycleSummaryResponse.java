package com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.response;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto.TenantLifecycleSummary;
import java.time.Instant;
import java.util.Objects;

/**
 * 生命周期工作区使用的最小公开读取投影。
 *
 * <p>只回显管理员资格数量；个人身份、成员明细、权限、凭据与租户正文不属于此契约。</p>
 */
public record TenantLifecycleSummaryResponse(
        String tenantId,
        String tenantCode,
        String displayName,
        String status,
        long version,
        Instant createdAt,
        Instant lastChangedAt,
        long administratorCount) {

    /**
     * 将应用读取投影转换为稳定 HTTP 字段。
     */
    public static TenantLifecycleSummaryResponse from(TenantLifecycleSummary summary) {
        summary = Objects.requireNonNull(summary, "租户生命周期读取投影不能为空");
        return new TenantLifecycleSummaryResponse(
                Long.toString(summary.tenantId()),
                summary.tenantCode(),
                summary.displayName(),
                summary.status(),
                summary.version(),
                summary.createdAt(),
                summary.lastChangedAt(), summary.administratorCount());
    }
}
