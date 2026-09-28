package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.exception.TenantFeatureErrorCode;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantFeatureSettingsStore;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantFeature;
import com.aiplatform.foundation.security.identity.TenantFeatureAvailability;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Objects;

/** 使用配置事实检查租户功能上限；不从浏览器权限或默认值推断功能可用。 */
public final class TenantFeatureAvailabilityService implements TenantFeatureAvailability {
    private final TenantFeatureSettingsStore store;

    public TenantFeatureAvailabilityService(TenantFeatureSettingsStore store) {
        this.store = Objects.requireNonNull(store, "功能配置存储不能为空");
    }

    @Override
    public void requireAvailable(TenantId tenantId, String featureCode) {
        var feature = TenantFeature.fromCode(featureCode);
        var settings = store.find(Objects.requireNonNull(tenantId, "租户标识不能为空").value())
                .orElseThrow(() -> new ApplicationException(TenantFeatureErrorCode.FEATURE_DISABLED));
        if (!settings.allows(feature)) throw new ApplicationException(TenantFeatureErrorCode.FEATURE_DISABLED);
    }
}
