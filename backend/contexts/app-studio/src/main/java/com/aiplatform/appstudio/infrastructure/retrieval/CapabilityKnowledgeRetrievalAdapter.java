package com.aiplatform.appstudio.infrastructure.retrieval;

import com.aiplatform.appstudio.domain.port.retrieval.KnowledgeRetrievalPort;
import com.aiplatform.contracts.capability.knowledge.KnowledgeRetrievalCapability;
import com.aiplatform.contracts.capability.knowledge.KnowledgeRetrievalRequest;
import com.aiplatform.contracts.capability.knowledge.KnowledgeRetrievalResponse;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * app-studio 知识检索出站适配器，只依赖正式 capability-contracts 契约。
 * 当前运行时未装配 knowledge-base provider 时必须 fail-closed，禁止恢复跨上下文实现依赖或临时桥接模块。
 */
@Component
public class CapabilityKnowledgeRetrievalAdapter implements KnowledgeRetrievalPort {
    static final String CAPABILITY_NOT_AVAILABLE_REASON = "知识检索开放能力提供方尚未接入，已按架构基线关闭回答";

    private final ObjectProvider<KnowledgeRetrievalCapability> capabilityProvider;

    public CapabilityKnowledgeRetrievalAdapter(ObjectProvider<KnowledgeRetrievalCapability> capabilityProvider) {
        this.capabilityProvider = Objects.requireNonNull(capabilityProvider, "知识检索能力提供方不能为空");
    }

    @Override
    public KnowledgeRetrievalResponse retrieve(KnowledgeRetrievalRequest request) {
        Objects.requireNonNull(request, "知识检索请求不能为空");
        KnowledgeRetrievalCapability capability = capabilityProvider.getIfAvailable();
        if (capability == null) {
            return new KnowledgeRetrievalResponse(
                    false,
                    List.of(),
                    List.of(),
                    CAPABILITY_NOT_AVAILABLE_REASON
            );
        }
        return capability.retrieve(request);
    }
}
