package com.aiplatform.identity.application.result;

/**
 * IAM 租户功能读取结果，表达当前租户被平台授予的功能开关状态。
 */
public record IamTenantFeatureResult(
        String featureCode,
        String featureName,
        boolean enabled,
        String source
) {
}
