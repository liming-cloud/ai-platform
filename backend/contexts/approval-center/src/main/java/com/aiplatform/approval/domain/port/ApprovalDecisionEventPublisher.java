package com.aiplatform.approval.domain.port;

import com.aiplatform.approval.domain.event.ApprovalDecisionRecordedEvent;

/**
 * 审批决策事件发布端口，底层消息实现由基础设施层适配。
 */
public interface ApprovalDecisionEventPublisher {
    void publish(ApprovalDecisionRecordedEvent event);
}
