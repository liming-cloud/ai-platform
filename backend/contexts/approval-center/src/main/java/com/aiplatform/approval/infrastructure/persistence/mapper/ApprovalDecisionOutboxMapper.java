package com.aiplatform.approval.infrastructure.persistence.mapper;

import com.aiplatform.approval.infrastructure.messaging.outbox.ApprovalDecisionOutboxRecord;
import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 审批决策事务 outbox 的 PostgreSQL seam。
 */
@Mapper
public interface ApprovalDecisionOutboxMapper {
    int enqueue(@Param("message") DomainEventMessage message);

    List<ApprovalDecisionOutboxRecord> claimBatch(
            @Param("workerId") String workerId,
            @Param("now") Instant now,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("limit") int limit
    );

    int markPublished(
            @Param("id") long id,
            @Param("workerId") String workerId,
            @Param("now") Instant now
    );

    int markRetry(
            @Param("id") long id,
            @Param("workerId") String workerId,
            @Param("now") Instant now,
            @Param("nextAttemptAt") Instant nextAttemptAt,
            @Param("lastError") String lastError
    );
}
