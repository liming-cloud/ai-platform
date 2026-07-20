package com.aiplatform.approval.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 审批动作请求 DTO，action 支持 APPROVE 和 REJECT。
 */
public record ApprovalTaskActionRequest(
        @NotBlank(message = "审批动作不能为空") String action,
        @NotBlank(message = "审批原因不能为空") String comment,
        @NotBlank(message = "预期审批证据摘要不能为空") String expectedEvidenceDigest
) {
}
