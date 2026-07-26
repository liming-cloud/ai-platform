package com.aiplatform.foundation.assistantdelivery.adapter.out.persistence;

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
 * 助手身份、不可变版本与幂等事实数据库映射器。
 */
public interface AssistantMapper {

    @Select("""
            SELECT pg_advisory_xact_lock(
                hashtextextended(#{scope}, 20260730)
            )
            """)
    String lockScope(@Param("scope") String scope);

    @Select("""
            SELECT request_fingerprint,
                   assistant_id::text AS assistant_id,
                   assistant_version_id::text AS assistant_version_id
            FROM assistant_delivery.assistant_request
            WHERE tenant_id = #{tenantId}
              AND operation = #{operation}
              AND idempotency_key_hash = #{keyHash}
            """)
    @Results(id = "assistantRequestRow", value = {
        @Result(column = "request_fingerprint", property = "requestFingerprint"),
        @Result(column = "assistant_id", property = "assistantId"),
        @Result(column = "assistant_version_id", property = "assistantVersionId")
    })
    AssistantRequestRow findRequest(
            @Param("tenantId") UUID tenantId,
            @Param("operation") String operation,
            @Param("keyHash") String keyHash);

    @Select("""
            SELECT count(*)
            FROM assistant_delivery.assistant
            WHERE tenant_id = #{tenantId}
            """)
    int countAssistants(@Param("tenantId") UUID tenantId);

    @Insert("""
            INSERT INTO assistant_delivery.assistant (
                tenant_id,
                id,
                display_name,
                latest_version_number,
                version,
                created_by,
                created_at
            ) VALUES (
                #{tenantId},
                #{id},
                #{displayName},
                #{latestVersionNumber},
                #{version},
                #{createdBy},
                #{createdAt}
            )
            """)
    int insertAssistant(
            @Param("tenantId") UUID tenantId,
            @Param("id") UUID id,
            @Param("displayName") String displayName,
            @Param("latestVersionNumber") int latestVersionNumber,
            @Param("version") long version,
            @Param("createdBy") UUID createdBy,
            @Param("createdAt") Instant createdAt);

    @Insert("""
            INSERT INTO assistant_delivery.assistant_request (
                tenant_id,
                operation,
                idempotency_key_hash,
                request_fingerprint,
                assistant_id,
                assistant_version_id,
                created_at
            ) VALUES (
                #{tenantId},
                #{operation},
                #{keyHash},
                #{requestFingerprint},
                #{assistantId},
                #{assistantVersionId},
                #{createdAt}
            )
            """)
    int insertRequest(
            @Param("tenantId") UUID tenantId,
            @Param("operation") String operation,
            @Param("keyHash") String keyHash,
            @Param("requestFingerprint") String requestFingerprint,
            @Param("assistantId") UUID assistantId,
            @Param("assistantVersionId") UUID assistantVersionId,
            @Param("createdAt") Instant createdAt);

    @Select("""
            SELECT tenant_id::text AS tenant_id,
                   id::text AS id,
                   display_name,
                   latest_version_number,
                   version,
                   created_by::text AS created_by,
                   created_at
            FROM assistant_delivery.assistant
            WHERE tenant_id = #{tenantId}
              AND id = #{assistantId}
            """)
    @Results(id = "assistantRow", value = {
        @Result(column = "tenant_id", property = "tenantId"),
        @Result(column = "id", property = "id"),
        @Result(column = "display_name", property = "displayName"),
        @Result(column = "latest_version_number", property = "latestVersionNumber"),
        @Result(column = "version", property = "version"),
        @Result(column = "created_by", property = "createdBy"),
        @Result(column = "created_at", property = "createdAt")
    })
    AssistantRow findAssistant(
            @Param("tenantId") UUID tenantId,
            @Param("assistantId") UUID assistantId);

    @Select("""
            SELECT tenant_id::text AS tenant_id,
                   id::text AS id,
                   display_name,
                   latest_version_number,
                   version,
                   created_by::text AS created_by,
                   created_at
            FROM assistant_delivery.assistant
            WHERE tenant_id = #{tenantId}
              AND id = #{assistantId}
            FOR UPDATE
            """)
    @ResultMap("assistantRow")
    AssistantRow findAssistantForUpdate(
            @Param("tenantId") UUID tenantId,
            @Param("assistantId") UUID assistantId);

    @Select("""
            SELECT tenant_id::text AS tenant_id,
                   id::text AS id,
                   display_name,
                   latest_version_number,
                   version,
                   created_by::text AS created_by,
                   created_at
            FROM assistant_delivery.assistant
            WHERE tenant_id = #{tenantId}
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit}
            """)
    @ResultMap("assistantRow")
    List<AssistantRow> findFirstPage(
            @Param("tenantId") UUID tenantId,
            @Param("limit") int limit);

