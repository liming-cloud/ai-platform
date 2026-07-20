package com.aiplatform.appstudio.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.appstudio.application.command.SubmitChatMessageFeedbackCommand;
import com.aiplatform.appstudio.domain.ChatMessage;
import com.aiplatform.appstudio.domain.ChatRole;
import com.aiplatform.appstudio.domain.ChatSession;
import com.aiplatform.appstudio.domain.port.ChatMessageFeedbackRepository;
import com.aiplatform.appstudio.domain.port.ChatSessionRepository;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChatMessageFeedbackApplicationServiceTest {
    private static final TenantId TENANT_ID = TenantId.of("2001");
    private static final Instant NOW = Instant.parse("2026-07-10T00:00:00Z");

    @Test
    void recordsFeedbackOnlyForAnOwnedAssistantMessage() {
        ChatSessionRepository sessions = mock(ChatSessionRepository.class);
        ChatMessageFeedbackRepository feedback = mock(ChatMessageFeedbackRepository.class);
        when(sessions.findById(TENANT_ID, "4001")).thenReturn(Optional.of(
                new ChatSession(TENANT_ID, "4001", "3001", "test-release-3001", 1, "1001", "合同问答", NOW, NOW)
        ));
        when(sessions.listMessages(TENANT_ID, "4001")).thenReturn(List.of(
                new ChatMessage(TENANT_ID, "5002", "4001", ChatRole.ASSISTANT, "回答", List.of(), NOW)
        ));
        when(feedback.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ChatMessageFeedbackApplicationService service = new ChatMessageFeedbackApplicationService(
                sessions, feedback, Clock.fixed(NOW, ZoneOffset.UTC)
        );

        var result = service.submit(new SubmitChatMessageFeedbackCommand(
                TENANT_ID, "1001", "4001", "5002", "HELPFUL", List.of("准确"), "已解决问题"
        ));

        assertThat(result.status()).isEqualTo("RECORDED");
        assertThat(result.rating()).isEqualTo("HELPFUL");
        verify(feedback).save(any());
    }

    @Test
    void hidesAnotherUsersSessionInsteadOfRecordingFeedback() {
        ChatSessionRepository sessions = mock(ChatSessionRepository.class);
        when(sessions.findById(TENANT_ID, "4001")).thenReturn(Optional.of(
                new ChatSession(TENANT_ID, "4001", "3001", "test-release-3001", 1, "1002", "合同问答", NOW, NOW)
        ));
        ChatMessageFeedbackApplicationService service = new ChatMessageFeedbackApplicationService(
                sessions, mock(ChatMessageFeedbackRepository.class), Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> service.submit(new SubmitChatMessageFeedbackCommand(
                TENANT_ID, "1001", "4001", "5002", "UNHELPFUL", List.of(), null
        ))).isInstanceOf(ApplicationException.class);
    }
}
