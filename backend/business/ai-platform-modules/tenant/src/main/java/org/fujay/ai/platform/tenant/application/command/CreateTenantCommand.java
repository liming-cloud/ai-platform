package org.fujay.ai.platform.tenant.application.command;

public record CreateTenantCommand(
        String tenantCode,
        String tenantName,
        String adminUsername,
        String adminDisplayName,
        String adminPassword,
        String status) {
}
