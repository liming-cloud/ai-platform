package com.aiplatform.foundation.modelruntime.application.runtime.facade;

import com.aiplatform.foundation.modelruntime.application.runtime.facade.ChatInvocationTransientException;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ChatRouteExhaustedException;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ChatRouteExecutor;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.GovernedChatRuntime;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatDeploymentRef;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatMessage;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatRouteExecution;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatRouteAttempt;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.GovernedChatRouteCandidate;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatRequest;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.common.ModelUsage;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 受控聊天路由的唯一后备循环；配置与输出契约错误不会被视为可后备失败。
 */
public final class DefaultChatRouteExecutor implements ChatRouteExecutor {

    private static final int MAX_CANDIDATES = 3;

    private final GovernedChatRuntime chats;

    public DefaultChatRouteExecutor(GovernedChatRuntime chats) {
        this.chats = Objects.requireNonNull(chats, "受控对话运行时不能为空");
    }

    @Override
    public ChatRouteExecution execute(
            TrustedRequestContext context,
            List<GovernedChatRouteCandidate> candidates,
            List<ChatMessage> messages,
            int maxOutputTokens) {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        candidates = List.copyOf(candidates);
        messages = List.copyOf(messages);
        if (candidates.isEmpty() || candidates.size() > MAX_CANDIDATES || maxOutputTokens < 1) {
            throw new IllegalArgumentException("聊天路由请求不完整");
        }
        for (var index = 0; index < candidates.size(); index++) {
            var candidate = candidates.get(index);
            Objects.requireNonNull(candidate, "聊天候选不能为空");
            if (maxOutputTokens > candidate.configuration().capabilities().outputLimit()) {
                throw new IllegalArgumentException("回答上限超过聊天候选能力");
            }
        }
        var attempts = new ArrayList<ChatRouteAttempt>();
        ChatInvocationTransientException lastFailure = null;
        for (var index = 0; index < candidates.size(); index++) {
            var candidate = candidates.get(index);
            var configuration = candidate.configuration();
            var deployment = new ChatDeploymentRef(
                    configuration.configurationId().value(), configuration.configurationRevision());
            var startedAt = System.nanoTime();
            try {
                var result = chats.chat(context, configuration, new ChatRequest(deployment, messages, maxOutputTokens));
                if (!deployment.equals(result.deployment())) {
                    throw new IllegalStateException("对话结果不符合冻结聊天候选");
                }
                attempts.add(new ChatRouteAttempt(
                        candidate, ChatRouteAttempt.Outcome.SUCCEEDED, result.usage(), elapsedMillis(startedAt)));
                return new ChatRouteExecution(candidate, result, attempts);
            } catch (ChatInvocationTransientException exception) {
                attempts.add(new ChatRouteAttempt(
                        candidate,
                        ChatRouteAttempt.Outcome.TRANSIENT_FAILURE,
                        ModelUsage.UNKNOWN,
                        elapsedMillis(startedAt)));
                lastFailure = exception;
            }
        }
        throw new ChatRouteExhaustedException(
                attempts,
                Objects.requireNonNull(lastFailure, "聊天路由未产生失败原因"));
    }

    private static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
