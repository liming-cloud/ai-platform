package com.aiplatform.foundation.modelruntime.application.platformsupply.dto.deployment;

import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.deployment.PlatformModelDeploymentInputs;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

/** 草稿声明的安全投影；所有公开标识使用字符串，拟授权不表示生效授权。 */
public record PlatformModelDeploymentInputsView(String parameterSource, String residency, Integer rerankCandidateLimit,
        Set<String> plannedTenantIds, String discoveryId, String candidateDisplayName, String classification,
        String declaredBySubjectId, Instant declaredAt) {
    /** 历史未声明事实保持未知。 */
    public static PlatformModelDeploymentInputsView from(PlatformModelDeploymentInputs value) {
        return value == null ? null : new PlatformModelDeploymentInputsView(value.parameterSource(), value.residency(),
                value.rerankCandidateLimit(), value.plannedTenantIds().stream().map(Object::toString).collect(Collectors.toUnmodifiableSet()),
                Long.toString(value.discoveryId()), value.candidateDisplayName() == null ? "名称暂未提供" : value.candidateDisplayName(), value.classification().name(),
                Long.toString(value.declaredBySubjectId()), value.declaredAt());
    }
}
