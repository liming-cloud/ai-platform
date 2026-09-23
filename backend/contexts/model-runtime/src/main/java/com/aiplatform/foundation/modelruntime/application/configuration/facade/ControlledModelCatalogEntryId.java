package com.aiplatform.foundation.modelruntime.application.configuration.facade;

/** 跨上下文使用的受控目录条目标识。 */
public record ControlledModelCatalogEntryId(long value) {
    public ControlledModelCatalogEntryId {
        if (value <= 0) {
            throw new IllegalArgumentException("受控目录条目标识必须是正整数");
        }
    }
}
