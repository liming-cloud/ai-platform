package com.aiplatform.appstudio.infrastructure.persistence.mapper;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ModelUsagePolicyEventReceiptMapper {
    int insertIfAbsent(
            @Param("eventId") String eventId,
            @Param("eventVersion") long eventVersion,
            @Param("occurredAt") Instant occurredAt
    );
}
