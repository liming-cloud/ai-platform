package com.aiplatform.foundation.provider.deterministic;

import com.aiplatform.foundation.modelruntime.application.runtime.port.out.ChatModelFactory;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ChatModelPort;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatDeploymentRef;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.common.ModelRuntimeSpecification;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import java.util.Objects;

/**
 * 构造与目录部署修订绑定的确定性对话模型。
 */
public final class DeterministicChatModelFactory implements ChatModelFactory {

    @Override
    public ProviderKind providerKind() {
        return ProviderKind.DETERMINISTIC;
    }

    @Override
    public ChatModelPort create(ModelRuntimeSpecification specification) {
        Objects.requireNonNull(specification, "模型运行时配置不能为空");
        if (specification.providerKind() != providerKind()
                || specification.modelType() != ModelType.CHAT) {
            throw new IllegalArgumentException("确定性对话模型配置类型不匹配");
        }
        return new DeterministicChatModel(new ChatDeploymentRef(
                specification.deploymentId(),
                specification.deploymentRevision()));
    }
}
