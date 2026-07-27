package com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat;

import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.common.ModelUsage;
import java.util.Objects;

/**
 * 对话模型的完整回答及标准化用量。
 */
public record ChatResult(
        ChatDeploymentRef deployment,
        String content,
        String finishReason,
        ModelUsage usage) {

    public ChatResult {
        Objects.requireNonNull(deployment, "对话部署不能为空");
        Objects.requireNonNull(content, "对话回答不能为空");
        Objects.requireNonNull(finishReason, "对话结束原因不能为空");
        Objects.requireNonNull(usage, "对话用量不能为空");
        if (finishReason.isBlank()) {
            throw new IllegalArgumentException("对话结束原因不能为空");
        }
    }
}
