package com.aiplatform.knowledge.presentation.capability;

import com.aiplatform.contracts.capability.knowledge.KnowledgeBaseAccessCheckCapability;
import com.aiplatform.contracts.capability.knowledge.KnowledgeBaseAccessCheckItem;
import com.aiplatform.contracts.capability.knowledge.KnowledgeBaseAccessCheckRequest;
import com.aiplatform.contracts.capability.knowledge.KnowledgeBaseAccessCheckResponse;
import com.aiplatform.knowledge.domain.KnowledgeBase;
import com.aiplatform.knowledge.domain.port.KnowledgeBaseRepository;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 只暴露发布绑定所需的可用性决策，不向 AppOps 泄露知识库领域模型或 ACL 明细。
 */
@Component
public class KnowledgeBaseAccessCheckCapabilityProvider implements KnowledgeBaseAccessCheckCapability {
    static final String RESOURCE_NOT_VISIBLE = "resource_not_visible";
    static final String KNOWLEDGE_BASE_NOT_PUBLISHED = "knowledge_base_not_published";

    private final KnowledgeBaseRepository knowledgeBases;

    public KnowledgeBaseAccessCheckCapabilityProvider(KnowledgeBaseRepository knowledgeBases) {
        this.knowledgeBases = Objects.requireNonNull(knowledgeBases, "知识库仓储不能为空");
    }

    @Override
    public KnowledgeBaseAccessCheckResponse check(KnowledgeBaseAccessCheckRequest request) {
        Objects.requireNonNull(request, "知识库访问检查请求不能为空");
        return new KnowledgeBaseAccessCheckResponse(request.knowledgeBaseIds().stream()
                .map(knowledgeBaseId -> checkOne(request, knowledgeBaseId))
                .toList());
    }

    private KnowledgeBaseAccessCheckItem checkOne(KnowledgeBaseAccessCheckRequest request, String knowledgeBaseId) {
        KnowledgeBase knowledgeBase = knowledgeBases.findById(request.tenantId(), knowledgeBaseId).orElse(null);
        if (knowledgeBase == null) {
            return KnowledgeBaseAccessCheckItem.denied(knowledgeBaseId, RESOURCE_NOT_VISIBLE);
        }
        if (!knowledgeBase.published()) {
            return KnowledgeBaseAccessCheckItem.denied(knowledgeBaseId, KNOWLEDGE_BASE_NOT_PUBLISHED);
        }
        if (!knowledgeBase.canBeRetrievedBy(request.principalId())) {
            return KnowledgeBaseAccessCheckItem.denied(knowledgeBaseId, RESOURCE_NOT_VISIBLE);
        }
        return KnowledgeBaseAccessCheckItem.allowed(knowledgeBaseId);
    }
}
