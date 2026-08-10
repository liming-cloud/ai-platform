package com.aiplatform.foundation.organizationaccess.application.authorization.dto;

import com.aiplatform.foundation.organizationaccess.domain.authorization.operation.TenantRoleRevocationResult;
import java.time.Instant;
import java.util.Objects;

/** 内置角色撤销后的最小安全读取投影。 */
public record TenantRoleRevocationView(
        String membershipId,
        String roleCode,
        long permissionVersion,
        Instant revokedAt) {

    /** 从已提交的领域操作结果生成 HTTP 可用摘要。 */
    public static TenantRoleRevocationView from(TenantRoleRevocationResult result) {
        result = Objects.requireNonNull(result, "角色撤销结果不能为空");
        return new TenantRoleRevocationView(
                Long.toString(result.membershipId().value()),
                result.role().name(),
                result.revisedMembership().permissionVersion(),
                result.revokedAt());
    }
}
