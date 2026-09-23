package com.aiplatform.foundation.organizationaccess.application.member.dto;

import java.time.Instant;

/** 平台租户管理员目录的最小安全摘要。 */
public record TenantAdministratorSummary(
        String membershipReference,
        String loginName,
        String status,
        long permissionVersion,
        long version,
        Instant createdAt,
        Instant lastSecurityChangeAt) {
}
