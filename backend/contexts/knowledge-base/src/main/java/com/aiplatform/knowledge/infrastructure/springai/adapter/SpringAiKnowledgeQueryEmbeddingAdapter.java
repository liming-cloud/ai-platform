package com.aiplatform.knowledge.infrastructure.springai.adapter;

import com.aiplatform.infra.springai.embedding.EmbeddingClientPort;
import com.aiplatform.infra.springai.embedding.model.EmbeddingRuntimeRequest;
import com.aiplatform.knowledge.domain.port.embedding.KnowledgeQueryEmbeddingPort;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelRuntimeDescriptor;
import com.aiplatform.shared.kernel.TenantId;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Spring AI 查询向量化适配器，供 knowledge-base 检索链路通过领域端口调用。
 */
@Component
public class SpringAiKnowledgeQueryEmbeddingAdapter implements KnowledgeQueryEmbeddingPort {
    private final EmbeddingClientPort embeddingClient;

    public SpringAiKnowledgeQueryEmbeddingAdapter(EmbeddingClientPort embeddingClient) {
        this.embeddingClient = Objects.requireNonNull(embeddingClient, "Embedding 客户端不能为空");
    }

    @Override
    public float[] embedQuery(TenantId tenantId, KnowledgeModelRuntimeDescriptor runtimeDescriptor, String query) {
        return embeddingClient.embed(toRuntimeRequest(tenantId, runtimeDescriptor), query);
    }

    private static EmbeddingRuntimeRequest toRuntimeRequest(
            TenantId tenantId,
            KnowledgeModelRuntimeDescriptor descriptor
    ) {
        Objects.requireNonNull(tenantId, "租户不能为空");
        Objects.requireNonNull(descriptor, "模型运行时描述不能为空");
        return new EmbeddingRuntimeRequest(
                tenantId.value(),
                descriptor.modelConfigId(),
                descriptor.providerId(),
                descriptor.providerCode(),
                descriptor.providerKind(),
                descriptor.connectionRef().endpoint(),
                descriptor.connectionRef().authType(),
                descriptor.connectionRef().secretRefId(),
                descriptor.modelCode(),
                descriptor.modelConfigVersion(),
                descriptor.modelType(),
                descriptor.effectiveOptions(),
                descriptor.effectiveOptionsDigest(),
                descriptor.policyVersion()
        );
    }
}
