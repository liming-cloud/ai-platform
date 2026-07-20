package com.aiplatform.identity.domain.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.model.PermissionCode;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RbacPermissionDomainInvariantsTest {
    private static final TenantId TENANT_ID = new TenantId("2001");
    private static final PermissionCode PERMISSION_CODE = new PermissionCode("knowledge-base:document:upload");

    @Test
    void normalizesRoleAndPermissionCatalogFactsBeforeSnapshotUse() {
        Role role = new Role(TENANT_ID, " role-001 ", " kb_admin ", " 知识库管理员 ", true);
        Permission permission = new Permission(
                TENANT_ID,
                " permission-001 ",
                PERMISSION_CODE,
                " 上传知识库文档 ",
                true
        );

        assertThat(role.roleId()).isEqualTo("role-001");
        assertThat(role.roleCode()).isEqualTo("kb_admin");
        assertThat(role.roleName()).isEqualTo("知识库管理员");
        assertThat(permission.permissionId()).isEqualTo("permission-001");
        assertThat(permission.permissionName()).isEqualTo("上传知识库文档");
    }

    @Test
    void normalizesGrantAndResourceScopeFactsBeforeAuthorizationUse() {
        ResourceScope scope = new ResourceScope(" knowledge_base ", " kb-001 ");
        UserRoleGrant userGrant = new UserRoleGrant(
                TENANT_ID,
                " user-grant-001 ",
                " user-001 ",
                " role-001 ",
                scope,
                Instant.parse("2026-06-01T08:00:00Z"),
                true
        );
        RolePermissionGrant permissionGrant = new RolePermissionGrant(
                TENANT_ID,
                " role-grant-001 ",
                " role-001 ",
                " permission-001 ",
                scope,
                null,
                true
        );

        assertThat(scope.resourceType()).isEqualTo("knowledge_base");
        assertThat(scope.resourceId()).isEqualTo("kb-001");
        assertThat(userGrant.grantId()).isEqualTo("user-grant-001");
        assertThat(userGrant.userId()).isEqualTo("user-001");
        assertThat(userGrant.roleId()).isEqualTo("role-001");
        assertThat(permissionGrant.grantId()).isEqualTo("role-grant-001");
        assertThat(permissionGrant.roleId()).isEqualTo("role-001");
        assertThat(permissionGrant.permissionId()).isEqualTo("permission-001");
    }

    @Test
    void normalizesAclAndDecisionFactsBeforePermissionEvaluation() {
        ResourceAcl acl = new ResourceAcl(
                TENANT_ID,
                " acl-001 ",
                new ResourceScope(" knowledge_base ", " kb-001 "),
                " user ",
                " user-001 ",
                PERMISSION_CODE,
                " ALLOW ",
                null,
                true
        );
        AuthorizationDecision decision = AuthorizationDecision.deny(
                " resource_acl_missing ",
                " 资源 ACL 不包含目标权限 "
        );

        assertThat(acl.aclId()).isEqualTo("acl-001");
        assertThat(acl.subjectType()).isEqualTo("user");
        assertThat(acl.subjectId()).isEqualTo("user-001");
        assertThat(acl.effect()).isEqualTo("allow");
        assertThat(decision.decisionCode()).isEqualTo("resource_acl_missing");
        assertThat(decision.reason()).isEqualTo("资源 ACL 不包含目标权限");
    }

    @Test
    void rejectsCatalogAndAclCoreFactsInsteadOfJdkNullPointerException() {
        ResourceScope scope = new ResourceScope("knowledge_base", "kb-001");

        assertThatThrownBy(() -> new Role(null, "role-001", "kb_admin", "知识库管理员", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("租户 ID不能为空");
        assertThatThrownBy(() -> new Permission(null, "permission-001", PERMISSION_CODE, "上传知识库文档", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("租户 ID不能为空");
        assertThatThrownBy(() -> new Permission(TENANT_ID, "permission-001", null, "上传知识库文档", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("权限编码不能为空");
        assertThatThrownBy(() -> new ResourceAcl(
                null,
                "acl-001",
                scope,
                "user",
                "user-001",
                PERMISSION_CODE,
                "allow",
                null,
                true
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("租户 ID不能为空");
        assertThatThrownBy(() -> new ResourceAcl(
                TENANT_ID,
                "acl-001",
                null,
                "user",
                "user-001",
                PERMISSION_CODE,
                "allow",
                null,
                true
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("资源范围不能为空");
        assertThatThrownBy(() -> new ResourceAcl(
                TENANT_ID,
                "acl-001",
                scope,
                "user",
                "user-001",
                null,
                "allow",
                null,
                true
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("权限编码不能为空");
    }

    @Test
    void rejectsGrantAndEvaluationFactsInsteadOfJdkNullPointerException() {
        ResourceScope scope = new ResourceScope("knowledge_base", "kb-001");
        UserRoleGrant userGrant = new UserRoleGrant(
                TENANT_ID,
                "user-grant-001",
                "user-001",
                "role-001",
                scope,
                null,
                true
        );
        RolePermissionGrant roleGrant = new RolePermissionGrant(
                TENANT_ID,
                "role-grant-001",
                "role-001",
                "permission-001",
                scope,
                null,
                true
        );
        ResourceAcl acl = new ResourceAcl(
                TENANT_ID,
                "acl-001",
                scope,
                "user",
                "user-001",
                PERMISSION_CODE,
                "allow",
                null,
                true
        );

        assertThatThrownBy(() -> new UserRoleGrant(
                TENANT_ID,
                "user-grant-001",
                "user-001",
                "role-001",
                null,
                null,
                true
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("资源范围不能为空");
        assertThatThrownBy(() -> new RolePermissionGrant(
                null,
                "role-grant-001",
                "role-001",
                "permission-001",
                scope,
                null,
                true
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("租户 ID不能为空");
        assertThatThrownBy(() -> new RolePermissionGrant(
                TENANT_ID,
                "role-grant-001",
                "role-001",
                "permission-001",
                null,
                null,
                true
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("资源范围不能为空");
        assertThatThrownBy(() -> userGrant.activeAt(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("校验时间不能为空");
        assertThatThrownBy(() -> roleGrant.activeAt(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("校验时间不能为空");
        assertThatThrownBy(() -> acl.activeAt(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("校验时间不能为空");
        assertThatThrownBy(() -> new AuthorizationDecision(true, "allowed", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("决策原因不能为空");
    }
}
