package org.fujay.ai.platform.tenant.domain.repository;

public interface TenantAuditRepository {

    void append(Long tenantId, Long actorId, String action, String result, String detail);
}
