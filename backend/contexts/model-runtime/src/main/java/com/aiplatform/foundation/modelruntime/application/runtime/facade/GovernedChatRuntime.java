package com.aiplatform.foundation.modelruntime.application.runtime.facade;

import com.aiplatform.foundation.modelruntime.application.configuration.dto.UsableModelConfigurationSnapshot;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatRequest;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatResult;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;

/**
 * 使用受控模型配置执行单次对话调用，调用结束后不保留 API Key 相关运行时对象。
 */
public interface GovernedChatRuntime {

    /**
     * 按冻结配置快照执行对话；快照、授权或目录状态变化时拒绝调用。
     */
    ChatResult chat(
            TrustedRequestContext context,
            UsableModelConfigurationSnapshot expected,
            ChatRequest request);
}
