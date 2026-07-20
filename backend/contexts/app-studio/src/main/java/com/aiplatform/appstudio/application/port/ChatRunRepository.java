package com.aiplatform.appstudio.application.port;

import com.aiplatform.appstudio.application.result.ChatMessageResult;
import com.aiplatform.appstudio.domain.ChatMessage;
import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunSnapshot;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * App Studio 聊天命令运行仓储。接口只暴露 claim、fenced 状态迁移和完成快照，隐藏 SQL 状态机细节。
 */
public interface ChatRunRepository {
    int FINALIZATION_MAX_ATTEMPTS = 3;
    int FINALIZATION_BACKOFF_SECONDS = 5;

    Claim claim(ClaimRequest request);

    void markModelDispatched(Ownership ownership, Instant leaseExpiresAt, Instant now);

    void recordResultReceived(
            Ownership ownership,
            Finalization finalization,
            Instant leaseExpiresAt,
            Instant now
    );

    List<Claim> claimRecoverableResults(RecoveryRequest request);

    FinalizationFailureDisposition recordFinalizationFailure(FinalizationFailureRequest request);

    List<DeadLetter> findDeadLetters(TenantId tenantId, int limit);

    ReplayClaim claimDeadLetterForReplay(ManualReplayRequest request);

    void complete(Ownership ownership, ChatMessageResult result, Instant now);

    void failPreparing(Ownership ownership, String failureCode, String failureSummary, Instant now);

    void markDispatchedUncertain(Ownership ownership, String failureCode, String failureSummary, Instant now);

    enum ClaimState {
        ACQUIRED,
        IN_PROGRESS,
        RESULT_RECEIVED,
        COMPLETED,
        FAILED,
        UNCERTAIN,
        DEAD_LETTER
    }

    enum FinalizationFailureDisposition {
        RETRY_SCHEDULED,
        DEAD_LETTER
    }

    enum ReplayClaimState {
        ACQUIRED,
        ALREADY_ACCEPTED
    }

    record RecoveryRequest(
            String leaseOwner,
            Instant now,
            Instant leaseExpiresAt,
            int limit,
            int maxAttempts
    ) {
        public RecoveryRequest(String leaseOwner, Instant now, Instant leaseExpiresAt, int limit) {
            this(leaseOwner, now, leaseExpiresAt, limit, FINALIZATION_MAX_ATTEMPTS);
        }

        public RecoveryRequest {
            leaseOwner = requireText(leaseOwner, "恢复 worker ID");
            now = Objects.requireNonNull(now, "当前时间不能为空");
            leaseExpiresAt = Objects.requireNonNull(leaseExpiresAt, "恢复租约到期时间不能为空");
            if (!leaseExpiresAt.isAfter(now)) {
                throw new IllegalArgumentException("恢复租约到期时间必须晚于当前时间");
            }
            if (limit < 1 || limit > 100) {
                throw new IllegalArgumentException("恢复批量大小必须位于 1..100");
            }
            if (maxAttempts != FINALIZATION_MAX_ATTEMPTS) {
                throw new IllegalArgumentException("最终化最大尝试次数必须固定为 3");
            }
        }
    }

    record FinalizationFailureRequest(
            Ownership ownership,
            String failureCode,
            String failureSummary,
            String traceId,
            Instant now,
            int maxAttempts,
            int baseBackoffSeconds
    ) {
        public FinalizationFailureRequest {
            ownership = Objects.requireNonNull(ownership, "运行 ownership 不能为空");
            failureCode = requireText(failureCode, "最终化失败码");
            failureSummary = requireText(failureSummary, "最终化失败摘要");
            traceId = requireText(traceId, "最终化失败 traceId");
            now = Objects.requireNonNull(now, "最终化失败时间不能为空");
            if (traceId.length() > 64) {
                throw new IllegalArgumentException("最终化失败 traceId 长度不能超过 64");
            }
            if (failureCode.length() > 128 || failureSummary.length() > 512) {
                throw new IllegalArgumentException("最终化失败字段超过持久化上限");
            }
            if (maxAttempts != FINALIZATION_MAX_ATTEMPTS) {
                throw new IllegalArgumentException("最终化最大尝试次数必须固定为 3");
            }
            if (baseBackoffSeconds < 1 || baseBackoffSeconds > 3600) {
                throw new IllegalArgumentException("最终化退避秒数必须位于 1..3600");
            }
        }
    }

