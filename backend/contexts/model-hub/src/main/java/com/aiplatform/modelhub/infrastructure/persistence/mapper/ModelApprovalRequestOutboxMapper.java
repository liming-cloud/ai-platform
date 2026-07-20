package com.aiplatform.modelhub.infrastructure.persistence.mapper;

import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import com.aiplatform.modelhub.infrastructure.messaging.outbox.ModelApprovalRequestOutboxRecord;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 模型审批请求 outbox 的 PostgreSQL 持久化 seam。
 */
@Mapper
public interface ModelApprovalRequestOutboxMapper {
    int enqueue(@Param("message") DomainEventMessage message);

    List<ModelApprovalRequestOutboxRecord> claimBatch(
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
