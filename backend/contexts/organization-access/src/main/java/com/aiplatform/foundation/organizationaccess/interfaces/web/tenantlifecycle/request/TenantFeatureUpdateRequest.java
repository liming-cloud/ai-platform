package com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.request;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto.TenantFeatureSettingsResult;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** 严格解析八项布尔设置；请求不允许夹带身份、安全控制或其他租户字段。 */
public record TenantFeatureUpdateRequest(Map<String, Boolean> availableFeatures, long expectedVersion) {
    public static TenantFeatureUpdateRequest from(Map<String, Object> payload) {
        if (payload == null || !payload.keySet().equals(Set.of("availableFeatures", "expectedVersion"))) {
            throw new IllegalArgumentException("功能配置字段不完整或包含未知字段");
        }
        if (!(payload.get("availableFeatures") instanceof Map<?, ?> raw)
                || raw.size() != TenantFeatureSettingsResult.publishedCodes().size()) {
            throw new IllegalArgumentException("必须提交全部租户功能代码");
        }
        var features = new HashMap<String, Boolean>();
        for (var entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String code) || !(entry.getValue() instanceof Boolean enabled)) {
                throw new IllegalArgumentException("租户功能必须使用已发布代码和布尔值");
            }
            features.put(code, enabled);
        }
        if (!features.keySet().equals(TenantFeatureSettingsResult.publishedCodes())) {
            throw new IllegalArgumentException("租户功能配置缺少已发布代码");
        }
        var versionValue = payload.get("expectedVersion");
        if (!(versionValue instanceof Integer || versionValue instanceof Long)
                || ((Number) versionValue).longValue() < 0) {
            throw new IllegalArgumentException("预期版本必须是非负整数");
        }
        return new TenantFeatureUpdateRequest(Map.copyOf(features), ((Number) versionValue).longValue());
    }
}
