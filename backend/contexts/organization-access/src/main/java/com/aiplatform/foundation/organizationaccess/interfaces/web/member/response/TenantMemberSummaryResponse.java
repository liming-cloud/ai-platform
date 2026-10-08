package com.aiplatform.foundation.organizationaccess.interfaces.web.member.response;

import com.aiplatform.foundation.organizationaccess.application.member.dto.TenantMemberSummary;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 成员目录的最小 HTTP 读取投影，不包含凭据、会话或完整权限集合。 */
public record TenantMemberSummaryResponse(
        String membershipId,
        String loginName,
        String membershipRole,
        String status,
        long permissionVersion,
        List<String> builtInRoles,
        Instant createdAt,
        long version,
        long assistantCount) {

    /** 由应用层成员目录投影构造 HTTP 响应。 */
    public static TenantMemberSummaryResponse from(TenantMemberSummary summary) {
        summary = Objects.requireNonNull(summary, "成员目录投影不能为空");
        return new TenantMemberSummaryResponse(
                summary.membershipId(),
                summary.loginName(),
                summary.membershipRole(),
                summary.status(),
                summary.permissionVersion(),
                summary.builtInRoles(),
                summary.createdAt(), summary.version(), summary.assistantCount());
    }
}
