package com.aiplatform.approval.presentation.dto;

final class ApprovalResponseContracts {
    private ApprovalResponseContracts() {
    }

    static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    static String normalizeApproverId(String status, String approverId) {
        if ("PENDING".equals(status)) {
            if (approverId == null) {
                return null;
            }
            if (approverId.isBlank()) {
                throw new IllegalArgumentException("审批人 ID不能为空");
            }
            throw new IllegalArgumentException("待审批任务不能携带审批人 ID");
        }
        return requireText(approverId, "审批人 ID");
    }
}
