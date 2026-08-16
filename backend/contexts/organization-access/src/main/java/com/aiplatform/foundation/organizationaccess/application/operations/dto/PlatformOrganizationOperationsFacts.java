package com.aiplatform.foundation.organizationaccess.application.operations.dto;

import java.util.Map;

/** 平台运营总览可消费的组织与账户脱敏计数。 */
public record PlatformOrganizationOperationsFacts(
        Map<String, Long> tenantsByStatus,
        Map<String, Long> platformAdministratorsByStatus,
        Map<String, Long> tenantAdministratorsByStatus,
        Map<String, Long> tenantUsersByStatus) {

    public PlatformOrganizationOperationsFacts {
        tenantsByStatus = Map.copyOf(tenantsByStatus);
        platformAdministratorsByStatus = Map.copyOf(platformAdministratorsByStatus);
        tenantAdministratorsByStatus = Map.copyOf(tenantAdministratorsByStatus);
        tenantUsersByStatus = Map.copyOf(tenantUsersByStatus);
    }
}
