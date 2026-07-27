package com.aiplatform.foundation.provider.deterministic;

import com.aiplatform.foundation.modelruntime.api.extension.EmbeddingModelFactory;
import com.aiplatform.foundation.modelruntime.api.invocation.EmbeddingModelPort;
import com.aiplatform.foundation.modelruntime.api.model.embedding.EmbeddingDeploymentRef;
import com.aiplatform.foundation.modelruntime.api.model.common.ModelRuntimeSpecification;
import com.aiplatform.foundation.modelruntime.api.model.common.ModelType;
import com.aiplatform.foundation.modelruntime.api.model.provider.ProviderKind;
import java.util.Objects;

/**
 * 构造与目录部署修订绑定的确定性嵌入模型。
 */
public final class DeterministicEmbeddingModelFactory implements EmbeddingModelFactory {

    @Override
    public ProviderKind providerKind() {
        return ProviderKind.DETERMINISTIC;
    }

    @Override
    public EmbeddingModelPort create(ModelRuntimeSpecification specification) {
        Objects.requireNonNull(specification, "模型运行时配置不能为空");
        if (specification.providerKind() != providerKind()
                || specification.modelType() != ModelType.EMBEDDING) {
            throw new IllegalArgumentException("确定性嵌入模型配置类型不匹配");
        }
        return new DeterministicEmbeddingModel(new EmbeddingDeploymentRef(
                specification.deploymentId(),
                specification.deploymentRevision(),
                specification.embeddingDimension()));
    }
}
