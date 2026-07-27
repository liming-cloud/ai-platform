package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.index.mapper;

import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.index.projection.IndexBuildJobRow;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.index.projection.IndexSourceRow;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.index.entity.IndexVersionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 索引版本、冻结来源与构建作业数据库映射器。
 */
public interface IndexVersionMapper extends BaseMapper<IndexVersionEntity> {

    String lockSpace(@Param("scope") String scope);

    List<IndexSourceRow> findPublishedSources(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId);

    IndexVersionEntity findVersionForUpdate(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("id") long id);

    IndexBuildJobRow claimableJob(@Param("now") Instant now);

    int markClaimed(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("indexVersionId") long indexVersionId,
            @Param("leaseId") long leaseId,
            @Param("leaseUntil") Instant leaseUntil);

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
