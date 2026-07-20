package com.aiplatform.appstudio.domain.port;

import com.aiplatform.appstudio.domain.AiApplication;
import com.aiplatform.shared.kernel.TenantId;
import java.util.List;
import java.util.Optional;

/**
 * 应用仓储端口，负责 AI 应用聚合的保存、查询与新编号分配。
 */
public interface AiApplicationRepository {
    String nextApplicationId();

    void acquireMutationLock(TenantId tenantId, String applicationId);

    AiApplication save(AiApplication application);

    Optional<AiApplication> findById(TenantId tenantId, String applicationId);

    List<AiApplication> findCatalogWindowByTenant(TenantId tenantId, int limit);

    List<AiApplication> findAllByTenant(TenantId tenantId);

    List<AiApplication> findVisibleApplications(TenantId tenantId, String principalId);
}
