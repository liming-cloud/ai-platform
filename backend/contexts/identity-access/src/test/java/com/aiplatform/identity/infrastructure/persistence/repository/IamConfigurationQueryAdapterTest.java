package com.aiplatform.identity.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.identity.infrastructure.persistence.entity.IamMenuConfigurationPersistenceEntity;
import com.aiplatform.identity.infrastructure.persistence.entity.IamPermissionPersistenceEntity;
import com.aiplatform.identity.infrastructure.persistence.entity.IamRolePersistenceEntity;
import com.aiplatform.identity.infrastructure.persistence.entity.IamTenantFeaturePersistenceEntity;
import com.aiplatform.identity.infrastructure.persistence.entity.IamUserConfigurationPersistenceEntity;
import com.aiplatform.identity.infrastructure.persistence.mapper.IamAuthorizationMapper;
import com.aiplatform.shared.kernel.TenantId;
import java.util.List;
import org.junit.jupiter.api.Test;

class IamConfigurationQueryAdapterTest {
    @Test
    void pagesTenantUsersInTheDatabaseAndCorrectsAnOutOfRangePage() {
        IamAuthorizationMapper mapper = mock(IamAuthorizationMapper.class);
        when(mapper.countIamUsers(2001L)).thenReturn(11L);
        when(mapper.findIamUsers(2001L, 10, 10L)).thenReturn(List.of(user("1011", "陈晓")));
        IamConfigurationQueryAdapter adapter = new IamConfigurationQueryAdapter(mapper);

        var page = adapter.findUsers(TenantId.of("2001"), 99, 10);

        assertThat(page.items()).extracting("userId").containsExactly("1011");
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.pageSize()).isEqualTo(10);
        assertThat(page.total()).isEqualTo(11);
        assertThat(page.totalPages()).isEqualTo(2);
        verify(mapper).countIamUsers(2001L);
        verify(mapper).findIamUsers(2001L, 10, 10L);
    }

    @Test
    void doesNotRunThePageQueryWhenTheTenantHasNoManageableUsers() {
        IamAuthorizationMapper mapper = mock(IamAuthorizationMapper.class);
        when(mapper.countIamUsers(2001L)).thenReturn(0L);
        IamConfigurationQueryAdapter adapter = new IamConfigurationQueryAdapter(mapper);

        var page = adapter.findUsers(TenantId.of("2001"), 3, 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.totalPages()).isZero();
        verify(mapper, never()).findIamUsers(2001L, 20, 0L);
    }

    @Test
    void readsTenantScopedIamConfigurationFacts() {
        IamAuthorizationMapper mapper = mock(IamAuthorizationMapper.class);
        when(mapper.findIamConfigurationMenus(2001L)).thenReturn(List.of(menu("5009", "admin.system-settings")));
        when(mapper.findIamTenantFeatures(2001L)).thenReturn(List.of(feature("identity-settings")));
        when(mapper.findIamRoles(2001L)).thenReturn(List.of(role("3001", "platform-admin")));
        when(mapper.findIamPermissions(2001L)).thenReturn(List.of(permission("4003", "identity-access:iam-config:read")));
        when(mapper.findIamRoleMenus(2001L, 3001L)).thenReturn(List.of(menu("5009", "admin.system-settings")));
        when(mapper.findIamRolePermissions(2001L, 3001L)).thenReturn(List.of(permission("4003", "identity-access:iam-config:read")));
        IamConfigurationQueryAdapter adapter = new IamConfigurationQueryAdapter(mapper);

        assertThat(adapter.findMenus(TenantId.of("2001"))).extracting("code")
                .containsExactly("admin.system-settings");
        assertThat(adapter.findTenantFeatures(TenantId.of("2001"))).extracting("featureCode")
                .containsExactly("identity-settings");
        assertThat(adapter.findRoles(TenantId.of("2001"))).extracting("code")
                .containsExactly("platform-admin");
        assertThat(adapter.findPermissions(TenantId.of("2001"))).extracting("code")
                .containsExactly("identity-access:iam-config:read");
        assertThat(adapter.findRoleMenus(TenantId.of("2001"), "3001")).extracting("code")
                .containsExactly("admin.system-settings");
        assertThat(adapter.findRolePermissions(TenantId.of("2001"), "3001")).extracting("code")
                .containsExactly("identity-access:iam-config:read");
    }

    @Test
    void failsClosedWhenMapperReturnsNullForConfigurationReads() {
        IamAuthorizationMapper mapper = mock(IamAuthorizationMapper.class);
        when(mapper.findIamRoles(2001L)).thenReturn(null);
        IamConfigurationQueryAdapter adapter = new IamConfigurationQueryAdapter(mapper);

        assertThatThrownBy(() -> adapter.findRoles(TenantId.of("2001")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IAM 角色查询结果不能为空");
    }

    private static IamMenuConfigurationPersistenceEntity menu(String id, String code) {
        IamMenuConfigurationPersistenceEntity row = new IamMenuConfigurationPersistenceEntity();
        row.setId(Long.valueOf(id));
        row.setCode(code);
        row.setName("系统设置");
        row.setScope("ADMIN");
        row.setPath("/admin/system-settings");
        row.setRouteName("AdminSystemSettings");
        row.setComponentKey("admin/system-settings");
        row.setIcon("setting");
        row.setRequiredPermissionCode("identity-access:system-setting:manage");
        row.setSort(50);
        row.setStatus("ACTIVE");
        row.setSystemBuiltin(true);
        row.setFeatureCodes("identity-settings");
        row.setTenantFeatureEnabled(true);
        return row;
    }

    private static IamTenantFeaturePersistenceEntity feature(String code) {
        IamTenantFeaturePersistenceEntity row = new IamTenantFeaturePersistenceEntity();
        row.setFeatureCode(code);
        row.setFeatureName("身份与租户设置");
        row.setEnabled(true);
        row.setSource("PLAN");
        return row;
    }

    private static IamRolePersistenceEntity role(String id, String code) {
        IamRolePersistenceEntity row = new IamRolePersistenceEntity();
        row.setId(Long.valueOf(id));
        row.setCode(code);
        row.setName("平台管理员");
        row.setType("PLATFORM");
        row.setDefaultMenuCode("admin.platform-overview");
        row.setStatus("ACTIVE");
        return row;
    }

    private static IamPermissionPersistenceEntity permission(String id, String code) {
        IamPermissionPersistenceEntity row = new IamPermissionPersistenceEntity();
        row.setId(Long.valueOf(id));
        row.setCode(code);
        row.setName("读取 IAM 配置");
        row.setResourceType("iam-config");
        row.setActionCode("read");
        row.setDescription("允许读取菜单、租户功能、角色和授权矩阵配置");
        row.setStatus("ACTIVE");
        return row;
    }

    private static IamUserConfigurationPersistenceEntity user(String id, String displayName) {
        IamUserConfigurationPersistenceEntity row = new IamUserConfigurationPersistenceEntity();
        row.setId(Long.valueOf(id));
        row.setUsername("tenant-user-" + id);
        row.setDisplayName(displayName);
        row.setEmail("tenant-user-" + id + "@example.com");
        row.setStatus("ACTIVE");
        row.setRoleCodes("business-user");
        return row;
    }
}
