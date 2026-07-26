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
 * 文本数据源的数据库映射器。
 */
public interface TextDataSourceMapper {

    @Select("""
            SELECT pg_advisory_xact_lock(hashtextextended(#{tenantId}, 20260728))
            """)
    String lockTenant(@Param("tenantId") String tenantId);

    @Select("""
            SELECT count(*)
            FROM knowledge_data.knowledge_space
            WHERE tenant_id = #{tenantId}
              AND id = #{knowledgeSpaceId}
              AND status <> 'DELETED'
            """)
    int countKnowledgeSpace(
            @Param("tenantId") UUID tenantId,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId);

    @Select("""
            SELECT count(*)
            FROM knowledge_data.text_data_source
            WHERE tenant_id = #{tenantId}
            """)
    int countTenantSources(@Param("tenantId") UUID tenantId);

    @Insert("""
            INSERT INTO knowledge_data.text_data_source (
                tenant_id,
                knowledge_space_id,
                id,
                name,
                status,
                version,
                content_version,
                published_content_version,
                created_by,
                created_at
            ) VALUES (
                #{tenantId},
                #{knowledgeSpaceId},
                #{id},
                #{name},
                #{status},
                #{version},
                #{contentVersion},
                #{publishedContentVersion},
                #{createdBy},
                #{createdAt}
            )
            """)
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

    @Select("""
            SELECT tenant_id::text AS tenant_id,
                   knowledge_space_id::text AS knowledge_space_id,
                   id::text AS id,
                   name,
                   status,
                   version,
                   content_version,
                   published_content_version,
                   created_by::text AS created_by,
                   created_at
            FROM knowledge_data.text_data_source
            WHERE tenant_id = #{tenantId}
              AND knowledge_space_id = #{knowledgeSpaceId}
              AND id = #{id}
            FOR UPDATE
            """)
    @Results(id = "textDataSourceRow", value = {
        @Result(column = "tenant_id", property = "tenantId"),
        @Result(column = "knowledge_space_id", property = "knowledgeSpaceId"),
        @Result(column = "id", property = "id"),
        @Result(column = "name", property = "name"),
        @Result(column = "status", property = "status"),
        @Result(column = "version", property = "version"),
        @Result(column = "content_version", property = "contentVersion"),
        @Result(
                column = "published_content_version",
                property = "publishedContentVersion"),
        @Result(column = "created_by", property = "createdBy"),
        @Result(column = "created_at", property = "createdAt")
    })
    TextDataSourceRow findByIdForUpdate(
            @Param("tenantId") UUID tenantId,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId,
            @Param("id") UUID id);

    @Select("""
            SELECT tenant_id::text AS tenant_id,
                   knowledge_space_id::text AS knowledge_space_id,
                   id::text AS id,
                   name,
                   status,
                   version,
                   content_version,
                   published_content_version,
                   created_by::text AS created_by,
                   created_at
            FROM knowledge_data.text_data_source
            WHERE tenant_id = #{tenantId}
              AND knowledge_space_id = #{knowledgeSpaceId}
              AND id = #{id}
            """)
    @ResultMap("textDataSourceRow")
    TextDataSourceRow findById(
            @Param("tenantId") UUID tenantId,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId,
            @Param("id") UUID id);

    @Select("""
            SELECT tenant_id::text AS tenant_id,
                   knowledge_space_id::text AS knowledge_space_id,
                   id::text AS id,
                   name,
                   status,
                   version,
                   content_version,
                   published_content_version,
                   created_by::text AS created_by,
                   created_at
            FROM knowledge_data.text_data_source
            WHERE tenant_id = #{tenantId}
              AND knowledge_space_id = #{knowledgeSpaceId}
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit}
            """)
    @ResultMap("textDataSourceRow")
    List<TextDataSourceRow> findFirstPage(
            @Param("tenantId") UUID tenantId,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId,
            @Param("limit") int limit);

    @Select("""
            SELECT tenant_id::text AS tenant_id,
                   knowledge_space_id::text AS knowledge_space_id,
                   id::text AS id,
                   name,
                   status,
                   version,
                   content_version,
                   published_content_version,
                   created_by::text AS created_by,
                   created_at
            FROM knowledge_data.text_data_source
            WHERE tenant_id = #{tenantId}
              AND knowledge_space_id = #{knowledgeSpaceId}
              AND (created_at, id) < (#{createdAt}, #{id})
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit}
            """)
    @ResultMap("textDataSourceRow")
    List<TextDataSourceRow> findAfter(
            @Param("tenantId") UUID tenantId,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId,
            @Param("createdAt") Instant createdAt,
            @Param("id") UUID id,
            @Param("limit") int limit);

