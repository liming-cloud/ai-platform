package com.aiplatform.foundation.modelruntime.domain.platformsupply.model;

import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import java.util.Objects;
import java.util.Set;

/** 平台权威发现产生的候选；未确认候选不得携带猜测的类型或能力。 */
public record PlatformDiscoveredModelCandidate(
        String modelCode,
        ModelType modelType,
        Set<ModelCapability> capabilities,
        PlatformModelCandidateClassification classification) implements ValueObject {

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

    /** 创建元数据不足、必须由管理员确认的候选。 */
    public static PlatformDiscoveredModelCandidate requiresAdministratorConfirmation(String modelCode) {
        return new PlatformDiscoveredModelCandidate(
                modelCode, null, Set.of(), PlatformModelCandidateClassification.REQUIRES_ADMIN_CONFIRMATION);
    }

    /** 创建由供应商权威元数据明确分类的候选。 */
    public static PlatformDiscoveredModelCandidate confirmedByProvider(
            String modelCode, ModelType modelType, Set<ModelCapability> capabilities) {
        return new PlatformDiscoveredModelCandidate(
                modelCode, modelType, capabilities, PlatformModelCandidateClassification.CONFIRMED_BY_PROVIDER);
    }

    /** 将管理员显式选择的模型类型固化为可部署候选。 */
    public static PlatformDiscoveredModelCandidate confirmedByAdministrator(
            String modelCode, ModelType modelType) {
        return new PlatformDiscoveredModelCandidate(
                modelCode,
                modelType,
                Set.of(modelType == ModelType.CHAT ? ModelCapability.CHAT : ModelCapability.EMBEDDING),
                PlatformModelCandidateClassification.CONFIRMED_BY_ADMIN);
    }
}
