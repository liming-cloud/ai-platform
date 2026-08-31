package com.aiplatform.foundation.organizationaccess.application.member.dto;

import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import java.time.Instant;
import java.util.Objects;

/** 平台租户管理员写操作的最小安全回显。 */
public record TenantAdministratorView(
        long membershipId,
        String loginName,
        String role,
        String status,
        long permissionVersion,
        long version,
        Instant createdAt) {

    /** 从已受权成员事实建立不含凭据、权限快照和会话标识的回显。 */
    public static TenantAdministratorView from(TenantMembership membership, String loginName) {
        membership = Objects.requireNonNull(membership, "管理员成员资格不能为空");
        loginName = Objects.requireNonNull(loginName, "管理员登录名不能为空");
        return new TenantAdministratorView(
                membership.id().value(),
                loginName,
                membership.role().name(),
                membership.status().name(),
                membership.permissionVersion(),
                membership.version(),
                membership.createdAt());
    }
}
