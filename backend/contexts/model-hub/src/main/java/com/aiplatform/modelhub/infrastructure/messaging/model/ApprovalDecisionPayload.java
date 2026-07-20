package com.aiplatform.modelhub.infrastructure.messaging.model;

/**
 * 审批决策事件载荷，模型中心只处理来源为 model-hub 的模型供应商审批。
 */
public record ApprovalDecisionPayload(
        String taskId,
        String approvalRequestId,
        String sourceContext,
        String sourceResourceType,
        String sourceResourceId,
        String status,
        String action,
        String approverId,
        String comment,
        String evidenceSchema,
        String evidenceDigest,
        String targetDigest
) {
    private static final String APPROVED_STATUS = "APPROVED";
    private static final String REJECTED_STATUS = "REJECTED";
    private static final String APPROVE_ACTION = "APPROVE";
    private static final String REJECT_ACTION = "REJECT";

    public ApprovalDecisionPayload {
        taskId = requireText(taskId, "审批任务 ID");
        approvalRequestId = requireText(approvalRequestId, "审批请求 ID");
        sourceContext = requireText(sourceContext, "来源上下文");
        sourceResourceType = requireText(sourceResourceType, "来源资源类型");
        sourceResourceId = requireText(sourceResourceId, "来源资源 ID");
        status = requireText(status, "审批状态");
        action = requireText(action, "审批动作");
        validateDecisionStatus(status, action);
        approverId = requireText(approverId, "审批人 ID");
        comment = normalizeComment(status, action, comment);
        evidenceSchema = normalizeOptional(evidenceSchema);
        evidenceDigest = normalizeOptional(evidenceDigest);
        targetDigest = normalizeOptional(targetDigest);
    }

    public ApprovalDecisionPayload(
            String taskId,
            String approvalRequestId,
            String sourceContext,
            String sourceResourceType,
            String sourceResourceId,
            String status,
            String action,
            String approverId,
            String comment
    ) {
        this(taskId, approvalRequestId, sourceContext, sourceResourceType, sourceResourceId,
                status, action, approverId, comment, null, null, null);
    }

    public boolean isApproved() {
        return APPROVED_STATUS.equals(status);
    }

    public void requireCurrentEvidence() {
        if (!com.aiplatform.shared.kernel.ApprovalEvidenceEnvelope.MODEL_APPROVAL_SCHEMA_V1.equals(evidenceSchema)) {
            throw new IllegalArgumentException("审批决策证据 schema 不受支持：" + evidenceSchema);
        }
        requireDigest(evidenceDigest, "审批证据摘要");
        requireDigest(targetDigest, "审批目标摘要");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static void validateDecisionStatus(String status, String action) {
        switch (action) {
            case APPROVE_ACTION -> requireStatus(status, APPROVED_STATUS);
            case REJECT_ACTION -> requireStatus(status, REJECTED_STATUS);
            default -> throw new IllegalArgumentException("审批动作不支持：" + action);
        }
    }

    private static void requireStatus(String actualStatus, String expectedStatus) {
        if (!APPROVED_STATUS.equals(actualStatus) && !REJECTED_STATUS.equals(actualStatus)) {
            throw new IllegalArgumentException("审批状态不支持：" + actualStatus);
        }
        if (!expectedStatus.equals(actualStatus)) {
            throw new IllegalArgumentException("审批状态与审批动作不一致");
        }
    }

    private static String normalizeComment(String status, String action, String comment) {
        if (REJECT_ACTION.equals(action) || REJECTED_STATUS.equals(status)) {
            if (comment == null || comment.isBlank()) {
                throw new IllegalArgumentException("拒绝审批必须填写意见");
            }
            return comment.trim();
        }
        return comment == null ? "" : comment.trim();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void requireDigest(String value, String fieldName) {
        if (value == null || !value.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException(fieldName + "必须是 64 位 SHA-256 十六进制值");
        }
    }
}
