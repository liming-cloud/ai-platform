package com.aiplatform.approval.domain.model;

import com.aiplatform.shared.kernel.AggregateRoot;
import com.aiplatform.shared.kernel.ApprovalEvidenceEnvelope;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;

/**
 * 审批任务聚合根，负责审批任务状态流转和拒绝意见校验。
 */
public record ApprovalTask(
        TenantId tenantId,
        String taskId,
        String approvalRequestId,
        String approvalType,
        String sourceContext,
        String sourceResourceType,
        String sourceResourceId,
        String title,
        ApprovalRiskLevel riskLevel,
        String requesterId,
        String approverId,
        ApprovalStatus status,
        Instant dueAt,
        Instant decisionAt,
        String traceId,
        ApprovalEvidenceEnvelope evidence,
        String targetDigest,
        Instant createdAt,
        Instant updatedAt,
        int version
) implements AggregateRoot<String> {
    /**
     * Compatibility constructor for historical rows created before approval evidence became mandatory.
     * Such tasks remain readable but {@link #decide(ApprovalDecision)} deliberately rejects them.
     */
    public ApprovalTask(
            TenantId tenantId,
            String taskId,
            String approvalRequestId,
            String approvalType,
            String sourceContext,
            String sourceResourceType,
            String sourceResourceId,
            String title,
            ApprovalRiskLevel riskLevel,
            String requesterId,
            String approverId,
            ApprovalStatus status,
            Instant dueAt,
            Instant decisionAt,
            String traceId,
            Instant createdAt,
            Instant updatedAt,
            int version
    ) {
        this(
                tenantId,
                taskId,
                approvalRequestId,
                approvalType,
                sourceContext,
                sourceResourceType,
                sourceResourceId,
                title,
                riskLevel,
                requesterId,
                approverId,
                status,
                dueAt,
                decisionAt,
                traceId,
                null,
                null,
                createdAt,
                updatedAt,
                version
        );
    }

    public ApprovalTask {
        tenantId = requireValue(tenantId, "租户 ID");
        taskId = requireText(taskId, "审批任务 ID");
        approvalRequestId = requireText(approvalRequestId, "审批请求 ID");
        approvalType = requireText(approvalType, "审批类型");
        sourceContext = requireText(sourceContext, "来源上下文");
        sourceResourceType = requireText(sourceResourceType, "来源资源类型");
        sourceResourceId = requireText(sourceResourceId, "来源资源 ID");
        title = requireText(title, "审批标题");
        riskLevel = requireValue(riskLevel, "风险等级");
        requesterId = requireText(requesterId, "申请人 ID");
        status = requireValue(status, "审批状态");
        approverId = normalizeApproverId(status, approverId, decisionAt);
        traceId = requireText(traceId, "调用链 ID");
        if (evidence == null || targetDigest == null || targetDigest.isBlank()) {
            if (evidence != null || (targetDigest != null && !targetDigest.isBlank())) {
                throw new IllegalArgumentException("审批证据与目标摘要必须同时存在");
            }
            evidence = null;
            targetDigest = null;
        } else {
            targetDigest = requireDigest(targetDigest, "审批目标摘要");
            validateTargetDigest(evidence, targetDigest);
        }
        requireNonNegative(version, "乐观锁版本");
        createdAt = requireValue(createdAt, "创建时间");
        updatedAt = requireValue(updatedAt, "更新时间");
    }

    public ApprovalTask decide(ApprovalDecision decision) {
        ApprovalDecision normalizedDecision = requireValue(decision, "审批决策");
        if (status != ApprovalStatus.PENDING) {
            throw new IllegalStateException("只有待审批任务允许决策，当前状态为 " + status.name());
        }
        if (!hasActionableEvidence()) {
            throw new IllegalStateException("审批任务缺少可信证据，不能执行决策");
        }
        if (requesterId.equals(normalizedDecision.actorId())) {
            throw new IllegalStateException("申请人不能审批自己的申请");
        }
        return new ApprovalTask(
                tenantId,
                taskId,
                approvalRequestId,
                approvalType,
                sourceContext,
                sourceResourceType,
                sourceResourceId,
                title,
                riskLevel,
                requesterId,
                normalizedDecision.actorId(),
                normalizedDecision.action().targetStatus(),
                dueAt,
                normalizedDecision.decidedAt(),
                traceId,
                evidence,
                targetDigest,
                createdAt,
                normalizedDecision.decidedAt(),
                version + 1
        );
    }

    @Override
    public String id() {
        return taskId;
    }

    public boolean hasActionableEvidence() {
        return evidence != null && targetDigest != null;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static String normalizeApproverId(ApprovalStatus status, String approverId, Instant decisionAt) {
        if (status == ApprovalStatus.PENDING) {
            if (approverId != null && !approverId.isBlank()) {
                throw new IllegalArgumentException("待审批任务不能携带审批人 ID");
            }
            if (approverId != null) {
                throw new IllegalArgumentException("审批人 ID不能为空");
            }
            if (decisionAt != null) {
                throw new IllegalArgumentException("待审批任务不能携带审批决策时间");
            }
            return null;
        }
        String normalized = requireText(approverId, "审批人 ID");
        requireValue(decisionAt, "审批决策时间");
        return normalized;
    }

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + "不能小于 0");
        }
    }

    private static <T> T requireValue(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }

    private static String requireDigest(String value, String fieldName) {
        String normalized = requireText(value, fieldName).toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(fieldName + "必须是 64 位 SHA-256 十六进制值");
        }
        return normalized;
    }

    private static void validateTargetDigest(ApprovalEvidenceEnvelope evidence, String targetDigest) {
        Object target = evidence.facts().get("target");
        if (!(target instanceof java.util.Map<?, ?> targetFacts)) {
            throw new IllegalArgumentException("审批证据 target 必须是对象");
        }
        String expected = ApprovalEvidenceEnvelope.digestFacts(java.util.Map.of("target", targetFacts));
        if (!java.security.MessageDigest.isEqual(
                expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                targetDigest.getBytes(java.nio.charset.StandardCharsets.US_ASCII)
        )) {
            throw new IllegalArgumentException("审批目标摘要与证据 target 不一致");
        }
    }
}
