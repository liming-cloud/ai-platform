package com.aiplatform.foundation.knowledgedata.adapter.out.persistence.index;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 索引版本、冻结来源与构建作业数据库映射器。
 */
public interface IndexVersionMapper {

    String lockSpace(@Param("scope") String scope);

    IndexVersionRequestRow findRequest(
            @Param("tenantId") long tenantId,
            @Param("operation") String operation,
            @Param("keyHash") String keyHash);

    List<IndexSourceRow> findPublishedSources(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId);

    int insertVersion(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("id") long id,
            @Param("sourceSnapshotDigest") String sourceSnapshotDigest,
            @Param("chunkPolicy") String chunkPolicy,
            @Param("embeddingDeploymentId") long embeddingDeploymentId,
            @Param("embeddingDeploymentRevision") long embeddingDeploymentRevision,
            @Param("embeddingDimension") int embeddingDimension,
            @Param("createdBy") long createdBy,
            @Param("createdAt") Instant createdAt);

    int insertSource(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("indexVersionId") long indexVersionId,
            @Param("dataSourceId") long dataSourceId,
            @Param("contentVersion") int contentVersion,
            @Param("objectKey") String objectKey,
            @Param("sha256") String sha256,
            @Param("byteSize") long byteSize,
            @Param("mediaType") String mediaType,
            @Param("contentCreatedAt") Instant contentCreatedAt);

    int insertJob(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("indexVersionId") long indexVersionId,
            @Param("createdAt") Instant createdAt);

    int insertRequest(
            @Param("tenantId") long tenantId,
            @Param("operation") String operation,
            @Param("keyHash") String keyHash,
            @Param("requestFingerprint") String requestFingerprint,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("indexVersionId") long indexVersionId,
            @Param("resultStatus") String resultStatus,
            @Param("resultVersion") long resultVersion,
            @Param("createdAt") Instant createdAt);

    IndexVersionRow findVersion(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("id") long id);

    IndexVersionRow findActive(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId);

    IndexVersionRow findVersionForUpdate(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("id") long id);

    List<IndexVersionRow> findFirstPage(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("limit") int limit);

    List<IndexVersionRow> findAfter(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("createdAt") Instant createdAt,
            @Param("id") long id,
            @Param("limit") int limit);

    IndexBuildJobRow claimableJob(@Param("now") Instant now);

    int markClaimed(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("indexVersionId") long indexVersionId,
            @Param("leaseId") long leaseId,
            @Param("leaseUntil") Instant leaseUntil);

    List<IndexSourceRow> findSources(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("indexVersionId") long indexVersionId);

    Integer findRunningLease(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("indexVersionId") long indexVersionId,
            @Param("leaseId") long leaseId);

    int markReady(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("indexVersionId") long indexVersionId,
            @Param("buildDigest") String buildDigest,
            @Param("chunkCount") int chunkCount,
            @Param("completedAt") Instant completedAt);

    int markDone(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("indexVersionId") long indexVersionId,
            @Param("leaseId") long leaseId,
            @Param("completedAt") Instant completedAt);

    int markFailedVersion(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("indexVersionId") long indexVersionId,
            @Param("failureCode") String failureCode);

    int markFailedJob(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("indexVersionId") long indexVersionId,
            @Param("leaseId") long leaseId,
            @Param("failureCode") String failureCode,
            @Param("failedAt") Instant failedAt);

    int supersedeActive(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("supersededAt") Instant supersededAt);

    int activateReady(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("indexVersionId") long indexVersionId,
            @Param("expectedVersion") long expectedVersion,
            @Param("activatedAt") Instant activatedAt);
}
