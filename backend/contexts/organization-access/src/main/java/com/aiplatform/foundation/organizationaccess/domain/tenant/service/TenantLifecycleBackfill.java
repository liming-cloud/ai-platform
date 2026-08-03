package com.aiplatform.foundation.organizationaccess.domain.tenant.service;

import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLifecycle;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;

/**
 * 仅以经人工确认的初始管理员主体回填历史租户生命周期。
 */
public final class TenantLifecycleBackfill {

    private TenantLifecycleBackfill() {
    }

    /**
     * 将明确映射转换为准备中的生命周期根。
     *
     * @throws IllegalArgumentException 当初始管理员映射缺失时拒绝。
     */
    public static TenantLifecycle backfill(
            TenantId tenantId,
            String tenantCode,
            String displayName,
            SubjectId initialAdministratorId,
            Instant createdAt) {
        if (initialAdministratorId == null) {
            throw new IllegalArgumentException("历史租户缺少经确认的初始管理员映射");
        }
        return TenantLifecycle.provision(
                Objects.requireNonNull(tenantId, "租户标识不能为空"),
                tenantCode,
                displayName,
                initialAdministratorId,
                Objects.requireNonNull(createdAt, "回填时间不能为空"));
    }
}
