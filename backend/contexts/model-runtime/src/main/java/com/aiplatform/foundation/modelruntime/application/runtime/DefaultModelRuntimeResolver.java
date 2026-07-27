package com.aiplatform.foundation.modelruntime.application.runtime;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.api.extension.ChatModelFactory;
import com.aiplatform.foundation.modelruntime.api.extension.EmbeddingModelFactory;
import com.aiplatform.foundation.modelruntime.api.invocation.ChatModelPort;
import com.aiplatform.foundation.modelruntime.api.invocation.EmbeddingModelPort;
import com.aiplatform.foundation.modelruntime.api.invocation.ModelRuntimeResolver;
import com.aiplatform.foundation.modelruntime.api.model.common.ModelRuntimeSpecification;
import com.aiplatform.foundation.modelruntime.api.model.common.ModelType;
import com.aiplatform.foundation.modelruntime.api.model.provider.ProviderKind;
import com.aiplatform.foundation.modelruntime.application.port.out.ModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.application.port.out.ProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.domain.model.ModelDeployment;
import com.aiplatform.foundation.modelruntime.domain.model.ModelDeploymentId;
import com.aiplatform.foundation.modelruntime.domain.model.ModelDeploymentStatus;
import com.aiplatform.foundation.modelruntime.domain.model.ProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.model.ProviderConnectionStatus;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 从已发布部署和冻结供应商修订构造并缓存模型运行时对象。
 */
public final class DefaultModelRuntimeResolver implements ModelRuntimeResolver {

    private final ModelDeploymentRepository deployments;
    private final ProviderConnectionRepository providers;
    private final Map<ProviderKind, ChatModelFactory> chatFactories;
    private final Map<ProviderKind, EmbeddingModelFactory> embeddingFactories;
    private final RevisionedRuntimeCache<RuntimeCacheKey, ChatModelPort> chatCache;
    private final RevisionedRuntimeCache<RuntimeCacheKey, EmbeddingModelPort> embeddingCache;

    public DefaultModelRuntimeResolver(
            ModelDeploymentRepository deployments,
            ProviderConnectionRepository providers,
            List<ChatModelFactory> chatFactories,
            List<EmbeddingModelFactory> embeddingFactories,
            int maximumCacheSize,
            Duration idleTimeout,
            Clock clock) {
        this.deployments = Objects.requireNonNull(deployments, "模型部署仓储不能为空");
        this.providers = Objects.requireNonNull(providers, "供应商连接仓储不能为空");
        this.chatFactories = indexFactories(
                chatFactories,
                ChatModelFactory::providerKind,
                "对话模型工厂");
        this.embeddingFactories = indexFactories(
                embeddingFactories,
                EmbeddingModelFactory::providerKind,
                "嵌入模型工厂");
        this.chatCache = new RevisionedRuntimeCache<>(maximumCacheSize, idleTimeout, clock);
        this.embeddingCache = new RevisionedRuntimeCache<>(maximumCacheSize, idleTimeout, clock);
    }

    @Override
    public ChatModelPort resolveChat(TenantId tenantId, long deploymentId) {
        var resolved = resolve(tenantId, deploymentId, ModelType.CHAT);
        var factory = chatFactories.get(resolved.provider().providerKind());
        if (factory == null) {
            throw unavailable("当前供应商没有对话模型扩展");
        }
        return chatCache.get(
                resolved.key(),
                () -> factory.create(resolved.specification()));
    }

    @Override
    public EmbeddingModelPort resolveEmbedding(TenantId tenantId, long deploymentId) {
        var resolved = resolve(tenantId, deploymentId, ModelType.EMBEDDING);
        var factory = embeddingFactories.get(resolved.provider().providerKind());
        if (factory == null) {
            throw unavailable("当前供应商没有嵌入模型扩展");
        }
        return embeddingCache.get(
                resolved.key(),
                () -> factory.create(resolved.specification()));
    }

