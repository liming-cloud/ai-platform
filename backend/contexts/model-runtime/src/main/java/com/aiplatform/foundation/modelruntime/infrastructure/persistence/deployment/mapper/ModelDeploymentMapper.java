package com.aiplatform.foundation.modelruntime.infrastructure.persistence.deployment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.deployment.entity.ModelDeploymentEntity;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.deployment.projection.ModelDeploymentRow;
import java.time.Instant;
import org.apache.ibatis.annotations.Param;

/**
 * 模型部署 MyBatis-Plus 映射器。
 */
public interface ModelDeploymentMapper extends BaseMapper<ModelDeploymentEntity> {

    String lockTenant(@Param("tenantId") String tenantId);

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

    ModelDeploymentRow findById(
            @Param("tenantId") long tenantId,
            @Param("id") long id);

    ModelDeploymentRow lockById(
            @Param("tenantId") long tenantId,
            @Param("id") long id);

    int markVerified(
            @Param("tenantId") long tenantId,
            @Param("id") long id,
            @Param("verificationId") long verificationId,
            @Param("verificationExpiresAt") Instant verificationExpiresAt,
            @Param("version") long version,
            @Param("expectedVersion") long expectedVersion);

    IPage<ModelDeploymentRow> findPage(
            IPage<?> page,
            @Param("tenantId") long tenantId);

}
