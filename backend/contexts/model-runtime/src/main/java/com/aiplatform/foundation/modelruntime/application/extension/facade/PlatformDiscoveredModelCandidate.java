package com.aiplatform.foundation.modelruntime.application.extension.facade;

import java.util.Objects;
import java.util.Set;

/** 平台发现扩展返回的候选契约，不暴露内部领域对象。 */
public record PlatformDiscoveredModelCandidate(
        String modelCode,
        ModelType modelType,
        Set<ModelCapability> capabilities,
        PlatformModelCandidateClassification classification) {

    public PlatformDiscoveredModelCandidate {
        Objects.requireNonNull(modelCode, "发现模型代码不能为空");
        if (modelCode.isBlank() || !modelCode.equals(modelCode.trim()) || modelCode.length() > 200) {
            throw new IllegalArgumentException("发现模型代码格式不正确");
        }
        classification = Objects.requireNonNull(classification, "模型候选分类不能为空");
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "发现模型能力不能为空"));
        if (capabilities.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("发现模型能力不能包含空值");
        }
        if (classification == PlatformModelCandidateClassification.REQUIRES_ADMIN_CONFIRMATION) {
            if (modelType != null || !capabilities.isEmpty()) {
                throw new IllegalArgumentException("待管理员确认候选不得携带猜测的模型类型或能力");
            }
        } else {
            modelType = Objects.requireNonNull(modelType, "已确认候选必须包含模型类型");
            if (modelType == ModelType.CHAT && !capabilities.contains(ModelCapability.CHAT)) {
                throw new IllegalArgumentException("已确认对话候选必须包含对话能力");
            }
            if (modelType == ModelType.EMBEDDING && !capabilities.contains(ModelCapability.EMBEDDING)) {
                throw new IllegalArgumentException("已确认嵌入候选必须包含嵌入能力");
            }
        }
    }

    public static PlatformDiscoveredModelCandidate requiresAdministratorConfirmation(String modelCode) {
        return new PlatformDiscoveredModelCandidate(
                modelCode, null, Set.of(), PlatformModelCandidateClassification.REQUIRES_ADMIN_CONFIRMATION);
    }

    public static PlatformDiscoveredModelCandidate confirmedByProvider(
            String modelCode, ModelType modelType, Set<ModelCapability> capabilities) {
        return new PlatformDiscoveredModelCandidate(
                modelCode, modelType, capabilities, PlatformModelCandidateClassification.CONFIRMED_BY_PROVIDER);
    }

    public static PlatformDiscoveredModelCandidate confirmedByAdministrator(
            String modelCode, ModelType modelType) {
        return new PlatformDiscoveredModelCandidate(
                modelCode,
                modelType,
                Set.of(modelType == ModelType.CHAT ? ModelCapability.CHAT : ModelCapability.EMBEDDING),
                PlatformModelCandidateClassification.CONFIRMED_BY_ADMIN);
    }

}
