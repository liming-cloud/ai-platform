package com.aiplatform.identity.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class IamMapperXmlTenantIsolationTest {
    @Test
    void userRoleQueryConstrainsRoleTenantAndGrantExpiry() throws IOException {
        String xml = mapperXml("mapper/identity/IamUserMapper.xml");

        assertThat(xml)
                .contains("<select id=\"findActiveTenantUserByLoginName\"")
                .contains("<select id=\"findActiveTenantUserById\"")
                .contains("JOIN iam_tenant t ON t.id = u.tenant_id")
                .contains("AND t.status = 'ACTIVE'")
                .contains("ur.tenant_id = #{tenantId}")
                .contains("AND (ur.expires_at IS NULL OR ur.expires_at &gt; now())")
                .contains("AND r.tenant_id = #{tenantId}")
                .contains("AND r.deleted_at IS NULL");
    }

    @Test
    void authorizationRoleQueryConstrainsRoleTenant() throws IOException {
        String xml = mapperXml("mapper/identity/IamAuthorizationMapper.xml");

        assertThat(xml)
                .contains("JOIN iam_tenant authorization_tenant ON authorization_tenant.id = ur.tenant_id")
                .contains("JOIN iam_user authorization_user ON authorization_user.id = ur.user_id")
                .contains("AND authorization_tenant.status = 'ACTIVE'")
                .contains("AND authorization_user.status = 'ACTIVE'")
                .contains("ur.tenant_id = #{tenantId}")
                .contains("AND r.tenant_id = #{tenantId}")
                .contains("AND r.deleted_at IS NULL");
    }

    @Test
    void iamConfigurationReadQueriesConstrainTenantAndRoleMatrices() throws IOException {
        String xml = mapperXml("mapper/identity/IamAuthorizationMapper.xml");

        assertThat(xml)
                .contains("<select id=\"findIamConfigurationMenus\"")
                .contains("ON tf.tenant_id = #{tenantId}")
                .contains("<select id=\"findIamTenantFeatures\"")
                .contains("WHERE tenant_id = #{tenantId}")
                .contains("<select id=\"findIamRoles\"")
                .contains("WHERE tenant_id = #{tenantId}")
                .contains("<select id=\"findIamRoleMenus\"")
                .contains("WHERE r.tenant_id = #{tenantId}")
                .contains("AND r.id = #{roleId}")
                .contains("AND rm.tenant_id = #{tenantId}")
                .contains("<select id=\"findIamRolePermissions\"")
                .contains("AND rp.tenant_id = #{tenantId}")
                .contains("<select id=\"findIamUsers\"")
                .contains("system_role.role_type = 'system'")
                .contains("system_ur.tenant_id = u.tenant_id");
    }

    @Test
    void iamConfigurationWriteQueriesConstrainTenantAndFailClosedBoundaries() throws IOException {
        String xml = mapperXml("mapper/identity/IamAuthorizationMapper.xml");

        assertThat(xml)
                .contains("<update id=\"updateIamTenantFeature\"")
                .contains("WHERE tenant_id = #{tenantId}")
                .contains("AND feature_code = #{featureCode}")
                .contains("<update id=\"softDeleteIamRoleMenus\"")
                .contains("WHERE tenant_id = #{tenantId}")
                .contains("AND role_id = #{roleId}")
                .contains("AND deleted_at IS NULL")
                .contains("<insert id=\"insertIamRolePermissionByCode\"")
                .contains("WHERE p.permission_code = #{permissionCode}")
                .contains("AND p.status = 'ACTIVE'")
                .contains("<select id=\"countAssignableRolePermissions\"")
                .contains("'app-studio:application:use'")
                .contains("'identity-access:permission-request:create'")
                .contains("<select id=\"findIamPermissions\"")
                .contains("'knowledge-base:retrieval:query'")
                .contains("role_type = 'business'")
                .contains("<select id=\"countRoleAssignedMenuCode\"")
                .contains("rm.tenant_id = #{tenantId}")
                .contains("rm.role_id = #{roleId}");
    }

    @Test
    void delegatedTenantActionsUseTenantScopedIdempotencyAndAssignmentChecks() throws IOException {
        String authorizationXml = mapperXml("mapper/identity/IamAuthorizationMapper.xml");
        String delegationXml = mapperXml("mapper/identity/DelegatedTenantActionMapper.xml");

        assertThat(authorizationXml)
                .contains("<select id=\"countActiveTenantUser\"")
                .contains("WHERE tenant_id = #{tenantId}")
                .contains("AND id = #{userId}")
                .contains("<select id=\"countActiveTenantAdminRole\"")
                .contains("AND role_code = 'tenant-admin'")
                .contains("AND role_type = 'tenant'")
                .contains("<insert id=\"insertIamUserRole\"");
        assertThat(delegationXml)
                .contains("<select id=\"findByRequestHash\"")
                .contains("WHERE tenant_id = #{tenantId}")
                .contains("AND operator_id = #{operatorId}")
                .contains("AND request_hash = #{requestHash}")
                .contains("ON CONFLICT (tenant_id, operator_id, request_hash) DO NOTHING")
                .contains("RETURNING id");
    }

    @Test
    void permissionRequestMapperConformsToMyBatisMapperDtd() throws IOException {
        String resourceName = "mapper/identity/IamPermissionRequestMapper.xml";
        Configuration configuration = new Configuration();

        try (var input = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName)) {
            assertThat(input).as(resourceName).isNotNull();
            new XMLMapperBuilder(input, configuration, resourceName, configuration.getSqlFragments()).parse();
        }
    }

    @Test
    void permissionRequestDecisionMigratesSanitizedTextWithinTenantAndPendingRow() throws IOException {
        String xml = mapperXml("mapper/identity/IamPermissionRequestMapper.xml");

        assertThat(xml)
                .contains("<update id=\"updateDecision\"")
                .contains("SET reason = #{reason}")
                .contains("decision_comment = #{decisionComment}")
                .contains("WHERE tenant_id = #{tenantId}")
                .contains("AND request_id = #{requestId}")
                .contains("AND status = 'PENDING'");
    }

    @Test
    void invitationOutboxDestroysCredentialPayloadAfterTerminalStates() throws IOException {
        String xml = mapperXml("mapper/identity/IamAuthorizationMapper.xml");

        assertThat(xml)
                .contains("<update id=\"markInvitationNotificationDelivered\"")
                .contains("payload_json = '{}'")
                .contains("<update id=\"expireInvitationNotificationPayloads\"")
                .contains("FROM iam_user_invitation i")
                .contains("n.tenant_id = i.tenant_id")
                .contains("n.aggregate_id = i.id")
                .contains("i.expires_at &lt;= now()")
                .contains("<update id=\"clearInvitationNotificationPayloads\"")
                .contains("aggregate_id = #{invitationId}");
    }

    private static String mapperXml(String resourceName) throws IOException {
        try (var input = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName)) {
            assertThat(input).as(resourceName).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
