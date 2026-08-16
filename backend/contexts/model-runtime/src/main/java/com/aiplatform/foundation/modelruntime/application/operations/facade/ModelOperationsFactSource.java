package com.aiplatform.foundation.modelruntime.application.operations.facade;

import com.aiplatform.foundation.modelruntime.application.operations.dto.ModelOperationsFacts;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Set;

/** 向运营上下文提供模型供应链状态计数，禁止返回凭据、Endpoint 或厂商响应。 */
public interface ModelOperationsFactSource {

    ModelOperationsFacts platformSummary();

    ModelOperationsFacts tenantSummary(TenantId tenantId);

    /** 返回没有可用已发布部署的已配置租户集合，仅供后端运营风险去重。 */
    Set<TenantId> tenantsWithoutPublishedDeployment();
}
