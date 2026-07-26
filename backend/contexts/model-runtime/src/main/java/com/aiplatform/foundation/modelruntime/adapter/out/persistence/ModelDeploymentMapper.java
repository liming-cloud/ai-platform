package com.aiplatform.foundation.modelruntime.adapter.out.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 模型部署 MyBatis-Plus 映射器。
 */
public interface ModelDeploymentMapper extends BaseMapper<ModelDeploymentEntity> {

    @Select("""
            SELECT pg_advisory_xact_lock(hashtextextended(#{tenantId}, 20260729))
            """)
    String lockTenant(@Param("tenantId") String tenantId);

    @Select("""
            SELECT request_fingerprint,
                   model_deployment_id::text AS model_deployment_id
            FROM model_runtime.model_deployment_request
            WHERE tenant_id = #{tenantId}
              AND idempotency_key_hash = #{keyHash}
            """)
    @Results({
        @Result(column = "request_fingerprint", property = "requestFingerprint"),
        @Result(column = "model_deployment_id", property = "modelDeploymentId")
    })
    ModelDeploymentRequestRow findRequest(
            @Param("tenantId") UUID tenantId,
            @Param("keyHash") String keyHash);

    @Select("""
            SELECT count(*)
            FROM model_runtime.model_deployment
            WHERE tenant_id = #{tenantId}
            """)
    int countAll(@Param("tenantId") UUID tenantId);

    @Insert("""
            INSERT INTO model_runtime.model_deployment (
                tenant_id, id, provider_connection_id, provider_connection_revision,
                model_code, display_name, model_type, capabilities, context_window,
                output_limit, embedding_dimension, status, version, created_at
            ) VALUES (
                #{tenantId}, #{id}, #{providerConnectionId}, #{providerConnectionRevision},
                #{modelCode}, #{displayName}, #{modelType},
                string_to_array(#{capabilities}, ','), #{contextWindow},
                #{outputLimit}, #{embeddingDimension}, #{status}, #{version}, #{createdAt}
            )
            """)
    int insertDeployment(
            @Param("tenantId") UUID tenantId,
            @Param("id") UUID id,
            @Param("providerConnectionId") UUID providerConnectionId,
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

    @Insert("""
            INSERT INTO model_runtime.model_deployment_request (
                tenant_id, idempotency_key_hash, request_fingerprint,
                model_deployment_id, created_at
            ) VALUES (
                #{tenantId}, #{keyHash}, #{requestFingerprint},
                #{modelDeploymentId}, #{createdAt}
            )
            """)
    int insertRequest(
            @Param("tenantId") UUID tenantId,
            @Param("keyHash") String keyHash,
            @Param("requestFingerprint") String requestFingerprint,
            @Param("modelDeploymentId") UUID modelDeploymentId,
            @Param("createdAt") Instant createdAt);

    @Select("""
            SELECT tenant_id::text AS tenant_id,
                   id::text AS id,
                   provider_connection_id::text AS provider_connection_id,
                   provider_connection_revision,
                   model_code,
                   display_name,
                   model_type,
                   array_to_string(capabilities, ',') AS capabilities,
                   context_window,
                   output_limit,
                   embedding_dimension,
                   status,
                   version,
                   created_at
            FROM model_runtime.model_deployment
            WHERE tenant_id = #{tenantId}
              AND id = #{id}
            """)
    @Results(id = "modelDeploymentRow", value = {
        @Result(column = "tenant_id", property = "tenantId"),
        @Result(column = "id", property = "id"),
        @Result(column = "provider_connection_id", property = "providerConnectionId"),
        @Result(column = "provider_connection_revision", property = "providerConnectionRevision"),
        @Result(column = "model_code", property = "modelCode"),
        @Result(column = "display_name", property = "displayName"),
        @Result(column = "model_type", property = "modelType"),
        @Result(column = "capabilities", property = "capabilities"),
        @Result(column = "context_window", property = "contextWindow"),
        @Result(column = "output_limit", property = "outputLimit"),
        @Result(column = "embedding_dimension", property = "embeddingDimension"),
        @Result(column = "status", property = "status"),
        @Result(column = "version", property = "version"),
        @Result(column = "created_at", property = "createdAt")
    })
    ModelDeploymentRow findById(
            @Param("tenantId") UUID tenantId,
            @Param("id") UUID id);

    @Select("""
            SELECT tenant_id::text AS tenant_id,
                   id::text AS id,
                   provider_connection_id::text AS provider_connection_id,
                   provider_connection_revision,
                   model_code,
                   display_name,
                   model_type,
                   array_to_string(capabilities, ',') AS capabilities,
                   context_window,
                   output_limit,
                   embedding_dimension,
                   status,
                   version,
                   created_at
            FROM model_runtime.model_deployment
            WHERE tenant_id = #{tenantId}
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit}
            """)
    @ResultMap("modelDeploymentRow")
    List<ModelDeploymentRow> findFirstPage(
            @Param("tenantId") UUID tenantId,
            @Param("limit") int limit);

    @Select("""
            SELECT tenant_id::text AS tenant_id,
                   id::text AS id,
                   provider_connection_id::text AS provider_connection_id,
                   provider_connection_revision,
                   model_code,
                   display_name,
                   model_type,
                   array_to_string(capabilities, ',') AS capabilities,
                   context_window,
                   output_limit,
                   embedding_dimension,
                   status,
                   version,
                   created_at
            FROM model_runtime.model_deployment
            WHERE tenant_id = #{tenantId}
              AND (created_at, id) < (#{createdAt}, #{id})
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit}
            """)
    @ResultMap("modelDeploymentRow")
    List<ModelDeploymentRow> findAfter(
            @Param("tenantId") UUID tenantId,
            @Param("createdAt") Instant createdAt,
            @Param("id") UUID id,
            @Param("limit") int limit);

    @Update("""
            UPDATE model_runtime.model_deployment
            SET status = #{status},
                version = #{nextVersion}
            WHERE tenant_id = #{tenantId}
              AND id = #{id}
              AND version = #{expectedVersion}
            """)
    int updateStatus(
            @Param("tenantId") UUID tenantId,
            @Param("id") UUID id,
            @Param("status") String status,
            @Param("nextVersion") long nextVersion,
            @Param("expectedVersion") long expectedVersion);
}
