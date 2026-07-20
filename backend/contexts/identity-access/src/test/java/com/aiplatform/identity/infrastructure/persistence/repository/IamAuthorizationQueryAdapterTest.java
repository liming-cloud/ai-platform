package com.aiplatform.identity.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiplatform.identity.infrastructure.persistence.converter.IamResourceAclPersistenceConverter;
import com.aiplatform.identity.infrastructure.persistence.entity.IamResourceAclPersistenceEntity;
import com.aiplatform.identity.infrastructure.persistence.mapper.IamAuthorizationMapper;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.model.PermissionCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IamAuthorizationQueryAdapterTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-25T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void queriesRolesAndPermissionsFromServerCatalog() {
        IamAuthorizationMapper mapper = mock(IamAuthorizationMapper.class);
        IamAuthorizationQueryAdapter adapter = adapter(mapper);
        when(mapper.findActiveRoleCodes(2001L, 1001L)).thenReturn(Set.of("platform-admin"));
        when(mapper.findActivePermissionCodes(2001L, 1001L))
                .thenReturn(Set.of("knowledge-base:document:upload"));

        assertThat(adapter.findRoleCodes(TenantId.of("2001"), "user", "1001"))
                .containsExactly("platform-admin");
        assertThat(adapter.findPermissionCodes(TenantId.of("2001"), "user", "1001"))
                .containsExactly(new PermissionCode("knowledge-base:document:upload"));
    }

    @Test
    void normalizesRoleAndPermissionCodesReturnedByMapper() {
        IamAuthorizationMapper mapper = mock(IamAuthorizationMapper.class);
        IamAuthorizationQueryAdapter adapter = adapter(mapper);
        when(mapper.findActiveRoleCodes(2001L, 1001L))
                .thenReturn(Set.of(" platform-admin ", " auditor "));
        when(mapper.findActivePermissionCodes(2001L, 1001L))
                .thenReturn(Set.of(" knowledge-base:document:upload "));

        assertThat(adapter.findRoleCodes(TenantId.of("2001"), "user", "1001"))
                .containsExactlyInAnyOrder("platform-admin", "auditor");
        assertThat(adapter.findPermissionCodes(TenantId.of("2001"), "user", "1001"))
                .containsExactly(new PermissionCode("knowledge-base:document:upload"));
    }

    @Test
    void allowsWhenPermissionSnapshotAndResourceAclMatch() {
        IamAuthorizationMapper mapper = mock(IamAuthorizationMapper.class);
        IamAuthorizationQueryAdapter adapter = adapter(mapper);
        PermissionCode permissionCode = new PermissionCode("knowledge-base:document:upload");
        when(mapper.findActivePermissionCodes(2001L, 1001L)).thenReturn(Set.of(permissionCode.value()));
        when(mapper.findActiveResourceAcls(
                2001L,
                1001L,
                "knowledge_base",
                7L,
                permissionCode.value()
        )).thenReturn(List.of(acl("allow")));

        boolean allowed = adapter.canAccess(
                TenantId.of("2001"),
                "1001",
                permissionCode,
                "knowledge_base",
                "7"
        );

        assertThat(allowed).isTrue();
    }

    @Test
    void failsClosedWhenAclIsMissingOrDenied() {
        IamAuthorizationMapper mapper = mock(IamAuthorizationMapper.class);
        IamAuthorizationQueryAdapter adapter = adapter(mapper);
        PermissionCode permissionCode = new PermissionCode("knowledge-base:document:upload");
        when(mapper.findActivePermissionCodes(2001L, 1001L)).thenReturn(Set.of(permissionCode.value()));
        when(mapper.findActiveResourceAcls(
                2001L,
                1001L,
                "knowledge_base",
                7L,
                permissionCode.value()
        )).thenReturn(List.of(acl("deny")));

        boolean allowed = adapter.canAccess(
                TenantId.of("2001"),
                "1001",
                permissionCode,
                "knowledge_base",
                "7"
        );

        assertThat(allowed).isFalse();
    }

    @Test
    void expandsAllowedResourceRolesToUserAudience() {
        IamAuthorizationMapper mapper = mock(IamAuthorizationMapper.class);
        IamAuthorizationQueryAdapter adapter = adapter(mapper);
        PermissionCode permission = new PermissionCode("app-studio:application:use");
        when(mapper.findAllowedResourceUserIds(
                2001L, "AI_APPLICATION", 14L, permission.value()
        )).thenReturn(List.of(1003L, 1004L));

        assertThat(adapter.findAllowedUserIds(
                TenantId.of("2001"), "AI_APPLICATION", "14", permission
        )).containsExactlyInAnyOrder("1003", "1004");
    }

    @Test
    void rejectsNullRoleQueryResultInsteadOfEmptyPermissionFallback() {
        IamAuthorizationMapper mapper = mock(IamAuthorizationMapper.class);
        IamAuthorizationQueryAdapter adapter = adapter(mapper);
        when(mapper.findActiveRoleCodes(2001L, 1001L)).thenReturn(null);

        assertThatThrownBy(() -> adapter.findRoleCodes(TenantId.of("2001"), "user", "1001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("角色编码查询结果不能为空");
    }

    @Test
    void rejectsBlankRoleCodeReturnedByMapperInsteadOfBadSaTokenRole() {
        IamAuthorizationMapper mapper = mock(IamAuthorizationMapper.class);
        IamAuthorizationQueryAdapter adapter = adapter(mapper);
        when(mapper.findActiveRoleCodes(2001L, 1001L)).thenReturn(Set.of(" "));

        assertThatThrownBy(() -> adapter.findRoleCodes(TenantId.of("2001"), "user", "1001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("角色编码不能为空");
    }

    @Test
    void rejectsNullRoleCodeReturnedByMapperInsteadOfBadSaTokenRole() {
        IamAuthorizationMapper mapper = mock(IamAuthorizationMapper.class);
        IamAuthorizationQueryAdapter adapter = adapter(mapper);
        Set<String> roleCodes = new LinkedHashSet<>();
        roleCodes.add(null);
        when(mapper.findActiveRoleCodes(2001L, 1001L)).thenReturn(roleCodes);

        assertThatThrownBy(() -> adapter.findRoleCodes(TenantId.of("2001"), "user", "1001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("角色编码不能为空");
    }

    @Test
    void rejectsNullPermissionQueryResultInsteadOfEmptyPermissionFallback() {
        IamAuthorizationMapper mapper = mock(IamAuthorizationMapper.class);
        IamAuthorizationQueryAdapter adapter = adapter(mapper);
        when(mapper.findActivePermissionCodes(2001L, 1001L)).thenReturn(null);

        assertThatThrownBy(() -> adapter.findPermissionCodes(TenantId.of("2001"), "user", "1001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("权限编码查询结果不能为空");
    }

    @Test
    void rejectsNullAclQueryResultInsteadOfDenyingSilently() {
        IamAuthorizationMapper mapper = mock(IamAuthorizationMapper.class);
        IamAuthorizationQueryAdapter adapter = adapter(mapper);
        PermissionCode permissionCode = new PermissionCode("knowledge-base:document:upload");
        when(mapper.findActivePermissionCodes(2001L, 1001L)).thenReturn(Set.of(permissionCode.value()));
        when(mapper.findActiveResourceAcls(
                2001L,
                1001L,
                "knowledge_base",
                7L,
                permissionCode.value()
        )).thenReturn(null);

        assertThatThrownBy(() -> adapter.canAccess(
                TenantId.of("2001"),
                "1001",
                permissionCode,
                "knowledge_base",
                "7"
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("资源 ACL 查询结果不能为空");
    }

    private static IamAuthorizationQueryAdapter adapter(IamAuthorizationMapper mapper) {
        return new IamAuthorizationQueryAdapter(
                mapper,
                new IamResourceAclPersistenceConverter(),
                CLOCK
        );
    }

    private static IamResourceAclPersistenceEntity acl(String effect) {
        IamResourceAclPersistenceEntity row = new IamResourceAclPersistenceEntity();
        row.setId(91L);
        row.setTenantId(2001L);
        row.setResourceType("knowledge_base");
        row.setResourceId(7L);
        row.setSubjectType("user");
        row.setSubjectId(1001L);
        row.setPermissionCode("knowledge-base:document:upload");
        row.setEffect(effect);
        return row;
    }
}
