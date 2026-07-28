package com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration;

import java.time.Instant;
import org.apache.ibatis.annotations.Param;

/**
 * 模型配置授权事实的 MyBatis 映射器。
 */
public interface ModelConfigurationAccessMapper {

    GrantRow findById(@Param("id") long id);

    boolean hasActiveGrant(
            @Param("modelConfigurationId") long modelConfigurationId,
            @Param("tenantId") long tenantId,
            @Param("membershipId") long membershipId);

    void insert(
            @Param("id") long id,
            @Param("modelConfigurationId") long modelConfigurationId,
            @Param("tenantId") long tenantId,
            @Param("membershipId") Long membershipId,
            @Param("status") String status,
            @Param("version") long version,
            @Param("createdAt") Instant createdAt);

    boolean revoke(
            @Param("id") long id,
            @Param("version") long version,
            @Param("expectedVersion") long expectedVersion,
            @Param("revokedAt") Instant revokedAt);

    record GrantRow(
            long id, long modelConfigurationId, long tenantId, Long membershipId,
            String status, long version, Instant createdAt, Instant revokedAt) {
    }
}
