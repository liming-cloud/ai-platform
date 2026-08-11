package com.aiplatform.foundation.organizationaccess.domain.authorization.service;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.domain.authorization.operation.TenantDirectPermissionMigrationResult;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 校验单一成员资格从历史直接权限转为角色权限的等价性。
 *
 * <p>该服务不接收角色名称、成员标识或调用方输入，也不推断职责。应用层必须先从已批准清单加载活动角色及其
 * 权限，再将迁移前后权限事实传入；任何未批准收权或扩权都失败关闭。</p>
 */
public final class TenantDirectPermissionMigration {

    private TenantDirectPermissionMigration() {
    }

    /**
     * 验证清单对应的角色权限可完全取代当前历史直接权限，并生成下一成员权限修订。
     *
     * @throws IllegalArgumentException 当没有历史直接权限、撤销项不属于历史权限、既有角色权限丢失，或迁移前后
     *     有效权限不等价时拒绝。
     * @throws IllegalStateException 当成员资格未启用而不能改变权限事实时拒绝。
     */
    public static TenantDirectPermissionMigrationResult migrate(
            TenantMembership membership,
            Set<PermissionCode> directPermissions,
            Set<PermissionCode> existingRolePermissions,
            Set<PermissionCode> resultingRolePermissions,
            Set<PermissionCode> approvedRevocations) {
        membership = Objects.requireNonNull(membership, "成员资格不能为空");
        var historical = immutable(directPermissions, "历史直接权限不能为空");
        var existing = immutable(existingRolePermissions, "既有角色权限不能为空");
        var resulting = immutable(resultingRolePermissions, "迁移后角色权限不能为空");
        var revocations = immutable(approvedRevocations, "批准撤销权限不能为空");
        if (historical.isEmpty()) {
            throw new IllegalArgumentException("没有历史直接权限不得执行迁移");
        }
        if (!historical.containsAll(revocations)) {
            throw new IllegalArgumentException("批准撤销权限必须属于历史直接权限");
        }
        if (!resulting.containsAll(existing)) {
            throw new IllegalArgumentException("迁移不得移除既有角色权限");
        }

        var expected = new HashSet<>(historical);
        expected.addAll(existing);
        expected.removeAll(revocations);
        if (!resulting.equals(expected)) {
            throw new IllegalArgumentException("迁移后角色权限必须与历史有效权限等价");
        }
        return new TenantDirectPermissionMigrationResult(
                membership.revisePermissions(), historical.size(), revocations);
    }

    private static Set<PermissionCode> immutable(Set<PermissionCode> values, String message) {
        return Set.copyOf(Objects.requireNonNull(values, message));
    }
}
