package com.aiplatform.foundation.organizationaccess.application.authorization.dto;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform.PlatformRoleBinding;
import java.time.Instant;
import java.util.Objects;

/** 平台固定角色绑定的无秘密写后投影。 */
public record PlatformRoleBindingView(
        long bindingId,
        long subjectId,
        String roleCode,
        String status,
        long version,
        Instant createdAt,
        Instant revokedAt) {

    /** 从领域绑定生成 HTTP 与控制台可用的最小事实。 */
    public static PlatformRoleBindingView from(PlatformRoleBinding binding) {
        binding = Objects.requireNonNull(binding, "平台角色绑定不能为空");
        return new PlatformRoleBindingView(
                binding.id().value(),
                binding.subjectId().value(),
                binding.role().name(),
                binding.status().name(),
                binding.version(),
                binding.createdAt(),
                binding.revokedAt());
    }
}
