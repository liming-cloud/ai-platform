package com.aiplatform.foundation.modelruntime.domain.configuration.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/**
 * 平台受控模型目录条目的稳定标识。
 */
public record ControlledModelCatalogEntryId(long value) implements ValueObject {

    public ControlledModelCatalogEntryId {
        if (value <= 0) {
            throw new IllegalArgumentException("受控模型目录条目标识必须是正整数");
        }
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
