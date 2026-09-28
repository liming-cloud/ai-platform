package com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.response;

import com.aiplatform.foundation.knowledgedata.application.knowledge.facade.KnowledgeQuotaUsageSnapshot;

/** 平台可见的脱敏已用量，不包含任何租户知识正文或来源标识。 */
public record TenantQuotaUsageResponse(
        long knowledgeBases,
        long documentsPerKnowledgeBase,
        String bytesPerKnowledgeBase,
        boolean complete) {

    public static TenantQuotaUsageResponse from(KnowledgeQuotaUsageSnapshot value) {
        return new TenantQuotaUsageResponse(value.knowledgeBases(), value.documentsPerKnowledgeBase(),
                value.bytesPerKnowledgeBase().toString(), value.complete());
    }
}
