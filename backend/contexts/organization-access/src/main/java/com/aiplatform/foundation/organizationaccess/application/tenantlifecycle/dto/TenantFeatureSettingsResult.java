package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto;

import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantFeature;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantFeatureSettings;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.stream.Collectors;

/** 平台功能配置的公开应用结果；接口层不依赖租户领域对象。 */
public record TenantFeatureSettingsResult(Map<String, Boolean> availableFeatures, long version) {
    public TenantFeatureSettingsResult {
        availableFeatures = Map.copyOf(Objects.requireNonNull(availableFeatures, "功能配置不能为空"));
    }

    /** 返回当前发布的全部功能代码，供 HTTP 边界拒绝未知和缺失字段。 */
    public static Set<String> publishedCodes() {
        return java.util.Arrays.stream(TenantFeature.values()).map(TenantFeature::code)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 将内部领域事实投影为不含租户内部标识的公开结果。 */
    public static TenantFeatureSettingsResult from(TenantFeatureSettings settings) {
        var values = new LinkedHashMap<String, Boolean>();
        for (var feature : TenantFeature.values()) values.put(feature.code(), settings.allows(feature));
        return new TenantFeatureSettingsResult(values, settings.version());
    }
}
