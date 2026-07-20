package com.aiplatform.approval.domain.model;

/**
 * 审批动作，统一把页面动作转换为审批任务生命周期状态。
 */
public enum ApprovalDecisionAction {
    APPROVE,
    REJECT;

    public ApprovalStatus targetStatus() {
        return switch (this) {
            case APPROVE -> ApprovalStatus.APPROVED;
            case REJECT -> ApprovalStatus.REJECTED;
        };
    }
}
