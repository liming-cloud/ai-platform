package com.aiplatform.appstudio.application.service;

import com.aiplatform.appstudio.application.command.SubmitChatMessageFeedbackCommand;
import com.aiplatform.appstudio.application.error.AppStudioErrorCodes;
import com.aiplatform.appstudio.application.result.ChatMessageFeedbackResult;
import com.aiplatform.appstudio.domain.ChatMessageFeedback;
import com.aiplatform.appstudio.domain.ChatMessageFeedbackRating;
import com.aiplatform.appstudio.domain.ChatRole;
import com.aiplatform.appstudio.domain.ChatSession;
import com.aiplatform.appstudio.domain.port.ChatMessageFeedbackRepository;
import com.aiplatform.appstudio.domain.port.ChatSessionRepository;
import com.aiplatform.shared.kernel.ApplicationException;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatMessageFeedbackApplicationService {
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageFeedbackRepository feedbackRepository;
    private final Clock clock;

    public ChatMessageFeedbackApplicationService(ChatSessionRepository sessionRepository,
                                                 ChatMessageFeedbackRepository feedbackRepository,
                                                 Clock clock) {
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "会话仓储不能为空");
        this.feedbackRepository = Objects.requireNonNull(feedbackRepository, "反馈仓储不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    @Transactional
    public ChatMessageFeedbackResult submit(SubmitChatMessageFeedbackCommand command) {
        Objects.requireNonNull(command, "反馈命令不能为空");
        String actorId = requireText(command.actorId(), "操作者 ID");
        String sessionId = requireText(command.sessionId(), "会话 ID");
        String assistantMessageId = requireText(command.assistantMessageId(), "助手消息 ID");
        ChatSession session = sessionRepository.findById(command.tenantId(), sessionId)
                .filter(candidate -> candidate.userId().equals(actorId))
                .orElseThrow(ChatMessageFeedbackApplicationService::resourceNotVisible);
        boolean messageExists = sessionRepository.listMessages(command.tenantId(), session.sessionId()).stream()
                .anyMatch(message -> message.messageId().equals(assistantMessageId) && message.role() == ChatRole.ASSISTANT);
        if (!messageExists) {
            throw resourceNotVisible();
        }
        ChatMessageFeedback feedback = new ChatMessageFeedback(
                command.tenantId(), UUID.randomUUID().toString(), sessionId, assistantMessageId, actorId,
                ChatMessageFeedbackRating.from(command.rating()), command.labels(), command.comment(), clock.instant()
        );
        return ChatMessageFeedbackResult.from(feedbackRepository.save(feedback));
    }

    private static ApplicationException resourceNotVisible() {
        return new ApplicationException(AppStudioErrorCodes.RESOURCE_NOT_VISIBLE, "会话消息不可见");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
