package com.aiplatform.foundation.organizationaccess.domain.tenant.model;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** 租户功能上限快照；每项必须显式存在，关闭功能不删除业务资源。 */
public record TenantFeatureSettings(TenantId tenantId, Map<TenantFeature, Boolean> availableFeatures,
        long version) {

    public TenantFeatureSettings {
        tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        availableFeatures = Map.copyOf(Objects.requireNonNull(availableFeatures, "功能配置不能为空"));
        if (availableFeatures.size() != TenantFeature.values().length) {
            throw new IllegalArgumentException("租户功能配置必须包含全部已发布代码");
        }
        for (var feature : TenantFeature.values()) {
            if (!availableFeatures.containsKey(feature)) {
                throw new IllegalArgumentException("租户功能配置缺少已发布代码");
            }
        }
        if (version < 0) throw new IllegalArgumentException("功能配置版本不能为负数");
    }

    /** 与固定原型一致的试点初值；开放上限不等于授予成员权限。 */
    public static TenantFeatureSettings defaults(TenantId tenantId) {
        var values = new EnumMap<TenantFeature, Boolean>(TenantFeature.class);
        for (var feature : TenantFeature.values()) values.put(feature, true);
        return new TenantFeatureSettings(tenantId, values, 0);
    }

    public boolean allows(TenantFeature feature) {
        return availableFeatures.get(Objects.requireNonNull(feature, "功能代码不能为空"));
    }
}
