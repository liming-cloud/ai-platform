package com.aiplatform.identity.infrastructure.persistence.entity;

import com.aiplatform.identity.application.result.IamTenantFeatureResult;

public class IamTenantFeaturePersistenceEntity {
    private String featureCode;
    private String featureName;
    private Boolean enabled;
    private String source;

    public IamTenantFeatureResult toResult() {
        return new IamTenantFeatureResult(
                requireText(featureCode, "功能编码"),
                requireText(featureName, "功能名称"),
                requireValue(enabled, "功能启用标记"),
                requireText(source, "功能来源")
        );
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static <T> T requireValue(T value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException(fieldName + "不能为空");
        }
        return value;
    }

    public String getFeatureCode() {
        return featureCode;
    }

    public void setFeatureCode(String featureCode) {
        this.featureCode = featureCode;
    }

    public String getFeatureName() {
        return featureName;
    }

    public void setFeatureName(String featureName) {
        this.featureName = featureName;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
