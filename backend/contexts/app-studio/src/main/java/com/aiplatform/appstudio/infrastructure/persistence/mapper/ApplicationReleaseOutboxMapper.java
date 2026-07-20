package com.aiplatform.appstudio.infrastructure.persistence.mapper;

import com.aiplatform.appstudio.infrastructure.messaging.outbox.ApplicationReleaseOutboxRecord;
import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ApplicationReleaseOutboxMapper {
    int enqueue(@Param("message") DomainEventMessage message);

    List<ApplicationReleaseOutboxRecord> claimBatch(
            @Param("workerId") String workerId,
            @Param("now") Instant now,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("limit") int limit
    );

    int markPublished(@Param("id") long id, @Param("workerId") String workerId, @Param("now") Instant now);

    int markRetry(
            @Param("id") long id,
            @Param("workerId") String workerId,
            @Param("now") Instant now,
            @Param("nextAttemptAt") Instant nextAttemptAt,
            @Param("lastError") String lastError
    );
}
