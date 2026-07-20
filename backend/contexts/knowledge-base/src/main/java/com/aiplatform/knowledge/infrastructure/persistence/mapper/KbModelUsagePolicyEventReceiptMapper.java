package com.aiplatform.knowledge.infrastructure.persistence.mapper;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KbModelUsagePolicyEventReceiptMapper {
    int insertIfAbsent(
            @Param("eventId") String eventId,
            @Param("eventVersion") long eventVersion,
            @Param("occurredAt") Instant occurredAt
    );
}
