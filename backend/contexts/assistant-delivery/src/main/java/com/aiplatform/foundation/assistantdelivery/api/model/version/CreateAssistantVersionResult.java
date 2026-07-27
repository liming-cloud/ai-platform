package com.aiplatform.foundation.assistantdelivery.api.model.version;

import java.util.Objects;

/**
 * 助手版本创建结果及幂等重放事实。
 */
public record CreateAssistantVersionResult(
        AssistantVersionView assistantVersion,
        boolean replayed) {

    public CreateAssistantVersionResult {
        Objects.requireNonNull(assistantVersion, "助手版本视图不能为空");
    }
}
