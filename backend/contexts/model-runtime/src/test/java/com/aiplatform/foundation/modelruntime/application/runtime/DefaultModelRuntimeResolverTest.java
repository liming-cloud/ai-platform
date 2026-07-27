package com.aiplatform.foundation.modelruntime.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.api.extension.ChatModelFactory;
import com.aiplatform.foundation.modelruntime.api.invocation.ChatModelPort;
import com.aiplatform.foundation.modelruntime.api.model.ChatChunk;
import com.aiplatform.foundation.modelruntime.api.model.ChatRequest;
import com.aiplatform.foundation.modelruntime.api.model.ChatResult;
import com.aiplatform.foundation.modelruntime.api.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.api.model.ModelRuntimeSpecification;
import com.aiplatform.foundation.modelruntime.api.model.ModelType;
import com.aiplatform.foundation.modelruntime.api.model.ProviderKind;
import com.aiplatform.foundation.modelruntime.application.port.out.ModelDeploymentCreation;
import com.aiplatform.foundation.modelruntime.application.port.out.ModelDeploymentCreationOutcome;
import com.aiplatform.foundation.modelruntime.application.port.out.ModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.application.port.out.ModelDeploymentSlice;
import com.aiplatform.foundation.modelruntime.application.port.out.ProviderConnectionCreation;
import com.aiplatform.foundation.modelruntime.application.port.out.ProviderConnectionCreationOutcome;
import com.aiplatform.foundation.modelruntime.application.port.out.ProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.application.port.out.ProviderConnectionSlice;
import com.aiplatform.foundation.modelruntime.domain.model.AuthenticationType;
import com.aiplatform.foundation.modelruntime.domain.model.ModelDeployment;
import com.aiplatform.foundation.modelruntime.domain.model.ModelDeploymentId;
import com.aiplatform.foundation.modelruntime.domain.model.ModelDeploymentStatus;
import com.aiplatform.foundation.modelruntime.domain.model.ProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.model.ProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.model.ProviderConnectionStatus;
import com.aiplatform.foundation.security.secret.SecretReference;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DefaultModelRuntimeResolverTest {

    private static final TenantId TENANT =
            new TenantId(100000000000000001L);
    private static final ProviderConnectionId PROVIDER_ID =
            new ProviderConnectionId(UUID.fromString("20000000-0000-0000-0000-000000000001"));
    private static final ModelDeploymentId DEPLOYMENT_ID =
            new ModelDeploymentId(UUID.fromString("30000000-0000-0000-0000-000000000001"));
    private static final Instant NOW = Instant.parse("2026-07-27T00:00:00Z");

    @Test
    void 应按租户和冻结修订复用已发布对话运行时() {
        var constructions = new AtomicInteger();
        var model = new StubChatModel();
        ChatModelFactory factory = new ChatModelFactory() {
            @Override
            public ProviderKind providerKind() {
                return ProviderKind.OPENAI_COMPATIBLE;
            }

            @Override
            public ChatModelPort create(ModelRuntimeSpecification specification) {
                constructions.incrementAndGet();
                assertEquals(TENANT, specification.tenantId());
                assertEquals(7, specification.providerRevision());
                assertEquals(3, specification.deploymentRevision());
                return model;
            }
        };
        var resolver = resolver(publishedChat(7, 3), verifiedProvider(7), factory);

        var first = resolver.resolveChat(TENANT, DEPLOYMENT_ID.value());
        var second = resolver.resolveChat(TENANT, DEPLOYMENT_ID.value());

        assertSame(model, first);
        assertSame(first, second);
        assertEquals(1, constructions.get());
    }

    @Test
    void 定向淘汰部署后应重建运行时对象() {
        var constructions = new AtomicInteger();
        ChatModelFactory factory = new ChatModelFactory() {
            @Override
            public ProviderKind providerKind() {
                return ProviderKind.OPENAI_COMPATIBLE;
            }

            @Override
            public ChatModelPort create(ModelRuntimeSpecification specification) {
                constructions.incrementAndGet();
                return new StubChatModel();
            }
        };
        var resolver = resolver(publishedChat(7, 3), verifiedProvider(7), factory);
        resolver.resolveChat(TENANT, DEPLOYMENT_ID.value());

        resolver.evictDeployment(TENANT, DEPLOYMENT_ID.value());
        resolver.resolveChat(TENANT, DEPLOYMENT_ID.value());

        assertEquals(2, constructions.get());
    }

    @Test
    void 未发布部署或供应商修订不匹配时必须拒绝构造() {
        var factory = new NoOpChatFactory();
        var draft = ModelDeployment.reconstitute(
                TENANT,
                DEPLOYMENT_ID,
                PROVIDER_ID,
                7,
                "gpt-compatible",
                "兼容对话模型",
                ModelType.CHAT,
                Set.of(ModelCapability.CHAT),
                8192,
                1024,
                null,
                ModelDeploymentStatus.VERIFIED,
                2,
                NOW);

        assertThrows(
                ModelRuntimeUnavailableException.class,
                () -> resolver(draft, verifiedProvider(7), factory)
                        .resolveChat(TENANT, DEPLOYMENT_ID.value()));
        assertThrows(
                ModelRuntimeUnavailableException.class,
                () -> resolver(publishedChat(7, 3), verifiedProvider(8), factory)
                        .resolveChat(TENANT, DEPLOYMENT_ID.value()));
    }

    private static DefaultModelRuntimeResolver resolver(
            ModelDeployment deployment,
            ProviderConnection provider,
            ChatModelFactory factory) {
        return new DefaultModelRuntimeResolver(
                new SingleDeploymentRepository(deployment),
                new SingleProviderRepository(provider),
                List.of(factory),
                List.of(),
                8,
                Duration.ofMinutes(5),
                Clock.systemUTC());
    }

    private static ModelDeployment publishedChat(long providerRevision, long revision) {
        return ModelDeployment.reconstitute(
                TENANT,
                DEPLOYMENT_ID,
                PROVIDER_ID,
                providerRevision,
                "gpt-compatible",
                "兼容对话模型",
                ModelType.CHAT,
                Set.of(ModelCapability.CHAT, ModelCapability.STREAMING_CHAT),
                8192,
                1024,
                null,
                ModelDeploymentStatus.PUBLISHED,
                revision,
                NOW);
    }

    private static ProviderConnection verifiedProvider(long revision) {
        return ProviderConnection.reconstitute(
                TENANT,
                PROVIDER_ID,
                ProviderKind.OPENAI_COMPATIBLE,
                "开放兼容连接",
                URI.create("https://models.example.com/v1"),
                "cn-test",
                AuthenticationType.API_KEY,
                new SecretReference("secret://vault/tenant/openai"),
                ProviderConnectionStatus.VERIFIED,
                revision,
                true,
                "OPENAI_COMPATIBLE_OK",
                NOW,
                NOW);
    }

    private static final class StubChatModel implements ChatModelPort {

        @Override
        public ChatResult chat(ChatRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Flow.Publisher<ChatChunk> stream(ChatRequest request) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class NoOpChatFactory implements ChatModelFactory {

        @Override
        public ProviderKind providerKind() {
            return ProviderKind.OPENAI_COMPATIBLE;
        }

        @Override
        public ChatModelPort create(ModelRuntimeSpecification specification) {
            return new StubChatModel();
        }
    }

    private record SingleDeploymentRepository(ModelDeployment deployment)
            implements ModelDeploymentRepository {

        @Override
        public ModelDeploymentCreationOutcome create(
                ModelDeploymentCreation creation,
                int tenantLimit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ModelDeployment> findById(
                TenantId tenantId,
                ModelDeploymentId deploymentId) {
            return deployment.tenantId().equals(tenantId)
                            && deployment.id().equals(deploymentId)
                    ? Optional.of(deployment)
                    : Optional.empty();
        }

        @Override
        public ModelDeploymentSlice findPage(TenantId tenantId, String cursor, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean update(ModelDeployment deployment, long expectedVersion) {
            throw new UnsupportedOperationException();
        }
    }

    private record SingleProviderRepository(ProviderConnection provider)
            implements ProviderConnectionRepository {

        @Override
        public ProviderConnectionCreationOutcome create(
                ProviderConnectionCreation creation,
                int tenantLimit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ProviderConnection> findById(
                TenantId tenantId,
                ProviderConnectionId connectionId) {
            return provider.tenantId().equals(tenantId)
                            && provider.id().equals(connectionId)
                    ? Optional.of(provider)
                    : Optional.empty();
        }

        @Override
        public ProviderConnectionSlice findPage(TenantId tenantId, String cursor, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean update(ProviderConnection connection, long expectedVersion) {
            throw new UnsupportedOperationException();
        }
    }
}
