package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.repository;

import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantRoleBindingStore;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantBuiltInRole;
import com.aiplatform.foundation.organizationaccess.domain.authorization.operation.TenantRoleBindingResult;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipRole;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipStatus;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.TenantRoleBindingMapper;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Objects;
import java.util.Optional;

/**
 * PostgreSQL 的成员角色绑定存储，只映射受锁事实和领域操作结果。
 */
public final class PostgresTenantRoleBindingStore implements TenantRoleBindingStore {

    private final TenantRoleBindingMapper mapper;

    public PostgresTenantRoleBindingStore(TenantRoleBindingMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "角色绑定映射器不能为空");
    }

    @Override
    public Optional<TenantMembership> lockEnabled(TenantId tenantId, MemberId membershipId) {
        var row = mapper.lockEnabled(
                Objects.requireNonNull(tenantId, "租户标识不能为空").value(),
                Objects.requireNonNull(membershipId, "成员标识不能为空").value());
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(TenantMembership.reconstitute(
                new MemberId(row.getMembershipId()),
                new TenantId(row.getTenantId()),
                new SubjectId(row.getSubjectId()),
                TenantMembershipRole.valueOf(row.getMembershipRole()),
                TenantMembershipStatus.valueOf(row.getStatus()),
                row.getPermissionVersion(),
                row.getVersion(),
                row.getCreatedAt()));
    }

    @Override
    public boolean hasBinding(TenantId tenantId, MemberId membershipId, TenantBuiltInRole role) {
        return mapper.countBindings(
                Objects.requireNonNull(tenantId, "租户标识不能为空").value(),
                Objects.requireNonNull(membershipId, "成员标识不能为空").value(),
                Objects.requireNonNull(role, "内置角色不能为空").name()) > 0;
    }

    @Override
    public void commit(TenantRoleBindingResult result) {
        result = Objects.requireNonNull(result, "角色绑定结果不能为空");
        var binding = result.binding();
        requireSingleWrite(mapper.insertBinding(
                binding.tenantId().value(),
                binding.membershipId().value(),
                binding.role().name(),
                binding.boundAt()), "成员角色绑定写入失败");
        var membership = result.revisedMembership();
        requireSingleWrite(mapper.revisePermissionVersion(
                membership.tenantId().value(),
                membership.id().value(),
                membership.permissionVersion(),
                membership.version()), "成员权限修订写入失败");
    }

    private static void requireSingleWrite(int rows, String message) {
        if (rows != 1) {
            throw new IllegalStateException(message);
        }
    }
}
