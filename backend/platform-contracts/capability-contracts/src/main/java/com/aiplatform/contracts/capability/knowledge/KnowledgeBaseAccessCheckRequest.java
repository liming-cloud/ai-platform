package com.aiplatform.contracts.capability.knowledge;

import com.aiplatform.shared.kernel.TenantId;
import java.util.List;
import java.util.Objects;

public record KnowledgeBaseAccessCheckRequest(
        TenantId tenantId,
        String principalType,
        String principalId,
        List<String> knowledgeBaseIds
) {
    public KnowledgeBaseAccessCheckRequest {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        principalType = KnowledgeRetrievalContracts.requireText(principalType, "主体类型");
        principalId = KnowledgeRetrievalContracts.requireText(principalId, "主体 ID");
        knowledgeBaseIds = KnowledgeRetrievalContracts.normalizeTextList(knowledgeBaseIds, "知识库 ID");
    }
}
