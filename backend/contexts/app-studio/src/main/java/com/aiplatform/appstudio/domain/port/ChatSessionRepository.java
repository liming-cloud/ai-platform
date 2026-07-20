package com.aiplatform.appstudio.domain.port;

import com.aiplatform.appstudio.domain.ChatMessage;
import com.aiplatform.appstudio.domain.ChatSession;
import com.aiplatform.shared.kernel.TenantId;
import java.util.List;
import java.util.Optional;

/**
 * 会话仓储端口，负责会话与消息的持久化读写。
 */
public interface ChatSessionRepository {
    String nextSessionId();

    String nextMessageId();

    ChatSession save(ChatSession session);

    Optional<ChatSession> findById(TenantId tenantId, String sessionId);

    boolean existsByClientMessageId(TenantId tenantId, String sessionId, String clientMessageId);

    List<ChatSession> listSessions(TenantId tenantId, String applicationId, String userId);

    ChatMessage appendMessage(ChatMessage message);

    List<ChatMessage> listMessages(TenantId tenantId, String sessionId);
}
