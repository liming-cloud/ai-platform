package com.aiplatform.foundation.knowledgedata.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 文本数据源的数据库映射器。
 */
public interface TextDataSourceMapper {

    String lockTenant(@Param("tenantId") String tenantId);

    int countKnowledgeSpace(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId);

    int countTenantSources(@Param("tenantId") long tenantId);

    int insertSource(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("id") long id,
            @Param("name") String name,
            @Param("status") String status,
            @Param("version") long version,
            @Param("contentVersion") int contentVersion,
            @Param("publishedContentVersion") int publishedContentVersion,
            @Param("createdBy") long createdBy,
            @Param("createdAt") Instant createdAt);

    TextDataSourceRow findByIdForUpdate(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("id") long id);

    TextDataSourceRow findById(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("id") long id);

    List<TextDataSourceRow> findFirstPage(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("limit") int limit);

    List<TextDataSourceRow> findAfter(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("createdAt") Instant createdAt,
            @Param("id") long id,
            @Param("limit") int limit);

    TextDataSourceRequestRow findRequest(
            @Param("tenantId") long tenantId,
            @Param("operation") String operation,
            @Param("keyHash") String keyHash);

    TextContentRow findContent(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("dataSourceId") long dataSourceId,
            @Param("version") int version);

    int insertContent(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("dataSourceId") long dataSourceId,
            @Param("version") int version,
            @Param("objectKey") String objectKey,
            @Param("sha256") String sha256,
            @Param("byteSize") long byteSize,
            @Param("mediaType") String mediaType,
            @Param("createdAt") Instant createdAt);

    int updateSource(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("id") long id,
            @Param("status") String status,
            @Param("newVersion") long newVersion,
            @Param("contentVersion") int contentVersion,
            @Param("publishedContentVersion") int publishedContentVersion,
            @Param("expectedVersion") long expectedVersion);

    int insertRequest(
            @Param("tenantId") long tenantId,
            @Param("operation") String operation,
            @Param("keyHash") String keyHash,
            @Param("requestFingerprint") String requestFingerprint,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("dataSourceId") long dataSourceId,
            @Param("resultStatus") String resultStatus,
            @Param("resultVersion") long resultVersion,
            @Param("resultContentVersion") int resultContentVersion,
            @Param("resultPublishedContentVersion") int resultPublishedContentVersion,
            @Param("createdAt") Instant createdAt);

    int insertOutboxEvent(
            @Param("tenantId") long tenantId,
            @Param("eventId") long eventId,
            @Param("aggregateId") long aggregateId,
            @Param("eventType") String eventType,
            @Param("payload") String payload,
            @Param("occurredAt") Instant occurredAt);
}
