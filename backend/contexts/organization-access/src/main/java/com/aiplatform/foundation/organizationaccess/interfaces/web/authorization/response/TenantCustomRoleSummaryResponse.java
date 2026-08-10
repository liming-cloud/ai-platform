package com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.response;

import com.aiplatform.foundation.organizationaccess.application.authorization.dto.TenantCustomRoleSummary;
import java.util.List;
import java.util.Objects;

/** 自定义角色目录的最小 HTTP 读取投影。 */
public record TenantCustomRoleSummaryResponse(
        String id,
        String name,
        List<String> permissionCodes,
        long version,
        String status,
        long boundMemberCount) {

    /** 由应用层目录投影构造 HTTP 响应。 */
    public static TenantCustomRoleSummaryResponse from(TenantCustomRoleSummary summary) {
        summary = Objects.requireNonNull(summary, "自定义角色目录投影不能为空");
        return new TenantCustomRoleSummaryResponse(
                summary.id(), summary.name(), summary.permissionCodes(), summary.version(),
                summary.status(), summary.boundMemberCount());
    }
}
