package com.aiplatform.modelhub.domain.port;

import com.aiplatform.modelhub.domain.ModelProvider;
import com.aiplatform.modelhub.domain.ModelProviderStatus;
import com.aiplatform.shared.kernel.TenantId;
import java.util.List;
import java.util.Optional;

/**
 * 模型供应商仓储端口，负责供应商聚合的保存、审批回写与查询投影装配。
 */
public interface ModelProviderRepository {
    String nextProviderId();

    ModelProvider insert(ModelProvider provider);

    Optional<ModelProvider> compareAndSet(
            ModelProvider updatedProvider,
            int expectedVersion,
            ModelProviderStatus expectedStatus
    );

    Optional<ModelProvider> compareAndSetApprovalDecision(
            ModelProvider decidedProvider,
            int expectedVersion,
            ModelProviderStatus expectedStatus,
            String expectedApprovalRequestId,
            String expectedEvidenceDigest,
            String expectedTargetDigest
    );

    Optional<ModelProvider> findById(TenantId tenantId, String providerId);

    List<ModelProvider> findAll(TenantId tenantId);
}
