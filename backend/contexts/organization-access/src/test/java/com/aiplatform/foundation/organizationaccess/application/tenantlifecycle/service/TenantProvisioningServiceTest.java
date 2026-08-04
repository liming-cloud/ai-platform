package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.command.TenantProvisioningCommand;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantProvisioningStore;
import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import com.aiplatform.foundation.organizationaccess.domain.member.model.EncodedPassword;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipRole;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLifecycleStatus;
import com.aiplatform.foundation.organizationaccess.domain.tenant.operation.TenantProvisioningResult;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TenantProvisioningServiceTest {

    private static final SubjectId INITIAL_ADMINISTRATOR = new SubjectId(4921825928971026434L);
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-03T00:00:00Z");

    @Test
    void 应以令牌派生平台操作者为前置并原子提交准备中的租户() {
        var committed = new AtomicReference<TenantProvisioningResult>();
        var service = service(Optional.of(activeAdministrator()), committed);

        var result = service.provision(
                platformContext(),
                new TenantProvisioningCommand("fresh-tenant", "新租户", INITIAL_ADMINISTRATOR));

        assertEquals(result, committed.get());
        assertEquals(TenantLifecycleStatus.PROVISIONING, result.lifecycle().status());
        assertEquals(INITIAL_ADMINISTRATOR, result.initialAdministrator().subjectId());
        assertEquals(TenantMembershipRole.TENANT_ADMINISTRATOR, result.initialAdministrator().role());
        assertEquals(OCCURRED_AT, result.occurredAt());
    }

    @Test
    void 不可用初始管理员不得产生任何提交() {
        var committed = new AtomicReference<TenantProvisioningResult>();
        var service = service(Optional.empty(), committed);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.provision(
                        platformContext(),
                        new TenantProvisioningCommand("fresh-tenant", "新租户", INITIAL_ADMINISTRATOR)));

        assertTrue(committed.get() == null);
    }

    private static TenantProvisioningService service(
            Optional<SubjectIdentity> administrator,
            AtomicReference<TenantProvisioningResult> committed) {
        TenantProvisioningStore store = new TenantProvisioningStore() {
            @Override
            public Optional<SubjectIdentity> lockActiveInitialAdministrator(SubjectId subjectId) {
                return administrator.filter(value -> value.id().equals(subjectId));
            }

            @Override
            public void commit(TenantProvisioningResult result) {
                committed.set(result);
            }
        };
        EntityIdGenerator identifiers = new EntityIdGenerator() {
            private long next = 100000000000000001L;

            @Override
            public long nextId() {
                return next++;
            }
        };
        return new TenantProvisioningService(
                store,
                identifiers,
                Clock.fixed(OCCURRED_AT, ZoneOffset.UTC));
    }

    private static SubjectIdentity activeAdministrator() {
        return SubjectIdentity.register(
                INITIAL_ADMINISTRATOR,
                "initial-admin",
                new EncodedPassword("valid"),
                OCCURRED_AT);
    }

    private static PlatformRequestContext platformContext() {
        return new PlatformRequestContext(
                new com.aiplatform.foundation.security.identity.SubjectId(4921825928971026436L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1L,
                Set.of(PlatformPermissions.SESSION_ACCESS, PlatformPermissions.TENANT_GOVERNANCE_WRITE),
                "trc_tenant_provisioning");
    }
}
