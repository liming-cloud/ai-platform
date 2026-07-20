package com.aiplatform.appstudio.infrastructure.persistence.mapper;

import com.aiplatform.appstudio.infrastructure.persistence.entity.AppChatRunPersistenceEntity;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppChatRunMapper {
    int insertIfAbsent(AppChatRunPersistenceEntity row);

    AppChatRunPersistenceEntity selectForUpdate(
            @Param("tenantId") Long tenantId,
            @Param("sessionId") Long sessionId,
            @Param("clientMessageId") String clientMessageId
    );

    AppChatRunPersistenceEntity selectRunForUpdate(
            @Param("tenantId") Long tenantId,
            @Param("runId") Long runId
    );

    int reclaimPreparing(
            @Param("tenantId") Long tenantId,
            @Param("runId") Long runId,
            @Param("leaseOwner") String leaseOwner,
            @Param("fencingToken") Long fencingToken,
            @Param("newLeaseOwner") String newLeaseOwner,
            @Param("newLeaseExpiresAt") Instant newLeaseExpiresAt,
            @Param("now") Instant now
    );

    int markExpiredDispatchedUncertain(
            @Param("tenantId") Long tenantId,
            @Param("runId") Long runId,
            @Param("leaseOwner") String leaseOwner,
            @Param("fencingToken") Long fencingToken,
            @Param("now") Instant now
    );

    int markModelDispatched(
            @Param("tenantId") Long tenantId,
            @Param("runId") Long runId,
            @Param("leaseOwner") String leaseOwner,
            @Param("fencingToken") Long fencingToken,
            @Param("leaseExpiresAt") Instant leaseExpiresAt,
            @Param("now") Instant now
    );

    int recordResultReceived(
            @Param("tenantId") Long tenantId,
            @Param("runId") Long runId,
            @Param("leaseOwner") String leaseOwner,
            @Param("fencingToken") Long fencingToken,
            @Param("finalizationJson") String finalizationJson,
            @Param("leaseExpiresAt") Instant leaseExpiresAt,
            @Param("now") Instant now
    );

    int reclaimResultReceived(
            @Param("tenantId") Long tenantId,
            @Param("runId") Long runId,
            @Param("leaseOwner") String leaseOwner,
            @Param("fencingToken") Long fencingToken,
            @Param("newLeaseOwner") String newLeaseOwner,
            @Param("newLeaseExpiresAt") Instant newLeaseExpiresAt,
            @Param("now") Instant now
    );

    List<AppChatRunPersistenceEntity> claimRecoverableResults(
            @Param("newLeaseOwner") String newLeaseOwner,
            @Param("newLeaseExpiresAt") Instant newLeaseExpiresAt,
            @Param("now") Instant now,
            @Param("limit") int limit,
            @Param("maxAttempts") int maxAttempts
    );

    int recordFinalizationFailure(
            @Param("tenantId") Long tenantId,
            @Param("runId") Long runId,
            @Param("leaseOwner") String leaseOwner,
            @Param("fencingToken") Long fencingToken,
            @Param("failureCode") String failureCode,
            @Param("failureSummary") String failureSummary,
            @Param("traceId") String traceId,
            @Param("now") Instant now,
            @Param("maxAttempts") int maxAttempts,
            @Param("baseBackoffSeconds") int baseBackoffSeconds
    );

    int deadLetterExhaustedFinalizations(
            @Param("now") Instant now,
            @Param("maxAttempts") int maxAttempts,
            @Param("failureCode") String failureCode,
            @Param("failureSummary") String failureSummary,
            @Param("traceId") String traceId,
            @Param("limit") int limit
    );

    List<AppChatRunPersistenceEntity> findDeadLetters(
            @Param("tenantId") Long tenantId,
            @Param("limit") int limit
    );

    int claimDeadLetterForReplay(
            @Param("tenantId") Long tenantId,
            @Param("runId") Long runId,
            @Param("newLeaseOwner") String newLeaseOwner,
            @Param("newLeaseExpiresAt") Instant newLeaseExpiresAt,
            @Param("replayRequestId") String replayRequestId,
            @Param("reason") String reason,
            @Param("now") Instant now
    );

    Long findReplayRequestRunId(
            @Param("tenantId") Long tenantId,
            @Param("replayRequestId") String replayRequestId
    );

    int insertReplayRequest(
            @Param("tenantId") Long tenantId,
            @Param("runId") Long runId,
            @Param("replayRequestId") String replayRequestId,
            @Param("actorId") Long actorId,
            @Param("reason") String reason,
            @Param("createdAt") Instant createdAt
    );

    int quarantineInvalidFinalization(
            @Param("tenantId") Long tenantId,
            @Param("runId") Long runId,
            @Param("leaseOwner") String leaseOwner,
            @Param("fencingToken") Long fencingToken,
            @Param("failureCode") String failureCode,
            @Param("failureSummary") String failureSummary,
            @Param("now") Instant now
    );

    int complete(
            @Param("tenantId") Long tenantId,
            @Param("runId") Long runId,
            @Param("leaseOwner") String leaseOwner,
            @Param("fencingToken") Long fencingToken,
            @Param("resultJson") String resultJson,
            @Param("now") Instant now
    );

    int failPreparing(
            @Param("tenantId") Long tenantId,
            @Param("runId") Long runId,
            @Param("leaseOwner") String leaseOwner,
            @Param("fencingToken") Long fencingToken,
            @Param("failureCode") String failureCode,
            @Param("failureSummary") String failureSummary,
            @Param("now") Instant now
    );

    int markDispatchedUncertain(
            @Param("tenantId") Long tenantId,
            @Param("runId") Long runId,
            @Param("leaseOwner") String leaseOwner,
            @Param("fencingToken") Long fencingToken,
            @Param("failureCode") String failureCode,
            @Param("failureSummary") String failureSummary,
            @Param("now") Instant now
    );
}
