package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out;

import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLifecycleStatus;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;

/**
 * 已锁定的历史租户与其候选初始管理员事实。
 *
 * <p>主体和成员资格可以为空，以便领域服务将缺失映射、跨租户主体或非成员情况统一失败关闭；
 * 该投影绝不写入日志或公开响应。</p>
 */
public record HistoricalTenantLifecycleBackfillFacts(
        TenantId tenantId,
        String tenantCode,
        String displayName,
        TenantLifecycleStatus targetStatus,
        SubjectIdentity initialAdministrator,
        TenantMembership existingMembership,
        Instant tenantCreatedAt) {

    public HistoricalTenantLifecycleBackfillFacts {
        tenantId = Objects.requireNonNull(tenantId, "历史租户标识不能为空");
        tenantCode = Objects.requireNonNull(tenantCode, "历史租户编码不能为空");
        displayName = Objects.requireNonNull(displayName, "历史租户显示名称不能为空");
        targetStatus = Objects.requireNonNull(targetStatus, "历史租户目标状态不能为空");
        tenantCreatedAt = Objects.requireNonNull(tenantCreatedAt, "历史租户创建时间不能为空");
    }
}
