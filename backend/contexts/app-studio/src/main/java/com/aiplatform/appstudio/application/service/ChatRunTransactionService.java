package com.aiplatform.appstudio.application.service;

import com.aiplatform.appstudio.application.command.SendChatMessageCommand;
import com.aiplatform.appstudio.application.error.AppStudioErrorCodes;
import com.aiplatform.appstudio.application.port.ChatRunRepository;
import com.aiplatform.appstudio.application.port.ChatRunRepository.Claim;
import com.aiplatform.appstudio.application.port.ChatRunRepository.ClaimRequest;
import com.aiplatform.appstudio.application.port.ChatRunRepository.ClaimState;
import com.aiplatform.appstudio.application.port.ChatRunRepository.Finalization;
import com.aiplatform.appstudio.application.port.ChatRunRepository.FinalizationFailureDisposition;
import com.aiplatform.appstudio.application.port.ChatRunRepository.FinalizationFailureRequest;
import com.aiplatform.appstudio.application.port.ChatRunRepository.Ownership;
import com.aiplatform.appstudio.application.port.ChatRunRepository.RecoveryRequest;
import com.aiplatform.appstudio.application.result.ChatMessageResult;
import com.aiplatform.appstudio.domain.ChatMessage;
import com.aiplatform.appstudio.domain.ChatRole;
import com.aiplatform.appstudio.domain.ChatSession;
import com.aiplatform.appstudio.domain.port.ChatSessionRepository;
import com.aiplatform.appstudio.domain.port.debug.AppStudioDebugRunSnapshotPort;
import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunSnapshot;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 聊天运行的短事务边界。外部检索与模型调用不应进入此服务。
 */
@Service
public class ChatRunTransactionService {
    private static final Duration PREPARING_LEASE = Duration.ofMinutes(5);
    private static final Duration MODEL_DISPATCH_LEASE = Duration.ofMinutes(15);
    private static final Duration RESULT_RECEIVED_LEASE = Duration.ofSeconds(30);
    private static final String DUPLICATE_SUMMARY = "该 clientMessageId 已存在历史消息，禁止重复执行";
    private static final String FINALIZATION_FAILURE_CODE = "FINALIZATION_TRANSACTION_FAILED";
    private static final String FINALIZATION_FAILURE_SUMMARY = "聊天最终化事务失败，已进入受控恢复";

    private final ChatRunRepository runRepository;
    private final ChatSessionRepository sessionRepository;
    private final AppStudioDebugRunSnapshotPort snapshotPort;
    private final Clock clock;

