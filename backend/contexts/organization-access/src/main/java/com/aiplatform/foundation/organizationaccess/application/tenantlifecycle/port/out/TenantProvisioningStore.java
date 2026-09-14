package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out;

import com.aiplatform.foundation.organizationaccess.domain.tenant.operation.TenantProvisioningResult;

/**
 * 新租户原子开户端口；不再读取或复用候选主体。
 */
public interface TenantProvisioningStore {

    /** 只读检查既有受控迁移是否已切换认证事实源，不负责初始化该事实。 */
    boolean authenticationReady();

    /**
     * 原子保存领域服务产生的完整建立结果。
     */
    void commit(TenantProvisioningResult result);
}
