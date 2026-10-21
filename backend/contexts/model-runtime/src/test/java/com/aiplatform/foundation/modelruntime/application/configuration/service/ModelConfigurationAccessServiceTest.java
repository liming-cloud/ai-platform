package com.aiplatform.foundation.modelruntime.application.configuration.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntry;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfiguration;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationAccessGrant;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationAccessGrantId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationOwnerId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationScope;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationAccessRepository;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationRepository;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModelConfigurationAccessServiceTest {

    @Test
    void 即使配置属于当前租户也不能绕过平台供应授权() {
        var configurations = new ConfigurationRepository();
        var tenant = new TenantId(2001L);
        var configuration = ModelConfiguration.create(new ModelConfigurationId(4001L),
                ModelConfigurationScope.TENANT, tenant, null, "未获供应授权的模型",
                new ControlledModelCatalogEntryId(3001L), Instant.parse("2026-07-28T00:00:00Z")).activate();
        var service = new ModelConfigurationAccessService(configurations, new AccessRepository(),
                () -> 5001L, Clock.systemUTC());

        assertFalse(service.mayUse(member(tenant, 6001L), configuration));
    }

    @Test
    void 平台授权撤销后成员立即不可再使用模型配置() {
        var configurations = new ConfigurationRepository();
        configurations.configuration = ModelConfiguration.create(
                new ModelConfigurationId(4001L), ModelConfigurationScope.PLATFORM,
                null, null, "平台模型", new ControlledModelCatalogEntryId(3001L),
                Instant.parse("2026-07-28T00:00:00Z")).activate();
        var grants = new AccessRepository();
        var service = new ModelConfigurationAccessService(
                configurations, grants, () -> 5001L,
                Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC));

        var targetTenant = new TenantId(2001L);
        configurations.supplyTenant = targetTenant;
        var grant = service.grant(platformAdmin(), new ModelConfigurationId(4001L), targetTenant, null);
        assertTrue(service.mayUse(member(targetTenant, 6001L), configurations.configuration));

        service.revoke(platformAdmin(), grant.id());
        assertFalse(service.mayUse(member(targetTenant, 6001L), configurations.configuration));
    }

    @Test
    void 租户管理员不能把本租户模型授权给其他租户() {
        var configurations = new ConfigurationRepository();
        configurations.configuration = ModelConfiguration.create(
                new ModelConfigurationId(4001L), ModelConfigurationScope.TENANT,
                new TenantId(2001L), null, "租户模型", new ControlledModelCatalogEntryId(3001L),
                Instant.parse("2026-07-28T00:00:00Z"));
        var service = new ModelConfigurationAccessService(
                configurations, new AccessRepository(), () -> 5001L, Clock.systemUTC());

        assertThrows(
                com.aiplatform.foundation.modelruntime.application.configuration.exception.ModelConfigurationNotFoundException.class,
                () -> service.grant(tenantAdmin(new TenantId(2001L)), new ModelConfigurationId(4001L),
                        new TenantId(2002L), null));
    }

    @Test
    void 已停用的平台模型即使保留活动授权也立即不可使用() {
        var configurations = new ConfigurationRepository();
        configurations.configuration = ModelConfiguration.create(
                        new ModelConfigurationId(4001L), ModelConfigurationScope.PLATFORM,
                        null, null, "平台模型", new ControlledModelCatalogEntryId(3001L),
                        Instant.parse("2026-07-28T00:00:00Z"))
                .activate()
                .disable();
        var grants = new AccessRepository();
        var service = new ModelConfigurationAccessService(
                configurations, grants, () -> 5001L, Clock.systemUTC());
        var targetTenant = new TenantId(2001L);
        configurations.supplyTenant = targetTenant;
        service.grant(platformAdmin(), new ModelConfigurationId(4001L), targetTenant, null);

        assertFalse(service.mayUse(member(targetTenant, 6001L), configurations.configuration));
    }

    private static TrustedRequestContext platformAdmin() {
        return context(new TenantId(1001L), 9001L, "model-platform:write");
    }

    private static TrustedRequestContext tenantAdmin(TenantId tenantId) {
        return context(tenantId, 9001L, "model:write");
    }

    private static TrustedRequestContext member(TenantId tenantId, long membershipId) {
        return context(tenantId, membershipId, "model:read");
    }

    private static TrustedRequestContext context(TenantId tenantId, long membershipId, String permission) {
        return new TrustedRequestContext(tenantId, new SubjectId(membershipId + 10000L),
                new MembershipId(membershipId), CredentialType.PERSON, AccessChannel.CONSOLE, 1,
                Set.of(new PermissionCode(permission)), "trace-model-grant");
    }

    private static final class ConfigurationRepository implements ModelConfigurationRepository {
        private ModelConfiguration configuration;
        private TenantId supplyTenant;
        @Override public boolean isSupplyAvailable(TenantId tenantId, ControlledModelCatalogEntryId id) {
            return tenantId.equals(supplyTenant) && id.equals(new ControlledModelCatalogEntryId(3001L));
        }
        @Override public Optional<ControlledModelCatalogEntry> findCatalogEntry(ControlledModelCatalogEntryId id) { return Optional.empty(); }
        @Override public void saveCatalogEntry(ControlledModelCatalogEntry entry) { }
        @Override public void updateCatalogEntry(ControlledModelCatalogEntry entry) { }
        @Override public Optional<ModelConfiguration> findConfiguration(ModelConfigurationId id) {
            return configuration != null && configuration.id().equals(id) ? Optional.of(configuration) : Optional.empty();
        }
        @Override public void updateConfiguration(ModelConfiguration value) { configuration = value; }
        @Override public void save(ModelConfiguration value) { configuration = value; }
    }

    private static final class AccessRepository implements ModelConfigurationAccessRepository {
        private ModelConfigurationAccessGrant grant;
        @Override public Optional<ModelConfigurationAccessGrant> findById(ModelConfigurationAccessGrantId id) {
            return grant != null && grant.id().equals(id) ? Optional.of(grant) : Optional.empty();
        }
        @Override public boolean hasActiveGrant(ModelConfigurationId configurationId, TenantId tenantId, ModelConfigurationOwnerId membershipId) {
            return grant != null && grant.status().name().equals("ACTIVE")
                    && grant.modelConfigurationId().equals(configurationId) && grant.granteeTenantId().equals(tenantId)
                    && (grant.granteeMembershipId() == null || grant.granteeMembershipId().equals(membershipId));
        }
        @Override public void save(ModelConfigurationAccessGrant value) { grant = value; }
        @Override public void update(ModelConfigurationAccessGrant value) { grant = value; }
    }
}
