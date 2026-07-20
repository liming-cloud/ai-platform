package com.aiplatform.identity.application.result;

public record IamTenantResult(
        String tenantId,
        String code,
        String name,
        String status,
        String planCode
) {
}
