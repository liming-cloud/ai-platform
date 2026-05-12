package org.fujay.ai.platform.tenant.application.port;

public record CurrentTenantPrincipal(Long tenantId, String actorType, Long actorId) {
}
