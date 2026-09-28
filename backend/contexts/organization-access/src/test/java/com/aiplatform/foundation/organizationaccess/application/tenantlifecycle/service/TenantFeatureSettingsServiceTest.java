package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantFeatureSettingsStore;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantSessionRevoker;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto.TenantFeatureSettingsResult;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantFeatureSettings;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TenantFeatureSettingsServiceTest {

    private final TenantFeatureSettingsStore store = mock(TenantFeatureSettingsStore.class);
    private final TenantSessionRevoker sessions = mock(TenantSessionRevoker.class);
    private final TenantFeatureSettingsService service = new TenantFeatureSettingsService(store, sessions);

    @Test
    void 无平台治理权限时不得读取配置或撤销会话() {
        var viewer = new PlatformRequestContext(
                new com.aiplatform.foundation.security.identity.SubjectId(4921825928971026436L),
                CredentialType.PERSON, AccessChannel.CONSOLE, 1L,
                Set.of(PlatformPermissions.SESSION_ACCESS), "trc_feature_test");
        assertThrows(RuntimeException.class, () -> service.get(viewer, 101L));
        verifyNoInteractions(store, sessions);
    }

    @Test
    void 保存关闭功能必须递增版本并撤销目标租户旧会话() {
        var tenant = new TenantId(101L);
        var current = TenantFeatureSettings.defaults(tenant);
        when(store.find(101L)).thenReturn(Optional.of(current));
        when(store.replace(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(0L))).thenReturn(true);
        var requested = new HashMap<>(TenantFeatureSettingsResult.from(current).availableFeatures());
        requested.put("knowledgeManagement", false);

        var saved = service.update(governor(), 101L, requested, 0L);

        assertEquals(1L, saved.version());
        assertEquals(false, saved.availableFeatures().get("knowledgeManagement"));
        org.mockito.Mockito.verify(sessions).revokeTenantSessions(tenant);
    }

    private static PlatformRequestContext governor() {
        return new PlatformRequestContext(
                new com.aiplatform.foundation.security.identity.SubjectId(4921825928971026436L),
                CredentialType.PERSON, AccessChannel.CONSOLE, 1L,
                Set.of(PlatformPermissions.SESSION_ACCESS, PlatformPermissions.TENANT_GOVERNANCE_WRITE),
                "trc_feature_test");
    }
}
