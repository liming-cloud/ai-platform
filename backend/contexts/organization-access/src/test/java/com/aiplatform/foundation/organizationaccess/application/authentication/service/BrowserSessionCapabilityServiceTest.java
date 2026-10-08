package com.aiplatform.foundation.organizationaccess.application.authentication.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aiplatform.foundation.organizationaccess.application.authentication.dto.BrowserSessionCapability.DisplayRole;
import com.aiplatform.foundation.organizationaccess.application.authentication.dto.BrowserSessionCapability.WorkspaceCategory;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantFeatureSettingsStore;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantLoginSecurityPolicyStore;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantFeature;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantFeatureSettings;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLoginSecurityPolicy;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.security.session.AuthenticatedSessionGateway;
import com.aiplatform.foundation.security.session.IssuedSession;
import com.aiplatform.foundation.security.session.PlatformSessionGateway;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 验证浏览器能力只投影已验证会话中的实际权限，不能由前端或静态菜单补写。
 */
class BrowserSessionCapabilityServiceTest {

    @Test
    void 关闭租户功能后新浏览器投影不得保留对应导航能力() {
        var tenantId = new TenantId(100000000000000001L);
        var context = new TrustedRequestContext(tenantId, new SubjectId(4921825928971026434L),
                new MembershipId(3382738893456539650L), CredentialType.PERSON, AccessChannel.CONSOLE,
                3L, Set.of(new PermissionCode("knowledge:write"), new PermissionCode("model:read")), "trc_feature_nav");
        var values = new EnumMap<>(TenantFeatureSettings.defaults(tenantId).availableFeatures());
        values.put(TenantFeature.KNOWLEDGE_MANAGEMENT, false);
        TenantFeatureSettingsStore store = new TenantFeatureSettingsStore() {
            @Override public Optional<TenantFeatureSettings> find(long requested) {
                return Optional.of(new TenantFeatureSettings(tenantId, values, 1));
            }
            @Override public boolean replace(TenantFeatureSettings next, long version) {
                throw new UnsupportedOperationException();
            }
        };
        var service = new BrowserSessionCapabilityService(
                tenantSessions(context), platformSessions(null), Duration.ofMinutes(30), store, policySettings(), memberAccess());

        var capability = service.inspect("tenant-session", null).capability().orElseThrow();

        assertEquals(Set.of("model:read"), capability.capabilityCodes());
        assertEquals(8 * 3600, capability.maximumSessionAgeSeconds());
    }

