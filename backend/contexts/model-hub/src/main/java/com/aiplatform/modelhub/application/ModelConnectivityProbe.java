package com.aiplatform.modelhub.application;

import com.aiplatform.modelhub.domain.ModelConfig;
import com.aiplatform.modelhub.domain.ModelProvider;
import com.aiplatform.modelhub.domain.ModelRuntimeConnectivity;

/**
 * 连通性探测执行 seam；调用方只依赖有界结果，不持有线程或超时实现细节。
 */
@FunctionalInterface
public interface ModelConnectivityProbe {
    ModelRuntimeConnectivity test(ModelProvider provider, ModelConfig config);
}
