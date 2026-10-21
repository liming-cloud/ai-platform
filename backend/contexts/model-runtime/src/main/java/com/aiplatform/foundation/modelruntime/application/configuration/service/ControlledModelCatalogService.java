package com.aiplatform.foundation.modelruntime.application.configuration.service;

import com.aiplatform.foundation.modelruntime.application.configuration.exception.ModelConfigurationNotFoundException;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ModelCatalogPermissionDeniedException;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntry;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryStatus;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationRepository;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeploymentId;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.util.Objects;

/** 目录只由平台部署原子发布创建；旧写入口仅恢复已存在快照，租户读取始终限定供应授权。 */
public final class ControlledModelCatalogService {
    private final ModelConfigurationRepository repository;

    public ControlledModelCatalogService(ModelConfigurationRepository repository) {
        this.repository = Objects.requireNonNull(repository, "模型配置仓储不能为空");
    }

    /** 兼容旧调用方恢复同一目录，不新建目录、授权或发布事实。 */
    public ControlledModelCatalogEntry createFromDeployment(PlatformRequestContext context,
            PlatformModelDeploymentId deploymentId, long expectedVersion) {
        requirePlatformWrite(context);
        if (expectedVersion < 0) throw new IllegalArgumentException("部署修订无效");
        return repository.findPublishedByDeployment(Objects.requireNonNull(deploymentId), expectedVersion)
                .orElseThrow(ModelConfigurationNotFoundException::new);
    }

    /** 旧独立发布入口只返回原子发布已经建立且当前有效的同一快照。 */
    public ControlledModelCatalogEntry publish(PlatformRequestContext context, ControlledModelCatalogEntryId entryId) {
        requirePlatformWrite(context);
        var entry = repository.findCatalogEntry(Objects.requireNonNull(entryId))
                .filter(value -> value.status() == ControlledModelCatalogEntryStatus.PUBLISHED)
                .filter(value -> value.sourceDeploymentId() != null && value.sourceDeploymentRevision() != null)
                .orElseThrow(ModelConfigurationNotFoundException::new);
        return repository.findPublishedByDeployment(entry.sourceDeploymentId(), entry.sourceDeploymentRevision())
                .filter(value -> value.id().equals(entryId)).orElseThrow(ModelConfigurationNotFoundException::new);
    }

    /** 当前租户的来源和活动供应授权在服务端分页及同范围总数之前过滤。 */
    public PageResult<ControlledModelCatalogEntry> listPublished(TrustedRequestContext context, PageQuery page) {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(page, "分页请求不能为空");
        var result = repository.findPublishedCatalogPage(context.tenantId(), page.pageNumber(), page.pageSize());
        return PageResult.of(result.items(), page, result.totalElements());
    }

    private static void requirePlatformWrite(PlatformRequestContext context) {
        if (context == null || !context.hasPermission(PlatformPermissions.MODEL_PLATFORM_WRITE))
            throw new ModelCatalogPermissionDeniedException();
    }
}
