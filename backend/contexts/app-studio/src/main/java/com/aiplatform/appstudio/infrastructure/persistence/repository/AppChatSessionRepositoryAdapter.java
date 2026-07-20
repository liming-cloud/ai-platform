package com.aiplatform.appstudio.infrastructure.persistence.repository;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.appstudio.domain.ChatMessage;
import com.aiplatform.appstudio.domain.ChatSession;
import com.aiplatform.appstudio.domain.port.ChatSessionRepository;
import com.aiplatform.appstudio.infrastructure.persistence.entity.AppChatMessagePersistenceEntity;
import com.aiplatform.appstudio.infrastructure.persistence.entity.AppChatSessionPersistenceEntity;
import com.aiplatform.appstudio.infrastructure.persistence.entity.KbCitationRecordPersistenceEntity;
import com.aiplatform.appstudio.infrastructure.persistence.mapper.AppChatSessionMapper;
import com.aiplatform.shared.kernel.TenantId;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class AppChatSessionRepositoryAdapter implements ChatSessionRepository {
    private final AppChatSessionMapper mapper;

    public AppChatSessionRepositoryAdapter(AppChatSessionMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "聊天会话 Mapper 不能为空");
    }

    @Override
    public String nextSessionId() {
        Long id = mapper.nextSessionId();
        if (id == null) {
            throw new IllegalStateException("聊天会话 ID 生成失败");
        }
        return String.valueOf(id);
    }

    @Override
    public String nextMessageId() {
        Long id = mapper.nextMessageId();
        if (id == null) {
            throw new IllegalStateException("聊天消息 ID 生成失败");
        }
        return String.valueOf(id);
    }

    @Override
    public ChatSession save(ChatSession session) {
        AppChatSessionPersistenceEntity entity = AppChatSessionPersistenceEntity.fromDomain(session);
        if (mapper.insert(entity) != 1) {
            throw new IllegalStateException("聊天会话保存失败");
        }
        return entity.toDomain();
    }

    @Override
    public Optional<ChatSession> findById(TenantId tenantId, String sessionId) {
        AppChatSessionPersistenceEntity entity = mapper.selectOne(new LambdaQueryWrapper<AppChatSessionPersistenceEntity>()
                .eq(AppChatSessionPersistenceEntity::getTenantId, parseRequiredLong(tenantId.value(), "租户 ID"))
                .eq(AppChatSessionPersistenceEntity::getId, parseRequiredLong(sessionId, "会话 ID"))
                .eq(AppChatSessionPersistenceEntity::getStatus, "ACTIVE")
                .isNull(AppChatSessionPersistenceEntity::getDeletedAt)
                .last("limit 1"));
        return Optional.ofNullable(entity).map(AppChatSessionPersistenceEntity::toDomain);
    }

    @Override
    public boolean existsByClientMessageId(TenantId tenantId, String sessionId, String clientMessageId) {
        return mapper.existsByClientMessageId(
                parseRequiredLong(tenantId.value(), "租户 ID"),
                parseRequiredLong(sessionId, "会话 ID"),
                Objects.requireNonNull(clientMessageId, "客户端消息 ID 不能为空").trim()
        );
    }

    @Override
    public List<ChatSession> listSessions(TenantId tenantId, String applicationId, String userId) {
        return mapper.findSessions(
                        parseRequiredLong(tenantId.value(), "租户 ID"),
                        parseRequiredLong(applicationId, "应用 ID"),
                        parseRequiredLong(userId, "用户 ID"))
                .stream()
                .map(AppChatSessionPersistenceEntity::toDomain)
                .toList();
    }

    @Override
    public ChatMessage appendMessage(ChatMessage message) {
        ChatSession session = findById(message.tenantId(), message.sessionId())
                .orElseThrow(() -> new IllegalArgumentException("聊天会话不存在或已被删除"));
        AppChatMessagePersistenceEntity entity = AppChatMessagePersistenceEntity.fromDomain(message, session.applicationId(), session.userId());
        if (mapper.insertMessage(entity) != 1) {
            throw new IllegalStateException("聊天消息保存失败");
        }
        for (KbCitationRecordPersistenceEntity citationRecord : KbCitationRecordPersistenceEntity.fromDomain(message)) {
            if (mapper.insertCitationRecord(citationRecord) != 1) {
                throw new IllegalStateException("聊天引用记录保存失败");
            }
        }
        if (mapper.touchSession(
                        parseRequiredLong(message.tenantId().value(), "租户 ID"),
                        parseRequiredLong(message.sessionId(), "会话 ID"),
                        message.createdAt()) != 1) {
            throw new IllegalStateException("聊天会话最近消息时间更新失败");
        }
        return message;
    }

    @Override
    public List<ChatMessage> listMessages(TenantId tenantId, String sessionId) {
        Long tenantDatabaseId = parseRequiredLong(tenantId.value(), "租户 ID");
        List<AppChatMessagePersistenceEntity> messages = mapper.findMessages(tenantDatabaseId, parseRequiredLong(sessionId, "会话 ID"));
        if (messages.isEmpty()) {
            return List.of();
        }
        List<Long> messageIds = messages.stream().map(AppChatMessagePersistenceEntity::getId).toList();
        Map<Long, List<com.aiplatform.appstudio.domain.Citation>> citationsByMessageId = new LinkedHashMap<>();
        for (KbCitationRecordPersistenceEntity record : mapper.findCitationRecordsByMessageIds(tenantDatabaseId, messageIds)) {
            citationsByMessageId
                    .computeIfAbsent(record.getChatMessageId(), ignored -> new ArrayList<>())
                    .add(record.toDomain());
        }
        return messages.stream()
                .map(entity -> entity.toDomain(citationsByMessageId.getOrDefault(entity.getId(), List.of())))
                .toList();
    }
}
