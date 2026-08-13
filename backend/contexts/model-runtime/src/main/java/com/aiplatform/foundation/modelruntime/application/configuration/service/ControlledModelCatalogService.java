package com.aiplatform.foundation.modelruntime.application.configuration.service;

import com.aiplatform.foundation.modelruntime.application.configuration.exception.ModelConfigurationNotFoundException;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ModelCatalogPermissionDeniedException;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntry;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelRuntimeCapabilities;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationRepository;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeploymentId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeploymentStatus;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionStatus;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionRepository;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import java.time.Clock;
import java.util.Objects;

/**
 * 仅允许平台管理员维护受控厂家、端点与模型编码目录。
 */
public final class ControlledModelCatalogService {

    private static final PermissionCode PLATFORM_WRITE = new PermissionCode("model-platform:write");

    private final ModelConfigurationRepository repository;
    private final EntityIdGenerator identifiers;
    private final Clock clock;
    private final PlatformModelDeploymentRepository platformDeployments;
    private final PlatformProviderConnectionRepository platformConnections;

    public ControlledModelCatalogService(
            ModelConfigurationRepository repository,
            EntityIdGenerator identifiers,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "模型配置仓储不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.platformDeployments = null;
        this.platformConnections = null;
    }

    public ControlledModelCatalogService(
            ModelConfigurationRepository repository, EntityIdGenerator identifiers, Clock clock,
            PlatformModelDeploymentRepository platformDeployments,
            PlatformProviderConnectionRepository platformConnections) {
        this.repository = Objects.requireNonNull(repository, "模型配置仓储不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.platformDeployments = Objects.requireNonNull(platformDeployments, "平台模型部署仓储不能为空");
        this.platformConnections = Objects.requireNonNull(platformConnections, "平台供应商连接仓储不能为空");
    }

    /**
     * 目录事实只能由服务端从已发布、当前健康的平台部署冻结，浏览器不能提交 Endpoint 或能力。
     */
    public ControlledModelCatalogEntry createFromDeployment(
            PlatformRequestContext context, PlatformModelDeploymentId deploymentId, long expectedVersion) {
        requirePlatformWrite(context);
        var deployment = requirePlatformDeployments().findById(Objects.requireNonNull(deploymentId, "平台模型部署标识不能为空"))
                .filter(value -> value.version() == expectedVersion)
                .filter(value -> value.status() == PlatformModelDeploymentStatus.PUBLISHED)
                .filter(value -> value.verificationExpiresAt() != null && value.verificationExpiresAt().isAfter(clock.instant()))
                .orElseThrow(() -> new IllegalStateException("平台模型部署不可用于建立目录"));
        var connection = requirePlatformConnections().findById(deployment.providerConnectionId())
                .filter(value -> value.version() == deployment.providerConnectionRevision())
                .filter(value -> value.status() == ProviderConnectionStatus.VERIFIED)
                .orElseThrow(() -> new IllegalStateException("平台供应商连接已变化或不可用"));
        var capabilities = deployment.modelType() == ModelType.CHAT
                ? ModelRuntimeCapabilities.chat(deployment.contextWindow(), deployment.outputLimit())
                : ModelRuntimeCapabilities.embedding(deployment.contextWindow(), deployment.embeddingDimension());
        var entry = ControlledModelCatalogEntry.createFromPlatformDeployment(
                new ControlledModelCatalogEntryId(identifiers.nextId()), deployment.id(), deployment.version(),
                connection.providerKind(), connection.endpoint(), deployment.modelCode(), deployment.modelType(),
                capabilities, clock.instant());
        repository.saveCatalogEntry(entry);
        return entry;
    }

    /** 发布前重新验证目录快照所引用的平台部署和连接仍可用。 */
    public ControlledModelCatalogEntry publish(
            PlatformRequestContext context, ControlledModelCatalogEntryId entryId) {
        requirePlatformWrite(context);
        var entry = repository.findCatalogEntry(entryId).orElseThrow(ModelConfigurationNotFoundException::new);
        var sourceId = Objects.requireNonNull(entry.sourceDeploymentId(), "目录缺少平台部署快照来源");
        var sourceRevision = Objects.requireNonNull(entry.sourceDeploymentRevision(), "目录缺少平台部署快照修订");
        var deployment = requirePlatformDeployments().findById(sourceId)
                .filter(value -> value.version() == sourceRevision)
                .filter(value -> value.status() == PlatformModelDeploymentStatus.PUBLISHED)
                .filter(value -> value.verificationExpiresAt() != null && value.verificationExpiresAt().isAfter(clock.instant()))
                .orElseThrow(() -> new IllegalStateException("平台模型部署不可用于发布目录"));
        requirePlatformConnections().findById(deployment.providerConnectionId())
                .filter(value -> value.version() == deployment.providerConnectionRevision())
                .filter(value -> value.status() == ProviderConnectionStatus.VERIFIED)
                .orElseThrow(() -> new IllegalStateException("平台供应商连接已变化或不可用"));
        var published = entry.publish();
        repository.updateCatalogEntry(published);
        return published;
    }

    /**
     * 向已认证成员提供可选的已发布目录分页，不暴露草稿条目或任何凭据材料。
     */
    public PageResult<ControlledModelCatalogEntry> listPublished(
            TrustedRequestContext context, PageQuery page) {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(page, "分页请求不能为空");
        var result = repository.findPublishedCatalogPage(
                page.pageNumber(), page.pageSize());
        return PageResult.of(result.items(), page, result.totalElements());
    }

    private static void requirePlatformWrite(TrustedRequestContext context) {
        if (context == null || !context.hasPermission(PLATFORM_WRITE)) {
            throw new ModelCatalogPermissionDeniedException();
        }
    }

    private static void requirePlatformWrite(PlatformRequestContext context) {
        if (context == null || !context.hasPermission(PLATFORM_WRITE)) {
            throw new ModelCatalogPermissionDeniedException();
        }
    }

    private PlatformModelDeploymentRepository requirePlatformDeployments() {
        if (platformDeployments == null) {
            throw new IllegalStateException("当前目录服务未配置平台部署来源");
        }
        return platformDeployments;
    }

    private PlatformProviderConnectionRepository requirePlatformConnections() {
        if (platformConnections == null) {
            throw new IllegalStateException("当前目录服务未配置平台连接来源");
        }
        return platformConnections;
    }
}
