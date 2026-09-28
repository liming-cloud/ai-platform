package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.knowledge.mapper;

import java.math.BigDecimal;
import org.apache.ibatis.annotations.Param;

/** 旧知识来源用量仅在知识数据上下文的 SQL 映射中聚合。 */
public interface KnowledgeQuotaUsageMapper {

    long countKnowledgeSpaces(@Param("tenantId") long tenantId);

    long maxDocumentsPerKnowledgeSpace(@Param("tenantId") long tenantId);

    BigDecimal maxBytesPerKnowledgeSpace(@Param("tenantId") long tenantId);

    long countUnmeteredSources(@Param("tenantId") long tenantId);
}
