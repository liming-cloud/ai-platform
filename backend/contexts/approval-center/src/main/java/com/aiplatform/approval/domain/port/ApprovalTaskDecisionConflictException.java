package com.aiplatform.approval.domain.port;

/**
 * 审批任务的数据库 CAS 未命中，表示同一待审批版本已被其他决策占用。
 */
public final class ApprovalTaskDecisionConflictException extends RuntimeException {
    public ApprovalTaskDecisionConflictException() {
        super("审批任务已被其他审批人处理，请刷新后重试");
    }
}
