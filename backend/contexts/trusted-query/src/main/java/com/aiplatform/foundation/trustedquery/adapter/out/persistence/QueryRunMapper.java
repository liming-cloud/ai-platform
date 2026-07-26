package com.aiplatform.foundation.trustedquery.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 查询运行、幂等占位和引用事实数据库映射器。
 */
public interface QueryRunMapper {

    @Select("""
            SELECT pg_advisory_xact_lock(
                hashtextextended(#{scope}, 20260731)
            )
            """)
    String lockScope(@Param("scope") String scope);

    @Select("""
            SELECT request_fingerprint,
                   query_run_id::text AS query_run_id
            FROM trusted_query.query_run_request
            WHERE tenant_id = #{tenantId}
              AND idempotency_key_hash = #{keyHash}
            """)
    @Results(id = "queryRunRequestRow", value = {
        @Result(column = "request_fingerprint", property = "requestFingerprint"),
        @Result(column = "query_run_id", property = "queryRunId")
    })
    QueryRunRequestRow findRequest(
            @Param("tenantId") UUID tenantId,
            @Param("keyHash") String keyHash);

    @Select("""
            SELECT count(*)
            FROM trusted_query.query_run
            WHERE tenant_id = #{tenantId}
              AND created_at >= #{periodStart}
              AND created_at < #{periodEnd}
            """)
    int countAcceptedInPeriod(
            @Param("tenantId") UUID tenantId,
            @Param("periodStart") Instant periodStart,
            @Param("periodEnd") Instant periodEnd);

    @Select("""
            SELECT count(*)
            FROM trusted_query.query_run
            WHERE tenant_id = #{tenantId}
              AND status = 'RUNNING'
              AND running_lease_until > #{acceptedAt}
            """)
    int countActiveLeases(
            @Param("tenantId") UUID tenantId,
            @Param("acceptedAt") Instant acceptedAt);

    @Select("""
            SELECT min(running_lease_until)
            FROM trusted_query.query_run
            WHERE tenant_id = #{tenantId}
              AND status = 'RUNNING'
              AND running_lease_until > #{acceptedAt}
            """)
    Instant findEarliestActiveLeaseExpiry(
            @Param("tenantId") UUID tenantId,
            @Param("acceptedAt") Instant acceptedAt);

    @Insert("""
            INSERT INTO trusted_query.query_run (
                tenant_id,
                id,
                assistant_version_id,
                requested_by,
                question_digest,
                quota_period_start,
                quota_period_end,
                daily_query_limit,
                daily_query_ordinal,
                concurrent_query_limit,
                accepted_lease_until,
                running_lease_until,
                status,
                answer,
                refusal_reason,
                input_tokens,
                output_tokens,
                total_tokens,
                version,
                created_at,
                completed_at
            ) VALUES (
                #{tenantId},
                #{id},
                #{assistantVersionId},
                #{requestedBy},
                #{questionDigest},
                #{quotaPeriodStart},
                #{quotaPeriodEnd},
                #{dailyQueryLimit},
                #{dailyQueryOrdinal},
                #{concurrentQueryLimit},
                #{acceptedLeaseUntil},
                #{acceptedLeaseUntil},
                'RUNNING',
                NULL,
                NULL,
                0,
                0,
                0,
                0,
                #{createdAt},
                NULL
            )
            """)
    int insertRun(
            @Param("tenantId") UUID tenantId,
            @Param("id") UUID id,
            @Param("assistantVersionId") UUID assistantVersionId,
            @Param("requestedBy") UUID requestedBy,
            @Param("questionDigest") String questionDigest,
            @Param("quotaPeriodStart") Instant quotaPeriodStart,
            @Param("quotaPeriodEnd") Instant quotaPeriodEnd,
            @Param("dailyQueryLimit") int dailyQueryLimit,
            @Param("dailyQueryOrdinal") int dailyQueryOrdinal,
            @Param("concurrentQueryLimit") int concurrentQueryLimit,
            @Param("acceptedLeaseUntil") Instant acceptedLeaseUntil,
            @Param("createdAt") Instant createdAt);

