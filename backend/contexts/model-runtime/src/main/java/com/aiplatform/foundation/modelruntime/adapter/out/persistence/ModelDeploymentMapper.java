package com.aiplatform.foundation.modelruntime.adapter.out.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 模型部署 MyBatis-Plus 映射器。
 */
public interface ModelDeploymentMapper extends BaseMapper<ModelDeploymentEntity> {

    String lockTenant(@Param("tenantId") String tenantId);

    ModelDeploymentRequestRow findRequest(
            @Param("tenantId") long tenantId,
            @Param("keyHash") String keyHash);

    int countAll(@Param("tenantId") long tenantId);

    int insertDeployment(
            @Param("tenantId") long tenantId,
            @Param("id") long id,
            @Param("providerConnectionId") long providerConnectionId,
            @Param("providerConnectionRevision") long providerConnectionRevision,
            @Param("modelCode") String modelCode,
            @Param("displayName") String displayName,
            @Param("modelType") String modelType,
            @Param("capabilities") String capabilities,
            @Param("contextWindow") int contextWindow,
            @Param("outputLimit") Integer outputLimit,
            @Param("embeddingDimension") Integer embeddingDimension,
            @Param("status") String status,
            @Param("version") long version,
            @Param("createdAt") Instant createdAt);

    int insertRequest(
            @Param("tenantId") long tenantId,
            @Param("keyHash") String keyHash,
            @Param("requestFingerprint") String requestFingerprint,
            @Param("modelDeploymentId") long modelDeploymentId,
            @Param("createdAt") Instant createdAt);

    ModelDeploymentRow findById(
            @Param("tenantId") long tenantId,
            @Param("id") long id);

    List<ModelDeploymentRow> findFirstPage(
            @Param("tenantId") long tenantId,
            @Param("limit") int limit);

    List<ModelDeploymentRow> findAfter(
            @Param("tenantId") long tenantId,
            @Param("createdAt") Instant createdAt,
            @Param("id") long id,
            @Param("limit") int limit);

    int updateStatus(
            @Param("tenantId") long tenantId,
            @Param("id") long id,
            @Param("status") String status,
            @Param("nextVersion") long nextVersion,
            @Param("expectedVersion") long expectedVersion);
}
