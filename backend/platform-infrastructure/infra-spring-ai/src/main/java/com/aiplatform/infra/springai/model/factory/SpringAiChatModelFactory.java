package com.aiplatform.infra.springai.model.factory;

import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * Spring AI ChatModel 工厂，只接收平台内部受控运行时请求。
 */
public interface SpringAiChatModelFactory {
    boolean supports(SpringAiModelRuntimeRequest request);

    ChatModel create(SpringAiModelRuntimeRequest request, ChatOptions options);
}
