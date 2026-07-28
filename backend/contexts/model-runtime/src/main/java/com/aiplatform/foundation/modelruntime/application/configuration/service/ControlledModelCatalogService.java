package com.aiplatform.foundation.modelruntime.application.configuration.service;

import com.aiplatform.foundation.modelruntime.application.configuration.exception.ModelConfigurationNotFoundException;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ModelCatalogPermissionDeniedException;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntry;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelRuntimeCapabilities;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationRepository;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import java.net.URI;
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

    public ControlledModelCatalogService(
            ModelConfigurationRepository repository,
            EntityIdGenerator identifiers,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "模型配置仓储不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    public ControlledModelCatalogEntry create(
            TrustedRequestContext context,
            ProviderKind providerKind,
            URI endpoint,
            String modelCode,
            ModelType modelType,
            ModelRuntimeCapabilities capabilities) {
        requirePlatformWrite(context);
        var entry = ControlledModelCatalogEntry.create(
                new ControlledModelCatalogEntryId(identifiers.nextId()),
                providerKind,
                endpoint,
                modelCode,
                modelType,
                capabilities,
                clock.instant());
        repository.saveCatalogEntry(entry);
        return entry;
    }

    public ControlledModelCatalogEntry publish(
            TrustedRequestContext context, ControlledModelCatalogEntryId entryId) {
        requirePlatformWrite(context);
        var published = repository.findCatalogEntry(entryId)
                .orElseThrow(ModelConfigurationNotFoundException::new)
                .publish();
        repository.updateCatalogEntry(published);
        return published;
    }

    private static void requirePlatformWrite(TrustedRequestContext context) {
        if (context == null || !context.hasPermission(PLATFORM_WRITE)) {
            throw new ModelCatalogPermissionDeniedException();
        }
    }
}
