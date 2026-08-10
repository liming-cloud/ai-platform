package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.repository;

import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantCustomRoleStore;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantCustomRole;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.TenantCustomRoleMapper;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Objects;

/** PostgreSQL 租户自定义角色存储，只提交领域聚合根已经判定的状态与权限集合。 */
public final class PostgresTenantCustomRoleStore implements TenantCustomRoleStore {

    private final TenantCustomRoleMapper mapper;

    public PostgresTenantCustomRoleStore(TenantCustomRoleMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "租户自定义角色映射器不能为空");
    }

    @Override
    public boolean hasActiveName(TenantId tenantId, String name) {
        return mapper.countActiveName(
                Objects.requireNonNull(tenantId, "租户标识不能为空").value(),
                Objects.requireNonNull(name, "角色名称不能为空")) > 0;
    }

    @Override
    public void insert(TenantCustomRole role) {
        role = Objects.requireNonNull(role, "租户自定义角色不能为空");
        requireSingleWrite(mapper.insertRole(
                role.id().value(),
                role.tenantId().value(),
                role.name(),
                role.status().name(),
                role.version(),
                role.createdAt(),
                role.disabledAt()), "租户自定义角色写入失败");
        for (var permission : role.permissions()) {
            requireSingleWrite(mapper.insertPermission(role.id().value(), permission.value()),
                    "租户自定义角色权限写入失败");
        }
    }

    private static void requireSingleWrite(int rows, String message) {
        if (rows != 1) {
            throw new IllegalStateException(message);
        }
    }
}
