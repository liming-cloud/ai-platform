package com.aiplatform.foundation.knowledgedata.adapter.out.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

/**
 * 知识空间的 MyBatis-Plus 映射器。
 */
public interface KnowledgeSpaceMapper extends BaseMapper<KnowledgeSpaceEntity> {

    @Select("""
            SELECT pg_advisory_xact_lock(hashtextextended(#{tenantId}, 20260727))
            """)
    String lockTenant(@Param("tenantId") String tenantId);

    @Select("""
            SELECT request_fingerprint,
                   knowledge_space_id::text AS knowledge_space_id
            FROM knowledge_data.knowledge_space_request
            WHERE tenant_id = #{tenantId}
              AND idempotency_key_hash = #{keyHash}
            """)
    @Results({
        @Result(column = "request_fingerprint", property = "requestFingerprint"),
        @Result(column = "knowledge_space_id", property = "knowledgeSpaceId")
    })
    IdempotencyRow findRequest(
            @Param("tenantId") UUID tenantId,
            @Param("keyHash") String keyHash);

    @Select("""
            SELECT count(*)
            FROM knowledge_data.knowledge_space
            WHERE tenant_id = #{tenantId}
              AND status <> 'DELETED'
            """)
    int countActive(@Param("tenantId") UUID tenantId);

    @Insert("""
            INSERT INTO knowledge_data.knowledge_space (
                tenant_id, id, name, description, status, version, created_by, created_at
            ) VALUES (
                #{tenantId}, #{id}, #{name}, #{description},
                #{status}, #{version}, #{createdBy}, #{createdAt}
            )
            """)
    int insertSpace(
            @Param("tenantId") UUID tenantId,
            @Param("id") UUID id,
            @Param("name") String name,
            @Param("description") String description,
            @Param("status") String status,
            @Param("version") long version,
            @Param("createdBy") UUID createdBy,
            @Param("createdAt") Instant createdAt);

    @Insert("""
            INSERT INTO knowledge_data.knowledge_space_request (
                tenant_id, idempotency_key_hash, request_fingerprint,
                knowledge_space_id, created_at
            ) VALUES (
                #{tenantId}, #{keyHash}, #{requestFingerprint},
                #{knowledgeSpaceId}, #{createdAt}
            )
            """)
    int insertRequest(
            @Param("tenantId") UUID tenantId,
            @Param("keyHash") String keyHash,
            @Param("requestFingerprint") String requestFingerprint,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId,
            @Param("createdAt") Instant createdAt);

    @Select("""
            SELECT tenant_id::text AS tenant_id,
                   id::text AS id,
                   name,
                   description,
                   status,
                   version,
                   created_by::text AS created_by,
                   created_at
            FROM knowledge_data.knowledge_space
            WHERE tenant_id = #{tenantId}
              AND id = #{id}
            """)
    @Results(id = "knowledgeSpaceRow", value = {
        @Result(column = "tenant_id", property = "tenantId"),
        @Result(column = "id", property = "id"),
        @Result(column = "name", property = "name"),
        @Result(column = "description", property = "description"),
        @Result(column = "status", property = "status"),
        @Result(column = "version", property = "version"),
        @Result(column = "created_by", property = "createdBy"),
        @Result(column = "created_at", property = "createdAt")
    })
    KnowledgeSpaceRow findById(
            @Param("tenantId") UUID tenantId,
            @Param("id") UUID id);

    @Select("""
            SELECT tenant_id::text AS tenant_id,
                   id::text AS id,
                   name,
                   description,
                   status,
                   version,
                   created_by::text AS created_by,
                   created_at
            FROM knowledge_data.knowledge_space
            WHERE tenant_id = #{tenantId}
              AND status <> 'DELETED'
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit}
            """)
    @Results(value = {
        @Result(column = "tenant_id", property = "tenantId"),
        @Result(column = "id", property = "id"),
        @Result(column = "name", property = "name"),
        @Result(column = "description", property = "description"),
        @Result(column = "status", property = "status"),
        @Result(column = "version", property = "version"),
        @Result(column = "created_by", property = "createdBy"),
        @Result(column = "created_at", property = "createdAt")
    })
    List<KnowledgeSpaceRow> findFirstPage(
            @Param("tenantId") UUID tenantId,
            @Param("limit") int limit);

    @Select("""
            SELECT tenant_id::text AS tenant_id,
                   id::text AS id,
                   name,
                   description,
                   status,
                   version,
                   created_by::text AS created_by,
                   created_at
            FROM knowledge_data.knowledge_space
            WHERE tenant_id = #{tenantId}
              AND status <> 'DELETED'
              AND (created_at, id) < (#{createdAt}, #{id})
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit}
            """)
    @Results(value = {
        @Result(column = "tenant_id", property = "tenantId"),
        @Result(column = "id", property = "id"),
        @Result(column = "name", property = "name"),
        @Result(column = "description", property = "description"),
        @Result(column = "status", property = "status"),
        @Result(column = "version", property = "version"),
        @Result(column = "created_by", property = "createdBy"),
        @Result(column = "created_at", property = "createdAt")
    })
    List<KnowledgeSpaceRow> findAfter(
            @Param("tenantId") UUID tenantId,
            @Param("createdAt") Instant createdAt,
            @Param("id") UUID id,
            @Param("limit") int limit);
}
