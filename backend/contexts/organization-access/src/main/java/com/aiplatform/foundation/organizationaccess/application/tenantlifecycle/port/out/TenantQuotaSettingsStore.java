package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out;

import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantQuota;
import java.util.Optional;

/** 租户配额版本事实的原子读写端口；条件更新失败必须视为并发冲突。 */
public interface TenantQuotaSettingsStore {

    Optional<TenantQuota> find(long tenantId);

    boolean replace(TenantQuota next, long expectedVersion);
}
