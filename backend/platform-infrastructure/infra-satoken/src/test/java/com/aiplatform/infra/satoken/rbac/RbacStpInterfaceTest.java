package com.aiplatform.infra.satoken.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.model.PermissionCode;
import com.aiplatform.shared.security.port.RbacAuthorizationQueryPort;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RbacStpInterfaceTest {
    @Test
    void returnsRolesAndPermissionsFromRbacAuthorizationQueryPort() {
        RbacAuthorizationQueryPort queryPort = mock(RbacAuthorizationQueryPort.class);
        RbacStpInterface stpInterface = new RbacStpInterface(queryPort);
        TenantId tenantId = TenantId.of("2001");
        when(queryPort.findRoleCodes(tenantId, "user", "1001"))
                .thenReturn(Set.of("platform-admin"));
        when(queryPort.findPermissionCodes(tenantId, "user", "1001"))
                .thenReturn(Set.of(new PermissionCode("knowledge-base:document:upload")));

        assertThat(stpInterface.getRoleList("2001:user:1001", "login"))
                .containsExactly("platform-admin");
        assertThat(stpInterface.getPermissionList("2001:user:1001", "login"))
                .containsExactly("knowledge-base:document:upload");
    }

    @Test
    void normalizesRolesAndPermissionsReturnedByAuthorizationPort() {
        RbacAuthorizationQueryPort queryPort = mock(RbacAuthorizationQueryPort.class);
        RbacStpInterface stpInterface = new RbacStpInterface(queryPort);
        TenantId tenantId = TenantId.of("2001");
        when(queryPort.findRoleCodes(tenantId, "user", "1001"))
                .thenReturn(Set.of(" platform-admin ", " auditor "));
        when(queryPort.findPermissionCodes(tenantId, "user", "1001"))
                .thenReturn(Set.of(new PermissionCode(" knowledge-base:document:upload ")));

        assertThat(stpInterface.getRoleList("2001:user:1001", "login"))
                .containsExactly("auditor", "platform-admin");
        assertThat(stpInterface.getPermissionList("2001:user:1001", "login"))
                .containsExactly("knowledge-base:document:upload");
    }

    @Test
    void returnsEmptyListsWhenLoginIdIsMissing() {
        RbacAuthorizationQueryPort queryPort = mock(RbacAuthorizationQueryPort.class);
        RbacStpInterface stpInterface = new RbacStpInterface(queryPort);

        assertThat(stpInterface.getPermissionList(null, "login")).isEmpty();
    }

    @Test
    void rejectsMalformedLoginIdInsteadOfEmptyPermissionFallback() {
        RbacAuthorizationQueryPort queryPort = mock(RbacAuthorizationQueryPort.class);
        RbacStpInterface stpInterface = new RbacStpInterface(queryPort);

        assertThatThrownBy(() -> stpInterface.getRoleList("invalid", "login"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Sa-Token 登录主体格式非法");
        assertThatThrownBy(() -> stpInterface.getPermissionList("2001::1001", "login"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Sa-Token 登录主体格式非法");
    }

    @Test
    void rejectsNullAuthorizationPortResultsInsteadOfEmptyRoleOrPermissionFallback() {
        RbacAuthorizationQueryPort queryPort = mock(RbacAuthorizationQueryPort.class);
        RbacStpInterface stpInterface = new RbacStpInterface(queryPort);
        TenantId tenantId = TenantId.of("2001");
        when(queryPort.findRoleCodes(tenantId, "user", "1001")).thenReturn(null);
        when(queryPort.findPermissionCodes(tenantId, "user", "1001")).thenReturn(null);

        assertThatThrownBy(() -> stpInterface.getRoleList("2001:user:1001", "login"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("角色编码查询结果不能为空");
        assertThatThrownBy(() -> stpInterface.getPermissionList("2001:user:1001", "login"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("权限编码查询结果不能为空");
    }

    @Test
    void rejectsBadAuthorizationPortElementsInsteadOfBadSaTokenRolesOrPermissions() {
        RbacAuthorizationQueryPort queryPort = mock(RbacAuthorizationQueryPort.class);
        RbacStpInterface stpInterface = new RbacStpInterface(queryPort);
        TenantId tenantId = TenantId.of("2001");
        when(queryPort.findRoleCodes(tenantId, "user", "1001")).thenReturn(Set.of(" "));
        Set<PermissionCode> permissionCodes = new LinkedHashSet<>();
        permissionCodes.add(null);
        when(queryPort.findPermissionCodes(tenantId, "user", "1001")).thenReturn(permissionCodes);

        assertThatThrownBy(() -> stpInterface.getRoleList("2001:user:1001", "login"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("角色编码不能为空");
        assertThatThrownBy(() -> stpInterface.getPermissionList("2001:user:1001", "login"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("权限编码不能为空");
    }

    @Test
    void rejectsNonStringLoginIdInsteadOfToStringPermissionFallback() {
        RbacAuthorizationQueryPort queryPort = mock(RbacAuthorizationQueryPort.class);
        RbacStpInterface stpInterface = new RbacStpInterface(queryPort);

        assertThatThrownBy(() -> stpInterface.getRoleList(new StringBuilder("2001:user:1001"), "login"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Sa-Token 登录主体类型非法");
        assertThatThrownBy(() -> stpInterface.getPermissionList(new StringBuilder("2001:user:1001"), "login"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Sa-Token 登录主体类型非法");
    }
}
