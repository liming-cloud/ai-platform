package com.aiplatform.foundation.modelruntime.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.modelruntime.application.configuration.dto.UsableModelConfigurationSnapshot;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.UsableModelConfigurationLookup;
import com.aiplatform.foundation.modelruntime.application.configuration.port.out.ModelConfigurationCredentialUser;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.EmbeddingModelPort;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ChatModelPort;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatDeploymentRef;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatMessage;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatRequest;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatResult;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatRole;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.common.ModelUsage;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingBatch;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingDeploymentRef;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingRequest;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingVector;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.common.ModelRuntimeSpecification;
import com.aiplatform.foundation.modelruntime.application.runtime.port.out.EmbeddingModelFactory;
import com.aiplatform.foundation.modelruntime.application.runtime.port.out.ChatModelFactory;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntry;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfiguration;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationScope;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelRuntimeCapabilities;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationRepository;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeploymentId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.security.secret.SecretValue;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ModelConfigurationRuntimeServiceTest {

    private static final TenantId TENANT_ID = new TenantId(2001L);
    private static final ModelConfigurationId CONFIGURATION_ID = new ModelConfigurationId(4001L);

    @Test
    void 在单次嵌入调用中使用已验证配置和短生命周期凭据() {
        var snapshot = snapshot();
        var factory = new RecordingEmbeddingFactory();
        var service = new ModelConfigurationRuntimeService(
                (context, id, type) -> Optional.of(snapshot),
                new ConfigurationRepository(),
                useCredential(),
                List.of(factory));

        var result = service.embed(member(), snapshot, request());

        assertEquals(1, result.vectors().size());
        assertEquals(CONFIGURATION_ID.value(), factory.specification.get().deploymentId());
        assertEquals(1024, factory.specification.get().embeddingDimension());
    }

    @Test
    void 快照修订变化时必须拒绝而不调用模型工厂() {
        var factory = new RecordingEmbeddingFactory();
        var service = new ModelConfigurationRuntimeService(
                (context, id, type) -> Optional.of(snapshot()),
                new ConfigurationRepository(),
                useCredential(),
                List.of(factory));
        var stale = new UsableModelConfigurationSnapshot(
                CONFIGURATION_ID, 2L, new ControlledModelCatalogEntryId(3001L), 1L,
                ModelType.EMBEDDING, ModelRuntimeCapabilities.embedding(8192, 1024));

        assertThrows(ModelRuntimeUnavailableException.class,
                () -> service.embed(member(), stale, request()));
        assertEquals(null, factory.specification.get());
    }

    @Test
    void 平台目录来源部署不存在时必须拒绝嵌入调用() {
        var sourceEntry = ControlledModelCatalogEntry.createFromPlatformDeployment(
                        new ControlledModelCatalogEntryId(3001L), new PlatformModelDeploymentId(7001L), 2L,
                        ProviderKind.OPENAI_COMPATIBLE, URI.create("https://example.com/v1"), "text-embedding-v1",
                        ModelType.EMBEDDING, ModelRuntimeCapabilities.embedding(8192, 1024),
                        Instant.parse("2026-08-13T00:00:00Z"))
                .publish();
        var deployments = mock(PlatformModelDeploymentRepository.class);
        when(deployments.findById(new PlatformModelDeploymentId(7001L))).thenReturn(Optional.empty());
        var factory = new RecordingEmbeddingFactory();
        var service = new ModelConfigurationRuntimeService(
                (context, id, type) -> Optional.of(snapshot()),
                new ConfigurationRepository(sourceEntry),
                useCredential(),
                List.of(factory),
                List.of(),
                deployments,
                mock(PlatformProviderConnectionRepository.class));

        assertThrows(ModelRuntimeUnavailableException.class, () -> service.embed(member(), snapshot(), request()));
        assertEquals(null, factory.specification.get());
    }

    @Test
    void 在单次对话调用中重验配置并拒绝失效快照() {
        var chat = chatSnapshot();
        var factory = new RecordingChatFactory();
        var service = new ModelConfigurationRuntimeService(
                (context, id, type) -> Optional.of(chat),
                new ChatConfigurationRepository(),
                useCredential(),
                List.of(),
                List.of(factory));

        var result = service.chat(member(), chat, chatRequest());

        assertEquals("已受控回答", result.content());
        assertEquals(CONFIGURATION_ID.value(), factory.specification.get().deploymentId());
        assertEquals(512, factory.specification.get().outputLimit());
    }

    @Test
    void 对话配置修订变化时必须拒绝而不调用模型工厂() {
        var factory = new RecordingChatFactory();
        var service = new ModelConfigurationRuntimeService(
                (context, id, type) -> Optional.of(chatSnapshot()),
                new ChatConfigurationRepository(),
                useCredential(),
                List.of(),
                List.of(factory));
        var stale = new UsableModelConfigurationSnapshot(
                CONFIGURATION_ID, 2L, new ControlledModelCatalogEntryId(3002L), 1L,
                ModelType.CHAT, ModelRuntimeCapabilities.chat(8192, 512));
        var request = new ChatRequest(
                new ChatDeploymentRef(CONFIGURATION_ID.value(), 2L),
                List.of(new ChatMessage(ChatRole.USER, "测试问题")),
                256);

        assertThrows(ModelRuntimeUnavailableException.class,
                () -> service.chat(member(), stale, request));
        assertEquals(null, factory.specification.get());
    }

    private static UsableModelConfigurationSnapshot snapshot() {
        return new UsableModelConfigurationSnapshot(
                CONFIGURATION_ID, 1L, new ControlledModelCatalogEntryId(3001L), 1L,
                ModelType.EMBEDDING, ModelRuntimeCapabilities.embedding(8192, 1024));
    }

    private static UsableModelConfigurationSnapshot chatSnapshot() {
        return new UsableModelConfigurationSnapshot(
                CONFIGURATION_ID, 1L, new ControlledModelCatalogEntryId(3002L), 1L,
                ModelType.CHAT, ModelRuntimeCapabilities.chat(8192, 512));
    }

    private static TrustedRequestContext member() {
        return new TrustedRequestContext(
                TENANT_ID, new SubjectId(16001L), new MembershipId(6001L),
                CredentialType.PERSON, AccessChannel.CONSOLE, 1,
                Set.of(new PermissionCode("model:read")), "trace-model-runtime");
    }

    private static EmbeddingRequest request() {
        return new EmbeddingRequest(
                new EmbeddingDeploymentRef(CONFIGURATION_ID.value(), 1L, 1024),
                List.of("测试文本"));
    }

    private static ChatRequest chatRequest() {
        return new ChatRequest(
                new ChatDeploymentRef(CONFIGURATION_ID.value(), 1L),
                List.of(new ChatMessage(ChatRole.USER, "测试问题")),
                256);
    }

    private static ModelConfigurationCredentialUser useCredential() {
        return new ModelConfigurationCredentialUser() {
            @Override
            public <T> T useCredential(ModelConfigurationId configurationId, java.util.function.Function<SecretValue, T> action) {
                try (var secret = SecretValue.copyOf("model-api-key".toCharArray())) {
                    return action.apply(secret);
                }
            }
        };
    }

    private static final class RecordingEmbeddingFactory implements EmbeddingModelFactory {
        private final AtomicReference<ModelRuntimeSpecification> specification = new AtomicReference<>();

        @Override public ProviderKind providerKind() { return ProviderKind.OPENAI_COMPATIBLE; }
        @Override public EmbeddingModelPort create(ModelRuntimeSpecification value) { throw new AssertionError("不得解析长期凭据引用"); }
        @Override public EmbeddingModelPort create(ModelRuntimeSpecification value, SecretValue credential) {
            specification.set(value);
            return request -> new EmbeddingBatch(request.deployment(), List.of(new EmbeddingVector(0, new float[1024])));
        }
    }

    private static final class RecordingChatFactory implements ChatModelFactory {
        private final AtomicReference<ModelRuntimeSpecification> specification = new AtomicReference<>();

        @Override public ProviderKind providerKind() { return ProviderKind.OPENAI_COMPATIBLE; }
        @Override public ChatModelPort create(ModelRuntimeSpecification value) { throw new AssertionError("不得解析长期凭据引用"); }
        @Override public ChatModelPort create(ModelRuntimeSpecification value, SecretValue credential) {
            specification.set(value);
            return new ChatModelPort() {
                @Override public ChatResult chat(ChatRequest request) {
                    return new ChatResult(request.deployment(), "已受控回答", "STOP", ModelUsage.UNKNOWN);
                }
                @Override public java.util.concurrent.Flow.Publisher<com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatChunk> stream(ChatRequest request) {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }

    private static final class ConfigurationRepository implements ModelConfigurationRepository {
        private final ControlledModelCatalogEntry entry;

        private ConfigurationRepository() {
            this(ControlledModelCatalogEntry.create(
                            new ControlledModelCatalogEntryId(3001L), ProviderKind.OPENAI_COMPATIBLE,
                            URI.create("https://example.com/v1"), "text-embedding-v1", ModelType.EMBEDDING,
                            ModelRuntimeCapabilities.embedding(8192, 1024), Instant.parse("2026-07-28T00:00:00Z"))
                    .publish());
        }

        private ConfigurationRepository(ControlledModelCatalogEntry entry) {
            this.entry = entry;
        }

        @Override public Optional<ControlledModelCatalogEntry> findCatalogEntry(ControlledModelCatalogEntryId id) { return entry.id().equals(id) ? Optional.of(entry) : Optional.empty(); }
        @Override public void saveCatalogEntry(ControlledModelCatalogEntry value) { }
        @Override public void updateCatalogEntry(ControlledModelCatalogEntry value) { }
        @Override public Optional<ModelConfiguration> findConfiguration(ModelConfigurationId id) { return Optional.empty(); }
        @Override public void updateConfiguration(ModelConfiguration value) { }
        @Override public void save(ModelConfiguration value) { }
    }

    private static final class ChatConfigurationRepository implements ModelConfigurationRepository {
        private final ControlledModelCatalogEntry entry = ControlledModelCatalogEntry.create(
                        new ControlledModelCatalogEntryId(3002L), ProviderKind.OPENAI_COMPATIBLE,
                        URI.create("https://example.com/v1"), "chat-v1", ModelType.CHAT,
                        ModelRuntimeCapabilities.chat(8192, 512), Instant.parse("2026-07-28T00:00:00Z"))
                .publish();

        @Override public Optional<ControlledModelCatalogEntry> findCatalogEntry(ControlledModelCatalogEntryId id) { return entry.id().equals(id) ? Optional.of(entry) : Optional.empty(); }
        @Override public void saveCatalogEntry(ControlledModelCatalogEntry value) { }
        @Override public void updateCatalogEntry(ControlledModelCatalogEntry value) { }
        @Override public Optional<ModelConfiguration> findConfiguration(ModelConfigurationId id) { return Optional.empty(); }
        @Override public void updateConfiguration(ModelConfiguration value) { }
        @Override public void save(ModelConfiguration value) { }
    }
}
