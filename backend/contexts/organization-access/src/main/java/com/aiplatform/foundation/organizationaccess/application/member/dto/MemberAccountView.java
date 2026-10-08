package com.aiplatform.foundation.organizationaccess.application.member.dto;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/** 账户独立页的最小投影，只有资源定位和版本，不含主体、凭据或会话。 */
public record MemberAccountView(String membershipId, String loginName, String membershipRole,
        String status, long version, long identityVersion, long permissionVersion, Instant createdAt,
        Map<String, Boolean> conversationFeatures, long featureVersion, Set<String> assistantIds,
        boolean passwordResetEligible) { }
