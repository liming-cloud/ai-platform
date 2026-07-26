package com.aiplatform.foundation.knowledgedata.adapter.out.persistence;

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
 * 索引版本、冻结来源与构建作业数据库映射器。
 */
public interface IndexVersionMapper {

    @Select("""
            SELECT pg_advisory_xact_lock(
                hashtextextended(#{scope}, 20260729)
            )
            """)
    String lockSpace(@Param("scope") String scope);

    @Select("""
            SELECT request_fingerprint,
                   knowledge_space_id::text AS knowledge_space_id,
                   index_version_id::text AS index_version_id
            FROM knowledge_data.index_version_request
            WHERE tenant_id = #{tenantId}
              AND operation = #{operation}
              AND idempotency_key_hash = #{keyHash}
            """)
    @Results(id = "indexRequestRow", value = {
        @Result(column = "request_fingerprint", property = "requestFingerprint"),
        @Result(column = "knowledge_space_id", property = "knowledgeSpaceId"),
        @Result(column = "index_version_id", property = "indexVersionId")
    })
    IndexVersionRequestRow findRequest(
            @Param("tenantId") UUID tenantId,
            @Param("operation") String operation,
            @Param("keyHash") String keyHash);

    @Select("""
            SELECT source.id::text AS data_source_id,
                   content.version AS content_version,
                   content.object_key,
                   content.sha256,
                   content.byte_size,
                   content.media_type,
                   content.created_at AS content_created_at
            FROM knowledge_data.text_data_source source
            JOIN knowledge_data.text_data_source_content content
              ON content.tenant_id = source.tenant_id
             AND content.knowledge_space_id = source.knowledge_space_id
             AND content.data_source_id = source.id
             AND content.version = source.published_content_version
            WHERE source.tenant_id = #{tenantId}
              AND source.knowledge_space_id = #{knowledgeSpaceId}
              AND source.status = 'PUBLISHED'
            ORDER BY source.id
            FOR SHARE OF source, content
            """)
    @Results(id = "indexSourceRow", value = {
        @Result(column = "data_source_id", property = "dataSourceId"),
        @Result(column = "content_version", property = "contentVersion"),
        @Result(column = "object_key", property = "objectKey"),
        @Result(column = "sha256", property = "sha256"),
        @Result(column = "byte_size", property = "byteSize"),
        @Result(column = "media_type", property = "mediaType"),
        @Result(column = "content_created_at", property = "contentCreatedAt")
    })
    List<IndexSourceRow> findPublishedSources(
            @Param("tenantId") UUID tenantId,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId);

    @Insert("""
            INSERT INTO knowledge_data.index_version (
                tenant_id,
                knowledge_space_id,
                id,
                source_snapshot_digest,
                chunk_policy,
                embedding_deployment_id,
                embedding_deployment_revision,
                embedding_dimension,
                status,
                version,
                build_digest,
                chunk_count,
                failure_code,
                created_by,
                created_at,
                ready_at,
                activated_at,
                superseded_at
            ) VALUES (
                #{tenantId},
                #{knowledgeSpaceId},
                #{id},
                #{sourceSnapshotDigest},
                #{chunkPolicy},
                #{embeddingDeploymentId},
                #{embeddingDeploymentRevision},
                #{embeddingDimension},
                'BUILDING',
                0,
                NULL,
                0,
                NULL,
                #{createdBy},
                #{createdAt},
                NULL,
                NULL,
                NULL
            )
            """)
    int insertVersion(
            @Param("tenantId") UUID tenantId,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId,
            @Param("id") UUID id,
            @Param("sourceSnapshotDigest") String sourceSnapshotDigest,
            @Param("chunkPolicy") String chunkPolicy,
            @Param("embeddingDeploymentId") UUID embeddingDeploymentId,
            @Param("embeddingDeploymentRevision") long embeddingDeploymentRevision,
            @Param("embeddingDimension") int embeddingDimension,
            @Param("createdBy") UUID createdBy,
            @Param("createdAt") Instant createdAt);

    @Insert("""
            INSERT INTO knowledge_data.index_version_source (
                tenant_id,
                knowledge_space_id,
                index_version_id,
                data_source_id,
                content_version,
                object_key,
                sha256,
                byte_size,
                media_type,
                content_created_at
            ) VALUES (
                #{tenantId},
                #{knowledgeSpaceId},
                #{indexVersionId},
                #{dataSourceId},
                #{contentVersion},
                #{objectKey},
                #{sha256},
                #{byteSize},
                #{mediaType},
                #{contentCreatedAt}
            )
            """)
    int insertSource(
            @Param("tenantId") UUID tenantId,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId,
            @Param("indexVersionId") UUID indexVersionId,
            @Param("dataSourceId") UUID dataSourceId,
            @Param("contentVersion") int contentVersion,
            @Param("objectKey") String objectKey,
            @Param("sha256") String sha256,
            @Param("byteSize") long byteSize,
            @Param("mediaType") String mediaType,
            @Param("contentCreatedAt") Instant contentCreatedAt);

