package com.aiplatform.foundation.modelruntime.application.runtime.facade;

import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatMessage;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatRouteExecution;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.GovernedChatRouteCandidate;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.List;

/**
 * 按冻结顺序执行受控聊天候选，并且只在调用前瞬态失败时尝试后备候选。
 */
public interface ChatRouteExecutor {

    ChatRouteExecution execute(
            TrustedRequestContext context,
            List<GovernedChatRouteCandidate> candidates,
            List<ChatMessage> messages,
            int maxOutputTokens);
}
