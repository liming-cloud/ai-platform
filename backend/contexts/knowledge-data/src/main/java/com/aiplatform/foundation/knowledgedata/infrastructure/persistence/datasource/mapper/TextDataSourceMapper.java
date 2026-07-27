package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper;

import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.entity.TextDataSourceEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.Instant;
import org.apache.ibatis.annotations.Param;

/**
 * 文本数据源的数据库映射器。
 */
public interface TextDataSourceMapper extends BaseMapper<TextDataSourceEntity> {

    String lockTenant(@Param("tenantId") String tenantId);

    TextDataSourceEntity findByIdForUpdate(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("id") long id);

    int insertOutboxEvent(
            @Param("tenantId") long tenantId,
            @Param("eventId") long eventId,
            @Param("aggregateId") long aggregateId,
            @Param("eventType") String eventType,
            @Param("payload") String payload,
            @Param("occurredAt") Instant occurredAt);
}
