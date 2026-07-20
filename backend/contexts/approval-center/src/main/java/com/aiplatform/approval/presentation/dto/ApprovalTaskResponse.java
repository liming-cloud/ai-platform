package com.aiplatform.approval.presentation.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审批任务响应 DTO，面向控制台展示审批来源和当前状态。
 */
public record ApprovalTaskResponse(
        String taskId,
        String approvalRequestId,
        String approvalType,
        String sourceContext,
        String sourceResourceType,
        String sourceResourceId,
        String title,
        String riskLevel,
        String riskLevelLabel,
        String requesterId,
        String approverId,
        String status,
        String statusLabel,
        String sourceContextLabel,
        String traceId,
        String evidenceSchema,
        String evidenceDigest,
        String targetDigest,
        Map<String, Object> evidence
) {
    /** Convenience constructor that derives presentation labels for a pre-evidence task. */
    public ApprovalTaskResponse(
            String taskId,
            String approvalRequestId,
            String approvalType,
            String sourceContext,
            String sourceResourceType,
            String sourceResourceId,
            String title,
            String riskLevel,
            String requesterId,
            String approverId,
            String status,
            String traceId
    ) {
        this(
                taskId,
                approvalRequestId,
                approvalType,
                sourceContext,
                sourceResourceType,
                sourceResourceId,
                title,
                riskLevel,
                labelRisk(riskLevel),
                requesterId,
                approverId,
                status,
                labelStatus(status),
                labelSource(sourceContext),
                traceId
        );
    }

    /** Read-only representation of a pre-evidence task. */
    public ApprovalTaskResponse(
            String taskId,
            String approvalRequestId,
            String approvalType,
            String sourceContext,
            String sourceResourceType,
            String sourceResourceId,
            String title,
            String riskLevel,
            String riskLevelLabel,
            String requesterId,
            String approverId,
            String status,
            String statusLabel,
            String sourceContextLabel,
            String traceId
    ) {
        this(
                taskId,
                approvalRequestId,
                approvalType,
                sourceContext,
                sourceResourceType,
                sourceResourceId,
                title,
                riskLevel,
                riskLevelLabel,
                requesterId,
                approverId,
                status,
                statusLabel,
                sourceContextLabel,
                traceId,
                null,
                null,
                null,
                null
        );
    }

    public ApprovalTaskResponse {
        taskId = ApprovalResponseContracts.requireText(taskId, "审批任务 ID");
        approvalRequestId = ApprovalResponseContracts.requireText(approvalRequestId, "审批请求 ID");
        approvalType = ApprovalResponseContracts.requireText(approvalType, "审批类型");
        sourceContext = ApprovalResponseContracts.requireText(sourceContext, "来源上下文");
        sourceResourceType = ApprovalResponseContracts.requireText(sourceResourceType, "来源资源类型");
        sourceResourceId = ApprovalResponseContracts.requireText(sourceResourceId, "来源资源 ID");
        title = ApprovalResponseContracts.requireText(title, "审批标题");
        riskLevel = ApprovalResponseContracts.requireText(riskLevel, "风险等级");
        riskLevelLabel = ApprovalResponseContracts.requireText(riskLevelLabel, "风险等级名称");
        requesterId = ApprovalResponseContracts.requireText(requesterId, "申请人 ID");
        status = ApprovalResponseContracts.requireText(status, "审批状态");
        statusLabel = ApprovalResponseContracts.requireText(statusLabel, "审批状态名称");
        sourceContextLabel = ApprovalResponseContracts.requireText(sourceContextLabel, "来源上下文名称");
        approverId = ApprovalResponseContracts.normalizeApproverId(status, approverId);
        traceId = ApprovalResponseContracts.requireText(traceId, "调用链 ID");
        if (evidence == null) {
            if (evidenceSchema != null || evidenceDigest != null || targetDigest != null) {
                throw new IllegalArgumentException("审批证据元数据必须同时存在");
            }
        } else {
            evidenceSchema = ApprovalResponseContracts.requireText(evidenceSchema, "审批证据 schema");
            evidenceDigest = ApprovalResponseContracts.requireText(evidenceDigest, "审批证据摘要");
            targetDigest = ApprovalResponseContracts.requireText(targetDigest, "审批目标摘要");
            // Creation evidence intentionally contains null before/modelConfigId values.
            evidence = Collections.unmodifiableMap(new LinkedHashMap<>(evidence));
        }
    }

    public static String labelRisk(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "HIGH_RISK" -> "高风险";
            case "NORMAL" -> "常规";
            default -> value;
        };
    }

    public static String labelStatus(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "PENDING" -> "待审批";
            case "APPROVED" -> "已通过";
            case "REJECTED" -> "已拒绝";
            case "RETURNED" -> "已退回";
            case "CANCELLED" -> "已撤销";
            default -> value;
        };
    }

    public static String labelSource(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.trim().toLowerCase(java.util.Locale.ROOT).replace('_', '-')) {
            case "model-hub" -> "模型中心";
            case "identity-access" -> "身份与权限";
            case "app-studio" -> "应用治理";
            default -> value;
        };
    }
}
