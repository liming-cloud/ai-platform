package com.aiplatform.approval.domain.port;

import com.aiplatform.approval.domain.model.ApprovalDecisionRecord;
import com.aiplatform.approval.domain.model.ApprovalTask;
import com.aiplatform.approval.domain.model.ApprovalTaskPage;
import com.aiplatform.approval.domain.model.ApprovalTaskQuery;
import com.aiplatform.shared.kernel.TenantId;
import java.util.Optional;

/**
 * 审批任务仓储端口，应用层只能通过领域对象读写审批状态和审批流水。
 */
public interface ApprovalTaskRepository {
    Optional<ApprovalTask> findById(TenantId tenantId, String taskId);

    ApprovalTaskPage findTasks(ApprovalTaskQuery query);

    ApprovalTask saveDecision(ApprovalTask task);

    void recordDecision(ApprovalDecisionRecord record);
}
