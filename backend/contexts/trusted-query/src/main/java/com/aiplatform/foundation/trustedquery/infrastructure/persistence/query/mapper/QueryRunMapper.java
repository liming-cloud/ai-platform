package com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.mapper;

import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.projection.QueryAuditFactRow;
import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.projection.QueryCitationRow;
import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.projection.QueryRunEventRow;
import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.projection.QueryRunRequestRow;
import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.projection.QueryRunRow;
import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.projection.QueryUsageRow;
import com.baomidou.mybatisplus.core.metadata.IPage;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 查询运行、幂等占位和引用事实数据库映射器。
 */
public interface QueryRunMapper {

    String lockScope(@Param("scope") String scope);

    QueryRunRequestRow findRequest(
            @Param("tenantId") long tenantId,
            @Param("keyHash") String keyHash);

    int countAcceptedInPeriod(
            @Param("tenantId") long tenantId,
            @Param("periodStart") Instant periodStart,
            @Param("periodEnd") Instant periodEnd);

    int countActiveLeases(
            @Param("tenantId") long tenantId,
            @Param("acceptedAt") Instant acceptedAt);

    Instant findEarliestActiveLeaseExpiry(
            @Param("tenantId") long tenantId,
            @Param("acceptedAt") Instant acceptedAt);

    QueryUsageRow summarizeUsage(
            @Param("tenantId") long tenantId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("currentPeriodStart") Instant currentPeriodStart,
            @Param("currentPeriodEnd") Instant currentPeriodEnd,
            @Param("observedAt") Instant observedAt);

    IPage<QueryAuditFactRow> findAuditPage(
            IPage<QueryAuditFactRow> page,
            @Param("tenantId") long tenantId);

    int insertRun(
            @Param("tenantId") long tenantId,
            @Param("id") long id,
            @Param("assistantVersionId") long assistantVersionId,
            @Param("requestedBy") long requestedBy,
            @Param("questionDigest") String questionDigest,
            @Param("quotaPeriodStart") Instant quotaPeriodStart,
            @Param("quotaPeriodEnd") Instant quotaPeriodEnd,
            @Param("dailyQueryLimit") int dailyQueryLimit,
            @Param("dailyQueryOrdinal") int dailyQueryOrdinal,
            @Param("concurrentQueryLimit") int concurrentQueryLimit,
            @Param("acceptedLeaseUntil") Instant acceptedLeaseUntil,
            @Param("createdAt") Instant createdAt);

    int insertRequest(
            @Param("tenantId") long tenantId,
            @Param("keyHash") String keyHash,
            @Param("requestFingerprint") String requestFingerprint,
            @Param("queryRunId") long queryRunId,
            @Param("createdAt") Instant createdAt);

    QueryRunRow findRun(
            @Param("tenantId") long tenantId,
            @Param("queryRunId") long queryRunId);

    List<QueryCitationRow> findCitations(
            @Param("tenantId") long tenantId,
            @Param("queryRunId") long queryRunId);

    int completeRun(
            @Param("tenantId") long tenantId,
            @Param("queryRunId") long queryRunId,
            @Param("status") String status,
            @Param("answer") String answer,
            @Param("refusalReason") String refusalReason,
            @Param("inputTokens") int inputTokens,
            @Param("outputTokens") int outputTokens,
            @Param("totalTokens") int totalTokens,
            @Param("newVersion") long newVersion,
            @Param("completedAt") Instant completedAt,
            @Param("expectedVersion") long expectedVersion);

    int insertCitation(
            @Param("tenantId") long tenantId,
            @Param("queryRunId") long queryRunId,
            @Param("sequence") int sequence,
            @Param("dataSourceId") long dataSourceId,
            @Param("contentVersion") int contentVersion,
            @Param("chunkId") String chunkId,
            @Param("ordinal") int ordinal,
            @Param("quote") String quote,
            @Param("score") double score);

    int insertEvent(
            @Param("tenantId") long tenantId,
            @Param("queryRunId") long queryRunId,
            @Param("sequence") long sequence,
            @Param("eventType") String eventType,
            @Param("status") String status,
            @Param("answer") String answer,
            @Param("refusalReason") String refusalReason,
            @Param("inputTokens") int inputTokens,
            @Param("outputTokens") int outputTokens,
            @Param("totalTokens") int totalTokens,
            @Param("occurredAt") Instant occurredAt);

    List<QueryRunEventRow> findEvents(
            @Param("tenantId") long tenantId,
            @Param("queryRunId") long queryRunId,
            @Param("afterSequence") long afterSequence,
            @Param("limit") int limit);
}
