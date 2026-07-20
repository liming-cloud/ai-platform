package com.aiplatform.appstudio.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.appstudio.application.command.ReplayChatRunDeadLetterCommand;
import com.aiplatform.appstudio.application.port.ChatRunRepository;
import com.aiplatform.appstudio.application.port.ChatRunRepository.ManualReplayRequest;
import com.aiplatform.appstudio.application.port.ChatRunRepository.ReplayClaim;
import com.aiplatform.appstudio.application.result.ChatMessageResult;
import com.aiplatform.appstudio.domain.ChatMessage;
import com.aiplatform.appstudio.domain.ChatRole;
import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunSnapshot;
import com.aiplatform.shared.audit.model.AuditEvent;
import com.aiplatform.shared.audit.port.AuditEventPublisher;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ChatRunManualReplayTransactionServiceTest {
    private static final TenantId TENANT_ID = TenantId.of("2001");
    private static final Instant NOW = Instant.parse("2026-07-18T08:00:00Z");

    @Test
    void acquiredReplayPersistsActorReasonAndRequestIdInAudit() {
        ChatRunRepository repository = mock(ChatRunRepository.class);
        AuditEventPublisher audit = mock(AuditEventPublisher.class);
        ReplayClaim acquired = ReplayClaim.acquired(ownership(), finalization());
        when(repository.claimDeadLetterForReplay(org.mockito.ArgumentMatchers.any())).thenReturn(acquired);
        ChatRunManualReplayTransactionService service = service(repository, audit);

        service.claim(command());

        ArgumentCaptor<ManualReplayRequest> request = ArgumentCaptor.forClass(ManualReplayRequest.class);
        verify(repository).claimDeadLetterForReplay(request.capture());
        assertThat(request.getValue().tenantId()).isEqualTo(TENANT_ID);
        assertThat(request.getValue().actorId()).isEqualTo("1002");
        assertThat(request.getValue().replayRequestId()).isEqualTo("replay-1");
        assertThat(request.getValue().reason()).isEqualTo("数据库恢复后人工重放");
        assertThat(request.getValue().leaseExpiresAt()).isEqualTo(NOW.plusSeconds(30));

        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).publish(event.capture());
        assertThat(event.getValue().tenantId()).isEqualTo("2001");
        assertThat(event.getValue().actorId()).isEqualTo("1002");
        assertThat(event.getValue().resourceId()).isEqualTo("9001");
        assertThat(event.getValue().action()).isEqualTo("APP_CHAT_RUN_DEAD_LETTER_REPLAY");
        assertThat(event.getValue().traceId()).isEqualTo("trace-1");
        assertThat(event.getValue().detailAttributes())
                .containsEntry("replayRequestId", "replay-1")
                .containsEntry("reason", "数据库恢复后人工重放");
    }

    @Test
    void idempotentReplayDoesNotDuplicateAudit() {
        ChatRunRepository repository = mock(ChatRunRepository.class);
        AuditEventPublisher audit = mock(AuditEventPublisher.class);
        when(repository.claimDeadLetterForReplay(org.mockito.ArgumentMatchers.any()))
                .thenReturn(ReplayClaim.alreadyAccepted("IN_PROGRESS", ownership()));

        service(repository, audit).claim(command());

        verify(audit, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void auditFailureFailsTheClaimTransaction() {
        ChatRunRepository repository = mock(ChatRunRepository.class);
        AuditEventPublisher audit = mock(AuditEventPublisher.class);
        when(repository.claimDeadLetterForReplay(org.mockito.ArgumentMatchers.any()))
                .thenReturn(ReplayClaim.acquired(ownership(), finalization()));
        doThrow(new IllegalStateException("audit unavailable"))
                .when(audit).publish(org.mockito.ArgumentMatchers.any());

        assertThatThrownBy(() -> service(repository, audit).claim(command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("audit unavailable");
    }

    private static ChatRunManualReplayTransactionService service(
            ChatRunRepository repository,
            AuditEventPublisher audit
    ) {
        return new ChatRunManualReplayTransactionService(
                repository, audit, Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static ReplayChatRunDeadLetterCommand command() {
        return new ReplayChatRunDeadLetterCommand(
                TENANT_ID, "9001", "1002", "replay-1", "数据库恢复后人工重放", "trace-1"
        );
    }

    private static ChatRunRepository.Ownership ownership() {
        return new ChatRunRepository.Ownership(
                TENANT_ID, "9001", "4001", "3001", "1001", "client-1", "manual-owner", 8
        );
    }

    private static ChatRunRepository.Finalization finalization() {
        ChatMessage user = new ChatMessage(
                TENANT_ID, "5001", "4001", ChatRole.USER, "hello", List.of(), null,
                "client-1", "trace-1", NOW
        );
        ChatMessage assistant = new ChatMessage(
                TENANT_ID, "5002", "4001", ChatRole.ASSISTANT, "answer", List.of(), null,
                null, "trace-1", NOW
        );
        ChatMessageResult result = new ChatMessageResult(
                "5001", "5002", "4001", "3001", "answer", true,
                List.of(), List.of(), null, 0, NOW, Map.of("traceId", "trace-1")
        );
        return new ChatRunRepository.Finalization(
                user,
                assistant,
                new AppStudioDebugRunSnapshot(TENANT_ID, "5002", "trace-1", NOW, List.of()),
                result
        );
    }
}
