package com.aiplatform.appstudio.domain.port.modelpolicy;

import com.aiplatform.appstudio.domain.modelpolicy.AppModelUsagePolicyProjection;
import com.aiplatform.shared.kernel.TenantId;
import java.util.Optional;

/**
 * AI 应用模型使用策略本地投影仓储，禁止回查 model-hub 应用服务。
 */
public interface AppModelUsagePolicyProjectionRepository {
    Optional<AppModelUsagePolicyProjection> findByModelConfigId(TenantId tenantId, String modelConfigId);

    AppModelUsagePolicyProjection save(AppModelUsagePolicyProjection projection);
}
