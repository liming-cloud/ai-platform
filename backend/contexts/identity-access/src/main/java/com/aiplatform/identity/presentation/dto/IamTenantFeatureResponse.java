package com.aiplatform.identity.presentation.dto;

import com.aiplatform.identity.application.result.IamTenantFeatureResult;

public record IamTenantFeatureResponse(
        String featureCode,
        String featureName,
        boolean enabled,
        String source
) {
    public IamTenantFeatureResponse {
        featureCode = IdentityResponseContracts.requireText(featureCode, "功能编码");
        featureName = IdentityResponseContracts.requireText(featureName, "功能名称");
        source = IdentityResponseContracts.requireText(source, "功能来源");
    }

    public static IamTenantFeatureResponse from(IamTenantFeatureResult result) {
        IdentityResponseContracts.requireValue(result, "IAM 租户功能结果");
        return new IamTenantFeatureResponse(
                result.featureCode(),
                result.featureName(),
                result.enabled(),
                result.source()
        );
    }
}
