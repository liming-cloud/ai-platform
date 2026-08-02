package com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat;

import java.util.Objects;

/**
 * 冻结聊天路由中一次候选调用的最小结果；不包含请求、回答或供应商错误文本。
 */
public record ChatRouteAttempt(GovernedChatRouteCandidate candidate, Outcome outcome) {

    public ChatRouteAttempt {
        candidate = Objects.requireNonNull(candidate, "聊天路由候选不能为空");
        outcome = Objects.requireNonNull(outcome, "聊天路由候选结果不能为空");
    }

    /**
     * 候选调用的受控结果类别。
     */
    public enum Outcome {
        SUCCEEDED,
        TRANSIENT_FAILURE
    }
}
