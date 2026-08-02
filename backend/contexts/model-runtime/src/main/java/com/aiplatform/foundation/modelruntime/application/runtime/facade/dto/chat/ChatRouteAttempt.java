package com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat;

import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.common.ModelUsage;
import java.util.Objects;

/**
 * 冻结聊天路由中一次候选调用的最小结果；不包含请求、回答或供应商错误文本。
 */
public record ChatRouteAttempt(
        GovernedChatRouteCandidate candidate,
        Outcome outcome,
        ModelUsage usage,
        long latencyMillis) {

    public ChatRouteAttempt {
        candidate = Objects.requireNonNull(candidate, "聊天路由候选不能为空");
        outcome = Objects.requireNonNull(outcome, "聊天路由候选结果不能为空");
        usage = Objects.requireNonNull(usage, "聊天路由候选用量不能为空");
        if (latencyMillis < 0) {
            throw new IllegalArgumentException("聊天路由候选延迟不能为负数");
        }
    }

    /**
     * 候选调用的受控结果类别。
     */
    public enum Outcome {
        SUCCEEDED,
        TRANSIENT_FAILURE
    }
}
