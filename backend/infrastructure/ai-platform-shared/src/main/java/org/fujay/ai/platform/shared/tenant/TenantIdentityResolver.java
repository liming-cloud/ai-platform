package org.fujay.ai.platform.shared.tenant;

import java.util.Optional;

public interface TenantIdentityResolver {

    Optional<TenantId> findTenantIdByCode(String tenantCode);
}
