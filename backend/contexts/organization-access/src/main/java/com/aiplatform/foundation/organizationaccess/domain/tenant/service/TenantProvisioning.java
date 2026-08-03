package com.aiplatform.foundation.organizationaccess.domain.tenant.service;

import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLifecycle;
import com.aiplatform.foundation.organizationaccess.domain.tenant.operation.TenantProvisioningResult;
import java.time.Instant;
import java.util.Objects;

/**
 * 收回租户建立与初始管理员成员资格一致性的无状态领域服务。
 */
public final class TenantProvisioning {

    private TenantProvisioning() {
    }

    /**
     * 为准备中的租户建立初始管理员成员资格。
     *
     * @throws IllegalArgumentException 当管理员主体与生命周期引用不一致时拒绝。
     * @throws IllegalStateException 当管理员主体已禁用时拒绝。
     */
    public static TenantProvisioningResult provision(
            TenantLifecycle lifecycle,
            SubjectIdentity administrator,
            MemberId membershipId,
            Instant occurredAt) {
        lifecycle = Objects.requireNonNull(lifecycle, "租户生命周期不能为空");
        administrator = Objects.requireNonNull(administrator, "初始管理员主体不能为空");
        membershipId = Objects.requireNonNull(membershipId, "初始管理员成员标识不能为空");
        occurredAt = Objects.requireNonNull(occurredAt, "操作时间不能为空");
        if (!lifecycle.initialAdministratorId().equals(administrator.id())) {
            throw new IllegalArgumentException("初始管理员主体与租户生命周期不一致");
        }
        if (!administrator.isActive()) {
            throw new IllegalStateException("初始管理员主体未启用");
        }
        var membership = TenantMembership.establishInitialAdministrator(
                membershipId, lifecycle.id(), administrator.id(), occurredAt);
        return new TenantProvisioningResult(lifecycle, membership, occurredAt);
    }
}
