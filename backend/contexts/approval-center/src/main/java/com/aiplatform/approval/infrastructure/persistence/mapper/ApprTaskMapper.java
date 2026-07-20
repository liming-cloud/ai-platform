package com.aiplatform.approval.infrastructure.persistence.mapper;

import com.aiplatform.approval.domain.model.ApprovalCancellationRequest;
import com.aiplatform.approval.domain.model.ApprovalTaskQuery;
import com.aiplatform.approval.infrastructure.persistence.entity.ApprTaskPersistenceEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ApprTaskMapper extends BaseMapper<ApprTaskPersistenceEntity> {
    Long lockApprovalRequest(
            @Param("tenantId") long tenantId,
            @Param("approvalRequestId") String approvalRequestId
    );

    Long upsertPending(@Param("entity") ApprTaskPersistenceEntity entity);

    Long upsertCancellation(@Param("cancellation") ApprovalCancellationRequest cancellation);

    int reconcileCancellation(
            @Param("tenantId") long tenantId,
            @Param("approvalRequestId") String approvalRequestId
    );

    int markCancellationAfterTerminalDecision(
            @Param("tenantId") long tenantId,
            @Param("approvalRequestId") String approvalRequestId
    );

    int decidePending(
            @Param("tenantId") long tenantId,
            @Param("taskId") long taskId,
            @Param("expectedVersion") int expectedVersion,
            @Param("targetStatus") String targetStatus,
            @Param("approverId") long approverId,
            @Param("decisionAt") Instant decisionAt
    );

    long countTasks(@Param("query") ApprovalTaskQuery query);

    List<ApprTaskPersistenceEntity> findTasks(@Param("query") ApprovalTaskQuery query);
}
