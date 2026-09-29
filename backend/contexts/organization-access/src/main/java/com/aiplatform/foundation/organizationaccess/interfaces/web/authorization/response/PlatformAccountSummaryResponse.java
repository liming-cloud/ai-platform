package com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.response;

import com.aiplatform.foundation.organizationaccess.application.authorization.dto.PlatformAccountSummary;
import java.time.Instant;
import java.util.Set;

/** 平台账户目录 HTTP 摘要；64 位主体标识必须以十进制字符串交付浏览器。 */
public record PlatformAccountSummaryResponse(
        String subjectId,
        String loginName,
        String status,
        long version,
        Instant createdAt,
        Set<String> activeRoleCodes,
        Instant lastSecurityChangeAt,
        boolean current) {

    /** 从应用层安全摘要转换，避免 JavaScript 解析 64 位数时丢失精度。 */
    public static PlatformAccountSummaryResponse from(PlatformAccountSummary summary) {
        return new PlatformAccountSummaryResponse(
                Long.toString(summary.subjectId()),
                summary.loginName(),
                summary.status(),
                summary.version(),
                summary.createdAt(),
                summary.activeRoleCodes(),
                summary.lastSecurityChangeAt(),
                false);
    }

    /** 当前主体标记由可信请求上下文计算，不接受浏览器传入身份声明。 */
    public static PlatformAccountSummaryResponse from(PlatformAccountSummary summary, long currentSubjectId) {
        var response = from(summary);
        return new PlatformAccountSummaryResponse(
                response.subjectId(), response.loginName(), response.status(), response.version(),
                response.createdAt(), response.activeRoleCodes(), response.lastSecurityChangeAt(),
                summary.subjectId() == currentSubjectId);
    }
}