    @Insert("""
            INSERT INTO trusted_query.query_run_request (
                tenant_id,
                idempotency_key_hash,
                request_fingerprint,
                query_run_id,
                created_at
            ) VALUES (
                #{tenantId},
                #{keyHash},
                #{requestFingerprint},
                #{queryRunId},
                #{createdAt}
            )
            """)
    int insertRequest(
            @Param("tenantId") UUID tenantId,
            @Param("keyHash") String keyHash,
            @Param("requestFingerprint") String requestFingerprint,
            @Param("queryRunId") UUID queryRunId,
            @Param("createdAt") Instant createdAt);

    @Select("""
            SELECT tenant_id::text AS tenant_id,
                   id::text AS id,
                   assistant_version_id::text AS assistant_version_id,
                   requested_by::text AS requested_by,
                   question_digest,
                   quota_period_start,
                   quota_period_end,
                   daily_query_limit,
                   daily_query_ordinal,
                   concurrent_query_limit,
                   accepted_lease_until,
                   status,
                   answer,
                   refusal_reason,
                   input_tokens,
                   output_tokens,
                   total_tokens,
                   version,
                   created_at,
                   completed_at
            FROM trusted_query.query_run
            WHERE tenant_id = #{tenantId}
              AND id = #{queryRunId}
            """)
    @Results(id = "queryRunRow", value = {
        @Result(column = "tenant_id", property = "tenantId"),
        @Result(column = "id", property = "id"),
        @Result(column = "assistant_version_id", property = "assistantVersionId"),
        @Result(column = "requested_by", property = "requestedBy"),
        @Result(column = "question_digest", property = "questionDigest"),
        @Result(column = "quota_period_start", property = "quotaPeriodStart"),
        @Result(column = "quota_period_end", property = "quotaPeriodEnd"),
        @Result(column = "daily_query_limit", property = "dailyQueryLimit"),
        @Result(column = "daily_query_ordinal", property = "dailyQueryOrdinal"),
        @Result(column = "concurrent_query_limit", property = "concurrentQueryLimit"),
        @Result(column = "accepted_lease_until", property = "acceptedLeaseUntil"),
        @Result(column = "status", property = "status"),
        @Result(column = "answer", property = "answer"),
        @Result(column = "refusal_reason", property = "refusalReason"),
        @Result(column = "input_tokens", property = "inputTokens"),
        @Result(column = "output_tokens", property = "outputTokens"),
        @Result(column = "total_tokens", property = "totalTokens"),
        @Result(column = "version", property = "version"),
        @Result(column = "created_at", property = "createdAt"),
        @Result(column = "completed_at", property = "completedAt")
    })
    QueryRunRow findRun(
            @Param("tenantId") UUID tenantId,
            @Param("queryRunId") UUID queryRunId);

    @Select("""
            SELECT sequence,
                   data_source_id::text AS data_source_id,
                   content_version,
                   chunk_id,
                   ordinal,
                   quote,
                   score
            FROM trusted_query.query_citation
            WHERE tenant_id = #{tenantId}
              AND query_run_id = #{queryRunId}
            ORDER BY sequence
            """)
    @Results(id = "queryCitationRow", value = {
        @Result(column = "sequence", property = "sequence"),
        @Result(column = "data_source_id", property = "dataSourceId"),
        @Result(column = "content_version", property = "contentVersion"),
        @Result(column = "chunk_id", property = "chunkId"),
        @Result(column = "ordinal", property = "ordinal"),
        @Result(column = "quote", property = "quote"),
        @Result(column = "score", property = "score")
    })
    List<QueryCitationRow> findCitations(
            @Param("tenantId") UUID tenantId,
            @Param("queryRunId") UUID queryRunId);

