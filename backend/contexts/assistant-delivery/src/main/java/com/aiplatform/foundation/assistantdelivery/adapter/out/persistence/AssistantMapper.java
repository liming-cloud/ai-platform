package com.aiplatform.foundation.assistantdelivery.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Param;

/**
 * 助手身份、不可变版本与幂等事实数据库映射器。
 */
public interface AssistantMapper {

    String lockScope(@Param("scope") String scope);

    AssistantRequestRow findRequest(
            @Param("tenantId") UUID tenantId,
            @Param("operation") String operation,
            @Param("keyHash") String keyHash);

    int countAssistants(@Param("tenantId") UUID tenantId);

    int insertAssistant(
            @Param("tenantId") UUID tenantId,
            @Param("id") UUID id,
            @Param("displayName") String displayName,
            @Param("latestVersionNumber") int latestVersionNumber,
            @Param("version") long version,
            @Param("createdBy") UUID createdBy,
            @Param("createdAt") Instant createdAt);

    int insertRequest(
            @Param("tenantId") UUID tenantId,
            @Param("operation") String operation,
            @Param("keyHash") String keyHash,
            @Param("requestFingerprint") String requestFingerprint,
            @Param("assistantId") UUID assistantId,
            @Param("assistantVersionId") UUID assistantVersionId,
            @Param("createdAt") Instant createdAt);

    AssistantRow findAssistant(
            @Param("tenantId") UUID tenantId,
            @Param("assistantId") UUID assistantId);

    AssistantRow findAssistantForUpdate(
            @Param("tenantId") UUID tenantId,
            @Param("assistantId") UUID assistantId);

    List<AssistantRow> findFirstPage(
            @Param("tenantId") UUID tenantId,
            @Param("limit") int limit);

    List<AssistantRow> findAfter(
            @Param("tenantId") UUID tenantId,
            @Param("createdAt") Instant createdAt,
            @Param("id") UUID id,
            @Param("limit") int limit);

    List<PublishedAssistantRow> findFirstPublishedPage(
            @Param("tenantId") UUID tenantId,
            @Param("limit") int limit);

    List<PublishedAssistantRow> findPublishedAfter(
            @Param("tenantId") UUID tenantId,
            @Param("publishedAt") Instant publishedAt,
            @Param("assistantVersionId") UUID assistantVersionId,
            @Param("limit") int limit);

    int countVersions(
            @Param("tenantId") UUID tenantId,
            @Param("assistantId") UUID assistantId);

    int advanceAssistant(
            @Param("tenantId") UUID tenantId,
            @Param("assistantId") UUID assistantId,
            @Param("latestVersionNumber") int latestVersionNumber,
            @Param("newVersion") long newVersion,
            @Param("expectedVersion") long expectedVersion);

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

    AssistantVersionRow findVersion(
            @Param("tenantId") UUID tenantId,
            @Param("assistantVersionId") UUID assistantVersionId);

    int publishVersion(
            @Param("tenantId") UUID tenantId,
            @Param("assistantVersionId") UUID assistantVersionId,
            @Param("status") String status,
            @Param("newVersion") long newVersion,
            @Param("publishedAt") Instant publishedAt,
            @Param("expectedVersion") long expectedVersion);
}
