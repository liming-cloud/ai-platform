package com.aiplatform.foundation.modelruntime.domain.provider.repository;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionId;
import java.util.Optional;

/**
 * 供应商连接持久化端口。
 */
public interface ProviderConnectionRepository {

    ProviderConnectionCreationOutcome create(
            ProviderConnectionCreation creation,
            int tenantLimit);

    Optional<ProviderConnection> findById(
            TenantId tenantId,
            ProviderConnectionId connectionId);

    ProviderConnectionPage findPage(
            TenantId tenantId,
            long pageNumber,
            long pageSize);

    boolean update(ProviderConnection connection, long expectedVersion);
}