    record ManualReplayRequest(
            TenantId tenantId,
            String runId,
            String actorId,
            String replayRequestId,
            String reason,
            String leaseOwner,
            Instant now,
            Instant leaseExpiresAt
    ) {
        public ManualReplayRequest {
            tenantId = Objects.requireNonNull(tenantId, "租户 ID 不能为空");
            runId = requireText(runId, "运行 ID");
            actorId = requireText(actorId, "重放操作者 ID");
            replayRequestId = requireText(replayRequestId, "重放请求 ID");
            reason = requireText(reason, "重放原因");
            leaseOwner = requireText(leaseOwner, "重放租约 owner");
            now = Objects.requireNonNull(now, "重放时间不能为空");
            leaseExpiresAt = Objects.requireNonNull(leaseExpiresAt, "重放租约到期时间不能为空");
            if (replayRequestId.length() > 128 || reason.length() > 512) {
                throw new IllegalArgumentException("重放请求字段超过持久化上限");
            }
            if (!leaseExpiresAt.isAfter(now)) {
                throw new IllegalArgumentException("重放租约到期时间必须晚于当前时间");
            }
        }
    }

    record DeadLetter(
            String runId,
            TenantId tenantId,
            String sessionId,
            String applicationId,
            String actorId,
            int finalizationAttemptCount,
            int finalizationCycleAttemptCount,
            int manualReplayCount,
            String failureCode,
            String failureSummary,
            String failureTraceId,
            Instant updatedAt
    ) {
        public DeadLetter {
            runId = requireText(runId, "运行 ID");
            tenantId = Objects.requireNonNull(tenantId, "租户 ID 不能为空");
            sessionId = requireText(sessionId, "会话 ID");
            applicationId = requireText(applicationId, "应用 ID");
            actorId = requireText(actorId, "原操作者 ID");
            failureCode = requireText(failureCode, "死信失败码");
            failureSummary = requireText(failureSummary, "死信失败摘要");
            failureTraceId = requireText(failureTraceId, "死信 traceId");
            updatedAt = Objects.requireNonNull(updatedAt, "死信更新时间不能为空");
            if (finalizationAttemptCount < 1 || finalizationCycleAttemptCount < 1 || manualReplayCount < 0) {
                throw new IllegalArgumentException("死信尝试计数无效");
            }
        }
    }

    record ReplayClaim(
            ReplayClaimState state,
            String persistedStatus,
            Ownership ownership,
            Finalization finalization
    ) {
        public ReplayClaim {
            state = Objects.requireNonNull(state, "重放 claim 状态不能为空");
            persistedStatus = requireText(persistedStatus, "重放持久化状态");
            ownership = Objects.requireNonNull(ownership, "重放 ownership 不能为空");
            if (state == ReplayClaimState.ACQUIRED && finalization == null) {
                throw new IllegalArgumentException("已获取重放必须携带最终化事实");
            }
            if (state != ReplayClaimState.ACQUIRED && finalization != null) {
                throw new IllegalArgumentException("幂等重放结果不得携带最终化事实");
            }
        }

        public static ReplayClaim acquired(Ownership ownership, Finalization finalization) {
            return new ReplayClaim(ReplayClaimState.ACQUIRED, "IN_PROGRESS", ownership, finalization);
        }

        public static ReplayClaim alreadyAccepted(String persistedStatus, Ownership ownership) {
            return new ReplayClaim(ReplayClaimState.ALREADY_ACCEPTED, persistedStatus, ownership, null);
        }
    }

    record ClaimRequest(
            TenantId tenantId,
            String sessionId,
            String applicationId,
            String actorId,
            String clientMessageId,
            String commandHash,
            String leaseOwner,
            Instant now,
            Instant leaseExpiresAt
    ) {
        public ClaimRequest {
            tenantId = Objects.requireNonNull(tenantId, "租户 ID 不能为空");
            sessionId = requireText(sessionId, "会话 ID");
            applicationId = requireText(applicationId, "应用 ID");
            actorId = requireText(actorId, "操作者 ID");
            clientMessageId = requireText(clientMessageId, "客户端消息 ID");
            commandHash = requireText(commandHash, "命令摘要");
            leaseOwner = requireText(leaseOwner, "租约 owner");
            now = Objects.requireNonNull(now, "当前时间不能为空");
            leaseExpiresAt = Objects.requireNonNull(leaseExpiresAt, "租约到期时间不能为空");
            if (!leaseExpiresAt.isAfter(now)) {
                throw new IllegalArgumentException("租约到期时间必须晚于当前时间");
            }
        }
    }

