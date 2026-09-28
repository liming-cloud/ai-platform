package com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.response;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto.TenantFeatureSettingsResult;
import java.util.Map;

/** 仅返回八项配置和版本，不包含成员、主体或租户内容。 */
public record TenantFeatureSettingsResponse(Map<String, Boolean> availableFeatures, long version) {
    public static TenantFeatureSettingsResponse from(TenantFeatureSettingsResult settings) {
        return new TenantFeatureSettingsResponse(settings.availableFeatures(), settings.version());
    }
}