    @Override
    public void evictProvider(TenantId tenantId, long providerConnectionId) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        if (providerConnectionId <= 0) {
            throw new IllegalArgumentException("供应商连接标识必须是正整数");
        }
        chatCache.evictIf(key -> key.matchesProvider(tenantId, providerConnectionId));
        embeddingCache.evictIf(key -> key.matchesProvider(tenantId, providerConnectionId));
    }

    @Override
    public void evictDeployment(TenantId tenantId, long deploymentId) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        if (deploymentId <= 0) {
            throw new IllegalArgumentException("模型部署标识必须是正整数");
        }
        chatCache.evictIf(key -> key.matchesDeployment(tenantId, deploymentId));
        embeddingCache.evictIf(key -> key.matchesDeployment(tenantId, deploymentId));
    }

    private ResolvedRuntime resolve(
            TenantId tenantId,
            long deploymentId,
            ModelType expectedType) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        if (deploymentId <= 0) {
            throw new IllegalArgumentException("模型部署标识必须是正整数");
        }
        var deployment = deployments
                .findById(tenantId, new ModelDeploymentId(deploymentId))
                .orElseThrow(() -> unavailable("模型部署不存在"));
        if (deployment.status() != ModelDeploymentStatus.PUBLISHED) {
            throw unavailable("只有已发布模型部署可以进入运行时");
        }
        if (deployment.modelType() != expectedType) {
            throw unavailable("模型部署类型与调用类型不一致");
        }
        var provider = providers
                .findById(tenantId, deployment.providerConnectionId())
                .orElseThrow(() -> unavailable("供应商连接不存在"));
        if (provider.status() != ProviderConnectionStatus.VERIFIED
                || provider.version() != deployment.providerConnectionRevision()) {
            throw unavailable("供应商连接状态或修订与已发布部署不一致");
        }

        var key = new RuntimeCacheKey(
                tenantId,
                provider.id().value(),
                provider.version(),
                deployment.id().value(),
                deployment.version(),
                expectedType);
        return new ResolvedRuntime(
                provider,
                key,
                specification(deployment, provider));
    }

    private static ModelRuntimeSpecification specification(
            ModelDeployment deployment,
            ProviderConnection provider) {
        return new ModelRuntimeSpecification(
                deployment.tenantId(),
                provider.id().value(),
                provider.version(),
                provider.providerKind(),
                provider.endpoint(),
                provider.credentialReference(),
                deployment.id().value(),
                deployment.version(),
                deployment.modelCode(),
                deployment.modelType(),
                deployment.contextWindow(),
                deployment.outputLimit(),
                deployment.embeddingDimension());
    }

    private static <T> Map<ProviderKind, T> indexFactories(
            List<T> factories,
            Function<T, ProviderKind> providerKind,
            String factoryName) {
        Objects.requireNonNull(factories, factoryName + "列表不能为空");
        var indexed = new EnumMap<ProviderKind, T>(ProviderKind.class);
        for (var factory : factories) {
            Objects.requireNonNull(factory, factoryName + "列表不能包含空值");
            var previous = indexed.put(providerKind.apply(factory), factory);
            if (previous != null) {
                throw new IllegalArgumentException("同一供应商不能注册多个" + factoryName);
            }
        }
        return Map.copyOf(indexed);
    }

    private static ModelRuntimeUnavailableException unavailable(String message) {
        return new ModelRuntimeUnavailableException(message);
    }

    private record ResolvedRuntime(
            ProviderConnection provider,
            RuntimeCacheKey key,
            ModelRuntimeSpecification specification) {
    }

    private record RuntimeCacheKey(
            TenantId tenantId,
            long providerConnectionId,
            long providerRevision,
            long deploymentId,
            long deploymentRevision,
            ModelType modelType) {

        private boolean matchesProvider(TenantId tenant, long providerId) {
            return tenantId.equals(tenant) && providerConnectionId == providerId;
        }

        private boolean matchesDeployment(TenantId tenant, long modelDeploymentId) {
            return tenantId.equals(tenant) && deploymentId == modelDeploymentId;
        }
    }
}
