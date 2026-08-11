package com.aiplatform.foundation.organizationaccess.application.authorization.dto;

import java.time.Instant;

/** 平台账户目录的最小安全摘要，不包含凭据、会话或权限快照。 */
public record PlatformAccountSummary(
        long subjectId,
        String loginName,
        String status,
        long version,
        Instant createdAt) {
}
