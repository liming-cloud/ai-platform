package com.aiplatform.appstudio.infrastructure.persistence.repository;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.appstudio.domain.ChatMessageFeedback;
import com.aiplatform.appstudio.domain.port.ChatMessageFeedbackRepository;
import com.aiplatform.appstudio.infrastructure.persistence.mapper.AppChatMessageFeedbackMapper;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import java.util.Objects;
import org.springframework.stereotype.Repository;

@Repository
public class AppChatMessageFeedbackRepositoryAdapter implements ChatMessageFeedbackRepository {
    private final AppChatMessageFeedbackMapper mapper;
    private final JsonMapper objectMapper;

    public AppChatMessageFeedbackRepositoryAdapter(AppChatMessageFeedbackMapper mapper, JsonMapper objectMapper) {
        this.mapper = Objects.requireNonNull(mapper, "反馈 Mapper 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 序列化器不能为空");
    }

    @Override
    public ChatMessageFeedback save(ChatMessageFeedback feedback) {
        Objects.requireNonNull(feedback, "消息反馈不能为空");
        String persistedFeedbackId = mapper.upsertFeedback(
                parseRequiredLong(feedback.tenantId().value(), "租户 ID"), feedback.feedbackId(),
                parseRequiredLong(feedback.sessionId(), "会话 ID"),
                parseRequiredLong(feedback.assistantMessageId(), "助手消息 ID"),
                parseRequiredLong(feedback.userId(), "用户 ID"), feedback.rating().name(),
                labelsJson(feedback), feedback.comment(), feedback.updatedAt()
        );
        if (persistedFeedbackId == null || persistedFeedbackId.isBlank()) {
            throw new IllegalStateException("消息反馈保存后未返回持久化 ID");
        }
        return new ChatMessageFeedback(
                feedback.tenantId(),
                persistedFeedbackId,
                feedback.sessionId(),
                feedback.assistantMessageId(),
                feedback.userId(),
                feedback.rating(),
                feedback.labels(),
                feedback.comment(),
                feedback.updatedAt()
        );
    }

    private String labelsJson(ChatMessageFeedback feedback) {
        try {
            return objectMapper.writeValueAsString(feedback.labels());
        } catch (JacksonException exception) {
            throw new IllegalStateException("反馈标签序列化失败", exception);
        }
    }
}
