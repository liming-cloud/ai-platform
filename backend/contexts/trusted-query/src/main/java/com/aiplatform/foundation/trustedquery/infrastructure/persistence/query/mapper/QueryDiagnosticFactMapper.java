package com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.mapper;

import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.entity.QueryDiagnosticFactEntity;
import java.time.Instant;
import org.apache.ibatis.annotations.Param;

/**
 * 内部查询诊断事实的最小数据库映射器。
 */
public interface QueryDiagnosticFactMapper {

    QueryDiagnosticFactEntity find(
            @Param("tenantId") long tenantId,
            @Param("queryRunId") long queryRunId);

    int insert(
            @Param("tenantId") long tenantId,
            @Param("queryRunId") long queryRunId,
            @Param("candidateCollection") String candidateCollection,
            @Param("candidateCount") Integer candidateCount,
            @Param("retrievalAuthorization") String retrievalAuthorization,
            @Param("chatModelRevision") long chatModelRevision,
            @Param("embeddingModelRevision") long embeddingModelRevision,
            @Param("citationValidation") String citationValidation,
            @Param("createdAt") Instant createdAt);
}
