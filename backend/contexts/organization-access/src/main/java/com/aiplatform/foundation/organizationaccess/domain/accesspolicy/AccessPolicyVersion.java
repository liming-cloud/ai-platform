package com.aiplatform.foundation.organizationaccess.domain.accesspolicy;

import java.time.Instant;
import java.util.Set;

/** 策略版本冻结发布候选与成员范围；只有 DRAFT 可保存或发布，预览指纹绑定真实依赖。 */
public record AccessPolicyVersion(long id, long policyId, int number, String status, long revision,
        long assistantId, long assistantVersionId, String assistantName, long knowledgeSpaceId, String knowledgeSpaceName,
        Set<Long> membershipIds, long authorizationRevision, long tenantFeatureVersion,
        String previewFingerprint, int allowCount, int denyCount, int leakCount, Instant previewedAt, Long publishedAuthorizationRevision) {
    public AccessPolicyVersion {
        membershipIds = Set.copyOf(membershipIds);
        if (id < 1 || policyId < 1 || number < 1 || revision < 0 || assistantId < 1 || assistantVersionId < 1
                || knowledgeSpaceId < 1 || membershipIds.isEmpty() || authorizationRevision < 0 || tenantFeatureVersion < 0
                || allowCount < 0 || denyCount < 0 || leakCount < 0
                || !Set.of("DRAFT", "PUBLISHED", "SUPERSEDED").contains(status)) {
            throw new IllegalArgumentException("策略版本事实不完整");
        }
    }
    /** @throws IllegalStateException 已发布与已被取代的历史正文均不可编辑或重放。 */
    public void requireDraft() {
        if (!status.equals("DRAFT")) throw new IllegalStateException("已发布版本不可修改，请建立新的策略草稿");
    }
}
