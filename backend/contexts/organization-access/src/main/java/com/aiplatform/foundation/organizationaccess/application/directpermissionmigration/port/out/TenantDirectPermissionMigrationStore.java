package com.aiplatform.foundation.organizationaccess.application.directpermissionmigration.port.out;

import com.aiplatform.foundation.organizationaccess.application.authorization.command.TenantDirectPermissionMigrationManifest;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.domain.authorization.operation.TenantDirectPermissionMigrationResult;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** 历史直接权限迁移所需的受锁读取与原子写入端口。 */
public interface TenantDirectPermissionMigrationStore {

    /** 锁定清单目标成员及其角色、直接权限事实，并验证清单目标角色可用。 */
    Optional<Facts> lock(TenantDirectPermissionMigrationManifest manifest);

    /** 写入缺失角色绑定、权限修订、最小审计和全部直接权限删除。 */
    void commit(
            TenantDirectPermissionMigrationManifest manifest,
            Facts facts,
            TenantDirectPermissionMigrationResult result);

    /** 受锁成员资格与清单指定角色投影出的迁移事实。 */
    record Facts(
            TenantMembership membership,
            Set<PermissionCode> directPermissions,
            Set<PermissionCode> existingRolePermissions,
            Set<PermissionCode> resultingRolePermissions,
            Set<String> missingBuiltInRoleCodes,
            Set<Long> missingCustomRoleIds) {

        public Facts {
            membership = Objects.requireNonNull(membership, "受锁成员资格不能为空");
            directPermissions = immutablePermissions(directPermissions, "历史直接权限不能为空");
            existingRolePermissions = immutablePermissions(existingRolePermissions, "既有角色权限不能为空");
            resultingRolePermissions = immutablePermissions(resultingRolePermissions, "迁移后角色权限不能为空");
            missingBuiltInRoleCodes = Set.copyOf(Objects.requireNonNull(missingBuiltInRoleCodes, "待绑定内置角色不能为空"));
            missingCustomRoleIds = Set.copyOf(Objects.requireNonNull(missingCustomRoleIds, "待绑定自定义角色不能为空"));
        }

        private static Set<PermissionCode> immutablePermissions(Set<PermissionCode> values, String message) {
            return Set.copyOf(Objects.requireNonNull(values, message));
        }
    }
}
