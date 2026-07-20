package com.aiplatform.appstudio.infrastructure.persistence.entity;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.appstudio.application.port.ChatRunRepository.ClaimRequest;
import java.time.Instant;

/**
 * app_chat_run 状态机行。模型拼接 prompt 不入该行；RESULT_RECEIVED 仅暂存版本化最终落库事实。
 */
public class AppChatRunPersistenceEntity {
    private Long runId;
    private Long tenantId;
    private Long sessionId;
    private Long applicationId;
    private Long actorId;
    private String clientMessageId;
    private String commandHash;
    private String status;
    private String phase;
    private String leaseOwner;
    private Instant leaseExpiresAt;
    private Long fencingToken;
    private Integer attemptCount;
    private Integer finalizationAttemptCount;
    private Integer finalizationCycleAttemptCount;
    private Integer manualReplayCount;
    private String finalizationJson;
    private String resultJson;
    private String failureCode;
    private String failureSummary;
    private String failureTraceId;
    private String lastReplayRequestId;
    private String lastReplayReason;
    private Instant completedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public static AppChatRunPersistenceEntity newClaim(ClaimRequest request) {
        AppChatRunPersistenceEntity row = new AppChatRunPersistenceEntity();
        row.setTenantId(parseRequiredLong(request.tenantId().value(), "租户 ID"));
        row.setSessionId(parseRequiredLong(request.sessionId(), "会话 ID"));
        row.setApplicationId(parseRequiredLong(request.applicationId(), "应用 ID"));
        row.setActorId(parseRequiredLong(request.actorId(), "操作者 ID"));
        row.setClientMessageId(request.clientMessageId());
        row.setCommandHash(request.commandHash());
        row.setStatus("IN_PROGRESS");
        row.setPhase("PREPARING");
        row.setLeaseOwner(request.leaseOwner());
        row.setLeaseExpiresAt(request.leaseExpiresAt());
        row.setFencingToken(1L);
        row.setAttemptCount(1);
        row.setFinalizationAttemptCount(0);
        row.setFinalizationCycleAttemptCount(0);
        row.setManualReplayCount(0);
        row.setCreatedAt(request.now());
        row.setUpdatedAt(request.now());
        return row;
    }

    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public Long getActorId() { return actorId; }
    public void setActorId(Long actorId) { this.actorId = actorId; }
    public String getClientMessageId() { return clientMessageId; }
    public void setClientMessageId(String clientMessageId) { this.clientMessageId = clientMessageId; }
    public String getCommandHash() { return commandHash; }
    public void setCommandHash(String commandHash) { this.commandHash = commandHash; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
    public String getLeaseOwner() { return leaseOwner; }
    public void setLeaseOwner(String leaseOwner) { this.leaseOwner = leaseOwner; }
    public Instant getLeaseExpiresAt() { return leaseExpiresAt; }
    public void setLeaseExpiresAt(Instant leaseExpiresAt) { this.leaseExpiresAt = leaseExpiresAt; }
    public Long getFencingToken() { return fencingToken; }
    public void setFencingToken(Long fencingToken) { this.fencingToken = fencingToken; }
    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }
    public Integer getFinalizationAttemptCount() { return finalizationAttemptCount; }
    public void setFinalizationAttemptCount(Integer finalizationAttemptCount) { this.finalizationAttemptCount = finalizationAttemptCount; }
    public Integer getFinalizationCycleAttemptCount() { return finalizationCycleAttemptCount; }
    public void setFinalizationCycleAttemptCount(Integer finalizationCycleAttemptCount) { this.finalizationCycleAttemptCount = finalizationCycleAttemptCount; }
    public Integer getManualReplayCount() { return manualReplayCount; }
    public void setManualReplayCount(Integer manualReplayCount) { this.manualReplayCount = manualReplayCount; }
    public String getFinalizationJson() { return finalizationJson; }
    public void setFinalizationJson(String finalizationJson) { this.finalizationJson = finalizationJson; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }
    public String getFailureSummary() { return failureSummary; }
    public void setFailureSummary(String failureSummary) { this.failureSummary = failureSummary; }
    public String getFailureTraceId() { return failureTraceId; }
    public void setFailureTraceId(String failureTraceId) { this.failureTraceId = failureTraceId; }
    public String getLastReplayRequestId() { return lastReplayRequestId; }
    public void setLastReplayRequestId(String lastReplayRequestId) { this.lastReplayRequestId = lastReplayRequestId; }
    public String getLastReplayReason() { return lastReplayReason; }
    public void setLastReplayReason(String lastReplayReason) { this.lastReplayReason = lastReplayReason; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
