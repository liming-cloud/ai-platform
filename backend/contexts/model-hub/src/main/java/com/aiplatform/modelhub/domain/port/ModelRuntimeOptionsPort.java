package com.aiplatform.modelhub.domain.port;

import com.aiplatform.modelhub.domain.ModelConfig;
import com.aiplatform.modelhub.domain.ModelProvider;
import com.aiplatform.modelhub.domain.ModelRuntimeConnectivity;
import com.aiplatform.modelhub.domain.ModelRuntimeValidation;

/**
 * 模型运行时 options 端口，由基础设施适配 Spring AI 的策略工厂和连通性探测。
 */
public interface ModelRuntimeOptionsPort {
    ModelRuntimeValidation validate(ModelProvider provider, ModelConfig config);

    ModelRuntimeConnectivity testConnectivity(ModelProvider provider, ModelConfig config);
}
