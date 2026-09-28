package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out;

import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantFeatureSettings;
import java.util.Optional;

/** 租户功能版本事实的读写端口；条件更新失败表示并发冲突。 */
public interface TenantFeatureSettingsStore {
    Optional<TenantFeatureSettings> find(long tenantId);
    boolean replace(TenantFeatureSettings next, long expectedVersion);
}
