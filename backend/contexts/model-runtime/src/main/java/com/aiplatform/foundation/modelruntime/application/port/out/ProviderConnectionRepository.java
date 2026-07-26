package com.aiplatform.foundation.modelruntime.application.port.out;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.domain.model.ProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.model.ProviderConnectionId;
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

    ProviderConnectionSlice findPage(TenantId tenantId, String cursor, int limit);

    boolean update(ProviderConnection connection, long expectedVersion);
}
