package com.aiplatform.foundation.organizationaccess.domain.tenant.operation;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantBuiltInRole;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantRoleBinding;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipRole;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLifecycle;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLifecycleStatus;
import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantQuota;
import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import java.time.Instant;
import java.util.Objects;

/**
 * 租户建立操作应在同一事务提交的不可变领域结果。
 */
public record TenantProvisioningResult(
        TenantLifecycle lifecycle,
        SubjectIdentity administratorIdentity,
        TenantMembership initialAdministrator,
        TenantRoleBinding initialAdministratorRoleBinding,
        TenantQuota quotas,
        TenantLifecycleChange lifecycleChange,
        Instant occurredAt) implements ValueObject {

    public TenantProvisioningResult {
        lifecycle = Objects.requireNonNull(lifecycle, "租户生命周期不能为空");
        administratorIdentity = Objects.requireNonNull(administratorIdentity, "新管理员身份不能为空");
        quotas = Objects.requireNonNull(quotas, "租户配额不能为空");
        initialAdministrator = Objects.requireNonNull(initialAdministrator, "初始管理员成员资格不能为空");
        initialAdministratorRoleBinding = Objects.requireNonNull(
                initialAdministratorRoleBinding, "初始管理员角色绑定不能为空");
        lifecycleChange = Objects.requireNonNull(lifecycleChange, "租户生命周期变化不能为空");
        occurredAt = Objects.requireNonNull(occurredAt, "操作时间不能为空");
        if (lifecycle.status() != TenantLifecycleStatus.ACTIVE
                || lifecycle.version() != 1L
                || !administratorIdentity.isActive()
                || administratorIdentity.version() != 0L
                || !administratorIdentity.id().equals(lifecycle.initialAdministratorId())
                || !lifecycle.id().equals(quotas.tenantId())
                || quotas.version() != 0L
                || !lifecycle.id().equals(initialAdministrator.tenantId())
                || !lifecycle.initialAdministratorId().equals(initialAdministrator.subjectId())
                || initialAdministrator.role() != TenantMembershipRole.TENANT_ADMINISTRATOR
                || !lifecycle.id().equals(initialAdministratorRoleBinding.tenantId())
                || !initialAdministrator.id().equals(initialAdministratorRoleBinding.membershipId())
                || initialAdministratorRoleBinding.role() != TenantBuiltInRole.TENANT_ADMINISTRATOR
                || !occurredAt.equals(initialAdministratorRoleBinding.boundAt())
                || !lifecycle.id().equals(lifecycleChange.tenantId())
                || lifecycle.status() != lifecycleChange.status()
                || lifecycle.version() != lifecycleChange.version()
                || !occurredAt.equals(lifecycleChange.occurredAt())
                || !occurredAt.equals(initialAdministrator.createdAt())) {
            throw new IllegalArgumentException("租户建立结果不满足生命周期与初始管理员一致性");
        }
    }
}
