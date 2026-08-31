package com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.response;

import com.aiplatform.foundation.organizationaccess.application.member.dto.TenantAdministratorView;
import java.time.Instant;

/** 租户管理员写操作的浏览器安全回显。 */
public record TenantAdministratorResponse(
        String membershipReference,
        String loginName,
        String role,
        String status,
        long permissionVersion,
        long version,
        Instant createdAt) {

    /** 将 64 位成员标识转换为十进制字符串，避免 JavaScript 精度损失。 */
    public static TenantAdministratorResponse from(TenantAdministratorView view) {
        return new TenantAdministratorResponse(
                Long.toString(view.membershipId()),
                view.loginName(),
                view.role(),
                view.status(),
                view.permissionVersion(),
                view.version(),
                view.createdAt());
    }
}