    record Ownership(
            TenantId tenantId,
            String runId,
            String sessionId,
            String applicationId,
            String actorId,
            String clientMessageId,
            String leaseOwner,
            long fencingToken
    ) {
        public Ownership {
            tenantId = Objects.requireNonNull(tenantId, "租户 ID 不能为空");
            runId = requireText(runId, "运行 ID");
            sessionId = requireText(sessionId, "会话 ID");
            applicationId = requireText(applicationId, "应用 ID");
            actorId = requireText(actorId, "操作者 ID");
            clientMessageId = requireText(clientMessageId, "客户端消息 ID");
            leaseOwner = requireText(leaseOwner, "租约 owner");
            if (fencingToken < 1) {
                throw new IllegalArgumentException("fencing token 必须大于 0");
            }
        }
    }

    /**
     * 已生成、可安全重放 Tx B 的最终事实。持久化 adapter 使用显式版本 DTO，避免把该 Java 类型当作长期 JSON schema。
     */
    record Finalization(
            ChatMessage userMessage,
            ChatMessage assistantMessage,
            AppStudioDebugRunSnapshot debugSnapshot,
            ChatMessageResult result
    ) {
        public Finalization {
            userMessage = Objects.requireNonNull(userMessage, "用户消息不能为空");
            assistantMessage = Objects.requireNonNull(assistantMessage, "助手消息不能为空");
            debugSnapshot = Objects.requireNonNull(debugSnapshot, "调试运行快照不能为空");
            result = Objects.requireNonNull(result, "聊天完成结果不能为空");
        }
    }

    record Claim(
            ClaimState state,
            Ownership ownership,
            ChatMessageResult completedResult,
            Finalization finalization,
            String failureCode,
            String failureSummary
    ) {
        public Claim {
            state = Objects.requireNonNull(state, "claim 状态不能为空");
            ownership = Objects.requireNonNull(ownership, "claim ownership 不能为空");
            if (state == ClaimState.COMPLETED && completedResult == null) {
                throw new IllegalArgumentException("已完成 claim 必须携带结果");
            }
            if (state != ClaimState.COMPLETED && completedResult != null) {
                throw new IllegalArgumentException("非完成 claim 不得携带完成结果");
            }
            if (state == ClaimState.RESULT_RECEIVED && finalization == null) {
                throw new IllegalArgumentException("已接收结果 claim 必须携带完成事实");
            }
            if (state != ClaimState.RESULT_RECEIVED && finalization != null) {
                throw new IllegalArgumentException("非结果接收 claim 不得携带完成事实");
            }
        }

        public static Claim acquired(Ownership ownership) {
            return new Claim(ClaimState.ACQUIRED, ownership, null, null, null, null);
        }

        public static Claim inProgress(Ownership ownership) {
            return new Claim(ClaimState.IN_PROGRESS, ownership, null, null, null, null);
        }

        public static Claim resultReceived(Ownership ownership, Finalization finalization) {
            return new Claim(
                    ClaimState.RESULT_RECEIVED,
                    ownership,
                    null,
                    Objects.requireNonNull(finalization),
                    null,
                    null
            );
        }

        public static Claim completed(Ownership ownership, ChatMessageResult result) {
            return new Claim(ClaimState.COMPLETED, ownership, Objects.requireNonNull(result), null, null, null);
        }

        public static Claim failed(Ownership ownership, String code, String summary) {
            return new Claim(ClaimState.FAILED, ownership, null, null, code, summary);
        }

        public static Claim uncertain(Ownership ownership, String code, String summary) {
            return new Claim(ClaimState.UNCERTAIN, ownership, null, null, code, summary);
        }

        public static Claim deadLetter(Ownership ownership, String code, String summary) {
            return new Claim(ClaimState.DEAD_LETTER, ownership, null, null, code, summary);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
