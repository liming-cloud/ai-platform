package com.aiplatform.foundation.organizationaccess.application.operations.dto;

import java.util.Map;

/** 当前租户运营总览可消费的成员角色与启停计数。 */
public record TenantOrganizationOperationsFacts(
        Map<String, Long> administratorsByStatus,
        Map<String, Long> usersByStatus) {

    public TenantOrganizationOperationsFacts {
        administratorsByStatus = Map.copyOf(administratorsByStatus);
        usersByStatus = Map.copyOf(usersByStatus);
    }
}
