package com.aiplatform.modelhub.infrastructure.persistence.mapper;

import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import com.aiplatform.modelhub.infrastructure.messaging.outbox.ModelUsagePolicyOutboxRecord;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 模型使用策略 outbox 的 PostgreSQL 持久化 seam。
 */
@Mapper
public interface ModelUsagePolicyOutboxMapper {
    Long nextPolicyRevision();

    String canonicalDigest(@Param("payloadJson") String payloadJson);

    int enqueue(@Param("message") DomainEventMessage message);

    List<ModelUsagePolicyOutboxRecord> claimBatch(
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
