package com.aiplatform.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.identity.application.command.CreateIamMenuCommand;
import com.aiplatform.identity.application.command.CurrentUserCommand;
import com.aiplatform.identity.application.command.LoginCommand;
import com.aiplatform.identity.application.command.LogoutCommand;
import com.aiplatform.identity.application.command.ReplaceIamMenuFeaturesCommand;
import com.aiplatform.identity.application.command.ReplaceIamRoleMenusCommand;
import com.aiplatform.identity.application.command.ReplaceIamRolePermissionsCommand;
import com.aiplatform.identity.application.command.UpdateIamMenuCommand;
import com.aiplatform.identity.application.command.UpdateIamRoleDefaultMenuCommand;
import com.aiplatform.identity.application.command.UpdateIamTenantFeatureCommand;
import com.aiplatform.identity.application.result.CurrentUserResult;
import com.aiplatform.identity.application.result.CurrentUserMenuResult;
import com.aiplatform.identity.application.result.LoginResult;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.model.PermissionCode;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IdentityApplicationContractInvariantsTest {
    private static final Instant EXPIRES_AT = Instant.parse("2026-05-22T03:30:00Z");

    @Test
    void loginCommandRejectsBlankFactsButPreservesRawPassword() {
        assertThatThrownBy(() -> new LoginCommand(TenantId.of("2001"), " ", "correct-password", "TRACE-001"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoginCommand(TenantId.of("2001"), "admin", " ", "TRACE-001"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoginCommand(TenantId.of("2001"), "admin", "correct-password", " "))
                .isInstanceOf(IllegalArgumentException.class);

        LoginCommand command = new LoginCommand(
                TenantId.of("2001"),
                " admin ",
                " correct-password ",
                " TRACE-001 "
        );

        assertThat(command.loginName()).isEqualTo("admin");
        assertThat(command.rawPassword()).isEqualTo(" correct-password ");
        assertThat(command.traceId()).isEqualTo("TRACE-001");
    }

    @Test
    void currentUserAndLogoutCommandsRejectBlankTraceAndNormalizeToken() {
        assertThatThrownBy(() -> new CurrentUserCommand(TenantId.of("2001"), "token-1001", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LogoutCommand(TenantId.of("2001"), " ", "TRACE-LOGOUT-001"))
                .isInstanceOf(IllegalArgumentException.class);

        CurrentUserCommand currentUserCommand = new CurrentUserCommand(
                TenantId.of("2001"),
                " token-1001 ",
                " TRACE-ME-001 "
        );
        LogoutCommand logoutCommand = new LogoutCommand(
                TenantId.of("2001"),
                " token-1001 ",
                " TRACE-LOGOUT-001 "
        );

        assertThat(currentUserCommand.tokenValue()).isEqualTo("token-1001");
        assertThat(currentUserCommand.traceId()).isEqualTo("TRACE-ME-001");
        assertThat(logoutCommand.tokenValue()).isEqualTo("token-1001");
        assertThat(logoutCommand.traceId()).isEqualTo("TRACE-LOGOUT-001");
    }

    @Test
    void iamConfigurationSaveCommandsNormalizeFactsBeforeApplicationUse() {
        CreateIamMenuCommand createMenuCommand = new CreateIamMenuCommand(
                TenantId.of("2001"),
                " 1001 ",
                " TRACE-IAM-SAVE-007 ",
                " admin.audit-log ",
                " 审计日志 ",
                " ADMIN ",
                " /admin/audit-log ",
                " AdminAuditLog ",
                " admin/audit-log ",
                " audit ",
                " identity-access:iam-menu:manage ",
                60,
                " ACTIVE ",
                List.of(" identity-settings ", "model-hub", "identity-settings")
        );
        UpdateIamMenuCommand menuCommand = new UpdateIamMenuCommand(
                TenantId.of("2001"),
                " 1001 ",
                " TRACE-IAM-SAVE-001 ",
                " 5009 ",
                " 系统设置 ",
                " /admin/system-settings ",
                " AdminSystemSettings ",
                " admin/system-settings ",
                " settings ",
                " identity-access:system-setting:manage ",
                30,
                " ACTIVE "
        );
        ReplaceIamMenuFeaturesCommand menuFeaturesCommand = new ReplaceIamMenuFeaturesCommand(
                TenantId.of("2001"),
                " 1001 ",
                " TRACE-IAM-SAVE-002 ",
                " 5009 ",
                List.of(" identity-settings ", "model-hub", "identity-settings")
        );
        ReplaceIamRoleMenusCommand roleMenusCommand = new ReplaceIamRoleMenusCommand(
                TenantId.of("2001"),
                " 1001 ",
                " TRACE-IAM-SAVE-003 ",
                " 3002 ",
                List.of(" 5003 ", "5002")
        );
        ReplaceIamRolePermissionsCommand rolePermissionsCommand = new ReplaceIamRolePermissionsCommand(
                TenantId.of("2001"),
                " 1001 ",
                " TRACE-IAM-SAVE-004 ",
                " 3002 ",
                List.of(" identity-access:workspace:use ", "knowledge-base:retrieval:query"),
                " 权限职责调整 "
        );
        UpdateIamTenantFeatureCommand tenantFeatureCommand = new UpdateIamTenantFeatureCommand(
                TenantId.of("2001"),
                " 1001 ",
                " TRACE-IAM-SAVE-005 ",
                " identity-settings ",
                true,
                " 产品能力开通 "
        );
        UpdateIamRoleDefaultMenuCommand defaultMenuCommand = new UpdateIamRoleDefaultMenuCommand(
                TenantId.of("2001"),
                " 1001 ",
                " TRACE-IAM-SAVE-006 ",
                " 3002 ",
                " tenant.overview "
        );

        assertThat(createMenuCommand.actorId()).isEqualTo("1001");
        assertThat(createMenuCommand.traceId()).isEqualTo("TRACE-IAM-SAVE-007");
        assertThat(createMenuCommand.code()).isEqualTo("admin.audit-log");
        assertThat(createMenuCommand.scope()).isEqualTo("ADMIN");
        assertThat(createMenuCommand.featureCodes()).containsExactly("identity-settings", "model-hub");
        assertThat(menuCommand.actorId()).isEqualTo("1001");
        assertThat(menuCommand.traceId()).isEqualTo("TRACE-IAM-SAVE-001");
        assertThat(menuCommand.menuId()).isEqualTo("5009");
        assertThat(menuCommand.icon()).isEqualTo("settings");
        assertThat(menuFeaturesCommand.featureCodes()).containsExactly("identity-settings", "model-hub");
        assertThat(roleMenusCommand.menuIds()).containsExactly("5002", "5003");
        assertThat(rolePermissionsCommand.permissionCodes()).containsExactly(
                "identity-access:workspace:use",
                "knowledge-base:retrieval:query"
        );
        assertThat(rolePermissionsCommand.reason()).isEqualTo("权限职责调整");
        assertThat(tenantFeatureCommand.featureCode()).isEqualTo("identity-settings");
        assertThat(tenantFeatureCommand.reason()).isEqualTo("产品能力开通");
        assertThat(defaultMenuCommand.defaultMenuCode()).isEqualTo("tenant.overview");
    }

    @Test
    void iamConfigurationSaveCommandsRejectBlankFactsBeforeApplicationUse() {
        assertThatThrownBy(() -> new CreateIamMenuCommand(
                TenantId.of("2001"), "1001", "TRACE", " ", "审计日志", "ADMIN",
                "/admin/audit-log", "AdminAuditLog", "admin/audit-log", null,
                "identity-access:iam-menu:manage", 60, "ACTIVE", List.of()
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CreateIamMenuCommand(
                TenantId.of("2001"), "1001", "TRACE", "admin.audit-log", "审计日志", "UNKNOWN",
                "/admin/audit-log", "AdminAuditLog", "admin/audit-log", null,
                "identity-access:iam-menu:manage", 60, "ACTIVE", List.of()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("菜单范围只允许");
        assertThatThrownBy(() -> new UpdateIamMenuCommand(
                TenantId.of("2001"), " ", "TRACE", "5009", "系统设置", "/admin/system-settings",
                "AdminSystemSettings", "admin/system-settings", null,
                "identity-access:system-setting:manage", 30, "ACTIVE"
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UpdateIamMenuCommand(
                TenantId.of("2001"), "1001", "TRACE", "5009", "系统设置", "/admin/system-settings",
                "AdminSystemSettings", "admin/system-settings", null,
                "identity-access:system-setting:manage", 30, "UNKNOWN"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("菜单状态只允许");
        assertThatThrownBy(() -> new ReplaceIamMenuFeaturesCommand(
                TenantId.of("2001"), "1001", "TRACE", "5009", List.of(" ")
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReplaceIamRoleMenusCommand(
                TenantId.of("2001"), "1001", "TRACE", " ", List.of("5002")
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReplaceIamRolePermissionsCommand(
                TenantId.of("2001"), "1001", "TRACE", "3002", List.of(" "), "权限职责调整"
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UpdateIamTenantFeatureCommand(
                TenantId.of("2001"), "1001", "TRACE", " ", true, "产品能力开通"
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReplaceIamRolePermissionsCommand(
                TenantId.of("2001"), "1001", "TRACE", "3002", List.of(), " "
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UpdateIamTenantFeatureCommand(
                TenantId.of("2001"), "1001", "TRACE", "identity-settings", true, " "
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UpdateIamRoleDefaultMenuCommand(
                TenantId.of("2001"), "1001", "TRACE", "3002", " "
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loginResultRejectsBlankFactsAndNormalizesRoleCodes() {
        assertThatThrownBy(() -> new LoginResult(
                TenantId.of("2001"),
                " ",
                "平台管理员",
                Set.of("platform_admin"),
                "token-1001",
                EXPIRES_AT
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoginResult(
                TenantId.of("2001"),
                "1001",
                "平台管理员",
                Set.of(" "),
                "token-1001",
                EXPIRES_AT
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoginResult(
                TenantId.of("2001"),
                "1001",
                "平台管理员",
                Set.of(),
                "token-1001",
                EXPIRES_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("角色编码集合不能为空");

        LoginResult result = new LoginResult(
                TenantId.of("2001"),
                " 1001 ",
                " 平台管理员 ",
                Set.of(" platform_admin ", "auditor"),
                " token-1001 ",
                EXPIRES_AT
        );

        assertThat(result.userId()).isEqualTo("1001");
        assertThat(result.displayName()).isEqualTo("平台管理员");
        assertThat(result.roleCodes()).containsExactlyInAnyOrder("platform_admin", "auditor");
        assertThat(result.accessToken()).isEqualTo("token-1001");
    }

    @Test
    void currentUserResultRejectsBlankFactsAndNormalizesRoleCodes() {
        assertThatThrownBy(() -> new CurrentUserResult(
                TenantId.of("2001"),
                "1001",
                " ",
                "平台管理员",
                Set.of("platform_admin"),
                Set.of(new PermissionCode("identity-access:session:read")),
                List.of(validMenu()),
                "/workspace/home",
                EXPIRES_AT
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CurrentUserResult(
                TenantId.of("2001"),
                "1001",
                "admin",
                "平台管理员",
                Set.of(" "),
                Set.of(new PermissionCode("identity-access:session:read")),
                List.of(validMenu()),
                "/workspace/home",
                EXPIRES_AT
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CurrentUserResult(
                TenantId.of("2001"),
                "1001",
                "admin",
                "平台管理员",
                Set.of(),
                Set.of(new PermissionCode("identity-access:session:read")),
                List.of(validMenu()),
                "/workspace/home",
                EXPIRES_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("角色编码集合不能为空");

        CurrentUserResult result = new CurrentUserResult(
                TenantId.of("2001"),
                " 1001 ",
                " admin ",
                " 平台管理员 ",
                Set.of(" platform_admin ", "auditor"),
                Set.of(new PermissionCode("identity-access:session:read")),
                List.of(validMenu()),
                " /workspace/home ",
                EXPIRES_AT
        );

        assertThat(result.userId()).isEqualTo("1001");
        assertThat(result.loginName()).isEqualTo("admin");
        assertThat(result.displayName()).isEqualTo("平台管理员");
        assertThat(result.roleCodes()).containsExactlyInAnyOrder("platform_admin", "auditor");
        assertThat(result.permissionCodes()).extracting(PermissionCode::value).containsExactly("identity-access:session:read");
        assertThat(result.menus()).extracting(CurrentUserMenuResult::code).containsExactly("business.my-workspace");
        assertThat(result.defaultRoute()).isEqualTo("/workspace/home");

        CurrentUserResult noPermissionResult = new CurrentUserResult(
                TenantId.of("2001"),
                "1001",
                "admin",
                "平台管理员",
                Set.of("platform_admin"),
                Set.of(),
                List.of(),
                "/403",
                EXPIRES_AT
        );
        assertThat(noPermissionResult.permissionCodes()).isEmpty();
    }

    private static CurrentUserMenuResult validMenu() {
        return new CurrentUserMenuResult(
                "business.my-workspace",
                "我的工作台",
                "BUSINESS",
                "/workspace/home",
                "BusinessMyWorkspace",
                "workspace/home",
                "identity-access:workspace:use",
                8
        );
    }
}
