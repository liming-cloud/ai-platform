package com.aiplatform.foundation.modelruntime.adapter.out.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 供应商连接 MyBatis-Plus 映射器。
 */
public interface ProviderConnectionMapper extends BaseMapper<ProviderConnectionEntity> {

    @Select("""
            SELECT pg_advisory_xact_lock(hashtextextended(#{tenantId}, 20260728))
            """)
    String lockTenant(@Param("tenantId") String tenantId);

    @Select("""
            SELECT request_fingerprint,
                   provider_connection_id::text AS provider_connection_id
            FROM model_runtime.provider_connection_request
            WHERE tenant_id = #{tenantId}
              AND idempotency_key_hash = #{keyHash}
            """)
    @Results({
        @Result(column = "request_fingerprint", property = "requestFingerprint"),
        @Result(column = "provider_connection_id", property = "providerConnectionId")
    })
    ProviderConnectionRequestRow findRequest(
            @Param("tenantId") UUID tenantId,
            @Param("keyHash") String keyHash);

    @Select("""
            SELECT count(*)
            FROM model_runtime.provider_connection
            WHERE tenant_id = #{tenantId}
              AND status <> 'DISABLED'
            """)
    int countActive(@Param("tenantId") UUID tenantId);

    @Insert("""
            INSERT INTO model_runtime.provider_connection (
                tenant_id, id, provider_kind, display_name, endpoint, region,
                authentication_type, credential_reference, status, version,
                last_probe_succeeded, last_probe_code, last_probed_at, created_at
            ) VALUES (
                #{tenantId}, #{id}, #{providerKind}, #{displayName}, #{endpoint}, #{region},
                #{authenticationType}, #{credentialReference}, #{status}, #{version},
                #{lastProbeSucceeded}, #{lastProbeCode}, #{lastProbedAt}, #{createdAt}
            )
            """)
    int insertConnection(
            @Param("tenantId") UUID tenantId,
            @Param("id") UUID id,
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

    @Insert("""
            INSERT INTO model_runtime.provider_connection_request (
                tenant_id, idempotency_key_hash, request_fingerprint,
                provider_connection_id, created_at
            ) VALUES (
                #{tenantId}, #{keyHash}, #{requestFingerprint},
                #{providerConnectionId}, #{createdAt}
            )
            """)
    int insertRequest(
            @Param("tenantId") UUID tenantId,
            @Param("keyHash") String keyHash,
            @Param("requestFingerprint") String requestFingerprint,
            @Param("providerConnectionId") UUID providerConnectionId,
            @Param("createdAt") Instant createdAt);

    @Select("""
            SELECT tenant_id::text AS tenant_id,
                   id::text AS id,
                   provider_kind,
                   display_name,
                   endpoint,
                   region,
                   authentication_type,
                   credential_reference,
                   status,
                   version,
                   last_probe_succeeded,
                   last_probe_code,
                   last_probed_at,
                   created_at
            FROM model_runtime.provider_connection
            WHERE tenant_id = #{tenantId}
              AND id = #{id}
            """)
    @Results(id = "providerConnectionRow", value = {
        @Result(column = "tenant_id", property = "tenantId"),
        @Result(column = "id", property = "id"),
        @Result(column = "provider_kind", property = "providerKind"),
        @Result(column = "display_name", property = "displayName"),
        @Result(column = "endpoint", property = "endpoint"),
        @Result(column = "region", property = "region"),
        @Result(column = "authentication_type", property = "authenticationType"),
        @Result(column = "credential_reference", property = "credentialReference"),
        @Result(column = "status", property = "status"),
        @Result(column = "version", property = "version"),
        @Result(column = "last_probe_succeeded", property = "lastProbeSucceeded"),
        @Result(column = "last_probe_code", property = "lastProbeCode"),
        @Result(column = "last_probed_at", property = "lastProbedAt"),
        @Result(column = "created_at", property = "createdAt")
    })
    ProviderConnectionRow findById(
            @Param("tenantId") UUID tenantId,
            @Param("id") UUID id);

    @Select("""
            SELECT tenant_id::text AS tenant_id,
                   id::text AS id,
                   provider_kind,
                   display_name,
                   endpoint,
                   region,
                   authentication_type,
                   credential_reference,
                   status,
                   version,
                   last_probe_succeeded,
                   last_probe_code,
                   last_probed_at,
                   created_at
            FROM model_runtime.provider_connection
            WHERE tenant_id = #{tenantId}
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit}
            """)
    @Results(value = {
        @Result(column = "tenant_id", property = "tenantId"),
        @Result(column = "id", property = "id"),
        @Result(column = "provider_kind", property = "providerKind"),
        @Result(column = "display_name", property = "displayName"),
        @Result(column = "endpoint", property = "endpoint"),
        @Result(column = "region", property = "region"),
        @Result(column = "authentication_type", property = "authenticationType"),
        @Result(column = "credential_reference", property = "credentialReference"),
        @Result(column = "status", property = "status"),
        @Result(column = "version", property = "version"),
        @Result(column = "last_probe_succeeded", property = "lastProbeSucceeded"),
        @Result(column = "last_probe_code", property = "lastProbeCode"),
        @Result(column = "last_probed_at", property = "lastProbedAt"),
        @Result(column = "created_at", property = "createdAt")
    })
    List<ProviderConnectionRow> findFirstPage(
            @Param("tenantId") UUID tenantId,
            @Param("limit") int limit);

    @Select("""
            SELECT tenant_id::text AS tenant_id,
                   id::text AS id,
                   provider_kind,
                   display_name,
                   endpoint,
                   region,
                   authentication_type,
                   credential_reference,
                   status,
                   version,
                   last_probe_succeeded,
                   last_probe_code,
                   last_probed_at,
                   created_at
            FROM model_runtime.provider_connection
            WHERE tenant_id = #{tenantId}
              AND (created_at, id) < (#{createdAt}, #{id})
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit}
            """)
    @Results(value = {
        @Result(column = "tenant_id", property = "tenantId"),
        @Result(column = "id", property = "id"),
        @Result(column = "provider_kind", property = "providerKind"),
        @Result(column = "display_name", property = "displayName"),
        @Result(column = "endpoint", property = "endpoint"),
        @Result(column = "region", property = "region"),
        @Result(column = "authentication_type", property = "authenticationType"),
        @Result(column = "credential_reference", property = "credentialReference"),
        @Result(column = "status", property = "status"),
        @Result(column = "version", property = "version"),
        @Result(column = "last_probe_succeeded", property = "lastProbeSucceeded"),
        @Result(column = "last_probe_code", property = "lastProbeCode"),
        @Result(column = "last_probed_at", property = "lastProbedAt"),
        @Result(column = "created_at", property = "createdAt")
    })
    List<ProviderConnectionRow> findAfter(
            @Param("tenantId") UUID tenantId,
            @Param("createdAt") Instant createdAt,
            @Param("id") UUID id,
            @Param("limit") int limit);

    @Update("""
            UPDATE model_runtime.provider_connection
            SET status = #{status},
                version = #{nextVersion},
                last_probe_succeeded = #{lastProbeSucceeded},
                last_probe_code = #{lastProbeCode},
                last_probed_at = #{lastProbedAt}
            WHERE tenant_id = #{tenantId}
              AND id = #{id}
              AND version = #{expectedVersion}
            """)
    int updateProbeFact(
            @Param("tenantId") UUID tenantId,
            @Param("id") UUID id,
            @Param("status") String status,
            @Param("nextVersion") long nextVersion,
            @Param("lastProbeSucceeded") Boolean lastProbeSucceeded,
            @Param("lastProbeCode") String lastProbeCode,
            @Param("lastProbedAt") Instant lastProbedAt,
            @Param("expectedVersion") long expectedVersion);
}