    @Select("""
            SELECT tenant_id::text AS tenant_id,
                   id::text AS id,
                   display_name,
                   latest_version_number,
                   version,
                   created_by::text AS created_by,
                   created_at
            FROM assistant_delivery.assistant
            WHERE tenant_id = #{tenantId}
              AND (created_at, id) < (#{createdAt}, #{id})
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit}
            """)
    @ResultMap("assistantRow")
    List<AssistantRow> findAfter(
            @Param("tenantId") UUID tenantId,
            @Param("createdAt") Instant createdAt,
            @Param("id") UUID id,
            @Param("limit") int limit);

    @Select("""
            WITH latest_published AS (
                SELECT DISTINCT ON (assistant.id)
                       assistant.id AS assistant_id,
                       assistant.display_name,
                       version.id AS assistant_version_id,
                       version.version_number,
                       version.published_at
                FROM assistant_delivery.assistant AS assistant
                JOIN assistant_delivery.assistant_version AS version
                  ON version.tenant_id = assistant.tenant_id
                 AND version.assistant_id = assistant.id
                WHERE assistant.tenant_id = #{tenantId}
                  AND version.status = 'PUBLISHED'
                ORDER BY assistant.id,
                         version.version_number DESC,
                         version.id DESC
            )
            SELECT assistant_id::text AS assistant_id,
                   display_name,
                   assistant_version_id::text AS assistant_version_id,
                   version_number,
                   published_at
            FROM latest_published
            ORDER BY published_at DESC, assistant_version_id DESC
            LIMIT #{limit}
            """)
    @Results(id = "publishedAssistantRow", value = {
        @Result(column = "assistant_id", property = "assistantId"),
        @Result(column = "display_name", property = "displayName"),
        @Result(column = "assistant_version_id", property = "assistantVersionId"),
        @Result(column = "version_number", property = "versionNumber"),
        @Result(column = "published_at", property = "publishedAt")
    })
    List<PublishedAssistantRow> findFirstPublishedPage(
            @Param("tenantId") UUID tenantId,
            @Param("limit") int limit);

    @Select("""
            WITH latest_published AS (
                SELECT DISTINCT ON (assistant.id)
                       assistant.id AS assistant_id,
                       assistant.display_name,
                       version.id AS assistant_version_id,
                       version.version_number,
                       version.published_at
                FROM assistant_delivery.assistant AS assistant
                JOIN assistant_delivery.assistant_version AS version
                  ON version.tenant_id = assistant.tenant_id
                 AND version.assistant_id = assistant.id
                WHERE assistant.tenant_id = #{tenantId}
                  AND version.status = 'PUBLISHED'
                ORDER BY assistant.id,
                         version.version_number DESC,
                         version.id DESC
            )
            SELECT assistant_id::text AS assistant_id,
                   display_name,
                   assistant_version_id::text AS assistant_version_id,
                   version_number,
                   published_at
            FROM latest_published
            WHERE (published_at, assistant_version_id)
                < (#{publishedAt}, #{assistantVersionId})
            ORDER BY published_at DESC, assistant_version_id DESC
            LIMIT #{limit}
            """)
    @ResultMap("publishedAssistantRow")
    List<PublishedAssistantRow> findPublishedAfter(
            @Param("tenantId") UUID tenantId,
            @Param("publishedAt") Instant publishedAt,
            @Param("assistantVersionId") UUID assistantVersionId,
            @Param("limit") int limit);

    @Select("""
            SELECT count(*)
            FROM assistant_delivery.assistant_version
            WHERE tenant_id = #{tenantId}
              AND assistant_id = #{assistantId}
            """)
    int countVersions(
            @Param("tenantId") UUID tenantId,
            @Param("assistantId") UUID assistantId);

    @Update("""
            UPDATE assistant_delivery.assistant
            SET latest_version_number = #{latestVersionNumber},
                version = #{newVersion}
            WHERE tenant_id = #{tenantId}
              AND id = #{assistantId}
              AND version = #{expectedVersion}
            """)
    int advanceAssistant(
            @Param("tenantId") UUID tenantId,
            @Param("assistantId") UUID assistantId,
            @Param("latestVersionNumber") int latestVersionNumber,
            @Param("newVersion") long newVersion,
            @Param("expectedVersion") long expectedVersion);

