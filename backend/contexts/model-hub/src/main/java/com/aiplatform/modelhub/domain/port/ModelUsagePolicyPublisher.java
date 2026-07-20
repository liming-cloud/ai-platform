package com.aiplatform.modelhub.domain.port;

import com.aiplatform.modelhub.domain.ModelConfig;
import com.aiplatform.modelhub.domain.ModelProvider;

/**
 * 模型使用策略发布端口，用领域事件向各业务上下文同步本地投影。
 */
public interface ModelUsagePolicyPublisher {
    void publish(ModelProvider provider, ModelConfig config);
}
