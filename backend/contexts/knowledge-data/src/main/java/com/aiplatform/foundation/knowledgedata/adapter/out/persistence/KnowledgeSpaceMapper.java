package com.aiplatform.foundation.knowledgedata.adapter.out.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Param;

/**
 * 知识空间的 MyBatis-Plus 映射器。
 */
public interface KnowledgeSpaceMapper extends BaseMapper<KnowledgeSpaceEntity> {

    String lockTenant(@Param("tenantId") String tenantId);

    IdempotencyRow findRequest(
            @Param("tenantId") long tenantId,
            @Param("keyHash") String keyHash);

    int countActive(@Param("tenantId") long tenantId);

    int insertSpace(
            @Param("tenantId") long tenantId,
            @Param("id") UUID id,
            @Param("name") String name,
            @Param("description") String description,
            @Param("status") String status,
            @Param("version") long version,
            @Param("createdBy") long createdBy,
            @Param("createdAt") Instant createdAt);

    int insertRequest(
            @Param("tenantId") long tenantId,
            @Param("keyHash") String keyHash,
            @Param("requestFingerprint") String requestFingerprint,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId,
            @Param("createdAt") Instant createdAt);

    KnowledgeSpaceRow findById(
            @Param("tenantId") long tenantId,
            @Param("id") UUID id);

    List<KnowledgeSpaceRow> findFirstPage(
            @Param("tenantId") long tenantId,
            @Param("limit") int limit);

    List<KnowledgeSpaceRow> findAfter(
            @Param("tenantId") long tenantId,
            @Param("createdAt") Instant createdAt,
            @Param("id") UUID id,
            @Param("limit") int limit);
}
