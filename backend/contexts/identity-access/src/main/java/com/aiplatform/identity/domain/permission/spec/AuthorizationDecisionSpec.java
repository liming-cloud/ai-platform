package com.aiplatform.identity.domain.permission.spec;

import com.aiplatform.identity.domain.permission.AuthorizationDecision;
import com.aiplatform.identity.domain.permission.ResourceAcl;
import com.aiplatform.identity.domain.permission.ResourceScope;
import com.aiplatform.shared.security.model.PermissionCode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 授权决策领域规则，统一执行权限快照和资源 ACL 的 fail-closed 校验。
 */
public final class AuthorizationDecisionSpec {
    public AuthorizationDecision decide(
            Set<PermissionCode> permissionSnapshot,
            List<ResourceAcl> aclSnapshot,
            ResourceScope resourceScope,
            PermissionCode requiredPermission,
            Instant now
    ) {
        Objects.requireNonNull(permissionSnapshot, "权限快照不能为空");
        Objects.requireNonNull(aclSnapshot, "ACL 快照不能为空");
        Objects.requireNonNull(resourceScope, "资源范围不能为空");
        Objects.requireNonNull(requiredPermission, "目标权限不能为空");
        Objects.requireNonNull(now, "校验时间不能为空");
        if (!permissionSnapshot.contains(requiredPermission)) {
            return AuthorizationDecision.deny("permission_missing", "权限快照不包含目标权限");
        }
        List<ResourceAcl> activeMatchedAcls = aclSnapshot.stream()
                .filter(acl -> acl.activeAt(now))
                .filter(acl -> acl.resourceScope().matches(resourceScope.resourceType(), resourceScope.resourceId()))
                .filter(acl -> acl.permissionCode().equals(requiredPermission))
                .toList();
        if (activeMatchedAcls.stream().anyMatch(ResourceAcl::denies)) {
            return AuthorizationDecision.deny("resource_acl_denied", "资源 ACL 显式拒绝目标权限");
        }
        if (activeMatchedAcls.stream().noneMatch(ResourceAcl::allows)) {
            return AuthorizationDecision.deny("resource_acl_missing", "资源 ACL 不包含目标权限");
        }
        return AuthorizationDecision.allow("allowed");
    }
}
