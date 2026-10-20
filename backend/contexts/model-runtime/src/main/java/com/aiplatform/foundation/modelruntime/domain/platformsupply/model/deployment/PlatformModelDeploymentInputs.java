package com.aiplatform.foundation.modelruntime.domain.platformsupply.model.deployment;

import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformModelCandidateClassification;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** 平台草稿的不可变声明快照；拟授权租户只表达选择，不能据此获得运行权限。 */
public record PlatformModelDeploymentInputs(String parameterSource, String residency, Integer rerankCandidateLimit,
        Set<Long> plannedTenantIds, long discoveryId, String candidateDisplayName,
        PlatformModelCandidateClassification classification, long declaredBySubjectId, Instant declaredAt) {
    public PlatformModelDeploymentInputs {
        parameterSource = text(parameterSource, 160);
        residency = text(residency, 120);
        // 历史发现没有独立名称时保留缺失；只允许展示层给出缺失提示，不能以编码补造名称。
        candidateDisplayName = candidateDisplayName == null ? null : text(candidateDisplayName, 200);
        plannedTenantIds = Set.copyOf(Objects.requireNonNull(plannedTenantIds, "必须明确选择拟授权租户"));
        if (plannedTenantIds.isEmpty() || plannedTenantIds.size() > 1000 || plannedTenantIds.stream().anyMatch(id -> id <= 0))
            throw new IllegalArgumentException("拟授权租户集合不正确");
        if (rerankCandidateLimit != null && rerankCandidateLimit <= 0) throw new IllegalArgumentException("重排候选上限必须为正数");
        if (discoveryId <= 0 || declaredBySubjectId <= 0) throw new IllegalArgumentException("来源或声明主体不正确");
        classification = Objects.requireNonNull(classification, "分类来源不能为空");
        if (classification == PlatformModelCandidateClassification.REQUIRES_ADMIN_CONFIRMATION)
            throw new IllegalArgumentException("未确认分类不能保存草稿");
        declaredAt = Objects.requireNonNull(declaredAt, "声明时间不能为空");
    }

    private static String text(String value, int maximum) {
        if (value == null || value.isBlank() || !value.equals(value.trim())
                || value.codePointCount(0, value.length()) > maximum
                || value.chars().anyMatch(c -> Character.isISOControl(c) || c == '\u2028' || c == '\u2029'))
            throw new IllegalArgumentException("声明文本必须是有界单行非秘密说明");
        return value;
    }
}
