package com.aiplatform.approval.domain.model;

/**
 * 审批任务状态，描述审批中心任务从待处理到完成的生命周期。
 */
public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    RETURNED,
    CANCELLED
}