    @Insert("""
            INSERT INTO knowledge_data.index_build_job (
                tenant_id,
                knowledge_space_id,
                index_version_id,
                status,
                attempt,
                lease_id,
                lease_until,
                failure_code,
                available_at,
                created_at,
                completed_at
            ) VALUES (
                #{tenantId},
                #{knowledgeSpaceId},
                #{indexVersionId},
                'PENDING',
                0,
                NULL,
                NULL,
                NULL,
                #{createdAt},
                #{createdAt},
                NULL
            )
            """)
    int insertJob(
            @Param("tenantId") UUID tenantId,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId,
            @Param("indexVersionId") UUID indexVersionId,
            @Param("createdAt") Instant createdAt);

    @Insert("""
            INSERT INTO knowledge_data.index_version_request (
                tenant_id,
                operation,
                idempotency_key_hash,
                request_fingerprint,
                knowledge_space_id,
                index_version_id,
                result_status,
                result_version,
                created_at
            ) VALUES (
                #{tenantId},
                #{operation},
                #{keyHash},
                #{requestFingerprint},
                #{knowledgeSpaceId},
                #{indexVersionId},
                #{resultStatus},
                #{resultVersion},
                #{createdAt}
            )
            """)
    int insertRequest(
            @Param("tenantId") UUID tenantId,
            @Param("operation") String operation,
            @Param("keyHash") String keyHash,
            @Param("requestFingerprint") String requestFingerprint,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId,
            @Param("indexVersionId") UUID indexVersionId,
            @Param("resultStatus") String resultStatus,
            @Param("resultVersion") long resultVersion,
            @Param("createdAt") Instant createdAt);

    @Select("""
            SELECT tenant_id::text AS tenant_id,
                   knowledge_space_id::text AS knowledge_space_id,
                   id::text AS id,
                   source_snapshot_digest,
                   chunk_policy,
                   embedding_deployment_id::text AS embedding_deployment_id,
                   embedding_deployment_revision,
                   embedding_dimension,
                   status,
                   version,
                   build_digest,
                   chunk_count,
                   failure_code,
                   created_by::text AS created_by,
                   created_at,
                   ready_at,
                   activated_at,
                   superseded_at
            FROM knowledge_data.index_version
            WHERE tenant_id = #{tenantId}
              AND knowledge_space_id = #{knowledgeSpaceId}
              AND id = #{id}
            """)
    @Results(id = "indexVersionRow", value = {
        @Result(column = "tenant_id", property = "tenantId"),
        @Result(column = "knowledge_space_id", property = "knowledgeSpaceId"),
        @Result(column = "id", property = "id"),
        @Result(column = "source_snapshot_digest", property = "sourceSnapshotDigest"),
        @Result(column = "chunk_policy", property = "chunkPolicy"),
        @Result(
                column = "embedding_deployment_id",
                property = "embeddingDeploymentId"),
        @Result(
                column = "embedding_deployment_revision",
                property = "embeddingDeploymentRevision"),
        @Result(column = "embedding_dimension", property = "embeddingDimension"),
        @Result(column = "status", property = "status"),
        @Result(column = "version", property = "version"),
        @Result(column = "build_digest", property = "buildDigest"),
        @Result(column = "chunk_count", property = "chunkCount"),
        @Result(column = "failure_code", property = "failureCode"),
        @Result(column = "created_by", property = "createdBy"),
        @Result(column = "created_at", property = "createdAt"),
        @Result(column = "ready_at", property = "readyAt"),
        @Result(column = "activated_at", property = "activatedAt"),
        @Result(column = "superseded_at", property = "supersededAt")
    })
    IndexVersionRow findVersion(
            @Param("tenantId") UUID tenantId,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId,
            @Param("id") UUID id);

