package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.knowledgedata.application.knowledge.facade.KnowledgeQuotaUsage;
import com.aiplatform.foundation.knowledgedata.application.knowledge.facade.KnowledgeQuotaUsageSnapshot;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.command.TenantQuotaUpdate;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.exception.TenantQuotaErrorCode;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantQuotaSettingsStore;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantQuota;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TenantQuotaSettingsServiceTest {

    private final TenantQuotaSettingsStore store = mock(TenantQuotaSettingsStore.class);
    private final KnowledgeQuotaUsage usage = mock(KnowledgeQuotaUsage.class);
    private final TenantQuotaSettingsService service = new TenantQuotaSettingsService(store, usage);

    @Test
    void 无平台治理权限不得读取用量或写入配额() {
        assertThrows(RuntimeException.class, () -> service.update(platform(Set.of()), 101L,
                new TenantQuotaUpdate(3, 1000, 10_000L, 500L, 100L, 0L)));
        verifyNoInteractions(store, usage);
    }

    @Test
    void 下调不得低于已核实的每库最大用量() {
        when(store.find(101L)).thenReturn(Optional.of(current()));
        when(usage.snapshot(101L)).thenReturn(new KnowledgeQuotaUsageSnapshot(
                2L, 5L, BigInteger.valueOf(600L), true));

        assertEquals(TenantQuotaErrorCode.BELOW_USAGE, assertThrows(ApplicationException.class, () -> service.update(governor(), 101L,
                new TenantQuotaUpdate(1, 1000, 10_000L, 500L, 100L, 0L))).errorCode());
        assertEquals(TenantQuotaErrorCode.BELOW_USAGE, assertThrows(ApplicationException.class, () -> service.update(governor(), 101L,
                new TenantQuotaUpdate(3, 4, 10_000L, 500L, 100L, 0L))).errorCode());
        assertEquals(TenantQuotaErrorCode.BELOW_USAGE, assertThrows(ApplicationException.class, () -> service.update(governor(), 101L,
                new TenantQuotaUpdate(3, 1000, 599L, 500L, 100L, 0L))).errorCode());
        verify(store, never()).replace(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void 用量不完整时拒绝降低前三项但允许提高上限() {
        when(store.find(101L)).thenReturn(Optional.of(current()));
        when(usage.snapshot(101L)).thenReturn(new KnowledgeQuotaUsageSnapshot(
                1L, 1L, BigInteger.valueOf(100L), false));
        when(store.replace(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(0L))).thenReturn(true);

        assertEquals(TenantQuotaErrorCode.USAGE_INCOMPLETE, assertThrows(ApplicationException.class, () -> service.update(governor(), 101L,
                new TenantQuotaUpdate(2, 1000, 10_000L, 500L, 100L, 0L))).errorCode());
        var saved = service.update(governor(), 101L,
                new TenantQuotaUpdate(4, 1000, 10_000L, 400L, 90L, 0L));
        assertEquals(1L, saved.version());
        assertEquals(4, saved.knowledgeBases());
    }

    @Test
    void 旧版本不得覆盖现有配额() {
        when(store.find(101L)).thenReturn(Optional.of(current()));
        assertEquals(TenantQuotaErrorCode.VERSION_CONFLICT, assertThrows(ApplicationException.class, () -> service.update(governor(), 101L,
                new TenantQuotaUpdate(4, 1000, 10_000L, 500L, 100L, 8L))).errorCode());
        verifyNoInteractions(usage);
    }

    @Test
    void 保存窗口内的版本竞争也不得覆盖新配置() {
        when(store.find(101L)).thenReturn(Optional.of(current()));
        when(usage.snapshot(101L)).thenReturn(new KnowledgeQuotaUsageSnapshot(
                1L, 1L, BigInteger.valueOf(100L), true));
        assertEquals(TenantQuotaErrorCode.VERSION_CONFLICT, assertThrows(ApplicationException.class,
                () -> service.update(governor(), 101L,
                        new TenantQuotaUpdate(4, 1000, 10_000L, 500L, 100L, 0L))).errorCode());
    }

    private static TenantQuota current() {
        return new TenantQuota(new TenantId(101L), 3, 1000, 10_000L, 500L, 100L, 0L);
    }

    private static PlatformRequestContext governor() {
        return platform(Set.of(PlatformPermissions.TENANT_GOVERNANCE_WRITE));
    }

    private static PlatformRequestContext platform(Set<PermissionCode> permissions) {
        var effective = new HashSet<>(permissions);
        effective.add(PlatformPermissions.SESSION_ACCESS);
        return new PlatformRequestContext(
                new com.aiplatform.foundation.security.identity.SubjectId(4921825928971026436L),
                CredentialType.PERSON, AccessChannel.CONSOLE, 1L, Set.copyOf(effective), "trc_quota_settings");
    }
}
