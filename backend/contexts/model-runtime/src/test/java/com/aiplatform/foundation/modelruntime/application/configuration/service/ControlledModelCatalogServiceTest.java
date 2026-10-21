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
    void 缺少按租户供应事实的旧仓储不能退回全局目录() {
        var repository = new InMemoryRepository();
        repository.saveCatalogEntry(ControlledModelCatalogEntry.create(
                new ControlledModelCatalogEntryId(3001L), ProviderKind.OPENAI_COMPATIBLE,
                URI.create("https://example.com/v1"), "test-chat-model", ModelType.CHAT,
                ModelRuntimeCapabilities.chat(32_768, 4_096), Instant.parse("2026-07-28T00:00:00Z")).publish());
        var service = new ControlledModelCatalogService(repository);

        assertThrows(UnsupportedOperationException.class,
                () -> service.listPublished(context("model:write"), new PageQuery(1, 20)));
    }

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
    void 旧目录写入口只能恢复已经原子发布的同一快照() {
        var repository = mock(ModelConfigurationRepository.class);
        var deploymentId = new PlatformModelDeploymentId(620000000000000001L);
        var entry = ControlledModelCatalogEntry.createFromPlatformDeployment(new ControlledModelCatalogEntryId(3001L),
                deploymentId, 3L, ProviderKind.OPENAI_COMPATIBLE, URI.create("https://example.com/v1"),
                "test-chat-model", ModelType.CHAT, ModelRuntimeCapabilities.chat(32_768, 4096), Instant.now()).publish();
        when(repository.findPublishedByDeployment(deploymentId, 3L)).thenReturn(Optional.of(entry));
        when(repository.findCatalogEntry(entry.id())).thenReturn(Optional.of(entry));
        var service = new ControlledModelCatalogService(repository);

        org.junit.jupiter.api.Assertions.assertSame(entry, service.createFromDeployment(platformContext(), deploymentId, 3L));
        org.junit.jupiter.api.Assertions.assertSame(entry, service.publish(platformContext(), entry.id()));
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).saveCatalogEntry(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).updateCatalogEntry(org.mockito.ArgumentMatchers.any());
        assertThrows(com.aiplatform.foundation.modelruntime.application.configuration.exception.ModelConfigurationNotFoundException.class,
                () -> service.createFromDeployment(platformContext(), deploymentId, 2L));
    }

    @Test
    void 已发布目录仅查询可信会话的精确租户范围() {
        var repository = mock(ModelConfigurationRepository.class);
        var published = ControlledModelCatalogEntry.create(new ControlledModelCatalogEntryId(3001L),
                ProviderKind.OPENAI_COMPATIBLE, URI.create("https://example.com/v1"), "test-chat-model", ModelType.CHAT,
                ModelRuntimeCapabilities.chat(32_768, 4096), Instant.now()).publish();
        var context = context("model:write");
        when(repository.findPublishedCatalogPage(context.tenantId(), 1, 20)).thenReturn(
                new ControlledModelCatalogPage(java.util.List.of(published), 1, 20, 1));

        var page = new ControlledModelCatalogService(repository).listPublished(context, new PageQuery(1, 20));
        assertEquals(1, page.items().size());
        assertEquals(published.id(), page.items().getFirst().id());
        org.mockito.Mockito.verify(repository).findPublishedCatalogPage(context.tenantId(), 1, 20);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).findPublishedCatalogPage(1, 20);
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
