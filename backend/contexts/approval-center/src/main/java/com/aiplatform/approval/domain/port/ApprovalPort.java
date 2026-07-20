package com.aiplatform.approval.domain.port;

import com.aiplatform.approval.domain.model.ApprovalCancellationRequest;
import com.aiplatform.approval.domain.model.ApprovalRequest;

/**
 * 审批中心内部端口，入口适配器通过该端口创建、复用或撤销审批任务。
 */
public interface ApprovalPort {
    String requestApproval(ApprovalRequest request);

    void cancelApproval(ApprovalCancellationRequest cancellation);
}
