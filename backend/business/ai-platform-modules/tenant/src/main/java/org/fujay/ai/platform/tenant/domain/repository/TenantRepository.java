package org.fujay.ai.platform.tenant.domain.repository;

import java.util.List;
import java.util.Optional;
import org.fujay.ai.platform.tenant.domain.aggregate.Tenant;

public interface TenantRepository {

    Optional<Tenant> findByCode(String tenantCode);

    Optional<Tenant> findById(Long tenantId);

    List<Tenant> list();

    Tenant save(Tenant tenant);
}
