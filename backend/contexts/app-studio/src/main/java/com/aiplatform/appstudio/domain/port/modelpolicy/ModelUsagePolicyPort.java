package com.aiplatform.appstudio.domain.port.modelpolicy;

import com.aiplatform.appstudio.domain.port.modelpolicy.model.AppModelUsageDecision;
import com.aiplatform.appstudio.domain.port.modelpolicy.model.AppModelUsageRequest;

/**
 * AI 应用上下文模型使用策略端口，由本地投影实现，禁止同步调用 model-hub 应用服务。
 */
public interface ModelUsagePolicyPort {
    AppModelUsageDecision decide(AppModelUsageRequest request);
}
