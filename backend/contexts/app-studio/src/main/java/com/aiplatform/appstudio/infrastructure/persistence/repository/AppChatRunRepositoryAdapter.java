package com.aiplatform.appstudio.infrastructure.persistence.repository;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.appstudio.application.error.AppStudioErrorCodes;
import com.aiplatform.appstudio.application.port.ChatRunRepository;
import com.aiplatform.appstudio.application.port.ChatRunRepository.Finalization;
import com.aiplatform.appstudio.application.port.ChatRunRepository.FinalizationFailureDisposition;
import com.aiplatform.appstudio.application.port.ChatRunRepository.FinalizationFailureRequest;
import com.aiplatform.appstudio.application.port.ChatRunRepository.ManualReplayRequest;
import com.aiplatform.appstudio.application.port.ChatRunRepository.RecoveryRequest;
import com.aiplatform.appstudio.application.port.ChatRunRepository.ReplayClaim;
import com.aiplatform.appstudio.application.result.ChatMessageResult;
import com.aiplatform.appstudio.infrastructure.persistence.entity.AppChatRunPersistenceEntity;
import com.aiplatform.appstudio.infrastructure.persistence.mapper.AppChatRunMapper;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.TenantId;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Repository;

@Repository
public class AppChatRunRepositoryAdapter implements ChatRunRepository {
    private static final String UNCERTAIN_CODE = "MODEL_RESULT_UNCERTAIN";
    private static final String UNCERTAIN_SUMMARY = "模型调用已派发但结果尚未确认";
    private static final String INVALID_FINALIZATION_CODE = "FINALIZATION_PAYLOAD_INVALID";
    private static final String INVALID_FINALIZATION_SUMMARY = "聊天完成事实载荷无效，已安全隔离";

    private final AppChatRunMapper mapper;
    private final AppChatRunFinalizationCodec finalizationCodec;

    public AppChatRunRepositoryAdapter(AppChatRunMapper mapper, JsonMapper objectMapper) {
        this.mapper = Objects.requireNonNull(mapper, "聊天运行 Mapper 不能为空");
        this.finalizationCodec = new AppChatRunFinalizationCodec(
                Objects.requireNonNull(objectMapper, "JSON mapper 不能为空")
        );
    }

    @Override
    public Claim claim(ClaimRequest request) {
        AppChatRunPersistenceEntity candidate = AppChatRunPersistenceEntity.newClaim(request);
        int inserted = mapper.insertIfAbsent(candidate);
        AppChatRunPersistenceEntity row = Objects.requireNonNull(
                mapper.selectForUpdate(candidate.getTenantId(), candidate.getSessionId(), request.clientMessageId()),
                "聊天运行 claim 后未找到持久化记录"
        );
        requireCanonicalIdentity(row, request);
        if (!request.commandHash().equals(row.getCommandHash())) {
            throw new ApplicationException(
                    AppStudioErrorCodes.DUPLICATE_MESSAGE_REQUEST,
                    "clientMessageId 已绑定到不同聊天命令"
            );
        }
        Ownership current = ownership(row);
        if (inserted == 1) {
            if (!request.leaseOwner().equals(row.getLeaseOwner())) {
                throw new IllegalStateException("新建聊天运行的租约 owner 不一致");
            }
            return Claim.acquired(current);
        }
        return switch (requiredText(row.getStatus(), "运行状态")) {
            case "COMPLETED" -> Claim.completed(current, readResult(row.getResultJson()));
            case "FAILED" -> Claim.failed(current, row.getFailureCode(), row.getFailureSummary());
            case "QUARANTINED" -> Claim.failed(current, row.getFailureCode(), row.getFailureSummary());
            case "UNCERTAIN" -> Claim.uncertain(current, row.getFailureCode(), row.getFailureSummary());
            case "DEAD_LETTER" -> Claim.deadLetter(current, row.getFailureCode(), row.getFailureSummary());
            case "IN_PROGRESS" -> claimInProgress(row, request, current);
            default -> throw new IllegalStateException("未知聊天运行状态：" + row.getStatus());
        };
    }

