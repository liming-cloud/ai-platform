package com.aiplatform.shared.security.feature.port;

import com.aiplatform.shared.kernel.TenantId;

/**
 * 租户功能开关读取端口，业务上下文只依赖该共享契约，不依赖 IAM 持久化实现。
 */
public interface TenantFeatureAccessPort {
    boolean isFeatureEnabled(TenantId tenantId, String featureCode);
}
