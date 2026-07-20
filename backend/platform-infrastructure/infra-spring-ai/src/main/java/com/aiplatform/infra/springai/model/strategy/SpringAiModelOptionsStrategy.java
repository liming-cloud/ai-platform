package com.aiplatform.infra.springai.model.strategy;

import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import com.aiplatform.infra.springai.model.result.SpringAiModelValidationResult;
import org.springframework.ai.model.ModelOptions;

/**
 * Spring AI 模型 options 策略，负责不同模型类型的配置校验和运行时 options 构建。
 */
public interface SpringAiModelOptionsStrategy {
    String name();

    boolean supports(SpringAiModelRuntimeRequest request);

    SpringAiModelValidationResult validate(SpringAiModelRuntimeRequest request);

    ModelOptions buildOptions(SpringAiModelRuntimeRequest request);
}
