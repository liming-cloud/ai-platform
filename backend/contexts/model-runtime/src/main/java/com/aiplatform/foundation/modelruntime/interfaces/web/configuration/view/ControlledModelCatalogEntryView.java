package com.aiplatform.foundation.modelruntime.interfaces.web.configuration.view;

import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntry;
import java.time.Instant;

/**
 * 受控模型目录的只读视图。
 */
public record ControlledModelCatalogEntryView(
        String id,
        String providerKind,
        String modelCode,
        String modelType,
        int contextWindow,
        Integer outputLimit,
        Integer embeddingDimension,
        String status,
        long version,
        Instant createdAt) {

    public static ControlledModelCatalogEntryView from(ControlledModelCatalogEntry entry) {
        return new ControlledModelCatalogEntryView(
                entry.id().toString(), entry.providerKind().name(), entry.modelCode(),
                entry.modelType().name(), entry.capabilities().contextWindow(),
                entry.capabilities().outputLimit(), entry.capabilities().embeddingDimension(),
                entry.status().name(), entry.version(), entry.createdAt());
    }
}
