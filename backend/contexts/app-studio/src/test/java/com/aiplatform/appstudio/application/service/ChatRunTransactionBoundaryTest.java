package com.aiplatform.appstudio.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.appstudio.application.command.SendChatMessageCommand;
import com.aiplatform.appstudio.application.command.ReplayChatRunDeadLetterCommand;
import com.aiplatform.appstudio.application.port.ChatRunRepository;
import com.aiplatform.appstudio.application.port.ChatRunRepository.Finalization;
import com.aiplatform.appstudio.application.result.ChatMessageResult;
import com.aiplatform.appstudio.domain.ChatMessage;
import com.aiplatform.appstudio.domain.ChatRole;
import com.aiplatform.appstudio.domain.ChatSession;
import com.aiplatform.appstudio.domain.port.ChatSessionRepository;
import com.aiplatform.appstudio.domain.port.debug.AppStudioDebugRunSnapshotPort;
import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunSnapshot;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.audit.model.AuditEvent;
import com.aiplatform.shared.audit.port.AuditEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringJUnitConfig(ChatRunTransactionBoundaryTest.TestConfiguration.class)
class ChatRunTransactionBoundaryTest {
    private static final TenantId TENANT_ID = TenantId.of("2001");
    private static final Instant NOW = Instant.parse("2026-07-17T10:00:00Z");

    @jakarta.annotation.Resource
    private ChatRunTransactionService service;
    @jakarta.annotation.Resource
    private RecordingTransactionManager transactionManager;
    @jakarta.annotation.Resource
    private TransactionalChatRunRepository runRepository;
    @jakarta.annotation.Resource
    private TransactionalChatSessionRepository sessionRepository;
    @jakarta.annotation.Resource
    private TransactionalDebugSnapshotPort snapshotPort;
    @jakarta.annotation.Resource
    private ChatRunManualReplayTransactionService manualReplayService;
    @jakarta.annotation.Resource
    private TransactionalReplayAuditPublisher replayAuditPublisher;

    @BeforeEach
    void reset() {
        transactionManager.reset();
        runRepository.reset();
        sessionRepository.reset();
        snapshotPort.reset();
        replayAuditPublisher.reset();
    }

    @Test
    void claimResultReceiptAndFinalizeUseIndependentTransactions() {
        ChatRunRepository.Claim claim = service.claim(command(), "3001");

        assertThat(runRepository.transactionActiveAtClaim).isTrue();
        assertThat(transactionManager.commitCount).isEqualTo(1);

        service.recordResultReceived(claim.ownership(), finalization());

        assertThat(runRepository.transactionActiveAtResultReceived).isTrue();
        assertThat(runRepository.resultReceived).isTrue();
        assertThat(transactionManager.commitCount).isEqualTo(2);

        service.complete(claim.ownership(), finalization());

        assertThat(sessionRepository.transactionActiveAtAppend).isTrue();
        assertThat(snapshotPort.transactionActiveAtRecord).isTrue();
        assertThat(runRepository.transactionActiveAtComplete).isTrue();
        assertThat(transactionManager.commitCount).isEqualTo(3);
        assertThat(sessionRepository.committedMessages).hasSize(2);
        assertThat(runRepository.completed).isTrue();
    }