    private Claim claimInProgress(AppChatRunPersistenceEntity row, ClaimRequest request, Ownership current) {
        Instant expiresAt = Objects.requireNonNull(row.getLeaseExpiresAt(), "租约到期时间不能为空");
        if (expiresAt.isAfter(request.now())) {
            return Claim.inProgress(current);
        }
        String phase = requiredText(row.getPhase(), "运行阶段");
        if ("RESULT_RECEIVED".equals(phase)) {
            if (requiredCount(row.getFinalizationCycleAttemptCount(), "最终化周期尝试次数")
                    >= FINALIZATION_MAX_ATTEMPTS) {
                String failureCode = row.getFailureCode() == null ? "FINALIZATION_ATTEMPTS_EXHAUSTED" : row.getFailureCode();
                String failureSummary = row.getFailureSummary() == null
                        ? "聊天最终化尝试已耗尽"
                        : row.getFailureSummary();
                requiredUpdated(mapper.recordFinalizationFailure(
                        row.getTenantId(), row.getRunId(), row.getLeaseOwner(), row.getFencingToken(),
                        failureCode, failureSummary,
                        row.getFailureTraceId() == null ? "client-reclaim-sweep" : row.getFailureTraceId(),
                        request.now(), FINALIZATION_MAX_ATTEMPTS, FINALIZATION_BACKOFF_SECONDS
                ), "耗尽聊天最终化死信标记");
                return Claim.deadLetter(current, failureCode, failureSummary);
            }
            requiredUpdated(mapper.reclaimResultReceived(
                    row.getTenantId(), row.getRunId(), row.getLeaseOwner(), row.getFencingToken(),
                    request.leaseOwner(), request.leaseExpiresAt(), request.now()
            ), "已接收结果租约恢复");
            Ownership recovered = recoveredOwnership(row, request);
            try {
                return Claim.resultReceived(recovered, readFinalization(row, recovered));
            } catch (InvalidFinalizationPayloadException exception) {
                quarantineInvalidFinalization(row, recovered, request.now());
                return Claim.failed(recovered, INVALID_FINALIZATION_CODE, INVALID_FINALIZATION_SUMMARY);
            }
        }
        if ("MODEL_DISPATCHED".equals(phase)) {
            requiredUpdated(mapper.markExpiredDispatchedUncertain(
                    row.getTenantId(), row.getRunId(), row.getLeaseOwner(), row.getFencingToken(), request.now()
            ), "过期模型运行不确定态标记");
            return Claim.uncertain(current, UNCERTAIN_CODE, UNCERTAIN_SUMMARY);
        }
        if (!"PREPARING".equals(phase)) {
            throw new IllegalStateException("未知聊天运行阶段：" + phase);
        }
        requiredUpdated(mapper.reclaimPreparing(
                row.getTenantId(), row.getRunId(), row.getLeaseOwner(), row.getFencingToken(),
                request.leaseOwner(), request.leaseExpiresAt(), request.now()
        ), "聊天运行租约恢复");
        return Claim.acquired(recoveredOwnership(row, request));
    }

    private static Ownership recoveredOwnership(AppChatRunPersistenceEntity row, ClaimRequest request) {
        return new Ownership(
                request.tenantId(),
                String.valueOf(row.getRunId()),
                request.sessionId(),
                request.applicationId(),
                request.actorId(),
                request.clientMessageId(),
                request.leaseOwner(),
                row.getFencingToken() + 1
        );
    }

    @Override
    public void markModelDispatched(Ownership ownership, Instant leaseExpiresAt, Instant now) {
        requiredUpdated(mapper.markModelDispatched(
                tenantId(ownership), runId(ownership), ownership.leaseOwner(), ownership.fencingToken(),
                Objects.requireNonNull(leaseExpiresAt), Objects.requireNonNull(now)
        ), "模型派发阶段标记");
    }

    @Override
    public void recordResultReceived(
            Ownership ownership,
            Finalization finalization,
            Instant leaseExpiresAt,
            Instant now
    ) {
        requiredUpdated(mapper.recordResultReceived(
                tenantId(ownership), runId(ownership), ownership.leaseOwner(), ownership.fencingToken(),
                finalizationCodec.write(Objects.requireNonNull(finalization, "聊天完成事实不能为空")),
                Objects.requireNonNull(leaseExpiresAt), Objects.requireNonNull(now)
        ), "模型结果接收标记");
    }