    public ChatRunTransactionService(
            ChatRunRepository runRepository,
            ChatSessionRepository sessionRepository,
            AppStudioDebugRunSnapshotPort snapshotPort,
            Clock clock
    ) {
        this.runRepository = Objects.requireNonNull(runRepository, "聊天运行仓储不能为空");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "聊天会话仓储不能为空");
        this.snapshotPort = Objects.requireNonNull(snapshotPort, "调试运行快照端口不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    @Transactional
    public Claim claim(SendChatMessageCommand command, String applicationId) {
        Objects.requireNonNull(command, "聊天命令不能为空");
        String requiredApplicationId = requireText(applicationId, "应用 ID");
        requireCanonicalSession(
                command.tenantId(), command.sessionId(), requiredApplicationId, command.actorId()
        );
        Instant now = Instant.now(clock);
        Claim claim = runRepository.claim(new ClaimRequest(
                command.tenantId(),
                command.sessionId(),
                requiredApplicationId,
                command.actorId(),
                command.clientMessageId(),
                ChatRunCommandHasher.hash(command, requiredApplicationId),
                UUID.randomUUID().toString(),
                now,
                now.plus(PREPARING_LEASE)
        ));
        if (claim.state() == ClaimState.ACQUIRED
                && sessionRepository.existsByClientMessageId(
                        command.tenantId(), command.sessionId(), command.clientMessageId())) {
            runRepository.failPreparing(
                    claim.ownership(),
                    AppStudioErrorCodes.DUPLICATE_MESSAGE_REQUEST.code(),
                    DUPLICATE_SUMMARY,
                    now
            );
            return Claim.failed(
                    claim.ownership(),
                    AppStudioErrorCodes.DUPLICATE_MESSAGE_REQUEST.code(),
                    DUPLICATE_SUMMARY
            );
        }
        return claim;
    }

    @Transactional
    public void markModelDispatched(Ownership ownership) {
        Instant now = Instant.now(clock);
        runRepository.markModelDispatched(
                requireOwnership(ownership),
                now.plus(MODEL_DISPATCH_LEASE),
                now
        );
    }

    @Transactional
    public void recordResultReceived(Ownership ownership, Finalization finalization) {
        Ownership requiredOwnership = requireOwnership(ownership);
        Finalization requiredFinalization = Objects.requireNonNull(finalization, "聊天完成事实不能为空");
        requireCanonicalSession(
                requiredOwnership.tenantId(),
                requiredOwnership.sessionId(),
                requiredOwnership.applicationId(),
                requiredOwnership.actorId()
        );
        requireCanonicalFinalization(requiredOwnership, requiredFinalization);
        Instant now = Instant.now(clock);
        runRepository.recordResultReceived(
                requiredOwnership,
                requiredFinalization,
                now.plus(RESULT_RECEIVED_LEASE),
                now
        );
    }

    @Transactional
    public java.util.List<Claim> claimRecoverableResults(int limit) {
        Instant now = Instant.now(clock);
        return runRepository.claimRecoverableResults(new RecoveryRequest(
                UUID.randomUUID().toString(),
                now,
                now.plus(RESULT_RECEIVED_LEASE),
                limit
        ));
    }

    @Transactional
    public FinalizationFailureDisposition recordFinalizationFailure(Ownership ownership, String traceId) {
        return runRepository.recordFinalizationFailure(new FinalizationFailureRequest(
                requireOwnership(ownership),
                FINALIZATION_FAILURE_CODE,
                FINALIZATION_FAILURE_SUMMARY,
                requireText(traceId, "最终化失败 traceId"),
                Instant.now(clock),
                ChatRunRepository.FINALIZATION_MAX_ATTEMPTS,
                ChatRunRepository.FINALIZATION_BACKOFF_SECONDS
        ));
    }

    @Transactional
    public void complete(Ownership ownership, Finalization finalization) {
        Ownership requiredOwnership = requireOwnership(ownership);
        Finalization requiredFinalization = Objects.requireNonNull(finalization, "聊天完成事实不能为空");
        requireCanonicalSession(
                requiredOwnership.tenantId(),
                requiredOwnership.sessionId(),
                requiredOwnership.applicationId(),
                requiredOwnership.actorId()
        );
        requireCanonicalFinalization(requiredOwnership, requiredFinalization);

        ChatMessage persistedUser = sessionRepository.appendMessage(requiredFinalization.userMessage());
        requirePersistedMessage(requiredFinalization.userMessage(), persistedUser, "用户消息");
        ChatMessage persistedAssistant = sessionRepository.appendMessage(requiredFinalization.assistantMessage());
        requirePersistedMessage(requiredFinalization.assistantMessage(), persistedAssistant, "助手消息");
        snapshotPort.record(requiredFinalization.debugSnapshot());
        runRepository.complete(requiredOwnership, requiredFinalization.result(), Instant.now(clock));
    }

    @Transactional
    public void failPreparing(Ownership ownership, String failureCode, String failureSummary) {
        runRepository.failPreparing(
                requireOwnership(ownership),
                requireText(failureCode, "失败码"),
                requireText(failureSummary, "失败摘要"),
                Instant.now(clock)
        );
    }

    @Transactional
    public void markDispatchedUncertain(Ownership ownership, String failureCode, String failureSummary) {
        runRepository.markDispatchedUncertain(
                requireOwnership(ownership),
                requireText(failureCode, "失败码"),
                requireText(failureSummary, "失败摘要"),
                Instant.now(clock)
        );
    }

    private ChatSession requireCanonicalSession(
            com.aiplatform.shared.kernel.TenantId tenantId,
            String sessionId,
            String applicationId,
            String actorId
    ) {
        ChatSession session = sessionRepository.findById(tenantId, sessionId)
                .orElseThrow(() -> new IllegalStateException("聊天会话不存在或已删除"));
        if (!tenantId.equals(session.tenantId())
                || !sessionId.equals(session.sessionId())
                || !applicationId.equals(session.applicationId())
                || !actorId.equals(session.userId())) {
            throw new IllegalStateException("会话持久化身份与聊天运行不一致");
        }
        return session;
    }

    private static void requireCanonicalFinalization(Ownership ownership, Finalization finalization) {
        ChatMessage user = finalization.userMessage();
        ChatMessage assistant = finalization.assistantMessage();
        ChatMessageResult result = finalization.result();
        AppStudioDebugRunSnapshot snapshot = finalization.debugSnapshot();
        if (!ownership.tenantId().equals(user.tenantId())
                || !ownership.sessionId().equals(user.sessionId())
                || user.role() != ChatRole.USER
                || !ownership.clientMessageId().equals(user.clientMessageId())
                || !ownership.tenantId().equals(assistant.tenantId())
                || !ownership.sessionId().equals(assistant.sessionId())
                || assistant.role() != ChatRole.ASSISTANT
                || assistant.clientMessageId() != null
                || !result.userMessageId().equals(user.messageId())
                || !result.assistantMessageId().equals(assistant.messageId())
                || !result.sessionId().equals(ownership.sessionId())
                || !result.applicationId().equals(ownership.applicationId())
                || !snapshot.tenantId().equals(ownership.tenantId())
                || !snapshot.assistantMessageId().equals(assistant.messageId())) {
            throw new IllegalStateException("聊天完成事实与运行 ownership 不一致");
        }
    }

    private static void requirePersistedMessage(ChatMessage expected, ChatMessage persisted, String fieldName) {
        if (persisted == null
                || !expected.tenantId().equals(persisted.tenantId())
                || !expected.messageId().equals(persisted.messageId())
                || !expected.sessionId().equals(persisted.sessionId())
                || expected.role() != persisted.role()
                || !Objects.equals(expected.clientMessageId(), persisted.clientMessageId())) {
            throw new IllegalStateException(fieldName + "持久化身份不一致");
        }
    }

    private static Ownership requireOwnership(Ownership ownership) {
        return Objects.requireNonNull(ownership, "聊天运行 ownership 不能为空");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

}