    @Select("""
            SELECT request_fingerprint,
                   knowledge_space_id::text AS knowledge_space_id,
                   data_source_id::text AS data_source_id,
                   result_status,
                   result_version,
                   result_content_version,
                   result_published_content_version
            FROM knowledge_data.text_data_source_request
            WHERE tenant_id = #{tenantId}
              AND operation = #{operation}
              AND idempotency_key_hash = #{keyHash}
            """)
    @Results({
        @Result(column = "request_fingerprint", property = "requestFingerprint"),
        @Result(column = "knowledge_space_id", property = "knowledgeSpaceId"),
        @Result(column = "data_source_id", property = "dataSourceId"),
        @Result(column = "result_status", property = "resultStatus"),
        @Result(column = "result_version", property = "resultVersion"),
        @Result(column = "result_content_version", property = "resultContentVersion"),
        @Result(
                column = "result_published_content_version",
                property = "resultPublishedContentVersion")
    })
    TextDataSourceRequestRow findRequest(
            @Param("tenantId") UUID tenantId,
            @Param("operation") String operation,
            @Param("keyHash") String keyHash);

    @Select("""
            SELECT version,
                   object_key,
                   sha256,
                   byte_size,
                   media_type,
                   created_at
            FROM knowledge_data.text_data_source_content
            WHERE tenant_id = #{tenantId}
              AND knowledge_space_id = #{knowledgeSpaceId}
              AND data_source_id = #{dataSourceId}
              AND version = #{version}
            """)
    @Results({
        @Result(column = "version", property = "version"),
        @Result(column = "object_key", property = "objectKey"),
        @Result(column = "sha256", property = "sha256"),
        @Result(column = "byte_size", property = "byteSize"),
        @Result(column = "media_type", property = "mediaType"),
        @Result(column = "created_at", property = "createdAt")
    })
    TextContentRow findContent(
            @Param("tenantId") UUID tenantId,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId,
            @Param("dataSourceId") UUID dataSourceId,
            @Param("version") int version);

    @Insert("""
            INSERT INTO knowledge_data.text_data_source_content (
                tenant_id,
                knowledge_space_id,
                data_source_id,
                version,
                object_key,
                sha256,
                byte_size,
                media_type,
                created_at
            ) VALUES (
                #{tenantId},
                #{knowledgeSpaceId},
                #{dataSourceId},
                #{version},
                #{objectKey},
                #{sha256},
                #{byteSize},
                #{mediaType},
                #{createdAt}
            )
            """)
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

    @Update("""
            UPDATE knowledge_data.text_data_source
            SET status = #{status},
                version = #{newVersion},
                content_version = #{contentVersion},
                published_content_version = #{publishedContentVersion}
            WHERE tenant_id = #{tenantId}
              AND knowledge_space_id = #{knowledgeSpaceId}
              AND id = #{id}
              AND version = #{expectedVersion}
            """)
    int updateSource(
            @Param("tenantId") UUID tenantId,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId,
            @Param("id") UUID id,
            @Param("status") String status,
            @Param("newVersion") long newVersion,
            @Param("contentVersion") int contentVersion,
            @Param("publishedContentVersion") int publishedContentVersion,
            @Param("expectedVersion") long expectedVersion);

    @Insert("""
            INSERT INTO knowledge_data.text_data_source_request (
                tenant_id,
                operation,
                idempotency_key_hash,
                request_fingerprint,
                knowledge_space_id,
                data_source_id,
                result_status,
                result_version,
                result_content_version,
                result_published_content_version,
                created_at
            ) VALUES (
                #{tenantId},
                #{operation},
                #{keyHash},
                #{requestFingerprint},
                #{knowledgeSpaceId},
                #{dataSourceId},
                #{resultStatus},
                #{resultVersion},
                #{resultContentVersion},
                #{resultPublishedContentVersion},
                #{createdAt}
            )
            """)
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

    @Insert("""
            INSERT INTO knowledge_data.outbox_event (
                tenant_id,
                event_id,
                aggregate_type,
                aggregate_id,
                event_type,
                payload,
                occurred_at
            ) VALUES (
                #{tenantId},
                #{eventId},
                'TEXT_DATA_SOURCE',
                #{aggregateId},
                #{eventType},
                CAST(#{payload} AS JSONB),
                #{occurredAt}
            )
            """)
    int insertOutboxEvent(
            @Param("tenantId") UUID tenantId,
            @Param("eventId") UUID eventId,
            @Param("aggregateId") UUID aggregateId,
            @Param("eventType") String eventType,
            @Param("payload") String payload,
            @Param("occurredAt") Instant occurredAt);
}
