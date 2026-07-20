package com.aiplatform.approval.application.error;

import com.aiplatform.shared.kernel.ErrorCode;

/**
 * 审批中心应用层错误码，统一承载 API、日志和审计中的失败原因。
 */
public final class ApprovalErrorCodes {
    public static final ErrorCode APPROVAL_TASK_NOT_FOUND =
            ErrorCode.of("AIP-APPR-NOTFOUND", "审批任务不存在");
    public static final ErrorCode APPROVAL_TASK_DECISION_INVALID =
            ErrorCode.of("AIP-APPR-DECISION", "审批任务决策不合法");
    public static final ErrorCode APPROVAL_TASK_DECISION_CONFLICT =
            ErrorCode.of("AIP-APPR-CONFLICT", "审批任务已被处理");
    public static final ErrorCode APPROVAL_TASK_QUERY_INVALID =
            ErrorCode.of("AIP-APPR-QUERY", "审批任务查询条件不合法");

    private ApprovalErrorCodes() {
    }
}
