package com.aiplatform.foundation.organizationaccess.application.authorization.command;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Objects;
import java.util.Set;

/**
 * 租户运营负责人批准的单成员历史直接权限迁移输入。
 *
 * <p>该值对象只用于受控迁移编排，不是浏览器或普通 HTTP 请求契约。目标角色必须由存储端口重读并验证为
 * 当前租户的已发布内置角色或活动自定义角色。</p>
 */
public record TenantDirectPermissionMigrationManifest(
        String manifestId,
        TenantId tenantId,
        MemberId membershipId,
        long expectedPermissionVersion,
        Set<PermissionCode> expectedDirectPermissions,
        Set<String> targetBuiltInRoleCodes,
        Set<Long> targetCustomRoleIds,
        Set<PermissionCode> approvedRevocations) {

    /**
     * @throws IllegalArgumentException 当清单标识、修订或角色目标非法时拒绝。
     */
    public TenantDirectPermissionMigrationManifest {
        manifestId = Objects.requireNonNull(manifestId, "迁移清单标识不能为空").strip();
        if (manifestId.isEmpty()) {
            throw new IllegalArgumentException("迁移清单标识不能为空");
        }
        tenantId = Objects.requireNonNull(tenantId, "迁移租户标识不能为空");
        membershipId = Objects.requireNonNull(membershipId, "迁移成员标识不能为空");
        if (expectedPermissionVersion < 0L) {
            throw new IllegalArgumentException("预期成员权限修订不能为负数");
        }
        expectedDirectPermissions = immutable(expectedDirectPermissions, "历史直接权限摘要不能为空");
        if (expectedDirectPermissions.isEmpty()) {
            throw new IllegalArgumentException("历史直接权限摘要不能为空");
        }
        targetBuiltInRoleCodes = immutableRoleCodes(targetBuiltInRoleCodes);
        targetCustomRoleIds = immutableCustomRoleIds(targetCustomRoleIds);
        if (targetBuiltInRoleCodes.isEmpty() && targetCustomRoleIds.isEmpty()) {
            throw new IllegalArgumentException("迁移清单必须指定目标角色");
        }
        approvedRevocations = immutable(approvedRevocations, "批准撤销权限不能为空");
        if (!expectedDirectPermissions.containsAll(approvedRevocations)) {
            throw new IllegalArgumentException("批准撤销权限必须属于历史直接权限摘要");
        }
    }

    private static Set<PermissionCode> immutable(Set<PermissionCode> values, String message) {
        return Set.copyOf(Objects.requireNonNull(values, message));
    }

    private static Set<String> immutableRoleCodes(Set<String> values) {
        values = Set.copyOf(Objects.requireNonNull(values, "目标内置角色不能为空"));
        if (values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("目标内置角色不能为空");
        }
        return values;
    }

    private static Set<Long> immutableCustomRoleIds(Set<Long> values) {
        values = Set.copyOf(Objects.requireNonNull(values, "目标自定义角色不能为空"));
        if (values.stream().anyMatch(value -> value == null || value <= 0L)) {
            throw new IllegalArgumentException("目标自定义角色标识必须为正数");
        }
        return values;
    }
}
