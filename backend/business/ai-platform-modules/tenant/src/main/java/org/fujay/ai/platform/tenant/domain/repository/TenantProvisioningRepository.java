package org.fujay.ai.platform.tenant.domain.repository;

import org.fujay.ai.platform.tenant.domain.aggregate.Tenant;
import org.fujay.ai.platform.tenant.domain.model.TenantUser;
import org.fujay.ai.platform.tenant.domain.model.UserCredential;

public interface TenantProvisioningRepository {

    void provisionTenant(Tenant tenant, TenantUser adminUser, UserCredential credential);
}
