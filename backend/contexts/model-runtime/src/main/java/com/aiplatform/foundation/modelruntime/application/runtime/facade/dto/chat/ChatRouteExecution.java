package com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat;

import java.util.Objects;

/**
 * 聊天路由执行结果，明确记录实际命中的冻结候选。
 */
public record ChatRouteExecution(
        GovernedChatRouteCandidate selected,
        ChatResult result,
        int attempts) {

    public ChatRouteExecution {
        selected = Objects.requireNonNull(selected, "命中聊天候选不能为空");
        result = Objects.requireNonNull(result, "聊天执行结果不能为空");
        if (attempts < 1) {
            throw new IllegalArgumentException("聊天路由尝试次数必须大于零");
        }
    }
}
