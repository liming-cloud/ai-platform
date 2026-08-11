package com.aiplatform.foundation.organizationaccess.application.authorization.dto;

import java.time.Instant;
import java.util.Set;

/** 平台账户目录的最小安全摘要，仅包含用于角色治理的固定角色代码。 */
public record PlatformAccountSummary(
        long subjectId,
        String loginName,
        String status,
        long version,
        Instant createdAt,
        Set<String> activeRoleCodes) {
}
