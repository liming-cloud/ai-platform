package com.aiplatform.modelhub.infrastructure.persistence.mapper;

import com.aiplatform.modelhub.infrastructure.messaging.inbox.ApprovalDecisionInboxReceipt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 模型中心审批决策 inbox 的唯一事件收据 seam。
 */
@Mapper
public interface ApprovalDecisionInboxMapper {
    int insertReceipt(@Param("receipt") ApprovalDecisionInboxReceipt receipt);

    String resolveLegacyApprovalRequestId(
            @Param("tenantId") long tenantId,
            @Param("taskId") long taskId,
            @Param("sourceContext") String sourceContext,
            @Param("sourceResourceType") String sourceResourceType,
            @Param("sourceResourceId") long sourceResourceId
    );
}
