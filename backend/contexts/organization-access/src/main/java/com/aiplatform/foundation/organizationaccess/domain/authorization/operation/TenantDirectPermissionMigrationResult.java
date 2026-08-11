package com.aiplatform.foundation.organizationaccess.domain.authorization.operation;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import java.util.Objects;
import java.util.Set;

/**
 * 已验证的单成员历史直接权限迁移结果。
 *
 * <p>结果只表达允许原子提交的成员修订、删除数量和最小审计摘要；角色引用及其持久化策略属于应用层清单和
 * 存储端口，不能由领域结果推断。</p>
 */
public record TenantDirectPermissionMigrationResult(
        TenantMembership revisedMembership,
        int removedDirectPermissionCount,
        Set<PermissionCode> approvedRevocations) {

    /**
     * @throws IllegalArgumentException 当删除数量为负时拒绝。
     */
    public TenantDirectPermissionMigrationResult {
        revisedMembership = Objects.requireNonNull(revisedMembership, "已修订成员资格不能为空");
        if (removedDirectPermissionCount < 0) {
            throw new IllegalArgumentException("删除直接权限数量不能为负数");
        }
        approvedRevocations = Set.copyOf(Objects.requireNonNull(approvedRevocations, "批准撤销权限不能为空"));
    }
}
