package com.aiplatform.identity.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.identity.application.result.CurrentUserResult;
import com.aiplatform.identity.application.result.CurrentUserMenuResult;
import com.aiplatform.identity.application.result.IamMenuConfigurationResult;
import com.aiplatform.identity.application.result.IamPermissionResult;
import com.aiplatform.identity.application.result.IamRoleResult;
import com.aiplatform.identity.application.result.IamTenantFeatureResult;
import com.aiplatform.identity.application.result.LoginResult;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.model.PermissionCode;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IdentityPresentationResponseContractTest {
    private static final Instant EXPIRES_AT = Instant.parse("2026-05-22T03:30:00Z");

    @Test
    void loginResponseNormalizesSessionFacts() {
        LoginResponse response = new LoginResponse(
                " token-1001 ",
                " 1001 ",
                " 2001 ",
                " 平台管理员 ",
                Set.of(" platform-admin ", " auditor "),
                EXPIRES_AT
        );

        assertThat(response.token()).isEqualTo("token-1001");
        assertThat(response.userId()).isEqualTo("1001");
        assertThat(response.tenantId()).isEqualTo("2001");
        assertThat(response.displayName()).isEqualTo("平台管理员");
        assertThat(response.roleCodes()).containsExactlyInAnyOrder("platform-admin", "auditor");
        assertThat(response.tokenExpiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    void currentUserResponseNormalizesAndSortsRoleCodes() {
        CurrentUserResponse response = new CurrentUserResponse(
                " 2001 ",
                " 1001 ",
                " admin ",
                " 平台管理员 ",
                List.of(" platform_admin ", "auditor", " platform_admin "),
                List.of(" model-hub:provider:read ", "app-studio:application:read", "model-hub:provider:read"),
                List.of(
                        new ConsoleMenuItem(
                                "admin.model-providers",
                                "模型",
                                "ADMIN",
                                "/admin/model-providers",
                                "AdminModelProviders",
                                "admin/model-providers/list",
                                "model-hub:provider:read",
                                20
                        ),
                        new ConsoleMenuItem(
                                "business.app-studio",
                                "AI应用",
                                "BUSINESS",
                                "/workspace/app-studio",
                                "BusinessAppStudio",
                                "business/app-studio/list",
                                "app-studio:application:read",
                                30
                        )
                ),
                " /admin/model-providers ",
                EXPIRES_AT
        );

        assertThat(response.tenantId()).isEqualTo("2001");
        assertThat(response.userId()).isEqualTo("1001");
        assertThat(response.loginName()).isEqualTo("admin");
        assertThat(response.displayName()).isEqualTo("平台管理员");
        assertThat(response.roleCodes()).containsExactly("auditor", "platform_admin");
        assertThat(response.permissionCodes()).containsExactly("app-studio:application:read", "model-hub:provider:read");
        assertThat(response.menus())
                .extracting(ConsoleMenuItem::componentKey)
                .containsExactly("admin/model-providers/list", "business/app-studio/list");
        assertThat(response.defaultRoute()).isEqualTo("/admin/model-providers");
        assertThat(response.accessTokenExpiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    void logoutResponseNormalizesStatus() {
        LogoutResponse response = new LogoutResponse(" LOGGED_OUT ");

        assertThat(response.status()).isEqualTo("LOGGED_OUT");
    }

    @Test
    void iamConfigurationResponsesNormalizeReadFacts() {
        IamMenuConfigurationResponse menu = IamMenuConfigurationResponse.from(new IamMenuConfigurationResult(
                " 5009 ",
                " admin.system-settings ",
                " 系统设置 ",
                " ADMIN ",
                " /admin/system-settings ",
                " AdminSystemSettings ",
                " admin/system-settings ",
                " setting ",
                " identity-access:system-setting:manage ",
                50,
                " ACTIVE ",
                true,
                List.of(" identity-settings ", "identity-settings"),
                true
        ));
        IamTenantFeatureResponse feature = IamTenantFeatureResponse.from(new IamTenantFeatureResult(
                " identity-settings ",
                " 身份与租户设置 ",
                true,
                " PLAN "
        ));
        IamRoleResponse role = IamRoleResponse.from(new IamRoleResult(
                " 3001 ",
                " platform-admin ",
                " 平台管理员 ",
                " PLATFORM ",
                " admin.platform-overview ",
                " ACTIVE ",
                null
        ));
        IamPermissionResponse permission = IamPermissionResponse.from(new IamPermissionResult(
                " 4003 ",
                " identity-access:iam-config:read ",
                " 读取 IAM 配置 ",
                " iam-config ",
                " read ",
                null,
                " ACTIVE "
        ));

        assertThat(menu.menuId()).isEqualTo("5009");
        assertThat(menu.featureCodes()).containsExactly("identity-settings");
        assertThat(feature.featureCode()).isEqualTo("identity-settings");
        assertThat(role.defaultMenuCode()).isEqualTo("admin.platform-overview");
        assertThat(permission.code()).isEqualTo("identity-access:iam-config:read");
    }

    @Test
    void responseFactoriesKeepNormalizedFacts() {
        LoginResponse loginResponse = LoginResponse.from(new LoginResult(
                TenantId.of(" 2001 "),
                " 1001 ",
                " 平台管理员 ",
                Set.of(" platform-admin ", " auditor "),
                " token-1001 ",
                EXPIRES_AT
        ));
        CurrentUserResponse currentUserResponse = CurrentUserResponse.from(new CurrentUserResult(
                TenantId.of(" 2001 "),
                " 1001 ",
                " admin ",
                " 平台管理员 ",
                Set.of(" platform-admin ", " auditor "),
                Set.of(
                        new PermissionCode("identity-access:platform-admin:use"),
                        new PermissionCode("model-hub:provider:read"),
                        new PermissionCode("knowledge-base:retrieval:query")
                ),
                List.of(
                        new CurrentUserMenuResult(
                                "admin.platform-overview",
                                "平台总览",
                                "ADMIN",
                                "/admin/platform-overview",
                                "AdminPlatformOverview",
                                "admin/platform-overview",
                                "identity-access:platform-admin:use",
                                1
                        ),
                        new CurrentUserMenuResult(
                                "admin.model-providers",
                                "模型",
                                "ADMIN",
                                "/admin/model-providers",
                                "AdminModelProviders",
                                "admin/model-providers/list",
                                "model-hub:provider:read",
                                20
                        )
                ),
                "/admin/platform-overview",
                EXPIRES_AT
        ));

        assertThat(loginResponse.tenantId()).isEqualTo("2001");
        assertThat(loginResponse.token()).isEqualTo("token-1001");
        assertThat(loginResponse.roleCodes()).containsExactlyInAnyOrder("platform-admin", "auditor");
        assertThat(currentUserResponse.tenantId()).isEqualTo("2001");
        assertThat(currentUserResponse.loginName()).isEqualTo("admin");
        assertThat(currentUserResponse.roleCodes()).containsExactly("auditor", "platform-admin");
        assertThat(currentUserResponse.permissionCodes()).containsExactly(
                "identity-access:platform-admin:use",
                "knowledge-base:retrieval:query",
                "model-hub:provider:read"
        );
        assertThat(currentUserResponse.menus()).hasSize(2);
    }



    @Test
    void rejectsBlankRequiredSessionFacts() {
        assertThatThrownBy(() -> new LoginResponse(
                " ",
                "1001",
                "2001",
                "平台管理员",
                Set.of("platform_admin"),
                EXPIRES_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("访问令牌不能为空");

        assertThatThrownBy(() -> new CurrentUserResponse(
                "2001",
                "1001",
                " ",
                "平台管理员",
                List.of("platform_admin"),
                List.of("model-hub:provider:read"),
                List.of(validMenuItem()),
                "/admin/model-providers",
                EXPIRES_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("登录名不能为空");

        assertThatThrownBy(() -> new LogoutResponse(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("登出状态不能为空");

        assertThatThrownBy(() -> new IamPermissionResponse(
                "4003",
                " ",
                "读取 IAM 配置",
                "iam-config",
                "read",
                null,
                "ACTIVE"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("权限编码不能为空");
    }

    @Test
    void rejectsBlankRoleCodeAndMissingExpiry() {
        assertThatThrownBy(() -> new LoginResponse(
                "token-1001",
                "1001",
                "2001",
                "平台管理员",
                Set.of(" "),
                EXPIRES_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("角色编码不能为空");

        assertThatThrownBy(() -> new LoginResponse(
                "token-1001",
                "1001",
                "2001",
                "平台管理员",
                Set.of(),
                EXPIRES_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("角色编码集合不能为空");

        assertThatThrownBy(() -> new CurrentUserResponse(
                "2001",
                "1001",
                "admin",
                "平台管理员",
                List.of(),
                List.of("model-hub:provider:read"),
                List.of(validMenuItem()),
                "/admin/model-providers",
                EXPIRES_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("角色编码列表不能为空");

        assertThatThrownBy(() -> new CurrentUserResponse(
                "2001",
                "1001",
                "admin",
                "平台管理员",
                List.of("platform_admin"),
                List.of("model-hub:provider:read"),
                List.of(validMenuItem()),
                "/admin/model-providers",
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("令牌过期时间不能为空");
    }

    private static ConsoleMenuItem validMenuItem() {
        return new ConsoleMenuItem(
                "admin.model-providers",
                "模型",
                "ADMIN",
                "/admin/model-providers",
                "AdminModelProviders",
                "admin/model-providers/list",
                "model-hub:provider:read",
                20
        );
    }
}
