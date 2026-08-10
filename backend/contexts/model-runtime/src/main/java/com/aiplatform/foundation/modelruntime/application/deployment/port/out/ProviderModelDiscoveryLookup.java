package com.aiplatform.foundation.modelruntime.application.deployment.port.out;

import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.DiscoveredModel;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionId;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Optional;

/** 为模型部署创建读取当前连接修订的有效发现候选。 */
public interface ProviderModelDiscoveryLookup {

    /**
     * 查找尚未失效的成功发现候选。
     *
     * @return 匹配候选；不存在、失败或过期时为空。
     */
    Optional<DiscoveredModel> findCurrentCandidate(
            TenantId tenantId,
            ProviderConnectionId providerConnectionId,
            long providerConnectionRevision,
            String modelCode,
            ModelType modelType,
            Instant now);
}
