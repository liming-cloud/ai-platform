package org.fujay.ai.platform.tenant.interfaces.request;

public record TenantCreateRequest(
        String tenantCode,
        String tenantName,
        String adminUsername,
        String adminDisplayName,
        String adminPassword,
        String status) {
}
