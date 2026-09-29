package com.aiplatform.foundation.organizationaccess.application.authorization.dto;

import java.time.Instant;
import java.util.Set;

/** 平台账户目录的安全摘要；安全变更时间不包含凭据或认证修订。 */
public record PlatformAccountSummary(
        long subjectId,
        String loginName,
        String status,
        long version,
        Instant createdAt,
        Set<String> activeRoleCodes,
        Instant lastSecurityChangeAt) {

    public PlatformAccountSummary(
            long subjectId, String loginName, String status, long version,
            Instant createdAt, Set<String> activeRoleCodes) {
        this(subjectId, loginName, status, version, createdAt, activeRoleCodes, null);
    }
}
