package com.aiplatform.foundation.modelruntime.application.platformsupply.dto.deployment;

import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import java.util.Set;

/** 完整草稿表单的公开候选；显示名称独立于供应商编码，未知分类仍须显式确认。 */
public record PlatformDeployableModel(String connectionId, long connectionRevision, String connectionDisplayName, String residency,
        String declaredCapability, String modelCode, String displayName, ModelType modelType, Set<ModelCapability> capabilities,
        String classification) {
    public PlatformDeployableModel {
        // 与 ADR-0070 的既有候选投影保持一致；缺失提示不是厂商事实。
        displayName = displayName == null ? "名称暂未提供" : displayName;
        capabilities = Set.copyOf(capabilities);
    }
}