    @Test
    void runCompletionFailureRollsBackMessagesAndEvidence() {
        ChatRunRepository.Claim claim = service.claim(command(), "3001");
        service.recordResultReceived(claim.ownership(), finalization());
        runRepository.failComplete = true;

        assertThatThrownBy(() -> service.complete(claim.ownership(), finalization()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("run completion failed");

        assertThat(transactionManager.rollbackCount).isEqualTo(1);
        assertThat(sessionRepository.committedMessages).isEmpty();
        assertThat(snapshotPort.committedSnapshots).isEmpty();
        assertThat(runRepository.completed).isFalse();
        assertThat(runRepository.resultReceived).isTrue();
    }

    @Test
    void replayAuditFailureRollsBackTheClaimTransaction() {
        replayAuditPublisher.fail = true;

        assertThatThrownBy(() -> manualReplayService.claim(replayCommand()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("audit unavailable");

        assertThat(transactionManager.rollbackCount).isEqualTo(1);
        assertThat(runRepository.manualReplayClaimed).isFalse();
        assertThat(replayAuditPublisher.audited).isFalse();
    }

    @Test
    void replayClaimAndAuditCommitTogether() {
        manualReplayService.claim(replayCommand());

        assertThat(transactionManager.commitCount).isEqualTo(1);
        assertThat(runRepository.manualReplayClaimed).isTrue();
        assertThat(replayAuditPublisher.audited).isTrue();
    }

    @Test
    void canonicalSessionIdentityIsRecheckedBeforeAnyFinalWrite() {
        ChatRunRepository.Claim claim = service.claim(command(), "3001");
        sessionRepository.session = new ChatSession(
                TENANT_ID, "4001", "3002", "test-release-3002", 1, "1001", "wrong application", NOW, NOW
        );

        assertThatThrownBy(() -> service.complete(claim.ownership(), finalization()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("会话持久化身份");

        assertThat(sessionRepository.appendAttempts).isZero();
        assertThat(runRepository.completeAttempts).isZero();
    }

    private static SendChatMessageCommand command() {
        return new SendChatMessageCommand(TENANT_ID, "1001", "4001", "client-1", "hello", "trace-1");
    }

    private static Finalization finalization() {
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
                List.of(), List.of(), null, 0, NOW, java.util.Map.of("traceId", "trace-1")
        );
        return new Finalization(
                user,
                assistant,
                new AppStudioDebugRunSnapshot(TENANT_ID, "5002", "trace-1", NOW, List.of()),
                result
        );
    }

    private static ReplayChatRunDeadLetterCommand replayCommand() {
        return new ReplayChatRunDeadLetterCommand(
                TENANT_ID, "9001", "1002", "replay-1", "人工恢复最终化", "trace-1"
        );
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfiguration {
        @Bean
        RecordingTransactionManager transactionManager() { return new RecordingTransactionManager(); }
        @Bean
        TransactionalChatRunRepository chatRunRepository() { return new TransactionalChatRunRepository(); }
        @Bean
        TransactionalChatSessionRepository chatSessionRepository() { return new TransactionalChatSessionRepository(); }
        @Bean
        TransactionalDebugSnapshotPort debugSnapshotPort() { return new TransactionalDebugSnapshotPort(); }
        @Bean
        Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
        @Bean
        ChatRunTransactionService service(
                ChatRunRepository runRepository,
                ChatSessionRepository sessionRepository,
                AppStudioDebugRunSnapshotPort snapshotPort,
                Clock clock
        ) {
            return new ChatRunTransactionService(runRepository, sessionRepository, snapshotPort, clock);
        }
        @Bean
        TransactionalReplayAuditPublisher replayAuditPublisher() {
            return new TransactionalReplayAuditPublisher();
        }
        @Bean
        ChatRunManualReplayTransactionService manualReplayService(
                ChatRunRepository runRepository,
                TransactionalReplayAuditPublisher auditPublisher,
                Clock clock
        ) {
            return new ChatRunManualReplayTransactionService(runRepository, auditPublisher, clock);
        }
    }

    static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        private int commitCount;
        private int rollbackCount;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { commitCount++; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbackCount++; }
        void reset() { commitCount = 0; rollbackCount = 0; }
    }

    static final class TransactionalChatRunRepository implements ChatRunRepository {
        private boolean transactionActiveAtClaim;
        private boolean transactionActiveAtResultReceived;
        private boolean transactionActiveAtComplete;
        private boolean resultReceived;
        private boolean completed;
        private boolean failComplete;
        private int completeAttempts;
        private boolean manualReplayClaimed;
        private final Ownership ownership = new Ownership(
                TENANT_ID, "9001", "4001", "3001", "1001", "client-1", "owner-1", 1
        );

        @Override
        public Claim claim(ClaimRequest request) {
            transactionActiveAtClaim = TransactionSynchronizationManager.isActualTransactionActive();
            return Claim.acquired(ownership);
        }

        @Override public void markModelDispatched(Ownership ownership, Instant leaseExpiresAt, Instant now) { }

        @Override
        public void recordResultReceived(
                Ownership ownership,
                Finalization finalization,
                Instant leaseExpiresAt,
                Instant now
        ) {
            transactionActiveAtResultReceived = TransactionSynchronizationManager.isActualTransactionActive();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { resultReceived = true; }
            });
        }

        @Override
        public List<Claim> claimRecoverableResults(RecoveryRequest request) {
            return List.of();
        }

        @Override
        public FinalizationFailureDisposition recordFinalizationFailure(FinalizationFailureRequest request) {
            return FinalizationFailureDisposition.RETRY_SCHEDULED;
        }

        @Override
        public List<DeadLetter> findDeadLetters(TenantId tenantId, int limit) {
            return List.of();
        }

        @Override
        public ReplayClaim claimDeadLetterForReplay(ManualReplayRequest request) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { manualReplayClaimed = true; }
            });
            return ReplayClaim.acquired(ownership, finalization());
        }

        @Override
        public void complete(Ownership ownership, ChatMessageResult result, Instant now) {
            completeAttempts++;
            transactionActiveAtComplete = TransactionSynchronizationManager.isActualTransactionActive();
            if (failComplete) {
                throw new IllegalStateException("run completion failed");
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { completed = true; }
            });
        }