    @Update("""
            UPDATE trusted_query.query_run
            SET status = #{status},
                answer = #{answer},
                refusal_reason = #{refusalReason},
                input_tokens = #{inputTokens},
                output_tokens = #{outputTokens},
                total_tokens = #{totalTokens},
                version = #{newVersion},
                completed_at = #{completedAt},
                running_lease_until = NULL
            WHERE tenant_id = #{tenantId}
              AND id = #{queryRunId}
              AND status = 'RUNNING'
              AND version = #{expectedVersion}
            """)
    int completeRun(
            @Param("tenantId") UUID tenantId,
            @Param("queryRunId") UUID queryRunId,
            @Param("status") String status,
            @Param("answer") String answer,
            @Param("refusalReason") String refusalReason,
            @Param("inputTokens") int inputTokens,
            @Param("outputTokens") int outputTokens,
            @Param("totalTokens") int totalTokens,
            @Param("newVersion") long newVersion,
            @Param("completedAt") Instant completedAt,
            @Param("expectedVersion") long expectedVersion);

    @Insert("""
            INSERT INTO trusted_query.query_citation (
                tenant_id,
                query_run_id,
                sequence,
                data_source_id,
                content_version,
                chunk_id,
                ordinal,
                quote,
                score
            ) VALUES (
                #{tenantId},
                #{queryRunId},
                #{sequence},
                #{dataSourceId},
                #{contentVersion},
                #{chunkId},
                #{ordinal},
                #{quote},
                #{score}
            )
            """)
    int insertCitation(
            @Param("tenantId") UUID tenantId,
            @Param("queryRunId") UUID queryRunId,
            @Param("sequence") int sequence,
            @Param("dataSourceId") UUID dataSourceId,
            @Param("contentVersion") int contentVersion,
            @Param("chunkId") String chunkId,
            @Param("ordinal") int ordinal,
            @Param("quote") String quote,
            @Param("score") double score);

    @Insert("""
            INSERT INTO trusted_query.query_run_event (
                tenant_id,
                query_run_id,
                sequence,
                event_type,
                status,
                answer,
                refusal_reason,
                input_tokens,
                output_tokens,
                total_tokens,
                occurred_at
            ) VALUES (
                #{tenantId},
                #{queryRunId},
                #{sequence},
                #{eventType},
                #{status},
                #{answer},
                #{refusalReason},
                #{inputTokens},
                #{outputTokens},
                #{totalTokens},
                #{occurredAt}
            )
            """)
    int insertEvent(
            @Param("tenantId") UUID tenantId,
            @Param("queryRunId") UUID queryRunId,
            @Param("sequence") long sequence,
            @Param("eventType") String eventType,
            @Param("status") String status,
            @Param("answer") String answer,
            @Param("refusalReason") String refusalReason,
            @Param("inputTokens") int inputTokens,
            @Param("outputTokens") int outputTokens,
            @Param("totalTokens") int totalTokens,
            @Param("occurredAt") Instant occurredAt);

    @Select("""
            SELECT tenant_id::text AS tenant_id,
                   query_run_id::text AS query_run_id,
                   sequence,
                   event_type,
                   status,
                   answer,
                   refusal_reason,
                   input_tokens,
                   output_tokens,
                   total_tokens,
                   occurred_at
            FROM trusted_query.query_run_event
            WHERE tenant_id = #{tenantId}
              AND query_run_id = #{queryRunId}
              AND sequence > #{afterSequence}
            ORDER BY sequence
            LIMIT #{limit}
            """)
    @Results(id = "queryRunEventRow", value = {
        @Result(column = "tenant_id", property = "tenantId"),
        @Result(column = "query_run_id", property = "queryRunId"),
        @Result(column = "sequence", property = "sequence"),
        @Result(column = "event_type", property = "eventType"),
        @Result(column = "status", property = "status"),
        @Result(column = "answer", property = "answer"),
        @Result(column = "refusal_reason", property = "refusalReason"),
        @Result(column = "input_tokens", property = "inputTokens"),
        @Result(column = "output_tokens", property = "outputTokens"),
        @Result(column = "total_tokens", property = "totalTokens"),
        @Result(column = "occurred_at", property = "occurredAt")
    })
    List<QueryRunEventRow> findEvents(
            @Param("tenantId") UUID tenantId,
            @Param("queryRunId") UUID queryRunId,
            @Param("afterSequence") long afterSequence,
            @Param("limit") int limit);
}
