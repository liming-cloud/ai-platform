package com.aiplatform.foundation.organizationaccess.application.member.dto;

import com.aiplatform.foundation.organizationaccess.domain.member.model.ConversationFeatures;
import java.time.Instant;
import java.util.Set;

/** 组织内部的无秘密身份、资格和授权事实；主体标识不得进入浏览器响应。 */
public record MemberAccountFact(long membershipId, long subjectId, String loginName,
        String membershipRole, String status, boolean identityActive, long identityVersion,
        long version, long permissionVersion, Instant createdAt, ConversationFeatures features,
        long featureVersion, Set<Long> assistantIds, boolean passwordResetEligible, boolean accessReady) {
    public MemberAccountFact {
        assistantIds = Set.copyOf(assistantIds);
    }
}
