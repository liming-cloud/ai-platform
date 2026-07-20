package com.aiplatform.knowledge.infrastructure.springai.adapter;

import com.aiplatform.infra.springai.embedding.EmbeddingClientPort;
import com.aiplatform.infra.springai.embedding.model.EmbeddingRuntimeRequest;
import com.aiplatform.knowledge.domain.port.embedding.KnowledgeDocumentEmbeddingPort;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelRuntimeDescriptor;
import com.aiplatform.shared.kernel.TenantId;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Spring AI 文档切片向量化适配器，放在 knowledge-base 基础设施层承接领域端口。
 */
@Component
public class SpringAiKnowledgeDocumentEmbeddingAdapter implements KnowledgeDocumentEmbeddingPort {
    private final EmbeddingClientPort embeddingClient;

    public SpringAiKnowledgeDocumentEmbeddingAdapter(EmbeddingClientPort embeddingClient) {
        this.embeddingClient = Objects.requireNonNull(embeddingClient, "Embedding 客户端不能为空");
    }

    @Override
    public float[] embedChunk(
            TenantId tenantId,
            KnowledgeModelRuntimeDescriptor runtimeDescriptor,
            String chunkContent
    ) {
        return embeddingClient.embed(toRuntimeRequest(tenantId, runtimeDescriptor), chunkContent);
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
