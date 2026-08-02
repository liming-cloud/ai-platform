package com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat;

import java.util.Objects;
import java.util.List;

/**
 * 聊天路由执行结果，明确记录实际命中的冻结候选。
 */
public record ChatRouteExecution(
        GovernedChatRouteCandidate selected,
        ChatResult result,
        List<ChatRouteAttempt> attempts) {

    public ChatRouteExecution {
        selected = Objects.requireNonNull(selected, "命中聊天候选不能为空");
        result = Objects.requireNonNull(result, "聊天执行结果不能为空");
        attempts = List.copyOf(attempts);
        if (attempts.isEmpty()
                || attempts.stream().anyMatch(value -> value == null)
                || attempts.getLast().outcome() != ChatRouteAttempt.Outcome.SUCCEEDED
                || !selected.equals(attempts.getLast().candidate())
                || attempts.subList(0, attempts.size() - 1).stream().anyMatch(value -> value.outcome()
                        != ChatRouteAttempt.Outcome.TRANSIENT_FAILURE)) {
            throw new IllegalArgumentException("成功聊天路由轨迹不正确");
        }
    }
}
