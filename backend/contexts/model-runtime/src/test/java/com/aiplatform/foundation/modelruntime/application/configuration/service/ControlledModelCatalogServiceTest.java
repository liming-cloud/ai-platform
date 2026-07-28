package com.aiplatform.foundation.modelruntime.application.configuration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntry;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfiguration;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationRepository;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ControlledModelCatalogServiceTest {

    @Test
    void 只有平台权限可创建并发布受控目录条目() {
        var repository = new InMemoryRepository();
        var service = new ControlledModelCatalogService(
                repository,
                () -> 3001L,
                Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC));

        assertThrows(com.aiplatform.foundation.modelruntime.application.provider.exception.ModelCatalogPermissionDeniedException.class, () -> service.create(
                context("model-tenant:write"), ProviderKind.OPENAI_COMPATIBLE,
                URI.create("https://example.com/v1"), "qwen-plus", ModelType.CHAT));

        var draft = service.create(
                context("model-platform:write"), ProviderKind.OPENAI_COMPATIBLE,
                URI.create("https://example.com/v1"), "qwen-plus", ModelType.CHAT);
        var published = service.publish(context("model-platform:write"), draft.id());

        assertEquals("DRAFT", draft.status().name());
        assertEquals("PUBLISHED", published.status().name());
        assertEquals(1L, published.version());
    }

    private static TrustedRequestContext context(String permission) {
        return new TrustedRequestContext(
                new TenantId(100000000000000001L),
                new SubjectId(1843651857942052866L),
                new MembershipId(3382738893456539650L),
                CredentialType.PERSON, AccessChannel.CONSOLE, 1,
                Set.of(new PermissionCode(permission)), "trace-catalog");
    }

    private static final class InMemoryRepository implements ModelConfigurationRepository {
        private ControlledModelCatalogEntry entry;

        @Override
        public Optional<ControlledModelCatalogEntry> findCatalogEntry(ControlledModelCatalogEntryId id) {
            return entry != null && entry.id().equals(id) ? Optional.of(entry) : Optional.empty();
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
        public void save(ModelConfiguration configuration) {
        }
    }
}
