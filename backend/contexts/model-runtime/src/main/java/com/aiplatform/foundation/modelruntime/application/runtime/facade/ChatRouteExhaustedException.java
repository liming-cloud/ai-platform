package com.aiplatform.foundation.modelruntime.application.runtime.facade;

import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatRouteAttempt;
import java.util.List;
import java.util.Objects;

/**
 * 冻结聊天路由的全部候选均在产生输出前瞬态失败；仅保留最小候选尝试事实。
 */
public final class ChatRouteExhaustedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<ChatRouteAttempt> attempts;

    public ChatRouteExhaustedException(List<ChatRouteAttempt> attempts, ChatInvocationTransientException cause) {
        super("冻结聊天路由的全部候选暂时不可用", Objects.requireNonNull(cause, "聊天路由失败原因不能为空"));
        attempts = List.copyOf(attempts);
        if (attempts.isEmpty() || attempts.stream().anyMatch(value -> value.outcome()
                != ChatRouteAttempt.Outcome.TRANSIENT_FAILURE)) {
            throw new IllegalArgumentException("耗尽的聊天路由必须只包含瞬态失败候选");
        }
        this.attempts = attempts;
    }

    /**
     * 返回按冻结顺序发生的候选尝试，不包含供应商错误细节。
     */
    public List<ChatRouteAttempt> attempts() {
        return attempts;
    }
}