    @Override
    public List<Claim> claimRecoverableResults(RecoveryRequest request) {
        RecoveryRequest requiredRequest = Objects.requireNonNull(request, "恢复请求不能为空");
        mapper.deadLetterExhaustedFinalizations(
                requiredRequest.now(), requiredRequest.maxAttempts(),
                "FINALIZATION_ATTEMPTS_EXHAUSTED", "聊天最终化尝试已耗尽", requiredRequest.leaseOwner(),
                requiredRequest.limit()
        );
        List<AppChatRunPersistenceEntity> rows = mapper.claimRecoverableResults(
                requiredRequest.leaseOwner(),
                requiredRequest.leaseExpiresAt(),
                requiredRequest.now(),
                requiredRequest.limit(),
                requiredRequest.maxAttempts()
        );
        List<Claim> recoverable = new ArrayList<>(rows.size());
        for (AppChatRunPersistenceEntity row : rows) {
            try {
                recoverable.add(recoverableClaim(row, requiredRequest));
        } catch (InvalidFinalizationPayloadException exception) {
                quarantineInvalidFinalization(row, ownership(row), requiredRequest.now());
            }
        }
        return List.copyOf(recoverable);
    }

    @Override
    public FinalizationFailureDisposition recordFinalizationFailure(FinalizationFailureRequest request) {
        FinalizationFailureRequest requiredRequest = Objects.requireNonNull(request, "最终化失败请求不能为空");
        Ownership ownership = requiredRequest.ownership();
        requiredUpdated(mapper.recordFinalizationFailure(
                tenantId(ownership), runId(ownership), ownership.leaseOwner(), ownership.fencingToken(),
                requiredRequest.failureCode(), requiredRequest.failureSummary(), requiredRequest.traceId(),
                requiredRequest.now(), requiredRequest.maxAttempts(), requiredRequest.baseBackoffSeconds()
        ), "聊天最终化失败记录");
        AppChatRunPersistenceEntity updated = Objects.requireNonNull(
                mapper.selectRunForUpdate(tenantId(ownership), runId(ownership)),
                "聊天最终化失败记录后运行不存在"
        );
        requireOwnershipRow(updated, ownership);
        return switch (requiredText(updated.getStatus(), "最终化失败后状态")) {
            case "IN_PROGRESS" -> FinalizationFailureDisposition.RETRY_SCHEDULED;
            case "DEAD_LETTER" -> FinalizationFailureDisposition.DEAD_LETTER;
            default -> throw new IllegalStateException("最终化失败产生了非法状态");
        };
    }

