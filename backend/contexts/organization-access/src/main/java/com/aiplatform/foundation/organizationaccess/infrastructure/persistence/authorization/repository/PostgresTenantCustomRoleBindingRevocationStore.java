package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.repository;

import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantCustomRoleBindingRevocationStore;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionGrantSet;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantCustomRole;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantCustomRoleId;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantCustomRoleStatus;
import com.aiplatform.foundation.organizationaccess.domain.authorization.operation.TenantCustomRoleBindingRevocationResult;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipRole;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipStatus;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.TenantCustomRoleBindingMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.TenantRoleBindingMapper;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** PostgreSQL 自定义角色解绑存储，只映射受锁事实并提交领域操作结果。 */
public final class PostgresTenantCustomRoleBindingRevocationStore
        implements TenantCustomRoleBindingRevocationStore {

    private final TenantRoleBindingMapper membershipMapper;
    private final TenantCustomRoleBindingMapper roleMapper;

    public PostgresTenantCustomRoleBindingRevocationStore(
            TenantRoleBindingMapper membershipMapper,
            TenantCustomRoleBindingMapper roleMapper) {
        this.membershipMapper = Objects.requireNonNull(membershipMapper, "成员角色绑定映射器不能为空");
        this.roleMapper = Objects.requireNonNull(roleMapper, "自定义角色绑定映射器不能为空");
    }

    @Override
    public Optional<TenantMembership> lockEnabled(TenantId tenantId, MemberId membershipId) {
        var row = membershipMapper.lockEnabled(
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
    public Optional<TenantCustomRole> lockActive(TenantId tenantId, TenantCustomRoleId roleId) {
        var row = roleMapper.lockActiveRole(
                Objects.requireNonNull(tenantId, "租户标识不能为空").value(),
                Objects.requireNonNull(roleId, "自定义角色标识不能为空").value());
        if (row == null) {
            return Optional.empty();
        }
        var permissions = roleMapper.findPermissionCodes(row.getCustomRoleId()).stream()
                .map(PermissionCode::new)
                .collect(Collectors.toUnmodifiableSet());
        return Optional.of(TenantCustomRole.reconstitute(
                new TenantCustomRoleId(row.getCustomRoleId()),
                new TenantId(row.getTenantId()),
                row.getRoleName(),
                new PermissionGrantSet(row.getVersion(), permissions),
                TenantCustomRoleStatus.valueOf(row.getStatus()),
                row.getCreatedAt(),
                row.getDisabledAt()));
    }

    @Override
    public boolean hasBinding(TenantId tenantId, MemberId membershipId, TenantCustomRoleId roleId) {
        return roleMapper.countBindings(
                Objects.requireNonNull(tenantId, "租户标识不能为空").value(),
                Objects.requireNonNull(membershipId, "成员标识不能为空").value(),
                Objects.requireNonNull(roleId, "自定义角色标识不能为空").value()) > 0;
    }

    @Override
    public boolean retainsAccessManageAfterRevocation(
            TenantId tenantId, MemberId membershipId, TenantCustomRoleId roleId) {
        return roleMapper.countAccessManageAfterRevocation(
                Objects.requireNonNull(tenantId, "租户标识不能为空").value(),
                Objects.requireNonNull(membershipId, "成员标识不能为空").value(),
                Objects.requireNonNull(roleId, "自定义角色标识不能为空").value()) > 0;
    }

    @Override
    public long countOtherEnabledAccessManagers(TenantId tenantId, MemberId membershipId) {
        return roleMapper.countOtherEnabledAccessManagers(
                Objects.requireNonNull(tenantId, "租户标识不能为空").value(),
                Objects.requireNonNull(membershipId, "成员标识不能为空").value());
    }

    @Override
    public void commit(TenantCustomRoleBindingRevocationResult result) {
        result = Objects.requireNonNull(result, "自定义角色解绑结果不能为空");
        requireSingleWrite(roleMapper.deleteBinding(
                result.tenantId().value(),
                result.membershipId().value(),
                result.customRoleId().value()), "自定义角色解绑写入失败");
        var membership = result.revisedMembership();
        requireSingleWrite(membershipMapper.revisePermissionVersion(
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
