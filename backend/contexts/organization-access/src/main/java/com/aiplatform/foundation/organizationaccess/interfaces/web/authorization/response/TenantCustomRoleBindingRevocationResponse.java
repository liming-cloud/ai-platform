package com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.response;

import com.aiplatform.foundation.organizationaccess.application.authorization.dto.TenantCustomRoleBindingRevocationView;
import java.util.Objects;

/** 已提交自定义角色解绑的最小公开投影，不返回角色策略、会话或凭据。 */
public record TenantCustomRoleBindingRevocationResponse(
        String membershipId,
        String customRoleId,
        long permissionVersion,
        String revokedAt) {

    /** 从已提交的领域操作结果投影 HTTP 响应。 */
    public static TenantCustomRoleBindingRevocationResponse from(
            TenantCustomRoleBindingRevocationView revocation) {
        revocation = Objects.requireNonNull(revocation, "自定义角色解绑视图不能为空");
        return new TenantCustomRoleBindingRevocationResponse(
                Long.toString(revocation.membershipId()),
                Long.toString(revocation.customRoleId()),
                revocation.permissionVersion(),
                revocation.occurredAt().toString());
    }
}