    @Select("""
            SELECT tenant_id::text AS tenant_id,
                   knowledge_space_id::text AS knowledge_space_id,
                   id::text AS id,
                   source_snapshot_digest,
                   chunk_policy,
                   embedding_deployment_id::text AS embedding_deployment_id,
                   embedding_deployment_revision,
                   embedding_dimension,
                   status,
                   version,
                   build_digest,
                   chunk_count,
                   failure_code,
                   created_by::text AS created_by,
                   created_at,
                   ready_at,
                   activated_at,
                   superseded_at
            FROM knowledge_data.index_version
            WHERE tenant_id = #{tenantId}
              AND knowledge_space_id = #{knowledgeSpaceId}
              AND id = #{id}
            FOR UPDATE
            """)
    @ResultMap("indexVersionRow")
    IndexVersionRow findVersionForUpdate(
            @Param("tenantId") UUID tenantId,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId,
            @Param("id") UUID id);

    @Select("""
            SELECT tenant_id::text AS tenant_id,
                   knowledge_space_id::text AS knowledge_space_id,
                   id::text AS id,
                   source_snapshot_digest,
                   chunk_policy,
                   embedding_deployment_id::text AS embedding_deployment_id,
                   embedding_deployment_revision,
                   embedding_dimension,
                   status,
                   version,
                   build_digest,
                   chunk_count,
                   failure_code,
                   created_by::text AS created_by,
                   created_at,
                   ready_at,
                   activated_at,
                   superseded_at
            FROM knowledge_data.index_version
            WHERE tenant_id = #{tenantId}
              AND knowledge_space_id = #{knowledgeSpaceId}
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit}
            """)
    @ResultMap("indexVersionRow")
    List<IndexVersionRow> findFirstPage(
            @Param("tenantId") UUID tenantId,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId,
            @Param("limit") int limit);

    @Select("""
            SELECT tenant_id::text AS tenant_id,
                   knowledge_space_id::text AS knowledge_space_id,
                   id::text AS id,
                   source_snapshot_digest,
                   chunk_policy,
                   embedding_deployment_id::text AS embedding_deployment_id,
                   embedding_deployment_revision,
                   embedding_dimension,
                   status,
                   version,
                   build_digest,
                   chunk_count,
                   failure_code,
                   created_by::text AS created_by,
                   created_at,
                   ready_at,
                   activated_at,
                   superseded_at
            FROM knowledge_data.index_version
            WHERE tenant_id = #{tenantId}
              AND knowledge_space_id = #{knowledgeSpaceId}
              AND (created_at, id) < (#{createdAt}, #{id})
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit}
            """)
    @ResultMap("indexVersionRow")
    List<IndexVersionRow> findAfter(
            @Param("tenantId") UUID tenantId,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId,
            @Param("createdAt") Instant createdAt,
            @Param("id") UUID id,
            @Param("limit") int limit);

    @Select("""
            SELECT tenant_id::text AS tenant_id,
                   knowledge_space_id::text AS knowledge_space_id,
                   index_version_id::text AS index_version_id,
                   attempt
            FROM knowledge_data.index_build_job
            WHERE available_at <= #{now}
              AND (
                    status = 'PENDING'
                    OR (status = 'RUNNING' AND lease_until <= #{now})
              )
            ORDER BY available_at, created_at, index_version_id
            FOR UPDATE SKIP LOCKED
            LIMIT 1
            """)
    @Results(id = "indexBuildJobRow", value = {
        @Result(column = "tenant_id", property = "tenantId"),
        @Result(column = "knowledge_space_id", property = "knowledgeSpaceId"),
        @Result(column = "index_version_id", property = "indexVersionId"),
        @Result(column = "attempt", property = "attempt")
    })
    IndexBuildJobRow claimableJob(@Param("now") Instant now);

    @Update("""
            UPDATE knowledge_data.index_build_job
            SET status = 'RUNNING',
                attempt = attempt + 1,
                lease_id = #{leaseId},
                lease_until = #{leaseUntil},
                failure_code = NULL,
                completed_at = NULL
            WHERE tenant_id = #{tenantId}
              AND knowledge_space_id = #{knowledgeSpaceId}
              AND index_version_id = #{indexVersionId}
            """)
    int markClaimed(
            @Param("tenantId") UUID tenantId,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId,
            @Param("indexVersionId") UUID indexVersionId,
            @Param("leaseId") UUID leaseId,
            @Param("leaseUntil") Instant leaseUntil);

    @Select("""
            SELECT data_source_id::text AS data_source_id,
                   content_version,
                   object_key,
                   sha256,
                   byte_size,
                   media_type,
                   content_created_at
            FROM knowledge_data.index_version_source
            WHERE tenant_id = #{tenantId}
              AND knowledge_space_id = #{knowledgeSpaceId}
              AND index_version_id = #{indexVersionId}
            ORDER BY data_source_id
            """)
    @ResultMap("indexSourceRow")
    List<IndexSourceRow> findSources(
            @Param("tenantId") UUID tenantId,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId,
            @Param("indexVersionId") UUID indexVersionId);

