package com.aiplatform.foundation.modelruntime.api.invocation;

import com.aiplatform.foundation.modelruntime.api.model.ChatChunk;
import com.aiplatform.foundation.modelruntime.api.model.ChatRequest;
import com.aiplatform.foundation.modelruntime.api.model.ChatResult;
import java.util.concurrent.Flow;

/**
 * 同步与流式对话共同使用的框架无关模型契约。
 */
public interface ChatModelPort {

    ChatResult chat(ChatRequest request);

    Flow.Publisher<ChatChunk> stream(ChatRequest request);
}
