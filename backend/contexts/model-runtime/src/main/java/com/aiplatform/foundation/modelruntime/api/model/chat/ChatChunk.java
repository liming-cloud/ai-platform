package com.aiplatform.foundation.modelruntime.api.model.chat;

import com.aiplatform.foundation.modelruntime.api.model.common.ModelUsage;
import java.util.Objects;

/**
 * 带单调序号的对话流片段。
 */
public record ChatChunk(
        ChatDeploymentRef deployment,
        long sequence,
        String contentDelta,
        boolean completed,
        ModelUsage usage) {

    public ChatChunk {
        Objects.requireNonNull(deployment, "对话部署不能为空");
        Objects.requireNonNull(contentDelta, "对话增量内容不能为空");
        if (sequence < 1) {
            throw new IllegalArgumentException("对话流序号必须大于零");
        }
        if (!completed && usage != null) {
            throw new IllegalArgumentException("只有结束片段可以携带最终用量");
        }
    }
}
