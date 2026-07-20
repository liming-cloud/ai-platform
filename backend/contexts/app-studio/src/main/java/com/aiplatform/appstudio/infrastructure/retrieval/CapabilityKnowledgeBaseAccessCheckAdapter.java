package com.aiplatform.appstudio.infrastructure.retrieval;

import com.aiplatform.appstudio.domain.port.retrieval.KnowledgeBaseAccessCheckPort;
import com.aiplatform.contracts.capability.knowledge.KnowledgeBaseAccessCheckCapability;
import com.aiplatform.contracts.capability.knowledge.KnowledgeBaseAccessCheckItem;
import com.aiplatform.contracts.capability.knowledge.KnowledgeBaseAccessCheckRequest;
import com.aiplatform.contracts.capability.knowledge.KnowledgeBaseAccessCheckResponse;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class CapabilityKnowledgeBaseAccessCheckAdapter implements KnowledgeBaseAccessCheckPort {
    static final String CAPABILITY_NOT_AVAILABLE = "knowledge_access_capability_not_available";

    private final ObjectProvider<KnowledgeBaseAccessCheckCapability> capabilityProvider;

    public CapabilityKnowledgeBaseAccessCheckAdapter(
            ObjectProvider<KnowledgeBaseAccessCheckCapability> capabilityProvider
    ) {
        this.capabilityProvider = Objects.requireNonNull(capabilityProvider, "知识库访问检查能力提供方不能为空");
    }

    @Override
    public KnowledgeBaseAccessCheckResponse check(KnowledgeBaseAccessCheckRequest request) {
        Objects.requireNonNull(request, "知识库访问检查请求不能为空");
        KnowledgeBaseAccessCheckCapability capability = capabilityProvider.getIfAvailable();
        if (capability == null) {
            return new KnowledgeBaseAccessCheckResponse(request.knowledgeBaseIds().stream()
                    .map(id -> KnowledgeBaseAccessCheckItem.denied(id, CAPABILITY_NOT_AVAILABLE))
                    .toList());
        }
        return capability.check(request);
    }
}
