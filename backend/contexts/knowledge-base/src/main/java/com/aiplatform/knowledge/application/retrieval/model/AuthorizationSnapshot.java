package com.aiplatform.knowledge.application.retrieval.model;

import com.aiplatform.shared.security.model.PermissionCode;
import java.util.Objects;
import java.util.Set;

/**
 * 检索授权快照，集中承载当前主体在知识库检索过程中的角色和权限编码。
 */
public record AuthorizationSnapshot(
        Set<String> roleCodes,
        Set<PermissionCode> permissionCodes
) {
    public AuthorizationSnapshot {
        roleCodes = Set.copyOf(Objects.requireNonNull(roleCodes, "角色编码不能为空"));
        permissionCodes = Set.copyOf(Objects.requireNonNull(permissionCodes, "权限编码不能为空"));
    }
}
