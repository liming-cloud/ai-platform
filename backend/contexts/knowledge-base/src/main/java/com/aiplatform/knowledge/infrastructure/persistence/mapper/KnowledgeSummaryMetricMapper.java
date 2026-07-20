package com.aiplatform.knowledge.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KnowledgeSummaryMetricMapper {
    long countKnowledgeBases(@Param("tenantId") Long tenantId);

    long countReadyKnowledgeBases(@Param("tenantId") Long tenantId);

    long countUnindexedKnowledgeBases(@Param("tenantId") Long tenantId);

    long countFailedKnowledgeBases(@Param("tenantId") Long tenantId);
}