    @Test
    void 有效平台会话只投影平台管理员角色() {
        var platformContext = new PlatformRequestContext(
                new SubjectId(4921825928971026436L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                4L,
                Set.of(PlatformPermissions.SESSION_ACCESS),
                "trc_platform_operator_capability");
        var service = new BrowserSessionCapabilityService(
                tenantSessions(null),
                platformSessions(platformContext),
                Duration.ofMinutes(30), featureSettings(), policySettings(), memberAccess());

        var capability = service.inspect(null, "platform-session").capability().orElseThrow();

        assertEquals(Set.of(WorkspaceCategory.PLATFORM_TENANT_GOVERNANCE), capability.workspaceCategories());
        assertEquals(Set.of(DisplayRole.PLATFORM_ADMINISTRATOR), capability.displayRoles());
        assertEquals(Set.of("platform-session:access"), capability.capabilityCodes());
        assertEquals(30 * 60, capability.maximumSessionAgeSeconds());
    }

    @Test
    void 平台管理员必须以独立显示角色投影全部固定平台权限() {
        var platformContext = new PlatformRequestContext(
                new SubjectId(4921825928971026436L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                4L,
                Set.of(
                        PlatformPermissions.SESSION_ACCESS,
                        PlatformPermissions.ACCOUNT_MANAGE,
                        PlatformPermissions.TENANT_GOVERNANCE_WRITE,
                        PlatformPermissions.MODEL_PLATFORM_WRITE),
                "trc_platform_administrator_capability");
        var service = new BrowserSessionCapabilityService(
                tenantSessions(null),
                platformSessions(platformContext),
                Duration.ofMinutes(30), featureSettings(), policySettings(), memberAccess());

        var capability = service.inspect(null, "platform-session").capability().orElseThrow();

        assertEquals(Set.of(DisplayRole.PLATFORM_ADMINISTRATOR), capability.displayRoles());
        assertEquals(
                Set.of(
                        "platform-session:access",
                        "platform:accounts:manage",
                        "tenant-governance:write",
                        "model-platform:write"),
                capability.capabilityCodes());
    }

    @Test
    void 应把双范围会话的实际权限投影为导航能力码() {
        var tenantContext = new TrustedRequestContext(
                new TenantId(100000000000000001L),
                new SubjectId(4921825928971026434L),
                new MembershipId(3382738893456539650L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                3L,
                Set.of(new PermissionCode("knowledge:read"), new PermissionCode("model:write")),
                "trc_browser_capability");
        var platformContext = new PlatformRequestContext(
                new SubjectId(4921825928971026436L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                4L,
                Set.of(PlatformPermissions.SESSION_ACCESS, PlatformPermissions.TENANT_GOVERNANCE_WRITE),
                "trc_platform_capability");
        var service = new BrowserSessionCapabilityService(
                tenantSessions(tenantContext),
                platformSessions(platformContext),
                Duration.ofMinutes(30), featureSettings(), policySettings(), memberAccess());

        var capability = service.inspect("tenant-session", "platform-session").capability().orElseThrow();

        assertEquals(
                Set.of(
                        "knowledge:read",
                        "model:write",
                        "platform-session:access",
                        "tenant-governance:write"),
                capability.capabilityCodes());
        assertEquals(
                Set.of(DisplayRole.TENANT_USER, DisplayRole.PLATFORM_ADMINISTRATOR),
                capability.displayRoles());
        assertEquals(8 * 3600, capability.maximumSessionAgeSeconds());
    }

    private static AuthenticatedSessionGateway tenantSessions(TrustedRequestContext context) {
        return new AuthenticatedSessionGateway() {
            @Override
            public IssuedSession issue(TrustedRequestContext ignored) {
                throw new UnsupportedOperationException();
            }

            @Override
            public TrustedRequestContext resolve(String tokenValue) {
                return context;
            }

            @Override
            public void revoke(String tokenValue) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void revokeMembershipSessions(MembershipId membershipId) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static TenantFeatureSettingsStore featureSettings() {
        return new TenantFeatureSettingsStore() {
            @Override public Optional<TenantFeatureSettings> find(long tenantId) {
                return Optional.of(TenantFeatureSettings.defaults(new TenantId(tenantId)));
            }
            @Override public boolean replace(TenantFeatureSettings next, long version) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static TenantLoginSecurityPolicyStore policySettings() {
        return new TenantLoginSecurityPolicyStore() {
            @Override public Optional<TenantLoginSecurityPolicy> find(long tenantId) {
                return Optional.of(TenantLoginSecurityPolicy.defaults(new TenantId(tenantId)));
            }
            @Override public boolean replace(TenantLoginSecurityPolicy next, long version) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static PlatformSessionGateway platformSessions(PlatformRequestContext context) {
        return new PlatformSessionGateway() {
            @Override
            public IssuedSession issue(PlatformRequestContext ignored) {
                throw new UnsupportedOperationException();
            }

            @Override
            public PlatformRequestContext resolve(String tokenValue) {
                return context;
            }

            @Override
            public void revoke(String tokenValue) {
                throw new UnsupportedOperationException();
            }
        };
    }
    private static com.aiplatform.foundation.organizationaccess.application.member.facade.MemberAccessFacade memberAccess() {
        var access = org.mockito.Mockito.mock(com.aiplatform.foundation.organizationaccess.application.member.facade.MemberAccessFacade.class);
        org.mockito.Mockito.when(access.read(org.mockito.ArgumentMatchers.any())).thenReturn(
                new com.aiplatform.foundation.organizationaccess.application.member.facade.MemberEffectiveAccess(
                        true, java.util.Map.of("assistantSelection", true, "conversation", true, "ownHistory", true, "authorizedCitations", true), Set.of()));
        return access;
    }
}
