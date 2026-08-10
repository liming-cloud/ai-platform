package com.aiplatform.foundation.organizationaccess.interfaces.web.member.response;

import com.aiplatform.foundation.organizationaccess.application.member.dto.TenantMemberStatusView;
import java.util.Objects;

/** 成员状态变更后的最小安全响应，不返回主体凭据或会话信息。 */
public record TenantMemberStatusResponse(
        String membershipId,
        String status,
        long permissionVersion) {

    /** 由应用层结果构造 HTTP 响应。 */
    public static TenantMemberStatusResponse from(TenantMemberStatusView view) {
        view = Objects.requireNonNull(view, "成员状态视图不能为空");
        return new TenantMemberStatusResponse(
                view.membershipId(), view.status(), view.permissionVersion());
    }
}
