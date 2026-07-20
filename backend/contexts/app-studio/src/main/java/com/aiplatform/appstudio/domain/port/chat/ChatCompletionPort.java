package com.aiplatform.appstudio.domain.port.chat;

import com.aiplatform.appstudio.domain.port.chat.model.ChatCompletionRequest;
import com.aiplatform.appstudio.domain.port.chat.model.ChatCompletionResult;

/**
 * AI 应用聊天生成端口，调用前必须先由本上下文模型使用策略端口产出运行时描述。
 */
public interface ChatCompletionPort {
    ChatCompletionResult complete(ChatCompletionRequest request);
}
