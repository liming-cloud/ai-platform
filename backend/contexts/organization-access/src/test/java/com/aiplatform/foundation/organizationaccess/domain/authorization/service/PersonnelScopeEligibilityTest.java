package com.aiplatform.foundation.organizationaccess.domain.authorization.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.domain.shared.exception.OrganizationAccessRuleViolation;
import org.junit.jupiter.api.Test;

class PersonnelScopeEligibilityTest {

    @Test
    void 活动平台管理员不得进入租户人员范围() {
        assertThrows(
                OrganizationAccessRuleViolation.class,
                () -> PersonnelScopeEligibility.requireTenantScope(true));
        assertDoesNotThrow(() -> PersonnelScopeEligibility.requireTenantScope(false));
    }

    @Test
    void 存在启用租户成员资格时不得进入平台人员范围() {
        assertThrows(
                OrganizationAccessRuleViolation.class,
                () -> PersonnelScopeEligibility.requirePlatformScope(1L));
        assertDoesNotThrow(() -> PersonnelScopeEligibility.requirePlatformScope(0L));
    }
}
