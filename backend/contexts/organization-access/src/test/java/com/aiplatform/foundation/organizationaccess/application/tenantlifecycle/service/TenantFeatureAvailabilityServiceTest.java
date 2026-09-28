package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantFeatureSettingsStore;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantFeature;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantFeatureSettings;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.EnumMap;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TenantFeatureAvailabilityServiceTest {
    private final TenantFeatureSettingsStore store = mock(TenantFeatureSettingsStore.class);
    private final TenantFeatureAvailabilityService service = new TenantFeatureAvailabilityService(store);

    @Test
    void 配置缺失与关闭都应拒绝而开放仅影响目标功能() {
        var tenant = new TenantId(201L);
        assertThrows(ApplicationException.class, () -> service.requireAvailable(tenant, "knowledgeManagement"));
        var values = new EnumMap<>(TenantFeatureSettings.defaults(tenant).availableFeatures());
        values.put(TenantFeature.KNOWLEDGE_MANAGEMENT, false);
        when(store.find(201L)).thenReturn(Optional.of(new TenantFeatureSettings(tenant, values, 1)));

        assertThrows(ApplicationException.class, () -> service.requireAvailable(tenant, "knowledgeManagement"));
        assertDoesNotThrow(() -> service.requireAvailable(tenant, "modelGovernance"));
        assertThrows(IllegalArgumentException.class, () -> service.requireAvailable(tenant, "securityBypass"));
    }
}
