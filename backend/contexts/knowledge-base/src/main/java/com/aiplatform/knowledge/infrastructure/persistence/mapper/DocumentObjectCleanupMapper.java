package com.aiplatform.knowledge.infrastructure.persistence.mapper;

import com.aiplatform.knowledge.infrastructure.persistence.projection.DocumentObjectCleanupRow;
import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DocumentObjectCleanupMapper {
    int observe(
            @Param("tenantId") long tenantId,
            @Param("knowledgeBaseId") long knowledgeBaseId,
            @Param("documentId") long documentId,
            @Param("bucket") String bucket,
            @Param("objectKey") String objectKey,
            @Param("sourceUri") String sourceUri,
            @Param("failureType") String failureType,
            @Param("observedAt") Instant observedAt,
            @Param("firstAttemptAt") Instant firstAttemptAt
    );

    DocumentObjectCleanupRow claimNext(
            @Param("workerId") String workerId,
            @Param("leaseToken") String leaseToken,
            @Param("now") Instant now,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("maxAttempts") int maxAttempts
    );

    Boolean hasLiveDocumentReference(
            @Param("tenantId") long tenantId,
            @Param("cleanupId") long cleanupId,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseToken") String leaseToken,
            @Param("fenceVersion") long fenceVersion,
            @Param("now") Instant now
    );

    int completeOwned(
            @Param("tenantId") long tenantId,
            @Param("cleanupId") long cleanupId,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseToken") String leaseToken,
            @Param("fenceVersion") long fenceVersion,
            @Param("now") Instant now
    );

    int retainOwned(
            @Param("tenantId") long tenantId,
            @Param("cleanupId") long cleanupId,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseToken") String leaseToken,
            @Param("fenceVersion") long fenceVersion,
            @Param("now") Instant now
    );

    int retryOwned(
            @Param("tenantId") long tenantId,
            @Param("cleanupId") long cleanupId,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseToken") String leaseToken,
            @Param("fenceVersion") long fenceVersion,
            @Param("failureCode") String failureCode,
            @Param("failureSummary") String failureSummary,
            @Param("nextRetryAt") Instant nextRetryAt,
            @Param("now") Instant now
    );

    int failOwned(
            @Param("tenantId") long tenantId,
            @Param("cleanupId") long cleanupId,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseToken") String leaseToken,
            @Param("fenceVersion") long fenceVersion,
            @Param("failureCode") String failureCode,
            @Param("failureSummary") String failureSummary,
            @Param("now") Instant now
    );
}
