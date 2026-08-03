package com.aiplatform.foundation.organizationaccess.domain.tenant.operation;

import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipRole;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLifecycle;
import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import java.util.List;
import java.util.Objects;

/**
 * 历史租户回填在单一事务中必须提交的不可变领域结果。
 *
 * <p>生命周期与成员资格是独立聚合；本结果只证明它们已经由领域服务共同验证，应用层据此原子写入根、
 * 成员角色与对应最小状态事实。</p>
 */
public record TenantLifecycleBackfillResult(
        TenantLifecycle lifecycle,
        TenantMembership initialAdministrator,
        List<TenantLifecycleChange> lifecycleChanges) implements ValueObject {

    public TenantLifecycleBackfillResult {
        lifecycle = Objects.requireNonNull(lifecycle, "租户生命周期不能为空");
        initialAdministrator = Objects.requireNonNull(initialAdministrator, "初始管理员成员资格不能为空");
        lifecycleChanges = List.copyOf(Objects.requireNonNull(lifecycleChanges, "生命周期事实不能为空"));
        if (!lifecycle.id().equals(initialAdministrator.tenantId())
                || !lifecycle.initialAdministratorId().equals(initialAdministrator.subjectId())
                || initialAdministrator.role() != TenantMembershipRole.TENANT_ADMINISTRATOR
                || lifecycleChanges.isEmpty()) {
            throw new IllegalArgumentException("历史租户回填结果不满足生命周期与初始管理员一致性");
        }
        var lastChange = lifecycleChanges.getLast();
        if (!lifecycle.id().equals(lastChange.tenantId())
                || lifecycle.status() != lastChange.status()
                || lifecycle.version() != lastChange.version()) {
            throw new IllegalArgumentException("历史租户回填结果的最终生命周期事实不一致");
        }
    }
}
