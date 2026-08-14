package com.aiplatform.foundation.modelruntime.application.configuration.service;

import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryStatus;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfiguration;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationOwnerId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationScope;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationRepository;
import com.aiplatform.foundation.modelruntime.application.configuration.exception.ModelConfigurationNotFoundException;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeploymentStatus;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionStatus;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import java.time.Clock;
import java.util.Objects;

/**
 * 从可信请求上下文派生模型配置归属，并限制只能选择已发布受控目录。
 */
public final class ModelConfigurationService {

    private static final PermissionCode PLATFORM_WRITE = new PermissionCode("model-platform:write");
    private static final PermissionCode MODEL_WRITE = new PermissionCode("model:write");

    private final ModelConfigurationRepository repository;
    private final Clock clock;
    private final EntityIdGenerator identifiers;
    private final PlatformModelDeploymentRepository platformDeployments;
    private final PlatformProviderConnectionRepository platformConnections;

    public ModelConfigurationService(
            ModelConfigurationRepository repository,
            Clock clock,
            EntityIdGenerator identifiers) {
        this.repository = Objects.requireNonNull(repository, "模型配置仓储不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
        this.platformDeployments = null;
        this.platformConnections = null;
    }

    /**
     * 平台供应链是新模型配置的唯一目录来源，失效来源不得被重新绑定或启用。
     */
    public ModelConfigurationService(
            ModelConfigurationRepository repository,
            Clock clock,
            EntityIdGenerator identifiers,
            PlatformModelDeploymentRepository platformDeployments,
            PlatformProviderConnectionRepository platformConnections) {
        this.repository = Objects.requireNonNull(repository, "模型配置仓储不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
        this.platformDeployments = Objects.requireNonNull(platformDeployments, "平台模型部署仓储不能为空");
        this.platformConnections = Objects.requireNonNull(platformConnections, "平台供应商连接仓储不能为空");
    }

    public ModelConfiguration create(
            TrustedRequestContext context,
            ModelConfigurationScope scope,
            String displayName,
            ControlledModelCatalogEntryId catalogEntryId) {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(scope, "模型配置范围不能为空");
        var catalog = repository.findCatalogEntry(Objects.requireNonNull(catalogEntryId, "受控目录条目标识不能为空"))
                .orElseThrow(() -> new IllegalStateException("受控模型目录条目不存在"));
        if (catalog.status() != ControlledModelCatalogEntryStatus.PUBLISHED) {
            throw new IllegalStateException("只能选择已发布受控模型目录条目");
        }
        requireCurrentPlatformSource(catalog);
        return switch (scope) {
            case PLATFORM -> createPlatform(context, displayName, catalogEntryId);
            case TENANT -> createTenant(context, displayName, catalogEntryId);
            case PERSONAL -> createPersonal(context, displayName, catalogEntryId);
        };
    }

    /**
     * 在轮换前以可信身份验证配置的管理范围，跨范围资源统一按不存在处理。
     */
    public ModelConfiguration loadForCredentialRotation(
            TrustedRequestContext context, ModelConfigurationId configurationId) {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        var configuration = repository.findConfiguration(
                        Objects.requireNonNull(configurationId, "模型配置标识不能为空"))
                .orElseThrow(ModelConfigurationNotFoundException::new);
        if (!canManage(context, configuration)) {
            throw new ModelConfigurationNotFoundException();
        }
        return configuration;
    }

    public ModelConfiguration activate(
            TrustedRequestContext context, ModelConfigurationId configurationId) {
        var configuration = loadForCredentialRotation(context, configurationId);
        var catalog = repository.findCatalogEntry(configuration.catalogEntryId())
                .filter(value -> value.status() == ControlledModelCatalogEntryStatus.PUBLISHED)
                .orElseThrow(ModelConfigurationNotFoundException::new);
        requireCurrentPlatformSource(catalog);
        var active = configuration.activate();
        repository.updateConfiguration(active);
        return active;
    }

    /**
     * 以与轮换相同的管理权限停用配置，确保既有授权不再允许使用。
     */
    public ModelConfiguration disable(
            TrustedRequestContext context, ModelConfigurationId configurationId) {
        var configuration = loadForCredentialRotation(context, configurationId);
        var disabled = configuration.disable();
        if (disabled != configuration) {
            repository.updateConfiguration(disabled);
        }
        return disabled;
    }

    /**
     * 判断可信主体能否维护配置；此判断不授予凭据读取能力。
     */
    public boolean mayManage(TrustedRequestContext context, ModelConfiguration configuration) {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(configuration, "模型配置不能为空");
        return canManage(context, configuration);
    }

    private ModelConfiguration createPlatform(
            TrustedRequestContext context,
            String displayName,
            ControlledModelCatalogEntryId catalogEntryId) {
        requirePermission(context, PLATFORM_WRITE);
        return save(ModelConfiguration.create(
                new ModelConfigurationId(identifiers.nextId()),
                ModelConfigurationScope.PLATFORM,
                null,
                null,
                displayName,
                catalogEntryId,
                clock.instant()));
    }

    private ModelConfiguration createTenant(
            TrustedRequestContext context,
            String displayName,
            ControlledModelCatalogEntryId catalogEntryId) {
        requirePermission(context, MODEL_WRITE);
        return save(ModelConfiguration.create(
                new ModelConfigurationId(identifiers.nextId()),
                ModelConfigurationScope.TENANT,
                context.tenantId(),
                null,
                displayName,
                catalogEntryId,
                clock.instant()));
    }

    private ModelConfiguration createPersonal(
            TrustedRequestContext context,
            String displayName,
            ControlledModelCatalogEntryId catalogEntryId) {
        requirePermission(context, MODEL_WRITE);
        return save(ModelConfiguration.create(
                new ModelConfigurationId(identifiers.nextId()),
                ModelConfigurationScope.PERSONAL,
                context.tenantId(),
                new ModelConfigurationOwnerId(context.membershipId().value()),
                displayName,
                catalogEntryId,
                clock.instant()));
    }

    private ModelConfiguration save(ModelConfiguration configuration) {
        repository.save(configuration);
        return configuration;
    }

    private static void requirePermission(TrustedRequestContext context, PermissionCode permission) {
        if (!context.hasPermission(permission)) {
            throw new IllegalStateException("当前成员没有模型配置写入权限");
        }
    }

    private void requireCurrentPlatformSource(
            com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntry catalog) {
        if (platformDeployments == null || platformConnections == null) {
            return;
        }
        var deploymentId = catalog.sourceDeploymentId();
        var deploymentRevision = catalog.sourceDeploymentRevision();
        if (deploymentId == null || deploymentRevision == null) {
            throw new IllegalStateException("受控模型目录缺少平台部署快照来源");
        }
        var deployment = platformDeployments.findById(deploymentId)
                .filter(value -> value.version() == deploymentRevision)
                .filter(value -> value.status() == PlatformModelDeploymentStatus.PUBLISHED)
                .filter(value -> value.verificationExpiresAt() != null
                        && value.verificationExpiresAt().isAfter(clock.instant()))
                .orElseThrow(() -> new IllegalStateException("平台模型部署已变化或不可用"));
        platformConnections.findById(deployment.providerConnectionId())
                .filter(value -> value.version() == deployment.providerConnectionRevision())
                .filter(value -> value.status() == ProviderConnectionStatus.VERIFIED)
                .orElseThrow(() -> new IllegalStateException("平台供应商连接已变化或不可用"));
    }

    private static boolean canManage(
            TrustedRequestContext context, ModelConfiguration configuration) {
        return switch (configuration.scope()) {
            case PLATFORM -> context.hasPermission(PLATFORM_WRITE);
            case TENANT -> context.hasPermission(MODEL_WRITE)
                    && configuration.tenantId().equals(context.tenantId());
            case PERSONAL -> context.hasPermission(MODEL_WRITE)
                    && configuration.tenantId().equals(context.tenantId())
                    && configuration.ownerMembershipId().value() == context.membershipId().value();
        };
    }
}
