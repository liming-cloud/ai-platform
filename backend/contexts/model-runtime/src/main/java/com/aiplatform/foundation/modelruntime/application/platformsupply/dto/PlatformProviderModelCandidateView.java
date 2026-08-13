package com.aiplatform.foundation.modelruntime.application.platformsupply.dto;

import java.util.Objects;
import java.util.Set;

/** 平台部署创建可选择的安全模型候选，不含连接配置或探测原文。 */
public record PlatformProviderModelCandidateView(String modelCode, String modelType, Set<String> capabilities) {
    public PlatformProviderModelCandidateView {
        modelCode = Objects.requireNonNull(modelCode, "模型代码不能为空");
        modelType = Objects.requireNonNull(modelType, "模型类型不能为空");
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "模型能力不能为空"));
    }
}
