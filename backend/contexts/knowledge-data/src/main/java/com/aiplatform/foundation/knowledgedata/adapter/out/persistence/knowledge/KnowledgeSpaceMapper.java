package com.aiplatform.foundation.knowledgedata.adapter.out.persistence.knowledge;

import com.aiplatform.foundation.knowledgedata.adapter.out.persistence.datasource.IdempotencyRow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.Instant;
import java.util.List;
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
            @Param("id") long id,
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
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("createdAt") Instant createdAt);

    KnowledgeSpaceRow findById(
            @Param("tenantId") long tenantId,
            @Param("id") long id);

    List<KnowledgeSpaceRow> findFirstPage(
            @Param("tenantId") long tenantId,
            @Param("limit") int limit);

    List<KnowledgeSpaceRow> findAfter(
            @Param("tenantId") long tenantId,
            @Param("createdAt") Instant createdAt,
            @Param("id") long id,
            @Param("limit") int limit);
}
