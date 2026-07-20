package com.aiplatform.appstudio.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.appstudio.application.port.ChatRunRepository;
import com.aiplatform.appstudio.application.port.ChatRunRepository.Claim;
import com.aiplatform.appstudio.application.port.ChatRunRepository.Finalization;
import com.aiplatform.appstudio.application.result.ChatMessageResult;
import com.aiplatform.appstudio.application.result.ChatRunRecoveryBatchResult;
import com.aiplatform.appstudio.domain.ChatMessage;
import com.aiplatform.appstudio.domain.ChatRole;
import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunSnapshot;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChatRunRecoveryServiceTest {
    private static final TenantId TENANT_ID = TenantId.of("2001");
    private static final Instant NOW = Instant.parse("2026-07-17T10:00:00Z");

    @Test
    void scansResultReceivedClaimsAndRetriesOnlyAtomicFinalization() {
        ChatRunTransactionService transactions = mock(ChatRunTransactionService.class);
        Claim first = Claim.resultReceived(ownership("9001", "worker-1", 2), finalization("5001", "5002"));
        Claim second = Claim.resultReceived(ownership("9002", "worker-1", 4), finalization("5003", "5004"));
        when(transactions.claimRecoverableResults(20)).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("database unavailable"))
                .when(transactions).complete(first.ownership(), first.finalization());
        ChatRunRecoveryService recovery = new ChatRunRecoveryService(transactions);

        ChatRunRecoveryBatchResult result = recovery.recoverBatch(20);

        assertThat(result.claimed()).isEqualTo(2);
        assertThat(result.completed()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        verify(transactions).complete(first.ownership(), first.finalization());
        verify(transactions).complete(second.ownership(), second.finalization());
        verify(transactions).recordFinalizationFailure(eq(first.ownership()), anyString());
    }

    @Test
    void failureRecordingOutageDoesNotStarveTheRemainingClaims() {
        ChatRunTransactionService transactions = mock(ChatRunTransactionService.class);
        Claim first = Claim.resultReceived(ownership("9001", "worker-1", 2), finalization("5001", "5002"));
        Claim second = Claim.resultReceived(ownership("9002", "worker-1", 4), finalization("5003", "5004"));
        when(transactions.claimRecoverableResults(20)).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("completion unavailable"))
                .when(transactions).complete(first.ownership(), first.finalization());
        doThrow(new IllegalStateException("failure ledger unavailable"))
                .when(transactions).recordFinalizationFailure(eq(first.ownership()), anyString());

        ChatRunRecoveryBatchResult result = new ChatRunRecoveryService(transactions).recoverBatch(20);

        assertThat(result.completed()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        verify(transactions).complete(second.ownership(), second.finalization());
    }

    private static ChatRunRepository.Ownership ownership(String runId, String owner, long fence) {
        return new ChatRunRepository.Ownership(
                TENANT_ID, runId, "4001", "3001", "1001", "client-" + runId, owner, fence
        );
    }

    private static Finalization finalization(String userId, String assistantId) {
        ChatMessage user = new ChatMessage(
                TENANT_ID, userId, "4001", ChatRole.USER, "hello", List.of(), null,
                "client-9001", "trace-1", NOW
        );
        ChatMessage assistant = new ChatMessage(
                TENANT_ID, assistantId, "4001", ChatRole.ASSISTANT, "answer", List.of(), null,
                null, "trace-1", NOW
        );
        ChatMessageResult result = new ChatMessageResult(
                userId, assistantId, "4001", "3001", "answer", true,
                List.of(), List.of(), null, 0, NOW, Map.of("traceId", "trace-1")
        );
        return new Finalization(
                user,
                assistant,
                new AppStudioDebugRunSnapshot(TENANT_ID, assistantId, "trace-1", NOW, List.of()),
                result
        );
    }
}
