package com.aiplatform.foundation.security.identity;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;

/** 租户功能事实的公开检查契约；缺失或关闭时实现必须失败关闭。 */
@FunctionalInterface
public interface TenantFeatureAvailability {
    void requireAvailable(TenantId tenantId, String featureCode);
}
