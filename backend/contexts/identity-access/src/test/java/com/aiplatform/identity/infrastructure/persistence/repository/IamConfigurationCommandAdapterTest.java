package com.aiplatform.identity.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.identity.application.command.AssignIamTenantAdministratorCommand;
import com.aiplatform.identity.application.command.CreateIamMenuCommand;
import com.aiplatform.identity.application.command.ReplaceIamRoleMenusCommand;
import com.aiplatform.identity.application.command.ReplaceIamRolePermissionsCommand;
import com.aiplatform.identity.application.command.UpdateIamRoleDefaultMenuCommand;
import com.aiplatform.identity.infrastructure.persistence.mapper.IamAuthorizationMapper;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.TenantId;
import java.util.List;
import org.junit.jupiter.api.Test;

class IamConfigurationCommandAdapterTest {
    @Test
    void assignsOnlyActiveTenantUserToTenantAdminRole() {
        IamAuthorizationMapper mapper = mock(IamAuthorizationMapper.class);
        when(mapper.countActiveTenantUser(9001L, 1008L)).thenReturn(1);
        when(mapper.countActiveTenantAdminRole(9001L, 3002L)).thenReturn(1);
        IamConfigurationCommandAdapter adapter = new IamConfigurationCommandAdapter(mapper);

        adapter.assignTenantAdministrator(new AssignIamTenantAdministratorCommand(
                TenantId.of("9001"),
                "1001",
                "TRACE-DELEGATION-001",
                "1008",
                "3002"
        ));

        verify(mapper).insertIamUserRole(9001L, 1008L, 3002L, "platform-delegation:1001");
    }

    @Test
    void rejectsRoleThatIsNotTenantAdminRole() {
        IamAuthorizationMapper mapper = mock(IamAuthorizationMapper.class);
        when(mapper.countActiveTenantUser(9001L, 1008L)).thenReturn(1);
        when(mapper.countActiveTenantAdminRole(9001L, 3003L)).thenReturn(0);
        IamConfigurationCommandAdapter adapter = new IamConfigurationCommandAdapter(mapper);

        assertThatThrownBy(() -> adapter.assignTenantAdministrator(new AssignIamTenantAdministratorCommand(
                TenantId.of("9001"),
                "1001",
                "TRACE-DELEGATION-002",
                "1008",
                "3003"
        )))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("AIP-IAM-RESOURCE-5004");

        verify(mapper, never()).insertIamUserRole(9001L, 1008L, 3003L, "platform-delegation:1001");
    }

    @Test
    void createsMenuWithTenantFeatureBindings() {
        IamAuthorizationMapper mapper = mock(IamAuthorizationMapper.class);
        when(mapper.countActivePermissionByCode("identity-access:iam-menu:manage")).thenReturn(1);
        when(mapper.countIamMenuByCodeOrPath("admin.audit-log", "/admin/audit-log")).thenReturn(0);
        when(mapper.countTenantFeatures(2001L, List.of("identity-settings"))).thenReturn(1);
        when(mapper.insertIamMenu(
                "admin.audit-log",
                "审计日志",
                "ADMIN",
                "/admin/audit-log",
                "AdminAuditLog",
                "admin/audit-log",
                "audit",
                "identity-access:iam-menu:manage",
                60,
                "ACTIVE",
                1001L
        )).thenReturn(5010L);
        when(mapper.countIamMenuById(5010L)).thenReturn(1);
        when(mapper.insertIamMenuFeature(5010L, "identity-settings")).thenReturn(1);
        IamConfigurationCommandAdapter adapter = new IamConfigurationCommandAdapter(mapper);

        String menuId = adapter.createMenu(new CreateIamMenuCommand(
                TenantId.of("2001"),
                "1001",
                "TRACE-IAM-SAVE-007",
                "admin.audit-log",
                "审计日志",
                "ADMIN",
                "/admin/audit-log",
                "AdminAuditLog",
                "admin/audit-log",
                "audit",
                "identity-access:iam-menu:manage",
                60,
                "ACTIVE",
                List.of("identity-settings", "identity-settings")
        ));

        assertThat(menuId).isEqualTo("5010");
        verify(mapper).insertIamMenuFeature(5010L, "identity-settings");
    }

