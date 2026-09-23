package com.aiplatform.foundation.modelruntime.application.runtime;

import com.aiplatform.foundation.modelruntime.application.configuration.facade.UsableModelConfigurationSnapshot;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.ModelConfigurationUsageContext;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.UsableModelConfigurationLookup;
import com.aiplatform.foundation.modelruntime.application.configuration.port.out.ModelConfigurationCredentialUser;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.common.ModelRuntimeSpecification;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.GovernedChatRuntime;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.GovernedEmbeddingRuntime;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatRequest;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatResult;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingBatch;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingRequest;
import com.aiplatform.foundation.modelruntime.application.runtime.port.out.EmbeddingModelFactory;
import com.aiplatform.foundation.modelruntime.application.runtime.port.out.ChatModelFactory;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryStatus;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeploymentStatus;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionStatus;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationRepository;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 以单次调用生命周期执行受控模型配置，避免含 API Key 的模型对象进入运行时缓存。
 */
public final class ModelConfigurationRuntimeService
        implements GovernedEmbeddingRuntime, GovernedChatRuntime {

    private final UsableModelConfigurationLookup configurations;
    private final ModelConfigurationRepository catalog;
    private final ModelConfigurationCredentialUser credentials;
    private final Map<ProviderKind, EmbeddingModelFactory> embeddingFactories;
    private final Map<ProviderKind, ChatModelFactory> chatFactories;
    private final PlatformModelDeploymentRepository platformDeployments;
    private final PlatformProviderConnectionRepository platformConnections;

    public ModelConfigurationRuntimeService(
            UsableModelConfigurationLookup configurations,
            ModelConfigurationRepository catalog,
            ModelConfigurationCredentialUser credentials,
            List<EmbeddingModelFactory> embeddingFactories) {
        this(configurations, catalog, credentials, embeddingFactories, List.of(), null, null);
    }

    public ModelConfigurationRuntimeService(
            UsableModelConfigurationLookup configurations,
            ModelConfigurationRepository catalog,
            ModelConfigurationCredentialUser credentials,
            List<EmbeddingModelFactory> embeddingFactories,
            List<ChatModelFactory> chatFactories) {
        this(configurations, catalog, credentials, embeddingFactories, chatFactories, null, null);
    }

    /** 生产运行时必须同时复核目录冻结的平台供应链，停用或修订漂移即失败关闭。 */
    public ModelConfigurationRuntimeService(
            UsableModelConfigurationLookup configurations,
            ModelConfigurationRepository catalog,
            ModelConfigurationCredentialUser credentials,
            List<EmbeddingModelFactory> embeddingFactories,
            List<ChatModelFactory> chatFactories,
            PlatformModelDeploymentRepository platformDeployments,
            PlatformProviderConnectionRepository platformConnections) {
        this.configurations = Objects.requireNonNull(configurations, "可用模型配置查询不能为空");
        this.catalog = Objects.requireNonNull(catalog, "受控模型目录仓储不能为空");
        this.credentials = Objects.requireNonNull(credentials, "模型凭据使用端口不能为空");
        this.embeddingFactories = indexFactories(embeddingFactories);
        this.chatFactories = indexChatFactories(chatFactories);
        this.platformDeployments = platformDeployments;
        this.platformConnections = platformConnections;
    }

    /**
     * 在本次对话调用内复核配置快照、解密凭据、构造模型并立即执行请求。
     */
    @Override
    public ChatResult chat(
            TrustedRequestContext context,
            UsableModelConfigurationSnapshot expected,
            ChatRequest request) {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(expected, "模型配置快照不能为空");
        Objects.requireNonNull(request, "对话请求不能为空");
        requireChatRequest(expected, request);
        var current = configurations.find(
                        context,
                        expected.configurationId(),
                        com.aiplatform.foundation.modelruntime.application.configuration.facade.ModelType.CHAT)
                .filter(expected::equals)
                .orElseThrow(() -> unavailable("模型配置不可用或冻结快照已变化"));
        return invokeChat(context.tenantId(), current, request);
    }

    /**
     * 在本次嵌入调用内复核配置快照、解密凭据、构造模型并立即执行请求。
     */
    @Override
    public EmbeddingBatch embed(
            TrustedRequestContext context,
            UsableModelConfigurationSnapshot expected,
            EmbeddingRequest request) {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(expected, "模型配置快照不能为空");
        Objects.requireNonNull(request, "嵌入请求不能为空");
        requireEmbeddingRequest(expected, request);
        var current = configurations.find(
                        context,
                        expected.configurationId(),
                        com.aiplatform.foundation.modelruntime.application.configuration.facade.ModelType.EMBEDDING)
                .filter(expected::equals)
                .orElseThrow(() -> unavailable("模型配置不可用或冻结快照已变化"));
        return invoke(context.tenantId(), current, request);
    }

    @Override
    public EmbeddingBatch embed(
            ModelConfigurationUsageContext context,
            UsableModelConfigurationSnapshot expected,
            EmbeddingRequest request) {
        Objects.requireNonNull(context, "模型配置使用上下文不能为空");
        Objects.requireNonNull(expected, "模型配置快照不能为空");
        Objects.requireNonNull(request, "嵌入请求不能为空");
        requireEmbeddingRequest(expected, request);
        var current = configurations.find(
                        context,
                        expected.configurationId(),
                        com.aiplatform.foundation.modelruntime.application.configuration.facade.ModelType.EMBEDDING)
                .filter(expected::equals)
                .orElseThrow(() -> unavailable("模型配置不可用或冻结快照已变化"));
        return invoke(context.tenantId(), current, request);
    }

    private EmbeddingBatch invoke(
            TenantId tenantId,
            UsableModelConfigurationSnapshot current,
            EmbeddingRequest request) {
        var entry = catalog.findCatalogEntry(new com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryId(
                        current.catalogEntryId().value()))
                .filter(value -> value.status() == ControlledModelCatalogEntryStatus.PUBLISHED)
                .filter(value -> value.version() == current.catalogEntryRevision())
                .filter(value -> value.modelType() == ModelType.EMBEDDING)
                .orElseThrow(() -> unavailable("受控模型目录已变化或不可用"));
        requireCurrentPlatformSource(entry);
        var factory = embeddingFactories.get(entry.providerKind());
        if (factory == null) {
            throw unavailable("当前供应商没有嵌入模型扩展");
        }
        var specification = new ModelRuntimeSpecification(
                tenantId,
                current.catalogEntryId().value(),
                current.catalogEntryRevision(),
                com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind.valueOf(
                        entry.providerKind().name()),
                entry.endpoint(),
                new CredentialReference("secret://governed-model-configuration/" + current.configurationId().value()).value(),
                current.configurationId().value(),
                current.configurationRevision(),
                entry.modelCode(),
                com.aiplatform.foundation.modelruntime.application.extension.facade.ModelType.EMBEDDING,
                current.capabilities().contextWindow(),
                null,
                current.capabilities().embeddingDimension());
        return credentials.useCredential(
                new com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId(
                        current.configurationId().value()),
                secret -> factory.create(specification, secret).embed(request));
    }

    private ChatResult invokeChat(
            TenantId tenantId,
            UsableModelConfigurationSnapshot current,
            ChatRequest request) {
        var entry = catalog.findCatalogEntry(new com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryId(
                        current.catalogEntryId().value()))
                .filter(value -> value.status() == ControlledModelCatalogEntryStatus.PUBLISHED)
                .filter(value -> value.version() == current.catalogEntryRevision())
                .filter(value -> value.modelType() == ModelType.CHAT)
                .orElseThrow(() -> unavailable("受控模型目录已变化或不可用"));
        requireCurrentPlatformSource(entry);
        var factory = chatFactories.get(entry.providerKind());
        if (factory == null) {
            throw unavailable("当前供应商没有对话模型扩展");
        }
        var specification = new ModelRuntimeSpecification(
                tenantId,
                current.catalogEntryId().value(),
                current.catalogEntryRevision(),
                com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind.valueOf(
                        entry.providerKind().name()),
                entry.endpoint(),
                new CredentialReference("secret://governed-model-configuration/" + current.configurationId().value()).value(),
                current.configurationId().value(),
                current.configurationRevision(),
                entry.modelCode(),
                com.aiplatform.foundation.modelruntime.application.extension.facade.ModelType.CHAT,
                current.capabilities().contextWindow(),
                current.capabilities().outputLimit(),
                null);
        return credentials.useCredential(
                new com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId(
                        current.configurationId().value()),
                secret -> factory.create(specification, secret).chat(request));
    }

    private static void requireEmbeddingRequest(
            UsableModelConfigurationSnapshot expected,
            EmbeddingRequest request) {
        if (expected.modelType() != com.aiplatform.foundation.modelruntime.application.configuration.facade.ModelType.EMBEDDING
                || request.deployment().deploymentId() != expected.configurationId().value()
                || request.deployment().revision() != expected.configurationRevision()
                || request.deployment().dimension()
                        != expected.capabilities().embeddingDimension()) {
            throw unavailable("嵌入请求与冻结模型配置不一致");
        }
    }

    private static void requireChatRequest(
            UsableModelConfigurationSnapshot expected,
            ChatRequest request) {
        if (expected.modelType() != com.aiplatform.foundation.modelruntime.application.configuration.facade.ModelType.CHAT
                || request.deployment().deploymentId() != expected.configurationId().value()
                || request.deployment().revision() != expected.configurationRevision()
                || request.maxOutputTokens() < 1
                || request.maxOutputTokens() > expected.capabilities().outputLimit()) {
            throw unavailable("对话请求与冻结模型配置不一致");
        }
    }

    private static Map<ProviderKind, EmbeddingModelFactory> indexFactories(
            List<EmbeddingModelFactory> factories) {
        Objects.requireNonNull(factories, "嵌入模型工厂列表不能为空");
        var indexed = new EnumMap<ProviderKind, EmbeddingModelFactory>(ProviderKind.class);
        for (var factory : factories) {
            Objects.requireNonNull(factory, "嵌入模型工厂列表不能包含空值");
            if (indexed.put(ProviderKind.valueOf(factory.providerKind().name()), factory) != null) {
                throw new IllegalArgumentException("同一供应商不能注册多个嵌入模型工厂");
            }
        }
        return Map.copyOf(indexed);
    }

    private static Map<ProviderKind, ChatModelFactory> indexChatFactories(
            List<ChatModelFactory> factories) {
        Objects.requireNonNull(factories, "对话模型工厂列表不能为空");
        var indexed = new EnumMap<ProviderKind, ChatModelFactory>(ProviderKind.class);
        for (var factory : factories) {
            Objects.requireNonNull(factory, "对话模型工厂列表不能包含空值");
            if (indexed.put(ProviderKind.valueOf(factory.providerKind().name()), factory) != null) {
                throw new IllegalArgumentException("同一供应商不能注册多个对话模型工厂");
            }
        }
        return Map.copyOf(indexed);
    }

    private static ModelRuntimeUnavailableException unavailable(String message) {
        return new ModelRuntimeUnavailableException(message);
    }

    private void requireCurrentPlatformSource(
            com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntry entry) {
        if (platformDeployments == null || platformConnections == null) {
            return;
        }
        var deploymentId = entry.sourceDeploymentId();
        var deploymentRevision = entry.sourceDeploymentRevision();
        if (deploymentId == null || deploymentRevision == null) {
            throw unavailable("受控模型目录缺少平台部署快照");
        }
        var deployment = platformDeployments.findById(deploymentId)
                .filter(value -> value.version() == deploymentRevision)
                .filter(value -> value.status() == PlatformModelDeploymentStatus.PUBLISHED)
                .filter(value -> value.verificationExpiresAt() != null
                        && value.verificationExpiresAt().isAfter(java.time.Instant.now()))
                .orElseThrow(() -> unavailable("平台模型部署已变化或不可用"));
        platformConnections.findById(deployment.providerConnectionId())
                .filter(value -> value.version() == deployment.providerConnectionRevision())
                .filter(value -> value.status() == ProviderConnectionStatus.VERIFIED)
                .orElseThrow(() -> unavailable("平台供应商连接已变化或不可用"));
    }
}
