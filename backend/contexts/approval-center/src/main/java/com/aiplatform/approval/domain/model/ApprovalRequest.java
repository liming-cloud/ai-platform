package com.aiplatform.approval.domain.model;

import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.kernel.ValueObject;
import com.aiplatform.shared.kernel.ApprovalEvidenceEnvelope;
import java.util.Objects;

/**
 * 审批中心审批申请值对象，由审批上下文入口把领域事件转换为审批任务事实。
 */
public record ApprovalRequest(
        TenantId tenantId,
        String approvalRequestId,
        String approvalType,
        String sourceContext,
        String sourceResourceType,
        String sourceResourceId,
        String title,
        ApprovalRiskLevel riskLevel,
        String requesterId,
        String traceId,
        ApprovalEvidenceEnvelope evidence,
        String targetDigest
) implements ValueObject {
    public ApprovalRequest {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        approvalRequestId = requireText(approvalRequestId, "审批请求 ID");
        approvalType = requireText(approvalType, "审批类型");
        sourceContext = requireText(sourceContext, "来源上下文");
        sourceResourceType = requireText(sourceResourceType, "来源资源类型");
        sourceResourceId = requireText(sourceResourceId, "来源资源 ID");
        title = requireText(title, "审批标题");
        Objects.requireNonNull(riskLevel, "风险等级不能为空");
        requesterId = requireText(requesterId, "申请人 ID");
        traceId = requireText(traceId, "调用链 ID");
        evidence = Objects.requireNonNull(evidence, "审批证据不能为空");
        targetDigest = requireDigest(targetDigest, "审批目标摘要");
        Object target = evidence.facts().get("target");
        if (!(target instanceof java.util.Map<?, ?> targetFacts)) {
            throw new IllegalArgumentException("审批证据 target 必须是对象");
        }
        String expectedTargetDigest = ApprovalEvidenceEnvelope.digestFacts(java.util.Map.of("target", targetFacts));
        if (!java.security.MessageDigest.isEqual(
                expectedTargetDigest.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                targetDigest.getBytes(java.nio.charset.StandardCharsets.US_ASCII)
        )) {
            throw new IllegalArgumentException("审批目标摘要与证据 target 不一致");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static String requireDigest(String value, String fieldName) {
        String normalized = requireText(value, fieldName).toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(fieldName + "必须是 64 位 SHA-256 十六进制值");
        }
        return normalized;
    }
}
