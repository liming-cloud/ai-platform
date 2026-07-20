package com.aiplatform.identity.domain.port;

import com.aiplatform.identity.domain.delegation.DelegatedTenantAction;
import com.aiplatform.shared.kernel.TenantId;
import java.util.Optional;

/**
 * 平台代管租户动作仓储，提供幂等查询和追加写入。
 */
public interface DelegatedTenantActionRepository {
    Optional<DelegatedTenantAction> findByRequestHash(TenantId tenantId, String operatorId, String requestHash);

    DelegatedTenantAction save(DelegatedTenantAction action);
}
