package com.aiplatform.foundation.modelruntime.adapter.out.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 供应商连接 MyBatis-Plus 映射器。
 */
public interface ProviderConnectionMapper extends BaseMapper<ProviderConnectionEntity> {

    String lockTenant(@Param("tenantId") String tenantId);

    ProviderConnectionRequestRow findRequest(
            @Param("tenantId") long tenantId,
            @Param("keyHash") String keyHash);

    int countActive(@Param("tenantId") long tenantId);

    int insertConnection(
            @Param("tenantId") long tenantId,
            @Param("id") long id,
            @Param("providerKind") String providerKind,
            @Param("displayName") String displayName,
            @Param("endpoint") String endpoint,
            @Param("region") String region,
            @Param("authenticationType") String authenticationType,
            @Param("credentialReference") String credentialReference,
            @Param("status") String status,
            @Param("version") long version,
            @Param("lastProbeSucceeded") Boolean lastProbeSucceeded,
            @Param("lastProbeCode") String lastProbeCode,
            @Param("lastProbedAt") Instant lastProbedAt,
            @Param("createdAt") Instant createdAt);

    int insertRequest(
            @Param("tenantId") long tenantId,
            @Param("keyHash") String keyHash,
            @Param("requestFingerprint") String requestFingerprint,
            @Param("providerConnectionId") long providerConnectionId,
            @Param("createdAt") Instant createdAt);

    ProviderConnectionRow findById(
            @Param("tenantId") long tenantId,
            @Param("id") long id);

    List<ProviderConnectionRow> findFirstPage(
            @Param("tenantId") long tenantId,
            @Param("limit") int limit);

    List<ProviderConnectionRow> findAfter(
            @Param("tenantId") long tenantId,
            @Param("createdAt") Instant createdAt,
            @Param("id") long id,
            @Param("limit") int limit);

    int updateProbeFact(
            @Param("tenantId") long tenantId,
            @Param("id") long id,
            @Param("status") String status,
            @Param("nextVersion") long nextVersion,
            @Param("lastProbeSucceeded") Boolean lastProbeSucceeded,
            @Param("lastProbeCode") String lastProbeCode,
            @Param("lastProbedAt") Instant lastProbedAt,
            @Param("expectedVersion") long expectedVersion);
}
