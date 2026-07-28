package com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration;

import java.time.Instant;
import org.apache.ibatis.annotations.Param;

/**
 * 受控目录与模型配置的持久化映射器。
 */
public interface ModelConfigurationMapper {

    CatalogRow findCatalogEntry(@Param("id") long id);

    void insertCatalogEntry(
            @Param("id") long id,
            @Param("providerKind") String providerKind,
            @Param("endpoint") String endpoint,
            @Param("modelCode") String modelCode,
            @Param("modelType") String modelType,
            @Param("contextWindow") int contextWindow,
            @Param("outputLimit") Integer outputLimit,
            @Param("embeddingDimension") Integer embeddingDimension,
            @Param("status") String status,
            @Param("version") long version,
            @Param("createdAt") Instant createdAt);

    boolean updateCatalogEntry(
            @Param("id") long id,
            @Param("status") String status,
            @Param("version") long version,
            @Param("expectedVersion") long expectedVersion);

    ConfigurationRow findConfiguration(@Param("id") long id);

    boolean updateConfiguration(
            @Param("id") long id,
            @Param("status") String status,
            @Param("version") long version,
            @Param("expectedVersion") long expectedVersion);

    void insertConfiguration(
            @Param("id") long id,
            @Param("scope") String scope,
            @Param("tenantId") Long tenantId,
            @Param("ownerMembershipId") Long ownerMembershipId,
            @Param("displayName") String displayName,
            @Param("catalogEntryId") long catalogEntryId,
            @Param("status") String status,
            @Param("version") long version,
            @Param("createdAt") Instant createdAt);

    record CatalogRow(
            long id, String providerKind, String endpoint, String modelCode, String modelType,
            Integer contextWindow, Integer outputLimit, Integer embeddingDimension,
            String status, long version, Instant createdAt) {
    }

    record ConfigurationRow(
            long id, String scope, Long tenantId, Long ownerMembershipId, String displayName,
            long catalogEntryId, String status, long version, Instant createdAt) {
    }
}
