package com.aiplatform.foundation.modelruntime.application.platformsupply.dto;

import java.util.Objects;
import java.util.Set;

/** 平台管理员可读取的安全模型候选；待确认候选不伪造类型或能力。 */
public record PlatformProviderModelCandidateView(
        String modelCode,
        String modelType,
        Set<String> capabilities,
        String classification) {
    public PlatformProviderModelCandidateView {
        modelCode = Objects.requireNonNull(modelCode, "模型代码不能为空");
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "模型能力不能为空"));
        classification = Objects.requireNonNull(classification, "候选分类不能为空");
    }

    /** 兼容既有已确认候选投影。 */
    public PlatformProviderModelCandidateView(
            String modelCode, String modelType, Set<String> capabilities) {
        this(modelCode, modelType, capabilities, "CONFIRMED_BY_PROVIDER");
    }
}
