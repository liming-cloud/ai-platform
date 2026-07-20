package com.aiplatform.contracts.capability.knowledge;

import com.aiplatform.shared.kernel.TenantId;
import java.util.List;
import java.util.Objects;

/**
 * 知识检索开放能力请求，调用方必须传入服务端可信租户、主体与权限快照。
 * embedding 模型和索引版本由 knowledge-base 根据知识库状态解析，调用方不得猜测。
 */
public record KnowledgeRetrievalRequest(
        TenantId tenantId,
        String principalId,
        String principalType,
        List<String> principalAclTokens,
        List<String> knowledgeBaseIds,
        String query,
        String retrievalProfile,
        int topK,
        double scoreThreshold,
        boolean requireCitations
) {
    public KnowledgeRetrievalRequest {
        tenantId = Objects.requireNonNull(tenantId, "租户上下文不能为空");
        principalId = KnowledgeRetrievalContracts.requireText(principalId, "访问主体");
        principalType = KnowledgeRetrievalContracts.requireText(principalType, "访问主体类型");
        principalAclTokens = KnowledgeRetrievalContracts.normalizeTextList(principalAclTokens, "访问主体权限快照");
        knowledgeBaseIds = KnowledgeRetrievalContracts.normalizeTextList(knowledgeBaseIds, "知识库范围");
        query = KnowledgeRetrievalContracts.requireText(query, "检索问题");
        retrievalProfile = KnowledgeRetrievalContracts.requireText(retrievalProfile, "检索画像");
        if (topK < 1) {
            throw new IllegalArgumentException("召回数量必须为正数");
        }
        scoreThreshold = KnowledgeRetrievalContracts.requireScore(scoreThreshold, "相似度阈值");
    }
}
