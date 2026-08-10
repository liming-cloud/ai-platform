package com.aiplatform.foundation.modelruntime.infrastructure.persistence.deployment.mapper;

import java.time.Instant;
import org.apache.ibatis.annotations.Param;

/**
 * 模型能力验证事实的只写 MyBatis 映射器。
 */
public interface ModelCapabilityVerificationMapper {

    int insert(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("deploymentId") long deploymentId,
            @Param("deploymentVersion") long deploymentVersion,
            @Param("providerConnectionId") long providerConnectionId,
            @Param("providerConnectionRevision") long providerConnectionRevision,
            @Param("modelCode") String modelCode,
            @Param("declaredCapabilities") String declaredCapabilities,
            @Param("observedCapabilities") String observedCapabilities,
            @Param("succeeded") boolean succeeded,
            @Param("resultCode") String resultCode,
            @Param("elapsedMillis") long elapsedMillis,
            @Param("probedAt") Instant probedAt,
            @Param("expiresAt") Instant expiresAt);
}
