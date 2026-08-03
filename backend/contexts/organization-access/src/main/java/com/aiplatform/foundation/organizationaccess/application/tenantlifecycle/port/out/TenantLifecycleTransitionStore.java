package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out;

import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLifecycle;
import com.aiplatform.foundation.organizationaccess.domain.tenant.operation.TenantLifecycleTransitionResult;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Optional;

/**
 * 生命周期迁移所需的受锁读取与原子提交端口。
 */
public interface TenantLifecycleTransitionStore {

    Optional<TenantLifecycle> lock(TenantId tenantId);

    void commit(TenantLifecycleTransitionResult result);
}