    @Test
    void rejectsCreateMenuWhenCodeOrPathExists() {
        IamAuthorizationMapper mapper = mock(IamAuthorizationMapper.class);
        when(mapper.countActivePermissionByCode("identity-access:iam-menu:manage")).thenReturn(1);
        when(mapper.countIamMenuByCodeOrPath("admin.audit-log", "/admin/audit-log")).thenReturn(1);
        IamConfigurationCommandAdapter adapter = new IamConfigurationCommandAdapter(mapper);

        assertThatThrownBy(() -> adapter.createMenu(new CreateIamMenuCommand(
                TenantId.of("2001"),
                "1001",
                "TRACE-IAM-SAVE-008",
                "admin.audit-log",
                "审计日志",
                "ADMIN",
                "/admin/audit-log",
                "AdminAuditLog",
                "admin/audit-log",
                "audit",
                "identity-access:iam-menu:manage",
                60,
                "ACTIVE",
                List.of()
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("菜单编码或路径已存在");

        verify(mapper, never()).insertIamMenu(
                "admin.audit-log",
                "审计日志",
                "ADMIN",
                "/admin/audit-log",
                "AdminAuditLog",
                "admin/audit-log",
                "audit",
                "identity-access:iam-menu:manage",
                60,
                "ACTIVE",
                1001L
        );
    }

    @Test
    void replacesRoleMenusWithTenantScopedAssignableMenus() {
        IamAuthorizationMapper mapper = mock(IamAuthorizationMapper.class);
        when(mapper.countManageableIamRoleByTenant(2001L, 3002L)).thenReturn(1);
        when(mapper.countAssignableRoleMenus(2001L, List.of(5002L, 5003L))).thenReturn(2);
        when(mapper.insertIamRoleMenu(2001L, 3002L, 5002L, 1001L)).thenReturn(1);
        when(mapper.insertIamRoleMenu(2001L, 3002L, 5003L, 1001L)).thenReturn(1);
        IamConfigurationCommandAdapter adapter = new IamConfigurationCommandAdapter(mapper);

        adapter.replaceRoleMenus(new ReplaceIamRoleMenusCommand(
                TenantId.of("2001"),
                "1001",
                "TRACE-IAM-SAVE-003",
                "3002",
                List.of("5003", "5002")
        ));

        verify(mapper).softDeleteIamRoleMenus(2001L, 3002L, 1001L);
        verify(mapper).insertIamRoleMenu(2001L, 3002L, 5002L, 1001L);
        verify(mapper).insertIamRoleMenu(2001L, 3002L, 5003L, 1001L);
    }

    @Test
    void rejectsRolePermissionsContainingPlatformManagePermission() {
        IamAuthorizationMapper mapper = mock(IamAuthorizationMapper.class);
        when(mapper.countManageableIamRoleByTenant(2001L, 3002L)).thenReturn(1);
        when(mapper.countAssignableRolePermissions(List.of("identity-access:iam-menu:manage"))).thenReturn(0);
        IamConfigurationCommandAdapter adapter = new IamConfigurationCommandAdapter(mapper);

        assertThatThrownBy(() -> adapter.replaceRolePermissions(new ReplaceIamRolePermissionsCommand(
                TenantId.of("2001"),
                "1001",
                "TRACE-IAM-SAVE-004",
                "3002",
                List.of("identity-access:iam-menu:manage"),
                "权限职责调整"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("角色权限包含不存在、停用或平台级权限");

        verify(mapper, never()).softDeleteIamRolePermissions(2001L, 3002L, 1001L);
    }

    @Test
    void rejectsDefaultMenuOutsideRoleEffectiveMenuSet() {
        IamAuthorizationMapper mapper = mock(IamAuthorizationMapper.class);
        when(mapper.countManageableIamRoleByTenant(2001L, 3002L)).thenReturn(1);
        when(mapper.countRoleAssignedMenuCode(2001L, 3002L, "admin.platform-overview")).thenReturn(0);
        IamConfigurationCommandAdapter adapter = new IamConfigurationCommandAdapter(mapper);

        assertThatThrownBy(() -> adapter.updateRoleDefaultMenu(new UpdateIamRoleDefaultMenuCommand(
                TenantId.of("2001"),
                "1001",
                "TRACE-IAM-SAVE-005",
                "3002",
                "admin.platform-overview"
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("默认首页不在角色有效菜单集合内");
    }

    @Test
    void rejectsPermissionReplacementForSystemRole() {
        IamAuthorizationMapper mapper = mock(IamAuthorizationMapper.class);
        when(mapper.countManageableIamRoleByTenant(2001L, 3001L)).thenReturn(0);
        IamConfigurationCommandAdapter adapter = new IamConfigurationCommandAdapter(mapper);

        assertThatThrownBy(() -> adapter.replaceRolePermissions(new ReplaceIamRolePermissionsCommand(
                TenantId.of("2001"),
                "1002",
                "TRACE-IAM-SAVE-SYSTEM-ROLE",
                "3001",
                List.of("app-studio:application:read"),
                "权限职责调整"
        )))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("角色不可维护");

        verify(mapper, never()).softDeleteIamRolePermissions(2001L, 3001L, 1002L);
    }
}
