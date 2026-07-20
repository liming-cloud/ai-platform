package com.aiplatform.modelhub.application;

import com.aiplatform.modelhub.domain.ModelConfig;
import com.aiplatform.modelhub.domain.ModelProvider;
import java.util.Objects;

/** Immutable authority snapshot used across the out-of-transaction connectivity probe. */
record ModelConnectivityProbeSnapshot(ModelProvider provider, ModelConfig config) {
    ModelConnectivityProbeSnapshot {
        Objects.requireNonNull(provider, "探测供应商快照不能为空");
        Objects.requireNonNull(config, "探测模型配置快照不能为空");
    }
}
