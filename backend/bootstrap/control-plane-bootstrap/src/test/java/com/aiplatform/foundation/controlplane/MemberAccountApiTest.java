package com.aiplatform.foundation.controlplane;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.session.AuthenticatedSessionGateway;
import com.aiplatform.foundation.security.session.InvalidAuthenticatedSessionException;
import com.redis.testcontainers.RedisContainer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** 正式 HTTP、完整 Flyway、PostgreSQL 和 Redis 验证；故障只注入隔离测试环境。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("demo")
@Testcontainers
class MemberAccountApiTest {
    private static final org.springframework.core.ParameterizedTypeReference<Map<String, Object>> RESPONSE = new org.springframework.core.ParameterizedTypeReference<>() {};
    private static final String PASSWORD = "Demo@2026!";
    private static final String REPLACEMENT = "Member-reset-test-2026!";
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:0.8.5-pg18-trixie")
            .withDatabaseName("member_account_test").withUsername("foundation").withPassword("foundation_test_only");
    @Container static final RedisContainer REDIS = new RedisContainer("redis:8.8.1-trixie");
    @LocalServerPort int port;
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.aiplatform.foundation.organizationaccess.application.member.facade.MemberAccessFacade access;
    @Autowired com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.MemberAccountMapper accountMapper;
    @Autowired com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantMemberSessionRevoker memberRevoker;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @MockitoSpyBean AuthenticatedSessionGateway sessions;
    @MockitoSpyBean com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantLifecycleReadPort tenantRead;

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("spring.data.redis.host", REDIS::getRedisHost);
        properties.add("spring.data.redis.port", REDIS::getRedisPort);
    }

    @Test
    void 开户幂等并发及全局重复均不能遗留重复事实() {
        var manager = login("admin", PASSWORD);
        var input = input(manager, "member-idempotent");
        var a = CompletableFuture.supplyAsync(() -> create(manager, input, "same-command"));
        var b = CompletableFuture.supplyAsync(() -> create(manager, input, "same-command"));
        var first = a.join(); var replay = b.join();
        assertEquals(HttpStatus.CREATED, first.getStatusCode());
        assertEquals(HttpStatus.CREATED, replay.getStatusCode());
        assertEquals(first.getBody().get("membershipId"), replay.getBody().get("membershipId"));
        assertEquals(1, identityCount("member-idempotent"));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM organization_access.member_account_operation WHERE operation_key = 'same-command'", Integer.class));
        var changed = new HashMap<>(input); changed.put("password", REPLACEMENT);
        assertEquals(HttpStatus.CONFLICT, create(manager, changed, "same-command").getStatusCode());
        assertEquals(HttpStatus.CONFLICT, create(manager, input, "other-command").getStatusCode());
        var reserved = input(manager, "platform-admin");
        assertEquals(HttpStatus.CONFLICT, create(manager, reserved, "reserved-global").getStatusCode());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM organization_access.member_account_operation WHERE operation_key = 'reserved-global'", Integer.class));
    }

    @Test
    void 资格角色功能及审计任一落库失败必须回滚整次开户() {
        var manager = login("admin", PASSWORD);
        for (var table : List.of("tenant_membership", "tenant_membership_role_binding", "member_conversation_features", "member_account_operation")) {
            var beforeMembers = count("tenant_membership");
            var beforeRoles = count("tenant_membership_role_binding");
            var beforeFeatures = count("member_conversation_features");
            var beforeAudits = count("member_account_operation");
            jdbc.execute("CREATE FUNCTION organization_access.fail_member_test() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION '合成开户故障'; END $$");
            jdbc.execute("CREATE TRIGGER fail_member_test BEFORE INSERT ON organization_access." + table
                    + " FOR EACH ROW EXECUTE FUNCTION organization_access.fail_member_test()");
            var name = "rollback-" + table.replace('_', '-');
            try {
                assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, create(manager, input(manager, name), "fail-" + table).getStatusCode());
                assertEquals(0, identityCount(name));
                assertEquals(beforeMembers, count("tenant_membership"));
                assertEquals(beforeRoles, count("tenant_membership_role_binding"));
                assertEquals(beforeFeatures, count("member_conversation_features"));
                assertEquals(beforeAudits, count("member_account_operation"));
            } finally {
                jdbc.execute("DROP TRIGGER fail_member_test ON organization_access." + table);
                jdbc.execute("DROP FUNCTION organization_access.fail_member_test()");
            }
        }
    }

    @Test
    void 唯一成员重置撤销旧会话且停用恢复不恢复旧会话() {
        var manager = login("admin", PASSWORD);
        var created = create(manager, input(manager, "member-lifecycle"), "lifecycle-create");
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        var id = (String) created.getBody().get("membershipId");
        var old = login("member-lifecycle", PASSWORD);
        var reset = Map.of("password", REPLACEMENT, "expectedVersion", 0, "expectedIdentityVersion", 0);
        assertEquals(HttpStatus.NO_CONTENT, write(manager, "/" + id + "/password-reset", reset, "lifecycle-reset").getStatusCode());
        assertThrows(InvalidAuthenticatedSessionException.class, () -> sessions.resolve(old));
        assertEquals(HttpStatus.UNAUTHORIZED, loginResponse("member-lifecycle", PASSWORD).getStatusCode());
        var current = login("member-lifecycle", REPLACEMENT);
        assertEquals(HttpStatus.NO_CONTENT, write(manager, "/" + id + "/password-reset", reset, "lifecycle-reset").getStatusCode());
        assertEquals(HttpStatus.CONFLICT, write(manager, "/" + id + "/password-reset", reset, "stale-reset").getStatusCode());
        var disabled = write(manager, "/" + id + "/disable", Map.of("expectedVersion", 0), "unused");
        assertEquals(HttpStatus.OK, disabled.getStatusCode());
        assertEquals("DISABLED", disabled.getBody().get("status"));
        assertThrows(InvalidAuthenticatedSessionException.class, () -> sessions.resolve(current));
        assertEquals(HttpStatus.UNAUTHORIZED, loginResponse("member-lifecycle", REPLACEMENT).getStatusCode());
        assertEquals(HttpStatus.CONFLICT, write(manager, "/" + id + "/enable", Map.of("expectedVersion", 0), "unused").getStatusCode());
        var enabled = write(manager, "/" + id + "/enable", Map.of("expectedVersion", 1), "unused");
        assertEquals(HttpStatus.OK, enabled.getStatusCode());
        assertEquals(created.getBody().get("conversationFeatures"), enabled.getBody().get("conversationFeatures"));
        assertEquals(created.getBody().get("assistantIds"), enabled.getBody().get("assistantIds"));
        assertThrows(InvalidAuthenticatedSessionException.class, () -> sessions.resolve(current));
        assertNotNull(login("member-lifecycle", REPLACEMENT));
        assertEquals(HttpStatus.CONFLICT, create(manager, input(manager, "member-lifecycle"), "lifecycle-create").getStatusCode());
    }

    @Test
    void 已发布助手选择真实落库且关系写入失败回滚全部开户事实() {
        var manager = login("admin", PASSWORD);
        var tenant = sessions.resolve(manager).tenantId().value();
        // 隔离发布事实只验证账户事务和拥有者目录，不证明模型调用或生产助手发布旅程。
        jdbc.update("""
                INSERT INTO knowledge_data.knowledge_space
                  (tenant_id, id, name, status, version, created_by, created_at)
                VALUES (?, 8101, '合成开户知识库', 'DRAFT', 0, 1, CURRENT_TIMESTAMP)
                """, tenant);
        jdbc.update("""
                INSERT INTO knowledge_data.index_version
                  (tenant_id, knowledge_space_id, id, source_snapshot_digest, chunk_policy,
                   embedding_deployment_id, embedding_deployment_revision, embedding_dimension,
                   status, version, build_digest, chunk_count, created_by, created_at, ready_at, activated_at)
                VALUES (?, 8101, 8102, ?, 'fixed-800-120', 8103, 1, 8, 'ACTIVE', 1, ?, 1,
                        1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, tenant, "a".repeat(64), "b".repeat(64));
        jdbc.update("""
                INSERT INTO assistant_delivery.assistant
                  (tenant_id, id, display_name, latest_version_number, version, created_by, created_at)
                VALUES (?, 8104, '合成开户已发布助手', 1, 1, 1, CURRENT_TIMESTAMP)
                """, tenant);
        jdbc.update("""
                INSERT INTO assistant_delivery.assistant_version
                  (tenant_id, id, assistant_id, version_number, chat_deployment_output_limit,
                   knowledge_space_id, index_version_id, index_version_revision,
                   chat_model_configuration_id, chat_model_configuration_revision, chat_catalog_entry_id, chat_catalog_entry_revision,
                   embedding_model_configuration_id, embedding_model_configuration_revision, embedding_catalog_entry_id, embedding_catalog_entry_revision,
                   embedding_dimension, system_instruction, max_output_tokens, retrieval_limit, minimum_score,
                   citation_policy, refusal_policy, status, version, created_by, created_at, published_at)
                VALUES (?, 8105, 8104, 1, 1024, 8101, 8102, 1, 8106, 1, 8107, 1,
                        8108, 1, 8109, 1, 8, '合成事务验证', 256, 5, 0.5, 'REQUIRED',
                        'INSUFFICIENT_EVIDENCE', 'PUBLISHED', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, tenant);
        var input = new HashMap<>(input(manager, "member-selected-assistant"));
        input.put("assistantIds", List.of("8104"));
        var revision = jdbc.queryForObject("SELECT authorization_revision FROM organization_access.tenant_feature_settings WHERE tenant_id = ?", Long.class, tenant);
        var before = List.of(count("tenant_membership"), count("tenant_membership_role_binding"),
                count("member_conversation_features"), count("member_account_operation"), count("member_assistant_authorization"));
        jdbc.execute("CREATE FUNCTION organization_access.fail_grant_test() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION '合成关系故障'; END $$");
        jdbc.execute("CREATE TRIGGER fail_grant_test BEFORE INSERT ON organization_access.member_assistant_authorization FOR EACH ROW EXECUTE FUNCTION organization_access.fail_grant_test()");
        try {
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, create(manager, input, "selected-assistant").getStatusCode());
            assertEquals(0, identityCount("member-selected-assistant"));
            assertEquals(before, List.of(count("tenant_membership"), count("tenant_membership_role_binding"),
                    count("member_conversation_features"), count("member_account_operation"), count("member_assistant_authorization")));
            assertEquals(revision, jdbc.queryForObject("SELECT authorization_revision FROM organization_access.tenant_feature_settings WHERE tenant_id = ?", Long.class, tenant));
        } finally {
            jdbc.execute("DROP TRIGGER fail_grant_test ON organization_access.member_assistant_authorization");
            jdbc.execute("DROP FUNCTION organization_access.fail_grant_test()");
        }
        var created = create(manager, input, "selected-assistant");
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertEquals(List.of("8104"), created.getBody().get("assistantIds"));
        assertEquals(revision + 1, jdbc.queryForObject("SELECT authorization_revision FROM organization_access.tenant_feature_settings WHERE tenant_id = ?", Long.class, tenant));
        access.requireAssistantVersion(sessions.resolve(login("member-selected-assistant", PASSWORD)), 8105, "conversation");
        var id = (String) created.getBody().get("membershipId");
        var configured = readFeatures(manager, id);
        var changed = Map.of("assistantSelection", false, "conversation", true, "ownHistory", false, "authorizedCitations", true);
        assertEquals(HttpStatus.OK, saveFeatures(manager, id, featureInput(configured.getBody(), changed)).getStatusCode());
        assertEquals(List.of("8104"), http.exchange(url("/" + id), HttpMethod.GET,
                new HttpEntity<>(headers(manager, "unused")), RESPONSE).getBody().get("assistantIds"));
        assertEquals(revision + 1, jdbc.queryForObject("SELECT authorization_revision FROM organization_access.tenant_feature_settings WHERE tenant_id = ?", Long.class, tenant));
        var context = sessions.resolve(login("member-selected-assistant", PASSWORD));
        access.requireAssistantVersion(context, 8105, "conversation");
        assertThrows(com.aiplatform.foundation.shared.kernel.error.ApplicationException.class, () -> access.requireFeature(context, "ownHistory"));
    }

    @Test
    void 对话功能独立保存推进版本并仅撤销当前资格会话且无变化不写入() {
        var manager = login("admin", PASSWORD);
        var created = create(manager, input(manager, "feature-save"), "feature-save-create");
        var id = (String) created.getBody().get("membershipId");
        var oldToken = login("feature-save", PASSWORD);
        var staleContext = sessions.resolve(oldToken);
        var before = readFeatures(manager, id);
        assertEquals(HttpStatus.OK, before.getStatusCode());
        assertEquals(0, before.getBody().get("featureVersion"));
        var changed = Map.of("assistantSelection", false, "conversation", true, "ownHistory", true, "authorizedCitations", false);
        var saved = saveFeatures(manager, id, featureInput(before.getBody(), changed));
        assertEquals(HttpStatus.OK, saved.getStatusCode());
        assertEquals(changed, saved.getBody().get("conversationFeatures"));
        assertEquals(1, saved.getBody().get("featureVersion"));
        assertEquals(changed, saved.getBody().get("effectiveFeatures"));
        assertThrows(InvalidAuthenticatedSessionException.class, () -> sessions.resolve(oldToken));
        assertThrows(com.aiplatform.foundation.shared.kernel.error.ApplicationException.class, () -> access.requireFeature(staleContext, "conversation"));
        assertNotNull(sessions.resolve(manager));
        var member = login("feature-save", PASSWORD);
        access.requireFeature(sessions.resolve(member), "ownHistory");
        var auditCount = featureAuditCount(id);
        assertEquals(HttpStatus.OK, saveFeatures(manager, id, featureInput(saved.getBody(), changed)).getStatusCode());
        assertEquals(auditCount, featureAuditCount(id));
        assertEquals(1, readFeatures(manager, id).getBody().get("featureVersion"));
        assertNotNull(sessions.resolve(member));
        assertEquals(HttpStatus.CONFLICT, saveFeatures(manager, id, featureInput(before.getBody(), changed)).getStatusCode());
    }

    @Test
    void 对话功能拒绝未知缺失非布尔范围伪造和管理员写入() {
        var manager = login("admin", PASSWORD);
        var created = create(manager, input(manager, "feature-strict"), "feature-strict-create");
        var id = (String) created.getBody().get("membershipId");
        var facts = readFeatures(manager, id).getBody();
        assertNotNull(facts);
        var good = featureInput(facts, Map.of("assistantSelection", false, "conversation", false, "ownHistory", false, "authorizedCitations", false));
        for (var field : List.of("tenantId", "subjectId", "assistantIds", "expectedAuthorizationRevision")) {
            var invalid = new HashMap<>(good); invalid.put(field, "forged");
            assertEquals(HttpStatus.BAD_REQUEST, saveFeatures(manager, id, invalid).getStatusCode());
            assertEquals(HttpStatus.BAD_REQUEST, http.exchange(url("/" + id + "/features?" + field + "=forged"), HttpMethod.GET,
                    new HttpEntity<>(headers(manager, "unused")), RESPONSE).getStatusCode());
        }
        for (var field : good.keySet()) {
            var invalid = new HashMap<>(good); invalid.remove(field);
            assertEquals(HttpStatus.BAD_REQUEST, saveFeatures(manager, id, invalid).getStatusCode());
        }
        for (var invalid : List.of(Map.of("conversation", true), Map.of("assistantSelection", false, "conversation", "true", "ownHistory", false, "authorizedCitations", false))) {
            var body = new HashMap<>(good); body.put("conversationFeatures", invalid);
            assertEquals(HttpStatus.BAD_REQUEST, saveFeatures(manager, id, body).getStatusCode());
        }
        var floating = new HashMap<>(good); floating.put("expectedFeatureVersion", 0.0);
        assertEquals(HttpStatus.BAD_REQUEST, saveFeatures(manager, id, floating).getStatusCode());
        var member = login("feature-strict", PASSWORD);
        assertEquals(HttpStatus.FORBIDDEN, readFeatures(member, id).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, saveFeatures(member, id, good).getStatusCode());
        var adminId = Long.toString(sessions.resolve(manager).membershipId().value());
        assertEquals(true, readFeatures(manager, adminId).getBody().get("readOnly"));
        assertEquals(HttpStatus.FORBIDDEN, saveFeatures(manager, adminId, good).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, readFeatures(manager, "3382738893456539999").getStatusCode());
        assertEquals(0, featureAuditCount(id));
    }

    @Test
    void 停用成员允许保存配置但不得恢复资格或登录() {
        var manager = login("admin", PASSWORD);
        var created = create(manager, input(manager, "feature-disabled"), "feature-disabled-create");
        var id = (String) created.getBody().get("membershipId");
        assertEquals(HttpStatus.OK, write(manager, "/" + id + "/disable", Map.of("expectedVersion", 0), "unused").getStatusCode());
        var before = readFeatures(manager, id).getBody();
        var values = Map.of("assistantSelection", false, "conversation", true, "ownHistory", true, "authorizedCitations", false);
        var saved = saveFeatures(manager, id, featureInput(before, values));
        assertEquals(HttpStatus.OK, saved.getStatusCode());
        assertEquals("DISABLED", saved.getBody().get("status"));
        assertEquals(values, saved.getBody().get("conversationFeatures"));
        assertEquals(Map.of("assistantSelection", false, "conversation", false, "ownHistory", false, "authorizedCitations", false), saved.getBody().get("effectiveFeatures"));
        assertEquals(HttpStatus.UNAUTHORIZED, loginResponse("feature-disabled", PASSWORD).getStatusCode());
    }

    @Test
    void 租户上限关闭保留已有值但禁止新增授权并拒绝过时上限版本() {
        var manager = login("admin", PASSWORD);
        var created = create(manager, input(manager, "feature-ceiling"), "feature-ceiling-create");
        var id = (String) created.getBody().get("membershipId");
        var before = readFeatures(manager, id).getBody();
        assertNotNull(before);
        var tenant = sessions.resolve(manager).tenantId().value();
        var ceiling = jdbc.queryForMap("SELECT conversation, own_history, version FROM organization_access.tenant_feature_settings WHERE tenant_id = ?", tenant);
        try {
            jdbc.update("UPDATE organization_access.tenant_feature_settings SET conversation = false, own_history = false, version = version + 1 WHERE tenant_id = ?", tenant);
            var retained = Map.of("assistantSelection", false, "conversation", true, "ownHistory", false, "authorizedCitations", false);
            assertEquals(HttpStatus.CONFLICT, saveFeatures(manager, id, featureInput(before, retained)).getStatusCode());
            var current = readFeatures(manager, id).getBody();
            var saved = saveFeatures(manager, id, featureInput(current, retained));
            assertEquals(HttpStatus.OK, saved.getStatusCode());
            assertEquals(true, ((Map<?, ?>) saved.getBody().get("conversationFeatures")).get("conversation"));
            assertEquals(false, ((Map<?, ?>) saved.getBody().get("effectiveFeatures")).get("conversation"));
            var escalated = Map.of("assistantSelection", false, "conversation", true, "ownHistory", true, "authorizedCitations", false);
            assertEquals(HttpStatus.BAD_REQUEST, saveFeatures(manager, id, featureInput(saved.getBody(), escalated)).getStatusCode());
            assertEquals(1, featureAuditCount(id));
        } finally {
            jdbc.update("UPDATE organization_access.tenant_feature_settings SET conversation = ?, own_history = ?, version = ? WHERE tenant_id = ?",
                    ceiling.get("conversation"), ceiling.get("own_history"), ceiling.get("version"), tenant);
        }
    }

    @Test
    void 并发保存只允许一个版本获胜且撤销故障回滚配置与审计() {
        var manager = login("admin", PASSWORD);
        var created = create(manager, input(manager, "feature-concurrent"), "feature-concurrent-create");
        var id = (String) created.getBody().get("membershipId");
        var values = Map.of("assistantSelection", false, "conversation", true, "ownHistory", true, "authorizedCitations", false);
        var command = featureInput(readFeatures(manager, id).getBody(), values);
        var a = CompletableFuture.supplyAsync(() -> saveFeatures(manager, id, command));
        var b = CompletableFuture.supplyAsync(() -> saveFeatures(manager, id, command));
        assertEquals(java.util.Set.of(HttpStatus.OK, HttpStatus.CONFLICT), java.util.Set.of(a.join().getStatusCode(), b.join().getStatusCode()));
        assertEquals(1, featureAuditCount(id));
        var old = login("feature-concurrent", PASSWORD);
        var before = readFeatures(manager, id).getBody();
        doThrow(new IllegalStateException("合成撤销故障")).when(sessions).revokeMembershipSessions(new MembershipId(Long.parseLong(id)));
        try {
            var changed = Map.of("assistantSelection", false, "conversation", false, "ownHistory", true, "authorizedCitations", false);
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, saveFeatures(manager, id, featureInput(before, changed)).getStatusCode());
            assertEquals(before, readFeatures(manager, id).getBody());
            assertEquals(1, featureAuditCount(id));
            assertNotNull(sessions.resolve(old));
        } finally { reset(sessions); }
    }

    private ResponseEntity<Map<String, Object>> readFeatures(String token, String id) {
        return http.exchange(url("/" + id + "/features"), HttpMethod.GET, new HttpEntity<>(headers(token, "unused")), RESPONSE);
    }

    @Test
    void 功能审计失败回滚保存值资格修订且不撤销会话() {
        var manager = login("admin", PASSWORD);
        var created = create(manager, input(manager, "feature-audit-failure"), "feature-audit-failure-create");
        var id = (String) created.getBody().get("membershipId");
        var old = login("feature-audit-failure", PASSWORD);
        var before = readFeatures(manager, id).getBody();
        var account = http.exchange(url("/" + id), HttpMethod.GET, new HttpEntity<>(headers(manager, "unused")), RESPONSE).getBody();
        jdbc.execute("CREATE FUNCTION organization_access.fail_feature_audit_test() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION '合成功能审计故障'; END $$");
        jdbc.execute("CREATE TRIGGER fail_feature_audit_test BEFORE INSERT ON organization_access.member_account_operation FOR EACH ROW EXECUTE FUNCTION organization_access.fail_feature_audit_test()");
        try {
            var values = Map.of("assistantSelection", false, "conversation", true, "ownHistory", true, "authorizedCitations", false);
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, saveFeatures(manager, id, featureInput(before, values)).getStatusCode());
            assertEquals(before, readFeatures(manager, id).getBody());
            assertEquals(account, http.exchange(url("/" + id), HttpMethod.GET, new HttpEntity<>(headers(manager, "unused")), RESPONSE).getBody());
            assertEquals(0, featureAuditCount(id));
            assertNotNull(sessions.resolve(old));
        } finally {
            jdbc.execute("DROP TRIGGER fail_feature_audit_test ON organization_access.member_account_operation");
            jdbc.execute("DROP FUNCTION organization_access.fail_feature_audit_test()");
        }
    }
    private ResponseEntity<Map<String, Object>> saveFeatures(String token, String id, Map<String, ?> input) {
        return http.exchange(url("/" + id + "/features"), HttpMethod.PUT, new HttpEntity<>(input, headers(token, "unused")), RESPONSE);
    }
    private Map<String, Object> featureInput(Map<String, Object> facts, Map<String, Boolean> values) {
        return Map.of("conversationFeatures", values, "expectedFeatureVersion", facts.get("featureVersion"),
                "expectedTenantFeatureVersion", facts.get("tenantFeatureVersion"));
    }
    private int featureAuditCount(String id) {
        return jdbc.queryForObject("SELECT count(*) FROM organization_access.member_account_operation WHERE membership_id = ? AND operation = 'FEATURES'", Integer.class, Long.parseLong(id));
    }

    @Test
    void 停用跨租户资格仍阻止重置且管理员及错租户目标只读() {
        var manager = login("admin", PASSWORD);
        var created = create(manager, input(manager, "member-shared"), "shared-create");
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        var id = (String) created.getBody().get("membershipId");
        var subject = jdbc.queryForObject("SELECT subject_id FROM organization_access.tenant_membership WHERE membership_id = ?", Long.class, Long.parseLong(id));
        jdbc.update("""
                INSERT INTO organization_access.tenant_membership (membership_id, tenant_id, subject_id, membership_role, status, permission_version, version)
                VALUES (3382738893456539998, 100000000000000002, ?, 'TENANT_USER', 'DISABLED', 0, 0)
                """, subject);
        var reset = Map.of("password", REPLACEMENT, "expectedVersion", 0, "expectedIdentityVersion", 0);
        assertEquals(HttpStatus.FORBIDDEN, write(manager, "/" + id + "/password-reset", reset, "shared-reset").getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, write(manager, "/3382738893456539998/disable", Map.of("expectedVersion", 0), "unused").getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, write(manager, "/3382738893456539650/disable", Map.of("expectedVersion", 0), "unused").getStatusCode());
        assertNotNull(login("member-shared", PASSWORD));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM organization_access.member_account_operation WHERE operation_key = 'shared-reset'", Integer.class));
    }

    @Test
    void 会话撤销失败不提交密码状态或成功审计() {
        var manager = login("admin", PASSWORD);
        var created = create(manager, input(manager, "member-revoke-failure"), "revoke-create");
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        var id = Long.parseLong((String) created.getBody().get("membershipId"));
        var subject = jdbc.queryForObject("SELECT subject_id FROM organization_access.tenant_membership WHERE membership_id = ?", Long.class, id);
        var old = login("member-revoke-failure", PASSWORD);
        try {
            doThrow(new IllegalStateException("合成撤销故障")).when(sessions).revokeMembershipSessions(new MembershipId(id));
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, write(manager, "/" + id + "/disable", Map.of("expectedVersion", 0), "unused").getStatusCode());
            assertEquals("ENABLED", jdbc.queryForObject("SELECT status FROM organization_access.tenant_membership WHERE membership_id = ?", String.class, id));
            doThrow(new IllegalStateException("合成撤销故障")).when(sessions).revokeSubjectSessions(new SubjectId(subject));
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, write(manager, "/" + id + "/password-reset",
                    Map.of("password", REPLACEMENT, "expectedVersion", 0, "expectedIdentityVersion", 0), "revoke-reset").getStatusCode());
            assertEquals(0L, jdbc.queryForObject("SELECT version FROM organization_access.subject_identity WHERE subject_id = ?", Long.class, subject));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM organization_access.member_account_operation WHERE membership_id = ? AND operation <> 'CREATE'", Integer.class, id));
            assertEquals(id, sessions.resolve(old).membershipId().value());
        } finally { reset(sessions); }
        assertNotNull(login("member-revoke-failure", PASSWORD));
        assertEquals(HttpStatus.UNAUTHORIZED, loginResponse("member-revoke-failure", REPLACEMENT).getStatusCode());
    }

    @Test
    void 迁移撤销失败保留标记且重试只处理待撤销资格() {
        var manager = login("admin", PASSWORD);
        var created = create(manager, input(manager, "member-cutover"), "cutover-create");
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        var id = Long.parseLong((String) created.getBody().get("membershipId"));
        var token = login("member-cutover", PASSWORD);
        var context = sessions.resolve(token);
        jdbc.update("UPDATE organization_access.tenant_membership SET member_access_pending_revocation = TRUE, permission_version = permission_version + 1 WHERE membership_id = ?", id);
        assertThrows(com.aiplatform.foundation.shared.kernel.error.ApplicationException.class, () -> access.read(context));
        var cutover = new com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.repository.MemberAccessCutover(
                accountMapper, memberRevoker, new org.springframework.transaction.support.TransactionTemplate(transactions));
        try {
            doThrow(new IllegalStateException("合成迁移撤销故障")).when(sessions).revokeMembershipSessions(new MembershipId(id));
            assertThrows(IllegalStateException.class, cutover::run);
            assertEquals(true, jdbc.queryForObject("SELECT member_access_pending_revocation FROM organization_access.tenant_membership WHERE membership_id = ?", Boolean.class, id));
        } finally { reset(sessions); }
        cutover.run();
        assertEquals(false, jdbc.queryForObject("SELECT member_access_pending_revocation FROM organization_access.tenant_membership WHERE membership_id = ?", Boolean.class, id));
        assertThrows(InvalidAuthenticatedSessionException.class, () -> sessions.resolve(token));
        assertDoesNotThrow(cutover::run);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM organization_access.member_assistant_authorization WHERE membership_id = ?", Integer.class, id));
    }

    @Test
    void 固定角色缺失或异常管理员绑定时不得沿用旧会话访问或重置() {
        var manager = login("admin", PASSWORD);
        var created = create(manager, input(manager, "member-role-facts"), "role-facts-create");
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        var id = Long.parseLong((String) created.getBody().get("membershipId"));
        var context = sessions.resolve(login("member-role-facts", PASSWORD));
        assertTrue(access.read(context).allows("conversation"));
        jdbc.update("DELETE FROM organization_access.tenant_membership_role_binding WHERE membership_id = ?", id);
        try {
            assertThrows(com.aiplatform.foundation.shared.kernel.error.ApplicationException.class, () -> access.read(context));
            jdbc.update("INSERT INTO organization_access.tenant_membership_role_binding (tenant_id, membership_id, role_code, bound_at) VALUES (100000000000000001, ?, 'TENANT_ADMINISTRATOR', CURRENT_TIMESTAMP)", id);
            assertThrows(com.aiplatform.foundation.shared.kernel.error.ApplicationException.class, () -> access.read(context));
            assertEquals(HttpStatus.FORBIDDEN, write(manager, "/" + id + "/password-reset",
                    Map.of("password", REPLACEMENT, "expectedVersion", 0, "expectedIdentityVersion", 0), "role-facts-reset").getStatusCode());
            assertEquals(HttpStatus.FORBIDDEN, write(manager, "/" + id + "/disable", Map.of("expectedVersion", 0), "unused").getStatusCode());
        } finally {
            jdbc.update("DELETE FROM organization_access.tenant_membership_role_binding WHERE membership_id = ?", id);
            jdbc.update("INSERT INTO organization_access.tenant_membership_role_binding (tenant_id, membership_id, role_code, bound_at) VALUES (100000000000000001, ?, 'TENANT_USER', CURRENT_TIMESTAMP)", id);
        }
    }

    @Test
    void 未知字段旧候选合同缺失版本和伪造助手均在写入前拒绝() {
        var manager = login("admin", PASSWORD);
        var base = input(manager, "member-rejected");
        for (var field : List.of("tenantId", "subjectId", "membershipRole")) {
            var request = new HashMap<>(base); request.put(field, "1");
            assertEquals(HttpStatus.BAD_REQUEST, create(manager, request, "unknown-" + field).getStatusCode());
        }
        var missing = new HashMap<>(base); missing.remove("expectedTenantFeatureVersion");
        assertEquals(HttpStatus.BAD_REQUEST, create(manager, missing, "missing-version").getStatusCode());
        var stale = new HashMap<>(base); stale.put("expectedAuthorizationRevision", 999);
        assertEquals(HttpStatus.CONFLICT, create(manager, stale, "stale-revision").getStatusCode());
        var forged = new HashMap<>(base); forged.put("assistantIds", List.of("410000000000009999"));
        assertEquals(HttpStatus.FORBIDDEN, create(manager, forged, "forged-assistant").getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, create(manager, Map.of("subjectId", "4921825928971026438"), "old-subject").getStatusCode());
        assertEquals(0, identityCount("member-rejected"));
    }

    @Test
    void 工作区上下文只返回令牌所属租户的真实名称并拒绝外部范围参数() {
        var token = login("admin", PASSWORD);
        var tenantId = sessions.resolve(token).tenantId().value();
        var endpoint = "http://localhost:" + port + "/api/v1/tenant/workspace-context";
        var response = http.exchange(endpoint, HttpMethod.GET, new HttpEntity<>(headers(token, "unused")), RESPONSE);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        var name = jdbc.queryForObject("SELECT display_name FROM organization_access.tenant_lifecycle WHERE tenant_id = ?", String.class, tenantId);
        assertEquals(Map.of("displayName", name), response.getBody());
        for (var field : List.of("tenantId", "subjectId", "sessionId", "displayName")) {
            assertEquals(HttpStatus.BAD_REQUEST, http.exchange(endpoint + "?" + field + "=forged",
                    HttpMethod.GET, new HttpEntity<>(headers(token, "unused")), RESPONSE).getStatusCode());
        }
    }

    @Test
    void 普通成员及平台会话不得读取租户工作区名称() {
        var manager = login("admin", PASSWORD);
        assertEquals(HttpStatus.CREATED, create(manager, input(manager, "workspace-context-member"), "workspace-context-member").getStatusCode());
        var member = login("workspace-context-member", PASSWORD);
        var platform = http.exchange("http://localhost:" + port + "/api/v1/platform/auth/sessions", HttpMethod.POST,
                new HttpEntity<>(Map.of("loginName", "platform-admin", "password", PASSWORD)), RESPONSE);
        assertEquals(HttpStatus.CREATED, platform.getStatusCode());
        for (var token : List.of(member, (String) platform.getBody().get("tokenValue"))) {
            var response = http.exchange("http://localhost:" + port + "/api/v1/tenant/workspace-context",
                    HttpMethod.GET, new HttpEntity<>(headers(token, "unused")), RESPONSE);
            assertTrue(List.of(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN).contains(response.getStatusCode()));
            assertFalse(response.getBody().containsKey("displayName"));
        }
    }

    @Test
    void 工作区租户事实缺失时失败关闭而不使用演示名称() {
        var token = login("admin", PASSWORD);
        var tenantId = sessions.resolve(token).tenantId().value();
        org.mockito.Mockito.doReturn(java.util.Optional.empty()).when(tenantRead).find(tenantId);
        try {
            var response = http.exchange("http://localhost:" + port + "/api/v1/tenant/workspace-context",
                    HttpMethod.GET, new HttpEntity<>(headers(token, "unused")), RESPONSE);
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertFalse(response.getBody().containsKey("displayName"));
        } finally { reset(tenantRead); }
    }

    @Test
    void 助手独立页只读与空集合保存不推进其他设置且严格拒绝过时和越界请求() {
        var manager = login("admin", PASSWORD);
        var created = create(manager, input(manager, "member-assistant-boundary"), "assistant-boundary-create");
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        var id = (String) created.getBody().get("membershipId");
        var endpoint = url("/" + id + "/assistants");
        var read = http.exchange(endpoint, HttpMethod.GET, new HttpEntity<>(headers(manager, "unused")), RESPONSE);
        assertEquals(HttpStatus.OK, read.getStatusCode());
        assertEquals(List.of(), read.getBody().get("assistantIds"));
        var revision = read.getBody().get("authorizationRevision");
        var command = Map.of("assistantIds", List.of(), "expectedAuthorizationRevision", revision);
        var saved = http.exchange(endpoint, HttpMethod.PUT, new HttpEntity<>(command, headers(manager, "unused")), RESPONSE);
        assertEquals(HttpStatus.OK, saved.getStatusCode());
        assertEquals(revision, saved.getBody().get("authorizationRevision"));
        assertEquals(created.getBody(), http.exchange(url("/" + id), HttpMethod.GET,
                new HttpEntity<>(headers(manager, "unused")), RESPONSE).getBody());
        assertEquals(HttpStatus.CONFLICT, http.exchange(endpoint, HttpMethod.PUT,
                new HttpEntity<>(Map.of("assistantIds", List.of(), "expectedAuthorizationRevision", 999999), headers(manager, "unused")), RESPONSE).getStatusCode());
        for (var field : List.of("tenantId", "subjectId", "conversationFeatures")) {
            var forged = new HashMap<String, Object>(command); forged.put(field, "forged");
            assertEquals(HttpStatus.BAD_REQUEST, http.exchange(endpoint, HttpMethod.PUT,
                    new HttpEntity<>(forged, headers(manager, "unused")), RESPONSE).getStatusCode());
        }
        assertEquals(HttpStatus.FORBIDDEN, http.exchange(endpoint, HttpMethod.PUT,
                new HttpEntity<>(Map.of("assistantIds", List.of("999999"), "expectedAuthorizationRevision", revision), headers(manager, "unused")), RESPONSE).getStatusCode());
        var member = login("member-assistant-boundary", PASSWORD);
        assertEquals(HttpStatus.FORBIDDEN, http.exchange(endpoint, HttpMethod.GET, new HttpEntity<>(headers(member, "unused")), RESPONSE).getStatusCode());
    }

    @Test
    void 策略目录是当前租户的真实分页且新草稿不能隐式授予助手() {
        var manager = login("admin", PASSWORD);
        var endpoint = "http://localhost:" + port + "/api/v1/tenant/access-policies";
        var response = http.exchange(endpoint + "?pageNumber=1&pageSize=20", HttpMethod.GET,
                new HttpEntity<>(headers(manager, "unused")), RESPONSE);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of(), response.getBody().get("items"));
        assertEquals(0, ((Number) response.getBody().get("totalElements")).intValue());
        assertEquals(HttpStatus.BAD_REQUEST, http.exchange(endpoint + "?tenantId=forged", HttpMethod.GET,
                new HttpEntity<>(headers(manager, "unused")), RESPONSE).getStatusCode());
    }

    private Map<String, Object> input(String token, String login) {
        var options = http.exchange(url("" + "/create-options"), HttpMethod.GET, new HttpEntity<>(headers(token, "unused")), Map.class);
        assertEquals(HttpStatus.OK, options.getStatusCode());
        return Map.of("loginName", login, "password", PASSWORD, "conversationFeatures", Map.of(
                "assistantSelection", true, "conversation", true, "ownHistory", false, "authorizedCitations", false),
                "assistantIds", List.of(), "expectedTenantFeatureVersion", options.getBody().get("tenantFeatureVersion"),
                "expectedAuthorizationRevision", options.getBody().get("authorizationRevision"));
    }
    private ResponseEntity<Map<String, Object>> create(String token, Map<String, ?> input, String key) { return write(token, "", input, key); }
    private ResponseEntity<Map<String, Object>> write(String token, String suffix, Map<String, ?> input, String key) {
        return http.exchange(url(suffix), HttpMethod.POST, new HttpEntity<>(input, headers(token, key)), RESPONSE);
    }
    private HttpHeaders headers(String token, String key) { var headers = new HttpHeaders(); headers.setBearerAuth(token); headers.set("Idempotency-Key", key); return headers; }
    private String url(String suffix) { return "http://localhost:" + port + "/api/v1/tenant/members" + suffix; }
    private ResponseEntity<Map<String, Object>> loginResponse(String name, String password) {
        return http.exchange("http://localhost:" + port + "/api/v1/auth/sessions", HttpMethod.POST,
                new HttpEntity<>(Map.of("tenantCode", "demo-store", "loginName", name, "password", password)), RESPONSE);
    }
    private String login(String name, String password) { var response = loginResponse(name, password); assertEquals(HttpStatus.CREATED, response.getStatusCode()); return (String) response.getBody().get("tokenValue"); }
    private int identityCount(String name) { return jdbc.queryForObject("SELECT count(*) FROM organization_access.subject_identity WHERE login_name = ?", Integer.class, name); }
    private int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM organization_access." + table, Integer.class); }
}