    @Select("""
            SELECT 1
            FROM knowledge_data.index_build_job
            WHERE tenant_id = #{tenantId}
              AND knowledge_space_id = #{knowledgeSpaceId}
              AND index_version_id = #{indexVersionId}
              AND status = 'RUNNING'
              AND lease_id = #{leaseId}
            FOR UPDATE
            """)
    Integer findRunningLease(
            @Param("tenantId") UUID tenantId,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId,
            @Param("indexVersionId") UUID indexVersionId,
            @Param("leaseId") UUID leaseId);

    @Update("""
            UPDATE knowledge_data.index_version
            SET status = 'READY',
                version = version + 1,
                build_digest = #{buildDigest},
                chunk_count = #{chunkCount},
                ready_at = #{completedAt}
            WHERE tenant_id = #{tenantId}
              AND knowledge_space_id = #{knowledgeSpaceId}
              AND id = #{indexVersionId}
              AND status = 'BUILDING'
            """)
    int markReady(
            @Param("tenantId") UUID tenantId,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId,
            @Param("indexVersionId") UUID indexVersionId,
            @Param("buildDigest") String buildDigest,
            @Param("chunkCount") int chunkCount,
            @Param("completedAt") Instant completedAt);

    @Update("""
            UPDATE knowledge_data.index_build_job
            SET status = 'DONE',
                lease_id = NULL,
                lease_until = NULL,
                failure_code = NULL,
                completed_at = #{completedAt}
            WHERE tenant_id = #{tenantId}
              AND knowledge_space_id = #{knowledgeSpaceId}
              AND index_version_id = #{indexVersionId}
              AND status = 'RUNNING'
              AND lease_id = #{leaseId}
            """)
    int markDone(
            @Param("tenantId") UUID tenantId,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId,
            @Param("indexVersionId") UUID indexVersionId,
            @Param("leaseId") UUID leaseId,
            @Param("completedAt") Instant completedAt);

    @Update("""
            UPDATE knowledge_data.index_version
            SET status = 'FAILED',
                version = version + 1,
                failure_code = #{failureCode}
            WHERE tenant_id = #{tenantId}
              AND knowledge_space_id = #{knowledgeSpaceId}
              AND id = #{indexVersionId}
              AND status = 'BUILDING'
            """)
    int markFailedVersion(
            @Param("tenantId") UUID tenantId,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId,
            @Param("indexVersionId") UUID indexVersionId,
            @Param("failureCode") String failureCode);

    @Update("""
            UPDATE knowledge_data.index_build_job
            SET status = 'FAILED',
                lease_id = NULL,
                lease_until = NULL,
                failure_code = #{failureCode},
                completed_at = #{failedAt}
            WHERE tenant_id = #{tenantId}
              AND knowledge_space_id = #{knowledgeSpaceId}
              AND index_version_id = #{indexVersionId}
              AND status = 'RUNNING'
              AND lease_id = #{leaseId}
            """)
    int markFailedJob(
            @Param("tenantId") UUID tenantId,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId,
            @Param("indexVersionId") UUID indexVersionId,
            @Param("leaseId") UUID leaseId,
            @Param("failureCode") String failureCode,
            @Param("failedAt") Instant failedAt);

    @Update("""
            UPDATE knowledge_data.index_version
            SET status = 'SUPERSEDED',
                version = version + 1,
                superseded_at = #{supersededAt}
            WHERE tenant_id = #{tenantId}
              AND knowledge_space_id = #{knowledgeSpaceId}
              AND status = 'ACTIVE'
            """)
    int supersedeActive(
            @Param("tenantId") UUID tenantId,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId,
            @Param("supersededAt") Instant supersededAt);

    @Update("""
            UPDATE knowledge_data.index_version
            SET status = 'ACTIVE',
                version = version + 1,
                activated_at = #{activatedAt}
            WHERE tenant_id = #{tenantId}
              AND knowledge_space_id = #{knowledgeSpaceId}
              AND id = #{indexVersionId}
              AND status = 'READY'
              AND version = #{expectedVersion}
            """)
    int activateReady(
            @Param("tenantId") UUID tenantId,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId,
            @Param("indexVersionId") UUID indexVersionId,
            @Param("expectedVersion") long expectedVersion,
            @Param("activatedAt") Instant activatedAt);
}
