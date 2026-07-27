package com.aiplatform.foundation.knowledgedata.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Param;

/**
 * 文本数据源的数据库映射器。
 */
public interface TextDataSourceMapper {

    String lockTenant(@Param("tenantId") String tenantId);

    int countKnowledgeSpace(
            @Param("tenantId") UUID tenantId,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId);

    int countTenantSources(@Param("tenantId") UUID tenantId);

    int insertSource(
            @Param("tenantId") UUID tenantId,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId,
            @Param("id") UUID id,
            @Param("name") String name,
            @Param("status") String status,
            @Param("version") long version,
            @Param("contentVersion") int contentVersion,
            @Param("publishedContentVersion") int publishedContentVersion,
            @Param("createdBy") UUID createdBy,
            @Param("createdAt") Instant createdAt);

    TextDataSourceRow findByIdForUpdate(
            @Param("tenantId") UUID tenantId,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId,
            @Param("id") UUID id);

    TextDataSourceRow findById(
            @Param("tenantId") UUID tenantId,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId,
            @Param("id") UUID id);

    List<TextDataSourceRow> findFirstPage(
            @Param("tenantId") UUID tenantId,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId,
            @Param("limit") int limit);

    List<TextDataSourceRow> findAfter(
            @Param("tenantId") UUID tenantId,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId,
            @Param("createdAt") Instant createdAt,
            @Param("id") UUID id,
            @Param("limit") int limit);

    TextDataSourceRequestRow findRequest(
            @Param("tenantId") UUID tenantId,
            @Param("operation") String operation,
            @Param("keyHash") String keyHash);

    TextContentRow findContent(
            @Param("tenantId") UUID tenantId,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId,
            @Param("dataSourceId") UUID dataSourceId,
            @Param("version") int version);

    int insertContent(
            @Param("tenantId") UUID tenantId,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId,
            @Param("dataSourceId") UUID dataSourceId,
            @Param("version") int version,
            @Param("objectKey") String objectKey,
            @Param("sha256") String sha256,
            @Param("byteSize") long byteSize,
            @Param("mediaType") String mediaType,
            @Param("createdAt") Instant createdAt);

    int updateSource(
            @Param("tenantId") UUID tenantId,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId,
            @Param("id") UUID id,
            @Param("status") String status,
            @Param("newVersion") long newVersion,
            @Param("contentVersion") int contentVersion,
            @Param("publishedContentVersion") int publishedContentVersion,
            @Param("expectedVersion") long expectedVersion);

    int insertRequest(
            @Param("tenantId") UUID tenantId,
            @Param("operation") String operation,
            @Param("keyHash") String keyHash,
            @Param("requestFingerprint") String requestFingerprint,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId,
            @Param("dataSourceId") UUID dataSourceId,
            @Param("resultStatus") String resultStatus,
            @Param("resultVersion") long resultVersion,
            @Param("resultContentVersion") int resultContentVersion,
            @Param("resultPublishedContentVersion") int resultPublishedContentVersion,
            @Param("createdAt") Instant createdAt);

    int insertOutboxEvent(
            @Param("tenantId") UUID tenantId,
            @Param("eventId") UUID eventId,
            @Param("aggregateId") UUID aggregateId,
            @Param("eventType") String eventType,
            @Param("payload") String payload,
            @Param("occurredAt") Instant occurredAt);
}
