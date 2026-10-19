package com.aiplatform.foundation.modelruntime.application.platformsupply.dto;

import java.util.Objects;
import java.util.Set;

/** 平台管理员可读取的安全模型候选；待确认候选不伪造类型或能力。 */
public record PlatformProviderModelCandidateView(
        String modelCode,
        String modelType,
        Set<String> capabilities,
        String classification,
        String displayName) {
    public PlatformProviderModelCandidateView {
        modelCode = Objects.requireNonNull(modelCode, "模型代码不能为空");
        // 历史记录未冻结名称时只表达缺失，供应商编码仍仅供受控选择与调用。
        displayName = displayName == null ? "名称暂未提供" : displayName;
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "模型能力不能为空"));
        classification = Objects.requireNonNull(classification, "候选分类不能为空");
    }

    /** 保留没有独立名称的候选事实，不将供应商编码显示为名称。 */
    public PlatformProviderModelCandidateView(String modelCode, String modelType,
            Set<String> capabilities, String classification) {
        this(modelCode, modelType, capabilities, classification, null);
    }

    /** 兼容既有已确认候选投影。 */
    public PlatformProviderModelCandidateView(
            String modelCode, String modelType, Set<String> capabilities) {
        this(modelCode, modelType, capabilities, "CONFIRMED_BY_PROVIDER");
    }
}
