package com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.response;

import com.aiplatform.foundation.organizationaccess.application.authorization.dto.TenantRoleRevocationView;
import java.util.Objects;

/** 已提交角色撤销的最小公开投影，不返回角色策略、会话或凭据。 */
public record TenantRoleRevocationResponse(
        String membershipId,
        String role,
        long permissionVersion,
        String revokedAt) {

    /** 从已提交的领域操作结果投影 HTTP 响应。 */
    public static TenantRoleRevocationResponse from(TenantRoleRevocationView revocation) {
        revocation = Objects.requireNonNull(revocation, "角色撤销视图不能为空");
        return new TenantRoleRevocationResponse(
                revocation.membershipId(),
                revocation.roleCode(),
                revocation.permissionVersion(),
                revocation.revokedAt().toString());
    }
}
