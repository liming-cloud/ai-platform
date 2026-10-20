package com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery;

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
        PlatformModelCandidateClassification classification,
        String displayName) implements ValueObject {

    /**
     * 校验不可变候选；缺失名称不得改变原分类，显式名称不接受空白或控制字符。
     *
     * @throws IllegalArgumentException 编码、名称或类型与能力不满足候选不变量。
     */
    public PlatformDiscoveredModelCandidate {
        Objects.requireNonNull(modelCode, "发现模型代码不能为空");
        if (modelCode.isBlank() || !modelCode.equals(modelCode.trim()) || modelCode.length() > 200) {
            throw new IllegalArgumentException("发现模型代码格式不正确");
        }
        // 名称只来自受控发现元数据；历史或无名称来源保持缺失，不能用调用编码补齐。
        if (displayName != null && (displayName.isBlank() || !displayName.equals(displayName.trim())
                || displayName.length() > 120 || displayName.codePoints().anyMatch(Character::isISOControl))) {
            throw new IllegalArgumentException("模型显示名称格式不正确");
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
            if (modelType == ModelType.RERANK && !capabilities.equals(Set.of(ModelCapability.RERANK))) {
                throw new IllegalArgumentException("已确认重排候选只能声明重排能力");
            }
        }
    }

    /** 无名称的既有候选保持明确缺失，不改变类型和能力事实。 */
    public PlatformDiscoveredModelCandidate(String modelCode, ModelType modelType,
            Set<ModelCapability> capabilities, PlatformModelCandidateClassification classification) {
        this(modelCode, modelType, capabilities, classification, null);
    }

    /** 从独立名称元数据建立待确认候选，不从名称推导模型能力。 */
    public static PlatformDiscoveredModelCandidate requiresAdministratorConfirmation(
            String modelCode, String displayName) {
        return new PlatformDiscoveredModelCandidate(modelCode, null, Set.of(),
                PlatformModelCandidateClassification.REQUIRES_ADMIN_CONFIRMATION, displayName);
    }

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
                Set.of(switch (Objects.requireNonNull(modelType, "确认模型类型不能为空")) {
                    case CHAT -> ModelCapability.CHAT;
                    case EMBEDDING -> ModelCapability.EMBEDDING;
                    case RERANK -> ModelCapability.RERANK;
                }),
                PlatformModelCandidateClassification.CONFIRMED_BY_ADMIN);
    }
}
