package com.aiplatform.foundation.modelruntime.application.runtime.facade;

import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatChunk;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatRequest;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatResult;
import java.util.concurrent.Flow;

/**
 * 同步与流式对话共同使用的框架无关模型契约。
 */
public interface ChatModelPort {

    ChatResult chat(ChatRequest request);

    Flow.Publisher<ChatChunk> stream(ChatRequest request);
}
