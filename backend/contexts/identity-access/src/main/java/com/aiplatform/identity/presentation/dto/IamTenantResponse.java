package com.aiplatform.identity.presentation.dto;

import com.aiplatform.identity.application.result.IamTenantResult;

public record IamTenantResponse(String tenantId, String code, String name, String status, String planCode) {
    public static IamTenantResponse from(IamTenantResult result) {
        return new IamTenantResponse(result.tenantId(), result.code(), result.name(), result.status(), result.planCode());
    }
}