    @Insert("""
            INSERT INTO assistant_delivery.assistant_version (
                tenant_id,
                id,
                assistant_id,
                version_number,
                chat_deployment_id,
                chat_deployment_revision,
                chat_deployment_output_limit,
                knowledge_space_id,
                index_version_id,
                index_version_revision,
                embedding_deployment_id,
                embedding_deployment_revision,
                embedding_dimension,
                system_instruction,
                max_output_tokens,
                retrieval_limit,
                minimum_score,
                citation_policy,
                refusal_policy,
                status,
                version,
                created_by,
                created_at,
                published_at
            ) VALUES (
                #{tenantId},
                #{id},
                #{assistantId},
                #{versionNumber},
                #{chatDeploymentId},
                #{chatDeploymentRevision},
                #{chatDeploymentOutputLimit},
                #{knowledgeSpaceId},
                #{indexVersionId},
                #{indexVersionRevision},
                #{embeddingDeploymentId},
                #{embeddingDeploymentRevision},
                #{embeddingDimension},
                #{systemInstruction},
                #{maxOutputTokens},
                #{retrievalLimit},
                #{minimumScore},
                #{citationPolicy},
                #{refusalPolicy},
                #{status},
                #{version},
                #{createdBy},
                #{createdAt},
                #{publishedAt}
            )
            """)
    int insertVersion(
            @Param("tenantId") UUID tenantId,
            @Param("id") UUID id,
            @Param("assistantId") UUID assistantId,
            @Param("versionNumber") int versionNumber,
            @Param("chatDeploymentId") UUID chatDeploymentId,
            @Param("chatDeploymentRevision") long chatDeploymentRevision,
            @Param("chatDeploymentOutputLimit") int chatDeploymentOutputLimit,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId,
            @Param("indexVersionId") UUID indexVersionId,
            @Param("indexVersionRevision") long indexVersionRevision,
            @Param("embeddingDeploymentId") UUID embeddingDeploymentId,
            @Param("embeddingDeploymentRevision") long embeddingDeploymentRevision,
            @Param("embeddingDimension") int embeddingDimension,
            @Param("systemInstruction") String systemInstruction,
            @Param("maxOutputTokens") int maxOutputTokens,
            @Param("retrievalLimit") int retrievalLimit,
            @Param("minimumScore") double minimumScore,
            @Param("citationPolicy") String citationPolicy,
            @Param("refusalPolicy") String refusalPolicy,
            @Param("status") String status,
            @Param("version") long version,
            @Param("createdBy") UUID createdBy,
            @Param("createdAt") Instant createdAt,
            @Param("publishedAt") Instant publishedAt);

    @Select("""
            SELECT tenant_id::text AS tenant_id,
                   id::text AS id,
                   assistant_id::text AS assistant_id,
                   version_number,
                   chat_deployment_id::text AS chat_deployment_id,
                   chat_deployment_revision,
                   chat_deployment_output_limit,
                   knowledge_space_id::text AS knowledge_space_id,
                   index_version_id::text AS index_version_id,
                   index_version_revision,
                   embedding_deployment_id::text AS embedding_deployment_id,
                   embedding_deployment_revision,
                   embedding_dimension,
                   system_instruction,
                   max_output_tokens,
                   retrieval_limit,
                   minimum_score,
                   citation_policy,
                   refusal_policy,
                   status,
                   version,
                   created_by::text AS created_by,
                   created_at,
                   published_at
            FROM assistant_delivery.assistant_version
            WHERE tenant_id = #{tenantId}
              AND id = #{assistantVersionId}
            """)
    @Results(id = "assistantVersionRow", value = {
        @Result(column = "tenant_id", property = "tenantId"),
        @Result(column = "id", property = "id"),
        @Result(column = "assistant_id", property = "assistantId"),
        @Result(column = "version_number", property = "versionNumber"),
        @Result(column = "chat_deployment_id", property = "chatDeploymentId"),
        @Result(column = "chat_deployment_revision", property = "chatDeploymentRevision"),
        @Result(
                column = "chat_deployment_output_limit",
                property = "chatDeploymentOutputLimit"),
        @Result(column = "knowledge_space_id", property = "knowledgeSpaceId"),
        @Result(column = "index_version_id", property = "indexVersionId"),
        @Result(column = "index_version_revision", property = "indexVersionRevision"),
        @Result(column = "embedding_deployment_id", property = "embeddingDeploymentId"),
        @Result(
                column = "embedding_deployment_revision",
                property = "embeddingDeploymentRevision"),
        @Result(column = "embedding_dimension", property = "embeddingDimension"),
        @Result(column = "system_instruction", property = "systemInstruction"),
        @Result(column = "max_output_tokens", property = "maxOutputTokens"),
        @Result(column = "retrieval_limit", property = "retrievalLimit"),
        @Result(column = "minimum_score", property = "minimumScore"),
        @Result(column = "citation_policy", property = "citationPolicy"),
        @Result(column = "refusal_policy", property = "refusalPolicy"),
        @Result(column = "status", property = "status"),
        @Result(column = "version", property = "version"),
        @Result(column = "created_by", property = "createdBy"),
        @Result(column = "created_at", property = "createdAt"),
        @Result(column = "published_at", property = "publishedAt")
    })
    AssistantVersionRow findVersion(
            @Param("tenantId") UUID tenantId,
            @Param("assistantVersionId") UUID assistantVersionId);

    @Update("""
            UPDATE assistant_delivery.assistant_version
            SET status = #{status},
                version = #{newVersion},
                published_at = #{publishedAt}
            WHERE tenant_id = #{tenantId}
              AND id = #{assistantVersionId}
              AND status = 'DRAFT'
              AND version = #{expectedVersion}
            """)
    int publishVersion(
            @Param("tenantId") UUID tenantId,
            @Param("assistantVersionId") UUID assistantVersionId,
            @Param("status") String status,
            @Param("newVersion") long newVersion,
            @Param("publishedAt") Instant publishedAt,
            @Param("expectedVersion") long expectedVersion);
}
