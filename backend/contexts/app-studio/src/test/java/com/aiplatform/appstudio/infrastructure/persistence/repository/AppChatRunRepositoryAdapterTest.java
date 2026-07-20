package com.aiplatform.appstudio.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.appstudio.application.port.ChatRunRepository;
import com.aiplatform.appstudio.application.port.ChatRunRepository.ClaimRequest;
import com.aiplatform.appstudio.application.port.ChatRunRepository.ClaimState;
import com.aiplatform.appstudio.application.port.ChatRunRepository.Finalization;
import com.aiplatform.appstudio.application.port.ChatRunRepository.FinalizationFailureDisposition;
import com.aiplatform.appstudio.application.port.ChatRunRepository.FinalizationFailureRequest;
import com.aiplatform.appstudio.application.port.ChatRunRepository.ManualReplayRequest;
import com.aiplatform.appstudio.application.port.ChatRunRepository.RecoveryRequest;
import com.aiplatform.appstudio.application.port.ChatRunRepository.ReplayClaimState;
import com.aiplatform.appstudio.application.result.ChatMessageResult;
import com.aiplatform.appstudio.domain.ChatMessage;
import com.aiplatform.appstudio.domain.ChatRole;
import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunSnapshot;
import com.aiplatform.appstudio.infrastructure.persistence.entity.AppChatRunPersistenceEntity;
import com.aiplatform.appstudio.infrastructure.persistence.mapper.AppChatRunMapper;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.TenantId;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppChatRunRepositoryAdapterTest {
    private static final Instant NOW = Instant.parse("2026-07-17T10:00:00Z");
    private static final String COMPLETED_RESULT_V1_WITH_UNKNOWN_FIELDS = """
            {
              "schemaVersion": 1,
              "futureEnvelopeField": "ignored",
              "result": {
                "userMessageId": "5001",
                "assistantMessageId": "5002",
                "sessionId": "4001",
                "applicationId": "3001",
                "answer": "answer",
                "answerable": true,
                "citations": [],
                "evidences": [],
                "noAnswerReason": null,
                "filteredCitationCount": 0,
                "createdAt": "2026-07-17T10:00:00Z",
                "metadata": {"traceId": "trace-1"},
                "futureResultField": {"ignored": true}
              }
            }
            """;

    @Mock
    private AppChatRunMapper mapper;

    private AppChatRunRepositoryAdapter repository;
    private JsonMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().findAndAddModules().build();
        repository = new AppChatRunRepositoryAdapter(mapper, objectMapper);
    }

    @Test
    void newClaimReturnsDatabaseIdentityAndFencingToken() {
        ClaimRequest request = request("owner-new");
        when(mapper.insertIfAbsent(any())).thenReturn(1);
        when(mapper.selectForUpdate(2001L, 4001L, "client-1"))
                .thenReturn(row("IN_PROGRESS", "PREPARING", "owner-new", 1L, NOW.plusSeconds(300), "hash-1"));

        ChatRunRepository.Claim claim = repository.claim(request);

        assertThat(claim.state()).isEqualTo(ClaimState.ACQUIRED);
        assertThat(claim.ownership().runId()).isEqualTo("9001");
        assertThat(claim.ownership().leaseOwner()).isEqualTo("owner-new");
        assertThat(claim.ownership().fencingToken()).isEqualTo(1L);
    }

    @Test
    void expiredPreparingLeaseCanBeReclaimedWithIncrementedFencingToken() {
        ClaimRequest request = request("owner-next");
        when(mapper.insertIfAbsent(any())).thenReturn(0);
        when(mapper.selectForUpdate(2001L, 4001L, "client-1"))
                .thenReturn(row("IN_PROGRESS", "PREPARING", "owner-old", 3L, NOW.minusSeconds(1), "hash-1"));
        when(mapper.reclaimPreparing(
                2001L, 9001L, "owner-old", 3L, "owner-next", NOW.plusSeconds(300), NOW
        )).thenReturn(1);

        ChatRunRepository.Claim claim = repository.claim(request);

        assertThat(claim.state()).isEqualTo(ClaimState.ACQUIRED);
        assertThat(claim.ownership().leaseOwner()).isEqualTo("owner-next");
        assertThat(claim.ownership().fencingToken()).isEqualTo(4L);
    }

    @Test
    void expiredModelDispatchBecomesUncertainWithoutOwnerReplacement() {
        ClaimRequest request = request("owner-next");
        AppChatRunPersistenceEntity row = row(
                "IN_PROGRESS", "MODEL_DISPATCHED", "owner-original", 5L, NOW.minusSeconds(1), "hash-1"
        );
        when(mapper.insertIfAbsent(any())).thenReturn(0);
        when(mapper.selectForUpdate(2001L, 4001L, "client-1")).thenReturn(row);
        when(mapper.markExpiredDispatchedUncertain(
                2001L, 9001L, "owner-original", 5L, NOW
        )).thenReturn(1);

        ChatRunRepository.Claim claim = repository.claim(request);

        assertThat(claim.state()).isEqualTo(ClaimState.UNCERTAIN);
        assertThat(claim.ownership().leaseOwner()).isEqualTo("owner-original");
        assertThat(claim.ownership().fencingToken()).isEqualTo(5L);
        verify(mapper, never()).reclaimPreparing(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void sameClientMessageWithDifferentCanonicalCommandIsRejectedBeforeMutation() {
        ClaimRequest request = request("owner-next");
        when(mapper.insertIfAbsent(any())).thenReturn(0);
        when(mapper.selectForUpdate(2001L, 4001L, "client-1"))
                .thenReturn(row("IN_PROGRESS", "PREPARING", "owner-old", 2L, NOW.plusSeconds(60), "different-hash"));

        assertThatThrownBy(() -> repository.claim(request))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("clientMessageId");

        verify(mapper, never()).reclaimPreparing(any(), any(), any(), any(), any(), any(), any());
        verify(mapper, never()).markExpiredDispatchedUncertain(any(), any(), any(), any(), any());
    }

    @Test
    void completedClaimReturnsThePersistedCanonicalResult() throws Exception {
        ChatMessageResult expected = result();
        AppChatRunPersistenceEntity row = row(
                "COMPLETED", "MODEL_DISPATCHED", "owner-original", 5L, NOW.minusSeconds(1), "hash-1"
        );
        row.setResultJson(new AppChatRunFinalizationCodec(objectMapper).writeCompletedResult(expected));
        row.setCompletedAt(NOW);
        when(mapper.insertIfAbsent(any())).thenReturn(0);
        when(mapper.selectForUpdate(2001L, 4001L, "client-1")).thenReturn(row);

        ChatRunRepository.Claim claim = repository.claim(request("owner-retry"));

        assertThat(claim.state()).isEqualTo(ClaimState.COMPLETED);
        assertThat(claim.completedResult()).isEqualTo(expected);
    }

    @Test
    void failedClaimReturnsOnlyStoredSafeFailureFacts() {
        AppChatRunPersistenceEntity row = row(
                "FAILED", "PREPARING", "owner-original", 2L, NOW.minusSeconds(1), "hash-1"
        );
        row.setFailureCode("AIP-APP-CHAT-6003");
        row.setFailureSummary("聊天运行在模型派发前失败（IllegalStateException）");
        when(mapper.insertIfAbsent(any())).thenReturn(0);
        when(mapper.selectForUpdate(2001L, 4001L, "client-1")).thenReturn(row);

        ChatRunRepository.Claim claim = repository.claim(request("owner-retry"));

        assertThat(claim.state()).isEqualTo(ClaimState.FAILED);
        assertThat(claim.failureSummary())
                .contains("IllegalStateException")
                .doesNotContain("prompt")
                .doesNotContain("secret");
    }

    @Test
    void completionMutationCarriesTenantRunOwnerAndFencingToken() {
        ChatRunRepository.Ownership ownership = new ChatRunRepository.Ownership(
                TenantId.of("2001"), "9001", "4001", "3001", "1001", "client-1", "owner-original", 7
        );
        when(mapper.complete(eq(2001L), eq(9001L), eq("owner-original"), eq(7L), any(), any()))
                .thenReturn(1);

        repository.complete(ownership, result(), NOW);

        verify(mapper).complete(eq(2001L), eq(9001L), eq("owner-original"), eq(7L),
                org.mockito.ArgumentMatchers.contains("\"assistantMessageId\":\"5002\""),
                eq(NOW));
    }

    @Test
    void completionPersistsExplicitVersionedResultEnvelope() throws Exception {
        ChatRunRepository.Ownership ownership = ownership("owner-original", 7L);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        when(mapper.complete(
                eq(2001L), eq(9001L), eq("owner-original"), eq(7L), payload.capture(), eq(NOW)
        )).thenReturn(1);

        repository.complete(ownership, result(), NOW);

        assertThat(objectMapper.readTree(payload.getValue()).path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(objectMapper.readTree(payload.getValue()).path("result").path("assistantMessageId").asText())
                .isEqualTo("5002");
    }

    @Test
    void exhaustedFinalizationFailureBecomesQueryableDeadLetterWithoutClearingPayload() {
        ChatRunRepository.Ownership ownership = ownership("worker-2", 9L);
        AppChatRunPersistenceEntity updated = row(
                "DEAD_LETTER", "RESULT_RECEIVED", "worker-2", 9L, NOW, "hash-1"
        );
        updated.setFinalizationAttemptCount(3);
        updated.setFinalizationCycleAttemptCount(3);
        updated.setFinalizationJson(new AppChatRunFinalizationCodec(objectMapper).write(finalization()));
        updated.setFailureCode("FINALIZATION_TRANSACTION_FAILED");
        updated.setFailureSummary("聊天最终化事务失败，已进入受控恢复");
        updated.setFailureTraceId("trace-recovery-3");
        updated.setUpdatedAt(NOW);
        when(mapper.recordFinalizationFailure(
                2001L, 9001L, "worker-2", 9L,
                "FINALIZATION_TRANSACTION_FAILED", "聊天最终化事务失败，已进入受控恢复",
                "trace-recovery-3", NOW, 3, 5
        )).thenReturn(1);
        when(mapper.selectRunForUpdate(2001L, 9001L)).thenReturn(updated);

        FinalizationFailureDisposition disposition = repository.recordFinalizationFailure(
                new FinalizationFailureRequest(
                        ownership,
                        "FINALIZATION_TRANSACTION_FAILED",
                        "聊天最终化事务失败，已进入受控恢复",
                        "trace-recovery-3",
                        NOW,
                        3,
                        5
                )
        );

        assertThat(disposition).isEqualTo(FinalizationFailureDisposition.DEAD_LETTER);
        assertThat(updated.getFinalizationJson()).contains("\"schemaVersion\":1");
    }

    @Test
    void deadLetterQueryIsTenantScopedAndRevalidatesRetainedV1Facts() {
        AppChatRunPersistenceEntity deadLetter = row(
                "DEAD_LETTER", "RESULT_RECEIVED", "worker-2", 9L, NOW, "hash-1"
        );
        deadLetter.setFinalizationAttemptCount(3);
        deadLetter.setFinalizationCycleAttemptCount(3);
        deadLetter.setManualReplayCount(1);
        deadLetter.setFinalizationJson(new AppChatRunFinalizationCodec(objectMapper).write(finalization()));
        deadLetter.setFailureCode("FINALIZATION_TRANSACTION_FAILED");
        deadLetter.setFailureSummary("聊天最终化事务失败，已进入受控恢复");
        deadLetter.setFailureTraceId("trace-recovery-3");
        deadLetter.setUpdatedAt(NOW);
        when(mapper.findDeadLetters(2001L, 20)).thenReturn(List.of(deadLetter));

        List<ChatRunRepository.DeadLetter> result = repository.findDeadLetters(TenantId.of("2001"), 20);

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.runId()).isEqualTo("9001");
            assertThat(item.finalizationAttemptCount()).isEqualTo(3);
            assertThat(item.failureTraceId()).isEqualTo("trace-recovery-3");
        });
        verify(mapper).findDeadLetters(2001L, 20);
    }

    @Test
    void manualReplayClaimUsesTenantRequestIdAndNewFence() {
        AppChatRunPersistenceEntity deadLetter = row(
                "DEAD_LETTER", "RESULT_RECEIVED", "worker-old", 7L, NOW, "hash-1"
        );
        deadLetter.setFinalizationAttemptCount(3);
        deadLetter.setFinalizationCycleAttemptCount(3);
        deadLetter.setFinalizationJson(new AppChatRunFinalizationCodec(objectMapper).write(finalization()));
        deadLetter.setFailureCode("FINALIZATION_TRANSACTION_FAILED");
        deadLetter.setFailureSummary("聊天最终化事务失败，已进入受控恢复");
        deadLetter.setFailureTraceId("trace-old");
        when(mapper.selectRunForUpdate(2001L, 9001L)).thenReturn(deadLetter);
        when(mapper.insertReplayRequest(
                2001L, 9001L, "replay-1", 1002L, "数据库恢复后人工重放", NOW
        )).thenReturn(1);
        when(mapper.claimDeadLetterForReplay(
                2001L, 9001L, "manual-owner", NOW.plusSeconds(30),
                "replay-1", "数据库恢复后人工重放", NOW
        )).thenReturn(1);

        ChatRunRepository.ReplayClaim claim = repository.claimDeadLetterForReplay(new ManualReplayRequest(
                TenantId.of("2001"), "9001", "1002", "replay-1", "数据库恢复后人工重放",
                "manual-owner", NOW, NOW.plusSeconds(30)
        ));

        assertThat(claim.state()).isEqualTo(ReplayClaimState.ACQUIRED);
        assertThat(claim.ownership().leaseOwner()).isEqualTo("manual-owner");
        assertThat(claim.ownership().fencingToken()).isEqualTo(8L);
        assertThat(claim.finalization()).isEqualTo(finalization());
    }

    @Test
    void sameManualReplayRequestIsIdempotentWithoutAnotherMutation() {
        AppChatRunPersistenceEntity accepted = row(
                "IN_PROGRESS", "RESULT_RECEIVED", "manual-owner", 8L, NOW.plusSeconds(30), "hash-1"
        );
        accepted.setLastReplayRequestId("replay-1");
        when(mapper.selectRunForUpdate(2001L, 9001L)).thenReturn(accepted);

        ChatRunRepository.ReplayClaim claim = repository.claimDeadLetterForReplay(new ManualReplayRequest(
                TenantId.of("2001"), "9001", "1002", "replay-1", "数据库恢复后人工重放",
                "another-owner", NOW, NOW.plusSeconds(30)
        ));

        assertThat(claim.state()).isEqualTo(ReplayClaimState.ALREADY_ACCEPTED);
        assertThat(claim.persistedStatus()).isEqualTo("IN_PROGRESS");
        verify(mapper, never()).claimDeadLetterForReplay(
                any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void historicalReplayRequestRemainsIdempotentAfterANewerReplayCycle() {
        AppChatRunPersistenceEntity deadAgain = row(
                "DEAD_LETTER", "RESULT_RECEIVED", "newer-owner", 12L, NOW, "hash-1"
        );
        deadAgain.setLastReplayRequestId("replay-2");
        deadAgain.setFailureCode("FINALIZATION_TRANSACTION_FAILED");
        deadAgain.setFailureSummary("聊天最终化事务失败，已进入受控恢复");
        deadAgain.setFailureTraceId("trace-newer");
        when(mapper.selectRunForUpdate(2001L, 9001L)).thenReturn(deadAgain);
        when(mapper.findReplayRequestRunId(2001L, "replay-1")).thenReturn(9001L);

        ChatRunRepository.ReplayClaim claim = repository.claimDeadLetterForReplay(new ManualReplayRequest(
                TenantId.of("2001"), "9001", "1002", "replay-1", "历史请求重试",
                "another-owner", NOW, NOW.plusSeconds(30)
        ));

        assertThat(claim.state()).isEqualTo(ReplayClaimState.ALREADY_ACCEPTED);
        assertThat(claim.persistedStatus()).isEqualTo("DEAD_LETTER");
        verify(mapper, never()).insertReplayRequest(any(), any(), any(), any(), any(), any());
        verify(mapper, never()).claimDeadLetterForReplay(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void completedClaimReadsV1FixtureAndIgnoresUnknownAdditiveFields() {
        AppChatRunPersistenceEntity row = row(
                "COMPLETED", "RESULT_RECEIVED", "owner-original", 7L, NOW.minusSeconds(1), "hash-1"
        );
        row.setResultJson(COMPLETED_RESULT_V1_WITH_UNKNOWN_FIELDS);
        row.setCompletedAt(NOW);
        when(mapper.insertIfAbsent(any())).thenReturn(0);
        when(mapper.selectForUpdate(2001L, 4001L, "client-1")).thenReturn(row);

        ChatRunRepository.Claim claim = repository.claim(request("owner-retry"));

        assertThat(claim.state()).isEqualTo(ClaimState.COMPLETED);
        assertThat(claim.completedResult()).isEqualTo(result());
    }

    @Test
    void completedClaimRejectsUnsupportedResultSchemaWithoutEchoingPayload() {
        AppChatRunPersistenceEntity row = row(
                "COMPLETED", "RESULT_RECEIVED", "owner-original", 7L, NOW.minusSeconds(1), "hash-1"
        );
        row.setResultJson(COMPLETED_RESULT_V1_WITH_UNKNOWN_FIELDS.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"));
        row.setCompletedAt(NOW);
        when(mapper.insertIfAbsent(any())).thenReturn(0);
        when(mapper.selectForUpdate(2001L, 4001L, "client-1")).thenReturn(row);

        assertThatThrownBy(() -> repository.claim(request("owner-retry")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("聊天完成结果载荷无法读取")
                .hasMessageNotContaining("futureEnvelopeField")
                .hasMessageNotContaining("answer");
    }

    @Test
    void expiredResultReceivedCanBeRecoveredFromVersionedSafePayloadWithoutModelReplay() {
        Finalization finalization = finalization();
        ChatRunRepository.Ownership original = ownership("owner-original", 7L);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        when(mapper.recordResultReceived(
                eq(2001L), eq(9001L), eq("owner-original"), eq(7L), payload.capture(),
                eq(NOW.plusSeconds(30)), eq(NOW)
        )).thenReturn(1);

        repository.recordResultReceived(original, finalization, NOW.plusSeconds(30), NOW);

        assertThat(payload.getValue())
                .contains("\"schemaVersion\":1")
                .contains("\"userContent\":\"hello\"")
                .doesNotContain("systemPrompt")
                .doesNotContain("runtimeDescriptor")
                .doesNotContain("secret");

        AppChatRunPersistenceEntity row = row(
                "IN_PROGRESS", "RESULT_RECEIVED", "owner-original", 7L, NOW.minusSeconds(1), "hash-1"
        );
        row.setFinalizationJson(payload.getValue());
        when(mapper.insertIfAbsent(any())).thenReturn(0);
        when(mapper.selectForUpdate(2001L, 4001L, "client-1")).thenReturn(row);
        when(mapper.reclaimResultReceived(
                2001L, 9001L, "owner-original", 7L, "owner-retry", NOW.plusSeconds(300), NOW
        )).thenReturn(1);

        ChatRunRepository.Claim claim = repository.claim(request("owner-retry"));

        assertThat(claim.state()).isEqualTo(ClaimState.RESULT_RECEIVED);
        assertThat(claim.ownership().leaseOwner()).isEqualTo("owner-retry");
        assertThat(claim.ownership().fencingToken()).isEqualTo(8L);
        assertThat(claim.finalization()).isEqualTo(finalization);
        verify(mapper, never()).markExpiredDispatchedUncertain(any(), any(), any(), any(), any());
    }

    @Test
    void recoveryBatchReturnsAlreadyFencedResultReceivedClaims() throws Exception {
        Finalization finalization = finalization();
        AppChatRunPersistenceEntity row = row(
                "IN_PROGRESS", "RESULT_RECEIVED", "worker-2", 9L, NOW.plusSeconds(30), "hash-1"
        );
        row.setFinalizationJson(finalizationJson(finalization));
        when(mapper.claimRecoverableResults("worker-2", NOW.plusSeconds(30), NOW, 20, 3))
                .thenReturn(List.of(row));

        List<ChatRunRepository.Claim> claims = repository.claimRecoverableResults(new RecoveryRequest(
                "worker-2", NOW, NOW.plusSeconds(30), 20
        ));

        assertThat(claims).hasSize(1);
        assertThat(claims.getFirst().state()).isEqualTo(ClaimState.RESULT_RECEIVED);
        assertThat(claims.getFirst().ownership().leaseOwner()).isEqualTo("worker-2");
        assertThat(claims.getFirst().ownership().fencingToken()).isEqualTo(9L);
        assertThat(claims.getFirst().finalization()).isEqualTo(finalization);
    }

    @Test
    void malformedPayloadIsIsolatedWithoutStarvingHealthyResultReceivedClaim() {
        AppChatRunPersistenceEntity malformed = row(
                "IN_PROGRESS", "RESULT_RECEIVED", "worker-2", 8L, NOW.plusSeconds(30), "hash-bad"
        );
        malformed.setFinalizationJson("{}");
        AppChatRunPersistenceEntity healthy = row(
                "IN_PROGRESS", "RESULT_RECEIVED", "worker-2", 9L, NOW.plusSeconds(30), "hash-good"
        );
        healthy.setRunId(9002L);
        healthy.setSessionId(4002L);
        healthy.setClientMessageId("client-2");
        Finalization healthyFinalization = finalization("4002", "client-2", "5003", "5004");
        healthy.setFinalizationJson(new AppChatRunFinalizationCodec(objectMapper).write(healthyFinalization));
        when(mapper.claimRecoverableResults("worker-2", NOW.plusSeconds(30), NOW, 20, 3))
                .thenReturn(List.of(malformed, healthy));
        when(mapper.quarantineInvalidFinalization(
                2001L, 9001L, "worker-2", 8L,
                "FINALIZATION_PAYLOAD_INVALID", "聊天完成事实载荷无效，已安全隔离", NOW
        )).thenReturn(1);

        List<ChatRunRepository.Claim> claims = repository.claimRecoverableResults(new RecoveryRequest(
                "worker-2", NOW, NOW.plusSeconds(30), 20
        ));

        assertThat(claims).singleElement().satisfies(claim -> {
            assertThat(claim.ownership().runId()).isEqualTo("9002");
            assertThat(claim.finalization()).isEqualTo(healthyFinalization);
        });
        verify(mapper).quarantineInvalidFinalization(
                2001L, 9001L, "worker-2", 8L,
                "FINALIZATION_PAYLOAD_INVALID", "聊天完成事实载荷无效，已安全隔离", NOW
        );
    }

    @Test
    void nonFiniteNestedEvidenceIsQuarantinedBeforeFinalTransactionReplay() {
        String validPayload = new AppChatRunFinalizationCodec(objectMapper).write(finalization());
        assertRecoveryPayloadQuarantined(validPayload.replace(
                "\"snapshotEvidences\":[]",
                "\"snapshotEvidences\":[{\"knowledgeBaseId\":\"7001\",\"documentId\":\"7101\","
                        + "\"chunkId\":\"7201\",\"score\":\"NaN\",\"retrievalSignal\":\"dense\","
                        + "\"indexVersion\":\"v1\"}]"
        ));
    }

    @Test
    void oversizedNestedEvidenceSignalIsQuarantinedBeforeFinalTransactionReplay() {
        String validPayload = new AppChatRunFinalizationCodec(objectMapper).write(finalization());
        assertRecoveryPayloadQuarantined(validPayload.replace(
                "\"snapshotEvidences\":[]",
                "\"snapshotEvidences\":[{\"knowledgeBaseId\":\"7001\",\"documentId\":\"7101\","
                        + "\"chunkId\":\"7201\",\"score\":0.8,\"retrievalSignal\":\""
                        + "s".repeat(65) + "\",\"indexVersion\":\"v1\"}]"
        ));
    }

    @Test
    void nonFiniteCompletedResultMetadataIsRejectedBeforePersistence() {
        ChatMessageResult poisonedResult = new ChatMessageResult(
                "5001", "5002", "4001", "3001", "answer", true,
                List.of(), List.of(), null, 0, NOW, Map.of("score", Double.NaN)
        );

        assertThatThrownBy(() -> new AppChatRunFinalizationCodec(objectMapper)
                .writeCompletedResult(poisonedResult))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("有限");
    }

    @Test
    void nonNumericMessageIdentityIsQuarantinedBeforeFinalTransactionReplay() {
        String validPayload = new AppChatRunFinalizationCodec(objectMapper).write(finalization());

        assertRecoveryPayloadQuarantined(validPayload
                .replace("\"5001\"", "\"not-a-user-message-id\"")
                .replace("\"5002\"", "\"not-an-assistant-message-id\""));
    }

    @Test
    void nonNumericNestedEvidenceIdentityIsQuarantinedBeforeFinalTransactionReplay() {
        String validPayload = new AppChatRunFinalizationCodec(objectMapper).write(finalization());

        assertRecoveryPayloadQuarantined(validPayload.replace(
                "\"snapshotEvidences\":[]",
                "\"snapshotEvidences\":[{\"knowledgeBaseId\":\"not-a-database-id\","
                        + "\"documentId\":\"7101\",\"chunkId\":\"7201\",\"score\":0.8,"
                        + "\"retrievalSignal\":\"dense\",\"indexVersion\":\"v1\"}]"
        ));
    }

    @Test
    void finalizationIdentityMismatchWithClaimedRowIsQuarantined() {
        String mismatchedPayload = new AppChatRunFinalizationCodec(objectMapper)
                .write(finalization("4999", "client-1", "5001", "5002"));

        assertRecoveryPayloadQuarantined(mismatchedPayload);
    }

    @Test
    void oversizedRecoveryPayloadIsQuarantinedWithoutEchoingItsContent() {
        AppChatRunPersistenceEntity row = row(
                "IN_PROGRESS", "RESULT_RECEIVED", "owner-old", 3L, NOW.minusSeconds(1), "hash-1"
        );
        row.setFinalizationJson("x".repeat(1_048_577));
        when(mapper.insertIfAbsent(any())).thenReturn(0);
        when(mapper.selectForUpdate(2001L, 4001L, "client-1")).thenReturn(row);
        when(mapper.reclaimResultReceived(
                2001L, 9001L, "owner-old", 3L, "owner-retry", NOW.plusSeconds(300), NOW
        )).thenReturn(1);
        when(mapper.quarantineInvalidFinalization(
                2001L, 9001L, "owner-retry", 4L,
                "FINALIZATION_PAYLOAD_INVALID", "聊天完成事实载荷无效，已安全隔离", NOW
        )).thenReturn(1);

        ChatRunRepository.Claim claim = repository.claim(request("owner-retry"));

        assertThat(claim.state()).isEqualTo(ClaimState.FAILED);
        assertThat(claim.failureCode()).isEqualTo("FINALIZATION_PAYLOAD_INVALID");
        assertThat(claim.failureSummary())
                .isEqualTo("聊天完成事实载荷无效，已安全隔离")
                .doesNotContain("xxxxx");
    }

    private static ChatMessageResult result() {
        return new ChatMessageResult(
                "5001", "5002", "4001", "3001", "answer", true,
                List.of(), List.of(), null, 0, NOW, Map.of("traceId", "trace-1")
        );
    }

    private String finalizationJson(Finalization finalization) {
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        when(mapper.recordResultReceived(
                eq(2001L), eq(9001L), eq("owner-original"), eq(7L), payload.capture(),
                eq(NOW.plusSeconds(30)), eq(NOW)
        )).thenReturn(1);
        repository.recordResultReceived(ownership("owner-original", 7L), finalization, NOW.plusSeconds(30), NOW);
        return payload.getValue();
    }

    private void assertRecoveryPayloadQuarantined(String payload) {
        AppChatRunPersistenceEntity poisoned = row(
                "IN_PROGRESS", "RESULT_RECEIVED", "worker-2", 8L, NOW.plusSeconds(30), "hash-bad"
        );
        poisoned.setFinalizationJson(payload);
        when(mapper.claimRecoverableResults("worker-2", NOW.plusSeconds(30), NOW, 20, 3))
                .thenReturn(List.of(poisoned));
        when(mapper.quarantineInvalidFinalization(
                2001L, 9001L, "worker-2", 8L,
                "FINALIZATION_PAYLOAD_INVALID", "聊天完成事实载荷无效，已安全隔离", NOW
        )).thenReturn(1);

        List<ChatRunRepository.Claim> claims = repository.claimRecoverableResults(new RecoveryRequest(
                "worker-2", NOW, NOW.plusSeconds(30), 20
        ));

        assertThat(claims).isEmpty();
        verify(mapper).quarantineInvalidFinalization(
                2001L, 9001L, "worker-2", 8L,
                "FINALIZATION_PAYLOAD_INVALID", "聊天完成事实载荷无效，已安全隔离", NOW
        );
    }

    private static Finalization finalization() {
        return finalization("4001", "client-1", "5001", "5002");
    }

    private static Finalization finalization(
            String sessionId,
            String clientMessageId,
            String userMessageId,
            String assistantMessageId
    ) {
        ChatMessage user = new ChatMessage(
                TenantId.of("2001"), userMessageId, sessionId, ChatRole.USER, "hello", List.of(), null,
                clientMessageId, "trace-1", NOW
        );
        ChatMessage assistant = new ChatMessage(
                TenantId.of("2001"), assistantMessageId, sessionId, ChatRole.ASSISTANT, "answer", List.of(), null,
                null, "trace-1", NOW
        );
        ChatMessageResult result = new ChatMessageResult(
                userMessageId, assistantMessageId, sessionId, "3001", "answer", true,
                List.of(), List.of(), null, 0, NOW, Map.of("traceId", "trace-1")
        );
        return new Finalization(
                user,
                assistant,
                new AppStudioDebugRunSnapshot(TenantId.of("2001"), assistantMessageId, "trace-1", NOW, List.of()),
                result
        );
    }

    private static ChatRunRepository.Ownership ownership(String owner, long fencingToken) {
        return new ChatRunRepository.Ownership(
                TenantId.of("2001"), "9001", "4001", "3001", "1001", "client-1", owner, fencingToken
        );
    }

    private static ClaimRequest request(String owner) {
        return new ClaimRequest(
                TenantId.of("2001"),
                "4001",
                "3001",
                "1001",
                "client-1",
                "hash-1",
                owner,
                NOW,
                NOW.plusSeconds(300)
        );
    }

    private static AppChatRunPersistenceEntity row(
            String status,
            String phase,
            String owner,
            long fencingToken,
            Instant leaseExpiresAt,
            String commandHash
    ) {
        AppChatRunPersistenceEntity row = new AppChatRunPersistenceEntity();
        row.setRunId(9001L);
        row.setTenantId(2001L);
        row.setSessionId(4001L);
        row.setApplicationId(3001L);
        row.setActorId(1001L);
        row.setClientMessageId("client-1");
        row.setCommandHash(commandHash);
        row.setStatus(status);
        row.setPhase(phase);
        row.setLeaseOwner(owner);
        row.setLeaseExpiresAt(leaseExpiresAt);
        row.setFencingToken(fencingToken);
        row.setAttemptCount(Math.toIntExact(fencingToken));
        row.setFinalizationAttemptCount("RESULT_RECEIVED".equals(phase) ? 1 : 0);
        row.setFinalizationCycleAttemptCount("RESULT_RECEIVED".equals(phase) ? 1 : 0);
        row.setManualReplayCount(0);
        row.setCreatedAt(NOW.minusSeconds(30));
        row.setUpdatedAt(NOW.minusSeconds(30));
        return row;
    }
}
