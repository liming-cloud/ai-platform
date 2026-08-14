package com.aiplatform.foundation.modelruntime.application.configuration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntry;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfiguration;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelRuntimeCapabilities;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationRepository;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ControlledModelCatalogPage;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeploymentId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeploymentStatus;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionStatus;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ControlledModelCatalogServiceTest {

    @Test
    void 目录服务不得暴露自由端点创建或旧租户上下文发布入口() {
        var legacyEntrypoints = Arrays.stream(ControlledModelCatalogService.class.getMethods())
                .filter(method -> method.getName().equals("create")
                        || (method.getName().equals("publish")
                        && method.getParameterTypes().length > 0
                        && method.getParameterTypes()[0].equals(TrustedRequestContext.class)))
                .toList();

        assertEquals(java.util.List.of(), legacyEntrypoints);
    }

    @Test
    void 目录只能从平台会话选定的已发布部署建立冻结快照() {
        var repository = new InMemoryRepository();
        var deployments = mock(PlatformModelDeploymentRepository.class);
        var connections = mock(PlatformProviderConnectionRepository.class);
        var deployment = mock(com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeployment.class);
        var connection = mock(PlatformProviderConnection.class);
        var deploymentId = new PlatformModelDeploymentId(620000000000000001L);
        var connectionId = new PlatformProviderConnectionId(610000000000000001L);
        when(deployment.version()).thenReturn(3L);
        when(deployment.id()).thenReturn(deploymentId);
        when(deployment.status()).thenReturn(PlatformModelDeploymentStatus.PUBLISHED);
        when(deployment.verificationExpiresAt()).thenReturn(Instant.parse("2026-08-14T00:00:00Z"));
        when(deployment.providerConnectionId()).thenReturn(connectionId);
        when(deployment.providerConnectionRevision()).thenReturn(5L);
        when(deployment.modelCode()).thenReturn("test-chat-model");
        when(deployment.modelType()).thenReturn(ModelType.CHAT);
        when(deployment.contextWindow()).thenReturn(32_768);
        when(deployment.outputLimit()).thenReturn(4_096);
        when(connection.version()).thenReturn(5L);
        when(connection.status()).thenReturn(ProviderConnectionStatus.VERIFIED);
        when(connection.providerKind()).thenReturn(ProviderKind.OPENAI_COMPATIBLE);
        when(connection.endpoint()).thenReturn(URI.create("https://example.com/v1"));
        when(deployments.findById(deploymentId)).thenReturn(Optional.of(deployment));
        when(connections.findById(connectionId)).thenReturn(Optional.of(connection));
        var service = new ControlledModelCatalogService(
                repository,
                () -> 3001L,
                Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC),
                deployments, connections);

        var entry = service.createFromDeployment(platformContext(), deploymentId, 3L);

        assertEquals("test-chat-model", entry.modelCode());
        assertEquals(4_096, entry.capabilities().outputLimit());
        assertEquals(deploymentId, entry.sourceDeploymentId());
        assertEquals(3L, entry.sourceDeploymentRevision());
    }

    @Test
    void 已发布目录可供有模型配置权限的成员分页选择且不返回草稿() {
        var repository = new InMemoryRepository();
        var service = new ControlledModelCatalogService(
                repository,
                () -> 3001L,
                Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC));
        var published = ControlledModelCatalogEntry.create(
                new ControlledModelCatalogEntryId(3001L), ProviderKind.OPENAI_COMPATIBLE,
                URI.create("https://example.com/v1"), "test-chat-model", ModelType.CHAT,
                ModelRuntimeCapabilities.chat(32_768, 4_096), Instant.parse("2026-07-28T00:00:00Z"))
                .publish();
        repository.saveCatalogEntry(published);

        var page = service.listPublished(
                context("model:write"), new PageQuery(1, 20));

        assertEquals(1, page.items().size());
        assertEquals("PUBLISHED", page.items().getFirst().status().name());
        assertEquals(published.id(), page.items().getFirst().id());
    }

    private static TrustedRequestContext context(String permission) {
        return new TrustedRequestContext(
                new TenantId(100000000000000001L),
                new SubjectId(1843651857942052866L),
                new MembershipId(3382738893456539650L),
                CredentialType.PERSON, AccessChannel.CONSOLE, 1,
                Set.of(new PermissionCode(permission)), "trace-catalog");
    }

    private static PlatformRequestContext platformContext() {
        return new PlatformRequestContext(
                new SubjectId(1843651857942052866L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1,
                Set.of(PlatformPermissions.SESSION_ACCESS, new PermissionCode("model-platform:write")),
                "trace-platform-catalog");
    }

    private static final class InMemoryRepository implements ModelConfigurationRepository {
        private ControlledModelCatalogEntry entry;

        @Override
        public Optional<ControlledModelCatalogEntry> findCatalogEntry(ControlledModelCatalogEntryId id) {
            return entry != null && entry.id().equals(id) ? Optional.of(entry) : Optional.empty();
        }

        @Override
        public ControlledModelCatalogPage findPublishedCatalogPage(
                long pageNumber, long pageSize) {
            var entries = entry != null && entry.status().name().equals("PUBLISHED")
                    ? java.util.List.of(entry) : java.util.List.<ControlledModelCatalogEntry>of();
            return new ControlledModelCatalogPage(
                    entries, pageNumber, pageSize, entries.size());
        }

        @Override
        public void saveCatalogEntry(ControlledModelCatalogEntry entry) {
            this.entry = entry;
        }

        @Override
        public void updateCatalogEntry(ControlledModelCatalogEntry entry) {
            this.entry = entry;
        }

        @Override
        public Optional<ModelConfiguration> findConfiguration(ModelConfigurationId id) {
            return Optional.empty();
        }

        @Override
        public void updateConfiguration(ModelConfiguration configuration) {
        }

        @Override
        public void save(ModelConfiguration configuration) {
        }
    }
}
