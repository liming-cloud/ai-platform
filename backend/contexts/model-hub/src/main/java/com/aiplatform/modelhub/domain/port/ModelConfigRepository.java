package com.aiplatform.modelhub.domain.port;

import com.aiplatform.modelhub.domain.ModelConfig;
import com.aiplatform.modelhub.domain.ModelConfigStatus;
import com.aiplatform.shared.kernel.TenantId;
import java.util.List;
import java.util.Optional;

/**
 * 模型配置仓储端口，所有查询必须以租户和供应商作为隔离条件。
 */
public interface ModelConfigRepository {
    String nextModelConfigId();

    ModelConfig insert(ModelConfig config);

    Optional<ModelConfig> compareAndSet(
            ModelConfig updatedConfig,
            int expectedVersion,
            ModelConfigStatus expectedStatus
    );

    Optional<ModelConfig> compareAndSetApprovalDecision(
            ModelConfig decidedConfig,
            int expectedVersion,
            ModelConfigStatus expectedStatus,
            String expectedApprovalRequestId,
            String expectedEvidenceDigest,
            String expectedTargetDigest
    );

    Optional<ModelConfig> findById(TenantId tenantId, String providerId, String modelConfigId);

    Optional<ModelConfig> findByModelConfigId(TenantId tenantId, String modelConfigId);

    Optional<ModelConfig> findByProviderAndCode(TenantId tenantId, String providerId, String modelCode);

    List<ModelConfig> findByProviderId(TenantId tenantId, String providerId);
}
