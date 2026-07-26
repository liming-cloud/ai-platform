package com.aiplatform.foundation.modelruntime.api.model;

import java.util.List;
import java.util.Objects;

/**
 * 绑定部署修订且数量受限的对话请求。
 */
public record ChatRequest(
        ChatDeploymentRef deployment,
        List<ChatMessage> messages,
        int maxOutputTokens) {

    public static final int MAX_MESSAGES = 64;

    public ChatRequest(
            ChatDeploymentRef deployment,
            List<ChatMessage> messages) {
        this(deployment, messages, 0);
    }

    public ChatRequest {
        Objects.requireNonNull(deployment, "对话部署不能为空");
        Objects.requireNonNull(messages, "对话消息不能为空");
        messages = List.copyOf(messages);
        if (messages.isEmpty() || messages.size() > MAX_MESSAGES) {
            throw new IllegalArgumentException("对话消息数量超出平台范围");
        }
        for (var message : messages) {
            Objects.requireNonNull(message, "对话消息不能包含空值");
        }
        if (maxOutputTokens < 0) {
            throw new IllegalArgumentException("回答令牌上限不能为负数");
        }
    }
}