        @Override public void failPreparing(Ownership ownership, String code, String summary, Instant now) { }
        @Override public void markDispatchedUncertain(Ownership ownership, String code, String summary, Instant now) { }

        void reset() {
            transactionActiveAtClaim = false;
            transactionActiveAtResultReceived = false;
            transactionActiveAtComplete = false;
            resultReceived = false;
            completed = false;
            failComplete = false;
            completeAttempts = 0;
            manualReplayClaimed = false;
        }
    }

    static final class TransactionalReplayAuditPublisher implements AuditEventPublisher {
        private boolean fail;
        private boolean audited;

        @Override
        public void publish(AuditEvent event) {
            if (fail) {
                throw new IllegalStateException("audit unavailable");
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { audited = true; }
            });
        }

        void reset() {
            fail = false;
            audited = false;
        }
    }

    static final class TransactionalChatSessionRepository implements ChatSessionRepository {
        private ChatSession session;
        private boolean transactionActiveAtAppend;
        private int appendAttempts;
        private List<ChatMessage> committedMessages;
        private final List<ChatMessage> pending = new ArrayList<>();

        @Override public String nextSessionId() { return "4002"; }
        @Override public String nextMessageId() { return "5003"; }
        @Override public ChatSession save(ChatSession session) { return session; }
        @Override public Optional<ChatSession> findById(TenantId tenantId, String sessionId) { return Optional.of(session); }
        @Override public boolean existsByClientMessageId(TenantId tenantId, String sessionId, String id) { return false; }
        @Override public List<ChatSession> listSessions(TenantId tenantId, String appId, String userId) { return List.of(session); }

        @Override
        public ChatMessage appendMessage(ChatMessage message) {
            appendAttempts++;
            transactionActiveAtAppend = TransactionSynchronizationManager.isActualTransactionActive();
            pending.add(message);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { committedMessages = List.copyOf(pending); }
                @Override public void afterCompletion(int status) { pending.clear(); }
            });
            return message;
        }

        @Override public List<ChatMessage> listMessages(TenantId tenantId, String sessionId) { return committedMessages; }

        void reset() {
            session = new ChatSession(
                    TENANT_ID, "4001", "3001", "test-release-3001", 1, "1001", "session", NOW, NOW
            );
            transactionActiveAtAppend = false;
            appendAttempts = 0;
            committedMessages = List.of();
            pending.clear();
        }
    }

    static final class TransactionalDebugSnapshotPort implements AppStudioDebugRunSnapshotPort {
        private boolean transactionActiveAtRecord;
        private List<AppStudioDebugRunSnapshot> committedSnapshots;
        @Override
        public void record(AppStudioDebugRunSnapshot snapshot) {
            transactionActiveAtRecord = TransactionSynchronizationManager.isActualTransactionActive();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { committedSnapshots = List.of(snapshot); }
            });
        }
        void reset() { transactionActiveAtRecord = false; committedSnapshots = List.of(); }
    }
}
