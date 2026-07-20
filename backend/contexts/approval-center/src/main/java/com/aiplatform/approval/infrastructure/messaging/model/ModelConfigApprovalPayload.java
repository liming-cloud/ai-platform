package com.aiplatform.approval.infrastructure.messaging.model;

import java.util.Map;

/**
 * 模型审批请求事件载荷，由 approval-center 消费后转换为审批任务。
 */
public record ModelConfigApprovalPayload(
        String approvalRequestId,
        String approvalType,
        String providerId,
        String modelConfigId,
        String sourceResourceType,
        String sourceResourceId,
        String changeSummary,
        String riskLevel,
        String operatorId,
        String evidenceSchema,
        String evidenceDigest,
        String targetDigest,
        Map<String, Object> evidence
) {
}
