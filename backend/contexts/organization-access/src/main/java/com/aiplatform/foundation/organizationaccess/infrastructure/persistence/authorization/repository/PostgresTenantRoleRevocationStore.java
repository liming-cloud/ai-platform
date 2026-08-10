package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.repository;

import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantRoleRevocationStore;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantBuiltInRole;
import com.aiplatform.foundation.organizationaccess.domain.authorization.operation.TenantRoleRevocationResult;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipRole;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipStatus;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.TenantRoleBindingMapper;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Objects;
import java.util.Optional;

/** PostgreSQL 的内置角色撤销存储，只映射受锁事实和同事务操作结果。 */
public final class PostgresTenantRoleRevocationStore implements TenantRoleRevocationStore {

    private final TenantRoleBindingMapper mapper;

    public PostgresTenantRoleRevocationStore(TenantRoleBindingMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "角色撤销映射器不能为空");
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
    public boolean retainsAccessManageAfterRevocation(
            TenantId tenantId, MemberId membershipId, TenantBuiltInRole role) {
        return mapper.countAccessManageAfterRevocation(
                Objects.requireNonNull(tenantId, "租户标识不能为空").value(),
                Objects.requireNonNull(membershipId, "成员标识不能为空").value(),
                Objects.requireNonNull(role, "内置角色不能为空").name()) > 0;
    }

    @Override
    public long countOtherEnabledAccessManagers(TenantId tenantId, MemberId membershipId) {
        return mapper.countOtherEnabledAccessManagers(
                Objects.requireNonNull(tenantId, "租户标识不能为空").value(),
                Objects.requireNonNull(membershipId, "成员标识不能为空").value());
    }

    @Override
    public void commit(TenantRoleRevocationResult result) {
        result = Objects.requireNonNull(result, "角色撤销结果不能为空");
        requireSingleWrite(mapper.deleteBinding(
                result.tenantId().value(),
                result.membershipId().value(),
                result.role().name()), "成员角色撤销写入失败");
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
