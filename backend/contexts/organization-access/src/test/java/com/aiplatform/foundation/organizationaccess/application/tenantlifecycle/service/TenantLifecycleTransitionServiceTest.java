package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.command.TenantLifecycleTransitionCommand;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantLifecycleTransitionStore;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLifecycle;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLifecycleAction;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLifecycleStatus;
import com.aiplatform.foundation.organizationaccess.domain.tenant.operation.TenantLifecycleTransitionResult;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TenantLifecycleTransitionServiceTest {

    private static final TenantId TENANT_ID = new TenantId(100000000000000001L);
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-03T04:00:00Z");

    @Test
    void 应在受权平台上下文下将准备中租户激活并提交不可变结果() {
        var committed = new AtomicReference<TenantLifecycleTransitionResult>();
        var service = service(Optional.of(provisioning()), committed);

        var result = service.transition(
                platformContext(),
                new TenantLifecycleTransitionCommand(TENANT_ID, TenantLifecycleAction.ACTIVATE));

        assertEquals(result, committed.get());
        assertEquals(TenantLifecycleStatus.ACTIVE, result.lifecycle().status());
        assertEquals(1L, result.lifecycle().version());
        assertEquals(OCCURRED_AT, result.change().occurredAt());
    }

    @Test
    void 非法重复激活不得提交任何状态事实() {
        var committed = new AtomicReference<TenantLifecycleTransitionResult>();
        var service = service(Optional.of(provisioning().activate()), committed);

        assertThrows(IllegalStateException.class, () -> service.transition(
                platformContext(),
                new TenantLifecycleTransitionCommand(TENANT_ID, TenantLifecycleAction.ACTIVATE)));

        assertTrue(committed.get() == null);
    }

    private static TenantLifecycleTransitionService service(
            Optional<TenantLifecycle> lifecycle,
            AtomicReference<TenantLifecycleTransitionResult> committed) {
        TenantLifecycleTransitionStore store = new TenantLifecycleTransitionStore() {
            @Override
            public Optional<TenantLifecycle> lock(TenantId tenantId) {
                return lifecycle.filter(value -> value.id().equals(tenantId));
            }

            @Override
            public void commit(TenantLifecycleTransitionResult result) {
                committed.set(result);
            }
        };
        return new TenantLifecycleTransitionService(
                store, Clock.fixed(OCCURRED_AT, ZoneOffset.UTC));
    }

    private static TenantLifecycle provisioning() {
        return TenantLifecycle.provision(
                TENANT_ID,
                "fresh-tenant",
                "新租户",
                new SubjectId(4921825928971026434L),
                Instant.parse("2026-08-03T03:00:00Z"));
    }

    private static PlatformRequestContext platformContext() {
        return new PlatformRequestContext(
                new com.aiplatform.foundation.security.identity.SubjectId(4921825928971026436L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1L,
                Set.of(PlatformPermissions.TENANT_GOVERNANCE_WRITE),
                "trc_tenant_transition");
    }
}
