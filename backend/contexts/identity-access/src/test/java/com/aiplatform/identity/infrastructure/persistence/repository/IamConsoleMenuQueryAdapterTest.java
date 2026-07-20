package com.aiplatform.identity.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiplatform.identity.infrastructure.persistence.entity.IamConsoleMenuPersistenceEntity;
import com.aiplatform.identity.infrastructure.persistence.mapper.IamAuthorizationMapper;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.model.PermissionCode;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IamConsoleMenuQueryAdapterTest {
    @Test
    void loadsAuthorizedMenusFromDatabaseFactsAndResolvesRoleDefaultRoute() {
        IamAuthorizationMapper mapper = mock(IamAuthorizationMapper.class);
        IamConsoleMenuQueryAdapter adapter = new IamConsoleMenuQueryAdapter(mapper);
        Set<PermissionCode> permissionCodes = Set.of(
                new PermissionCode("identity-access:platform-admin:use"),
                new PermissionCode("model-hub:provider:read")
        );
        when(mapper.findAuthorizedConsoleMenus(
                2001L,
                1001L,
                Set.of("identity-access:platform-admin:use", "model-hub:provider:read")
        )).thenReturn(List.of(
                menu("admin.model-providers", "模型", "ADMIN", "/admin/model-providers", "AdminModelProviders", "admin/model-providers/list", "model-hub:provider:read", 20),
                menu("admin.platform-overview", "平台总览", "ADMIN", "/admin/platform-overview", "AdminPlatformOverview", "admin/platform-overview", "identity-access:platform-admin:use", 1)
        ));
        when(mapper.findActiveRoleDefaultMenuCodes(2001L, 1001L))
                .thenReturn(List.of("admin.platform-overview"));

        var snapshot = adapter.findAuthorizedMenus(
                TenantId.of("2001"),
                "user",
                "1001",
                Set.of("platform-admin"),
                permissionCodes
        );

        assertThat(snapshot.menus()).extracting("code")
                .containsExactly("admin.platform-overview", "admin.model-providers");
        assertThat(snapshot.defaultRoute()).isEqualTo("/admin/platform-overview");
    }

    @Test
    void fallsBackToFirstEffectiveMenuWhenRoleDefaultIsNotAuthorized() {
        IamAuthorizationMapper mapper = mock(IamAuthorizationMapper.class);
        IamConsoleMenuQueryAdapter adapter = new IamConsoleMenuQueryAdapter(mapper);
        Set<PermissionCode> permissionCodes = Set.of(new PermissionCode("identity-access:workspace:use"));
        when(mapper.findAuthorizedConsoleMenus(2001L, 1003L, Set.of("identity-access:workspace:use")))
                .thenReturn(List.of(menu("business.my-workspace", "我的工作台", "BUSINESS", "/workspace/home", "BusinessMyWorkspace", "workspace/home", "identity-access:workspace:use", 8)));
        when(mapper.findActiveRoleDefaultMenuCodes(2001L, 1003L))
                .thenReturn(List.of("admin.platform-overview"));

        var snapshot = adapter.findAuthorizedMenus(
                TenantId.of("2001"),
                "user",
                "1003",
                Set.of("business-user"),
                permissionCodes
        );

        assertThat(snapshot.defaultRoute()).isEqualTo("/workspace/home");
    }

    @Test
    void failsClosedWhenMapperReturnsNullMenus() {
        IamAuthorizationMapper mapper = mock(IamAuthorizationMapper.class);
        IamConsoleMenuQueryAdapter adapter = new IamConsoleMenuQueryAdapter(mapper);
        when(mapper.findAuthorizedConsoleMenus(2001L, 1001L, Set.of("identity-access:workspace:use")))
                .thenReturn(null);

        assertThatThrownBy(() -> adapter.findAuthorizedMenus(
                TenantId.of("2001"),
                "user",
                "1001",
                Set.of("business-user"),
                Set.of(new PermissionCode("identity-access:workspace:use"))
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("授权菜单查询结果不能为空");
    }

    private static IamConsoleMenuPersistenceEntity menu(
            String code,
            String name,
            String platform,
            String path,
            String routeName,
            String componentKey,
            String permission,
            int sort
    ) {
        IamConsoleMenuPersistenceEntity row = new IamConsoleMenuPersistenceEntity();
        row.setCode(code);
        row.setName(name);
        row.setPlatform(platform);
        row.setPath(path);
        row.setRouteName(routeName);
        row.setComponentKey(componentKey);
        row.setPermission(permission);
        row.setSort(sort);
        return row;
    }
}
