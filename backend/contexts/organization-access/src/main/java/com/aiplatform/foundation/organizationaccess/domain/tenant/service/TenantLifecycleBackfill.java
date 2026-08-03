package com.aiplatform.foundation.organizationaccess.domain.tenant.service;

import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLifecycle;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLifecycleStatus;
import com.aiplatform.foundation.organizationaccess.domain.tenant.operation.TenantLifecycleBackfillResult;
import com.aiplatform.foundation.organizationaccess.domain.tenant.operation.TenantLifecycleChange;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;

/**
 * 将人工确认的历史租户事实回填为可证明的生命周期操作结果。
 *
 * <p>历史状态只决定最终目标，不得直接构造 `ACTIVE` 或 `SUSPENDED`。服务始终从
 * `PROVISIONING` 建立根，并只通过聚合根的合法状态迁移得到最终状态。</p>
 */
public final class TenantLifecycleBackfill {

    private TenantLifecycleBackfill() {
    }

    /**
     * 回填一个已验证的历史租户，并返回需原子提交的根、成员资格和最小状态事实。
     *
     * @throws IllegalArgumentException 当映射缺失、主体与成员归属不一致或目标状态不适合回填时拒绝。
     * @throws IllegalStateException 当主体或成员资格未启用时拒绝。
     */
    public static TenantLifecycleBackfillResult backfill(
            TenantId tenantId,
            String tenantCode,
            String displayName,
            TenantLifecycleStatus targetStatus,
            SubjectIdentity initialAdministrator,
            TenantMembership existingMembership,
            Instant tenantCreatedAt,
            Instant occurredAt) {
        tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        targetStatus = requireTargetStatus(targetStatus);
        if (initialAdministrator == null) {
            throw new IllegalArgumentException("历史租户缺少经确认的初始管理员映射");
        }
        existingMembership = Objects.requireNonNull(existingMembership, "初始管理员成员资格不能为空");
        tenantCreatedAt = Objects.requireNonNull(tenantCreatedAt, "历史租户创建时间不能为空");
        occurredAt = Objects.requireNonNull(occurredAt, "回填时间不能为空");
        if (!initialAdministrator.isActive()) {
            throw new IllegalStateException("初始管理员主体未启用");
        }
        if (!tenantId.equals(existingMembership.tenantId())
                || !initialAdministrator.id().equals(existingMembership.subjectId())) {
            throw new IllegalArgumentException("初始管理员映射与历史成员资格不一致");
        }

        var lifecycle = TenantLifecycle.provision(
                tenantId, tenantCode, displayName, initialAdministrator.id(), tenantCreatedAt);
        var changes = new ArrayList<TenantLifecycleChange>();
        changes.add(changeOf(lifecycle, occurredAt));
        lifecycle = lifecycle.activate();
        changes.add(changeOf(lifecycle, occurredAt));
        if (targetStatus == TenantLifecycleStatus.SUSPENDED) {
            lifecycle = lifecycle.suspend();
            changes.add(changeOf(lifecycle, occurredAt));
        }
        return new TenantLifecycleBackfillResult(
                lifecycle,
                existingMembership.confirmInitialAdministrator(),
                changes);
    }

    private static TenantLifecycleStatus requireTargetStatus(TenantLifecycleStatus targetStatus) {
        targetStatus = Objects.requireNonNull(targetStatus, "历史租户目标状态不能为空");
        if (targetStatus == TenantLifecycleStatus.PROVISIONING) {
            throw new IllegalArgumentException("历史租户回填目标不能是准备中状态");
        }
        return targetStatus;
    }

    private static TenantLifecycleChange changeOf(TenantLifecycle lifecycle, Instant occurredAt) {
        return new TenantLifecycleChange(
                lifecycle.id(), lifecycle.status(), lifecycle.version(), occurredAt);
    }
}