    @Override
    public List<DeadLetter> findDeadLetters(TenantId tenantId, int limit) {
        TenantId requiredTenantId = Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("死信查询条数必须位于 1..100");
        }
        return mapper.findDeadLetters(parseRequiredLong(requiredTenantId.value(), "租户 ID"), limit).stream()
                .map(row -> deadLetter(requiredTenantId, row))
                .toList();
    }

    @Override
    public ReplayClaim claimDeadLetterForReplay(ManualReplayRequest request) {
        ManualReplayRequest requiredRequest = Objects.requireNonNull(request, "人工重放请求不能为空");
        Long tenantId = parseRequiredLong(requiredRequest.tenantId().value(), "租户 ID");
        Long runId = parseRequiredLong(requiredRequest.runId(), "运行 ID");
        AppChatRunPersistenceEntity row = mapper.selectRunForUpdate(tenantId, runId);
        if (row == null) {
            throw new ApplicationException(AppStudioErrorCodes.RESOURCE_NOT_VISIBLE, "聊天运行不可见");
        }
        Ownership current = ownership(row);
        Long replayedRunId = mapper.findReplayRequestRunId(tenantId, requiredRequest.replayRequestId());
        if (replayedRunId != null && replayedRunId > 0) {
            if (replayedRunId.equals(runId)) {
                return ReplayClaim.alreadyAccepted(requiredText(row.getStatus(), "运行状态"), current);
            }
            throw new ApplicationException(
                    AppStudioErrorCodes.CHAT_RUN_REPLAY_CONFLICT,
                    "重放请求 ID 已绑定到其他聊天运行"
            );
        }
        if (requiredRequest.replayRequestId().equals(row.getLastReplayRequestId())) {
            return ReplayClaim.alreadyAccepted(requiredText(row.getStatus(), "运行状态"), current);
        }
        if (!"DEAD_LETTER".equals(row.getStatus()) || !"RESULT_RECEIVED".equals(row.getPhase())) {
            throw new ApplicationException(AppStudioErrorCodes.CHAT_RUN_REPLAY_CONFLICT, "只有最终化死信可以人工重放");
        }
        Finalization finalization = readFinalization(row, current);
        int inserted = mapper.insertReplayRequest(
                tenantId,
                runId,
                requiredRequest.replayRequestId(),
                parseRequiredLong(requiredRequest.actorId(), "重放操作者 ID"),
                requiredRequest.reason(),
                requiredRequest.now()
        );
        if (inserted != 1) {
            Long concurrentRunId = mapper.findReplayRequestRunId(tenantId, requiredRequest.replayRequestId());
            if (concurrentRunId != null && concurrentRunId > 0 && runId.equals(concurrentRunId)) {
                return ReplayClaim.alreadyAccepted(requiredText(row.getStatus(), "运行状态"), current);
            }
            throw new ApplicationException(
                    AppStudioErrorCodes.CHAT_RUN_REPLAY_CONFLICT,
                    "重放请求 ID 已绑定到其他聊天运行"
            );
        }
        requiredUpdated(mapper.claimDeadLetterForReplay(
                tenantId, runId, requiredRequest.leaseOwner(), requiredRequest.leaseExpiresAt(),
                requiredRequest.replayRequestId(), requiredRequest.reason(), requiredRequest.now()
        ), "聊天最终化死信重放 claim");
        Ownership replayOwnership = new Ownership(
                current.tenantId(), current.runId(), current.sessionId(), current.applicationId(),
                current.actorId(), current.clientMessageId(), requiredRequest.leaseOwner(),
                current.fencingToken() + 1
        );
        return ReplayClaim.acquired(replayOwnership, finalization);
    }

    private Claim recoverableClaim(AppChatRunPersistenceEntity row, RecoveryRequest request) {
        if (!"IN_PROGRESS".equals(row.getStatus())
                || !"RESULT_RECEIVED".equals(row.getPhase())
                || !request.leaseOwner().equals(row.getLeaseOwner())) {
            throw new IllegalStateException("恢复查询返回了非 RESULT_RECEIVED ownership");
        }
        Ownership ownership = ownership(row);
        return Claim.resultReceived(ownership, readFinalization(row, ownership));
    }

    private Finalization readFinalization(AppChatRunPersistenceEntity row, Ownership ownership) {
        Finalization finalization = finalizationCodec.read(row.getFinalizationJson());
        if (!ownership.tenantId().equals(finalization.userMessage().tenantId())
                || !ownership.tenantId().equals(finalization.assistantMessage().tenantId())
                || !ownership.tenantId().equals(finalization.debugSnapshot().tenantId())
                || !ownership.sessionId().equals(finalization.userMessage().sessionId())
                || !ownership.sessionId().equals(finalization.assistantMessage().sessionId())
                || !ownership.sessionId().equals(finalization.result().sessionId())
                || !ownership.applicationId().equals(finalization.result().applicationId())
                || !ownership.clientMessageId().equals(finalization.userMessage().clientMessageId())
                || finalization.assistantMessage().clientMessageId() != null
                || !finalization.userMessage().messageId().equals(finalization.result().userMessageId())
                || !finalization.assistantMessage().messageId().equals(finalization.result().assistantMessageId())
                || !finalization.assistantMessage().messageId()
                        .equals(finalization.debugSnapshot().assistantMessageId())) {
            throw new InvalidFinalizationPayloadException(
                    new IllegalArgumentException("聊天完成事实与持久化运行身份不一致")
            );
        }
        return finalization;
    }

    private DeadLetter deadLetter(TenantId expectedTenantId, AppChatRunPersistenceEntity row) {
        Ownership ownership = ownership(row);
        if (!expectedTenantId.equals(ownership.tenantId())
                || !"DEAD_LETTER".equals(row.getStatus())
                || !"RESULT_RECEIVED".equals(row.getPhase())) {
            throw new IllegalStateException("死信查询返回了跨租户或非死信运行");
        }
        readFinalization(row, ownership);
        return new DeadLetter(
                ownership.runId(), ownership.tenantId(), ownership.sessionId(), ownership.applicationId(),
                ownership.actorId(), requiredCount(row.getFinalizationAttemptCount(), "最终化总尝试次数"),
                requiredCount(row.getFinalizationCycleAttemptCount(), "最终化周期尝试次数"),
                requiredNonNegativeCount(row.getManualReplayCount(), "人工重放次数"),
                row.getFailureCode(), row.getFailureSummary(), row.getFailureTraceId(),
                Objects.requireNonNull(row.getUpdatedAt(), "死信更新时间不能为空")
        );
    }

    private void quarantineInvalidFinalization(
            AppChatRunPersistenceEntity row,
            Ownership ownership,
            Instant now
    ) {
        requiredUpdated(mapper.quarantineInvalidFinalization(
                row.getTenantId(), row.getRunId(), ownership.leaseOwner(), ownership.fencingToken(),
                INVALID_FINALIZATION_CODE, INVALID_FINALIZATION_SUMMARY, Objects.requireNonNull(now)
        ), "无效聊天完成事实隔离");
    }

    @Override
    public void complete(Ownership ownership, ChatMessageResult result, Instant now) {
        requiredUpdated(mapper.complete(
                tenantId(ownership), runId(ownership), ownership.leaseOwner(), ownership.fencingToken(),
                writeResult(Objects.requireNonNull(result, "聊天完成结果不能为空")), Objects.requireNonNull(now)
        ), "聊天运行完成");
    }

    @Override
    public void failPreparing(Ownership ownership, String failureCode, String failureSummary, Instant now) {
        requiredUpdated(mapper.failPreparing(
                tenantId(ownership), runId(ownership), ownership.leaseOwner(), ownership.fencingToken(),
                safeFailureText(failureCode, "失败码"), safeFailureText(failureSummary, "失败摘要"), Objects.requireNonNull(now)
        ), "聊天运行失败标记");
    }

    @Override
    public void markDispatchedUncertain(Ownership ownership, String failureCode, String failureSummary, Instant now) {
        requiredUpdated(mapper.markDispatchedUncertain(
                tenantId(ownership), runId(ownership), ownership.leaseOwner(), ownership.fencingToken(),
                safeFailureText(failureCode, "失败码"), safeFailureText(failureSummary, "失败摘要"), Objects.requireNonNull(now)
        ), "聊天运行不确定态标记");
    }

    private void requireCanonicalIdentity(AppChatRunPersistenceEntity row, ClaimRequest request) {
        if (!Objects.equals(row.getTenantId(), parseRequiredLong(request.tenantId().value(), "租户 ID"))
                || !Objects.equals(row.getSessionId(), parseRequiredLong(request.sessionId(), "会话 ID"))
                || !Objects.equals(row.getApplicationId(), parseRequiredLong(request.applicationId(), "应用 ID"))
                || !Objects.equals(row.getActorId(), parseRequiredLong(request.actorId(), "操作者 ID"))
                || !Objects.equals(row.getClientMessageId(), request.clientMessageId())) {
            throw new IllegalStateException("聊天运行持久化身份与 claim 请求不一致");
        }
    }

    private static void requireOwnershipRow(AppChatRunPersistenceEntity row, Ownership ownership) {
        if (!Objects.equals(row.getTenantId(), tenantId(ownership))
                || !Objects.equals(row.getRunId(), runId(ownership))
                || !Objects.equals(row.getLeaseOwner(), ownership.leaseOwner())
                || !Objects.equals(row.getFencingToken(), ownership.fencingToken())) {
            throw new IllegalStateException("聊天运行持久化 ownership 不一致");
        }
    }

    private Ownership ownership(AppChatRunPersistenceEntity row) {
        return new Ownership(
                TenantId.of(String.valueOf(row.getTenantId())),
                String.valueOf(Objects.requireNonNull(row.getRunId(), "运行 ID 不能为空")),
                String.valueOf(row.getSessionId()),
                String.valueOf(row.getApplicationId()),
                String.valueOf(row.getActorId()),
                row.getClientMessageId(),
                row.getLeaseOwner(),
                Objects.requireNonNull(row.getFencingToken(), "fencing token 不能为空")
        );
    }

    private ChatMessageResult readResult(String json) {
        return finalizationCodec.readCompletedResult(json);
    }

    private String writeResult(ChatMessageResult result) {
        return finalizationCodec.writeCompletedResult(result);
    }

    private static Long tenantId(Ownership ownership) {
        return parseRequiredLong(ownership.tenantId().value(), "租户 ID");
    }

    private static Long runId(Ownership ownership) {
        return parseRequiredLong(ownership.runId(), "运行 ID");
    }

    private static String safeFailureText(String value, String fieldName) {
        String normalized = requiredText(value, fieldName);
        if (normalized.length() > 512) {
            throw new IllegalArgumentException(fieldName + "长度不能超过 512");
        }
        return normalized;
    }

    private static String requiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static int requiredCount(Integer value, String fieldName) {
        if (value == null || value < 1) {
            throw new IllegalStateException(fieldName + "必须大于 0");
        }
        return value;
    }

    private static int requiredNonNegativeCount(Integer value, String fieldName) {
        if (value == null || value < 0) {
            throw new IllegalStateException(fieldName + "不能小于 0");
        }
        return value;
    }

    private static void requiredUpdated(int updated, String operation) {
        if (updated != 1) {
            throw new IllegalStateException(operation + "失败：租约或 fencing token 已失效");
        }
    }
}
