package com.aiplatform.foundation.modelruntime.application.configuration.facade;

import java.util.Objects;

/**
 * 跨上下文传递的可用模型配置冻结事实，不包含凭据、端点或厂商实现细节。
 */
public record UsableModelConfigurationSnapshot(
        ModelConfigurationId configurationId,
        long configurationRevision,
        ControlledModelCatalogEntryId catalogEntryId,
        long catalogEntryRevision,
        ModelType modelType,
        ModelRuntimeCapabilities capabilities) {

    public UsableModelConfigurationSnapshot {
        configurationId = Objects.requireNonNull(configurationId, "模型配置标识不能为空");
        catalogEntryId = Objects.requireNonNull(catalogEntryId, "受控模型目录条目标识不能为空");
        modelType = Objects.requireNonNull(modelType, "模型类型不能为空");
        capabilities = Objects.requireNonNull(capabilities, "模型能力不能为空");
        if (configurationRevision < 1 || catalogEntryRevision < 1) {
            throw new IllegalArgumentException("模型配置与目录修订必须大于零");
        }
        capabilities.requireFor(modelType);
    }
}
