package com.aiplatform.foundation.provider.deterministic;

import com.aiplatform.foundation.modelruntime.application.runtime.port.out.EmbeddingModelFactory;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.EmbeddingModelPort;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingDeploymentRef;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.common.ModelRuntimeSpecification;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
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
