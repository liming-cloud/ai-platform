package com.aiplatform.infra.springai.model.runtime;

import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import java.util.Objects;
import org.springframework.ai.model.ModelOptions;

/**
 * Spring AI 模型运行时探测请求，携带已经过策略校验的模型 options。
 */
public record SpringAiModelRuntimeProbe(
        SpringAiModelRuntimeRequest request,
        ModelOptions options
) {
    public SpringAiModelRuntimeProbe {
        request = Objects.requireNonNull(request, "模型运行时请求不能为空");
        options = Objects.requireNonNull(options, "Spring AI 模型 options 不能为空");
    }
}
