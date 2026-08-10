package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.repository;

import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantCustomRoleLifecycleStore;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionGrantSet;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantCustomRole;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantCustomRoleId;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantCustomRoleStatus;
import com.aiplatform.foundation.organizationaccess.domain.authorization.operation.TenantCustomRoleLifecycleResult;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipRole;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipStatus;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.TenantCustomRoleLifecycleMapper;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** PostgreSQL 自定义角色生命周期存储，只锁定事实并提交领域操作结果。 */
public final class PostgresTenantCustomRoleLifecycleStore implements TenantCustomRoleLifecycleStore {

    private final TenantCustomRoleLifecycleMapper mapper;

    public PostgresTenantCustomRoleLifecycleStore(TenantCustomRoleLifecycleMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "自定义角色生命周期映射器不能为空");
    }

    @Override
    public Optional<TenantCustomRole> lockActive(TenantId tenantId, TenantCustomRoleId roleId) {
        var row = mapper.lockActiveRole(
                Objects.requireNonNull(tenantId, "租户标识不能为空").value(),
                Objects.requireNonNull(roleId, "自定义角色标识不能为空").value());
        if (row == null) {
            return Optional.empty();
        }
        var permissions = mapper.findPermissionCodes(row.getCustomRoleId()).stream()
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
    public List<TenantMembership> lockEnabledBoundMembers(TenantId tenantId, TenantCustomRoleId roleId) {
        return mapper.lockEnabledBoundMembers(
                        Objects.requireNonNull(tenantId, "租户标识不能为空").value(),
                        Objects.requireNonNull(roleId, "自定义角色标识不能为空").value())
                .stream()
                .map(row -> TenantMembership.reconstitute(
                        new MemberId(row.getMembershipId()),
                        new TenantId(row.getTenantId()),
                        new SubjectId(row.getSubjectId()),
                        TenantMembershipRole.valueOf(row.getMembershipRole()),
                        TenantMembershipStatus.valueOf(row.getStatus()),
                        row.getPermissionVersion(),
                        row.getVersion(),
                        row.getCreatedAt()))
                .toList();
    }

    @Override
    public long countEnabledAccessManagersAfterChange(
            TenantId tenantId, TenantCustomRoleId roleId, boolean retainsAccessManage) {
        return mapper.countEnabledAccessManagersAfterChange(
                Objects.requireNonNull(tenantId, "租户标识不能为空").value(),
                Objects.requireNonNull(roleId, "自定义角色标识不能为空").value(),
                retainsAccessManage);
    }

    @Override
    public void commit(TenantCustomRoleLifecycleResult result) {
        result = Objects.requireNonNull(result, "自定义角色生命周期结果不能为空");
        var role = result.role();
        requireSingleWrite(mapper.updateRole(
                role.tenantId().value(), role.id().value(), role.status().name(), role.version(), role.disabledAt()),
                "自定义角色更新失败");
        if (role.isActive()) {
            mapper.deletePermissions(role.id().value());
            for (var permission : role.permissions()) {
                requireSingleWrite(mapper.insertPermission(role.id().value(), permission.value()),
                        "自定义角色权限写入失败");
            }
        }
        for (var membership : result.revisedMemberships()) {
            requireSingleWrite(mapper.revisePermissionVersion(
                    membership.tenantId().value(), membership.id().value(),
                    membership.permissionVersion(), membership.version()), "成员权限修订写入失败");
        }
    }

    private static void requireSingleWrite(int rows, String message) {
        if (rows != 1) {
            throw new IllegalStateException(message);
        }
    }
}
