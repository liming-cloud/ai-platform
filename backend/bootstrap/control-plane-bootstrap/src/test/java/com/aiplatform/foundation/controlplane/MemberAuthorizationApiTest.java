package com.aiplatform.foundation.controlplane;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.session.AuthenticatedSessionGateway;
import com.aiplatform.foundation.security.session.InvalidAuthenticatedSessionException;
import com.redis.testcontainers.RedisContainer;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
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

/** 真实 HTTP、Flyway、PostgreSQL 和 Redis 的统一授权反例；发布种子仅属于隔离测试环境。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("demo")
@Testcontainers
class MemberAuthorizationApiTest {
    private static final org.springframework.core.ParameterizedTypeReference<Map<String, Object>> RESPONSE = new org.springframework.core.ParameterizedTypeReference<>() {};
    private static final String PASSWORD = "Demo@2026!";
    private static final AtomicLong IDS = new AtomicLong(9100000);
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:0.8.5-pg18-trixie")
            .withDatabaseName("member_authorization_test").withUsername("foundation").withPassword("foundation_test_only");
    @Container static final RedisContainer REDIS = new RedisContainer("redis:8.8.1-trixie");
    @LocalServerPort int port;
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired com.aiplatform.foundation.assistantdelivery.application.assistant.facade.PublishedAssistantAuthorizationLookup assistantLocks;
    @Autowired com.aiplatform.foundation.organizationaccess.application.member.facade.MemberAccessFacade access;
    @MockitoSpyBean AuthenticatedSessionGateway sessions;
    @MockitoSpyBean com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.AccessPolicyAuditMapper policyAudits;
    @DynamicPropertySource static void infrastructure(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl); properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword); properties.add("spring.data.redis.host", REDIS::getRedisHost);
        properties.add("spring.data.redis.port", REDIS::getRedisPort);
    }

    @Test
    void 助手发布版本租户上限及资格变更都使预览失效() {
        var manager = login("admin"); var assistant = seed(manager); var member = member(manager); var id = (String) member.get("membershipId");
        var draft = command(manager, createPolicy(manager, assistant, List.of(id)), "preview").getBody();
        jdbc.update("""
                INSERT INTO assistant_delivery.assistant_version
                SELECT (jsonb_populate_record(NULL::assistant_delivery.assistant_version,
                    to_jsonb(v) || jsonb_build_object('id', ?::bigint, 'version_number', 2))).*
                FROM assistant_delivery.assistant_version v WHERE v.id = ?
                """, IDS.addAndGet(20), assistant + 1);
        jdbc.update("UPDATE assistant_delivery.assistant SET latest_version_number = 2, version = version + 1 WHERE id = ?", assistant);
        var stale = exchange(manager, policyPath(draft), HttpMethod.GET, null).getBody();
        assertEquals(false, ((Map<?, ?>) stale.get("preview")).get("passed"));
        assertEquals(HttpStatus.CONFLICT, command(manager, stale, "publish").getStatusCode());
        var fresh = command(manager, stale, "preview").getBody();
        jdbc.update("UPDATE organization_access.tenant_feature_settings SET version = version + 1 WHERE tenant_id = ?", sessions.resolve(manager).tenantId().value());
        stale = exchange(manager, policyPath(draft), HttpMethod.GET, null).getBody();
        assertEquals(false, ((Map<?, ?>) stale.get("preview")).get("passed"));
        assertEquals(HttpStatus.CONFLICT, command(manager, stale, "publish").getStatusCode());
        fresh = command(manager, stale, "preview").getBody();
        assertEquals(HttpStatus.OK, exchange(manager, "/tenant/members/" + id + "/disable", HttpMethod.POST, Map.of("expectedVersion", member.get("version"))).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, command(manager, fresh, "publish").getStatusCode());
        assertEquals(0, grants(manager, id).size());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM organization_access.access_policy_audit WHERE version_id = ? AND action = 'PUBLISH'", Integer.class, Long.parseLong((String) draft.get("versionId"))));
    }

    @Test
    void 千成员二百助手使用隔离数据库真实筛选分页与授权预览() {
        var platform = exchange(null, "/platform/auth/sessions", HttpMethod.POST, Map.of("loginName", "platform-admin", "password", PASSWORD));
        assertEquals(HttpStatus.CREATED, platform.getStatusCode());
        var code = "scale-" + IDS.addAndGet(10000); var administrator = "owner-" + code;
        var tenant = exchange((String) platform.getBody().get("tokenValue"), "/platform/tenants", HttpMethod.POST,
                Map.of("tenantCode", code, "displayName", "合成规模验收租户",
                        "administrator", Map.of("loginName", administrator, "password", PASSWORD),
                        "quotas", Map.of("knowledgeBases", 1000, "documentsPerKnowledgeBase", 1000, "bytesPerKnowledgeBase", "12500000000", "maxFileBytes", "75000000", "maxTextBytes", "2097152")));
        assertEquals(HttpStatus.CREATED, tenant.getStatusCode());
        var logged = exchange(null, "/auth/sessions", HttpMethod.POST, Map.of("tenantCode", code, "loginName", administrator, "password", PASSWORD));
        assertEquals(HttpStatus.CREATED, logged.getStatusCode());
        var manager = (String) logged.getBody().get("tokenValue"); var context = sessions.resolve(manager);
        long base = IDS.addAndGet(10000), tenantId = context.tenantId().value(); var prefix = "scale-member-" + base + "-";
        // 密码摘要仅在隔离数据库内复制，浏览器与日志不读取；规模种子不是正式成员开户证据。
        jdbc.update("""
                INSERT INTO organization_access.subject_identity(subject_id,login_name,password_hash,status,version,created_at)
                SELECT ? + n, ? || n, source.password_hash, 'ACTIVE', 0, CURRENT_TIMESTAMP
                FROM generate_series(1,1000) n CROSS JOIN organization_access.subject_identity source WHERE source.subject_id = ?
                """, base, prefix, context.subjectId().value());
        jdbc.update("""
                INSERT INTO organization_access.tenant_membership(tenant_id,membership_id,subject_id,membership_role,status,version,permission_version,created_at)
                SELECT ?, ? + n, ? + n, 'TENANT_USER', 'ENABLED', 0, 0, CURRENT_TIMESTAMP FROM generate_series(1,1000) n
                """, tenantId, base + 2000, base);
        jdbc.update("""
                INSERT INTO organization_access.tenant_membership_role_binding(tenant_id,membership_id,role_code,bound_at)
                SELECT ?, ? + n, 'TENANT_USER', CURRENT_TIMESTAMP FROM generate_series(1,1000) n
                """, tenantId, base + 2000);
        jdbc.update("""
                INSERT INTO organization_access.member_conversation_features(tenant_id,membership_id,assistant_selection,conversation,own_history,authorized_citations,version)
                SELECT ?, ? + n, TRUE, TRUE, TRUE, TRUE, 0 FROM generate_series(1,1000) n
                """, tenantId, base + 2000);
        var assistants = new ArrayList<Long>(); for (int i = 0; i < 200; i++) assistants.add(seed(manager));
        var members = exchange(manager, "/tenant/access-policies/member-options?q=" + prefix + "&pageNumber=50&pageSize=20", HttpMethod.GET, null);
        assertEquals(HttpStatus.OK, members.getStatusCode()); assertEquals(1000, members.getBody().get("totalElements"));
        assertEquals(20, ((List<?>) members.getBody().get("items")).size()); assertEquals(false, members.getBody().get("hasNext"));
        var choices = exchange(manager, "/tenant/member-assistants?pageNumber=10&pageSize=20", HttpMethod.GET, null);
        assertEquals(HttpStatus.OK, choices.getStatusCode()); assertEquals(200, choices.getBody().get("totalElements"));
        assertEquals(20, ((List<?>) choices.getBody().get("items")).size()); assertEquals(false, choices.getBody().get("hasNext"));
        var selected = List.of(Long.toString(base + 2001), Long.toString(base + 3000));
        var draft = createPolicy(manager, assistants.getFirst(), selected);
        var preview = command(manager, draft, "preview"); assertEquals(HttpStatus.OK, preview.getStatusCode());
        var facts = (Map<?, ?>) preview.getBody().get("preview");
        assertEquals(8, facts.get("allowCount")); assertEquals(3992, facts.get("denyCount")); assertEquals(0, facts.get("leakCount"));
        var foreign = login("admin");
        assertEquals(HttpStatus.FORBIDDEN, exchange(foreign, policyPath(draft), HttpMethod.GET, null).getStatusCode());
        var own = member(foreign);
        assertEquals(HttpStatus.FORBIDDEN, memberAssistants(foreign, (String) own.get("membershipId"), List.of(Long.toString(assistants.getFirst()))).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, exchange(foreign, "/tenant/members/" + selected.getFirst() + "/assistants", HttpMethod.GET, null).getStatusCode());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM organization_access.member_assistant_authorization WHERE tenant_id = ?", Integer.class, tenantId));
        if (!System.getProperty("foundation.authorization.browserDirectory", "").isBlank()) {
            isolatedBrowser(code, administrator, prefix, base + 2001, assistants.getFirst());
        }
    }

    @Test
    void 成员开户必须与助手拥有者的发布行锁串行而非先读后写() throws Exception {
        var manager = login("admin"); var assistant = seed(manager);
        var tenant = sessions.resolve(manager).tenantId();
        var locked = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1);
        var holder = CompletableFuture.runAsync(() -> new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertTrue(assistantLocks.lock(tenant, assistant).isPresent()); locked.countDown();
            try { assertTrue(release.await(10, java.util.concurrent.TimeUnit.SECONDS)); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
        }));
        assertTrue(locked.await(5, java.util.concurrent.TimeUnit.SECONDS));
        var options = exchange(manager, "/tenant/members/create-options", HttpMethod.GET, null).getBody();
        var request = Map.of("loginName", "locked-member-" + IDS.incrementAndGet(), "password", PASSWORD,
                "conversationFeatures", Map.of("assistantSelection", true, "conversation", true, "ownHistory", false, "authorizedCitations", false),
                "assistantIds", List.of(Long.toString(assistant)), "expectedTenantFeatureVersion", options.get("tenantFeatureVersion"),
                "expectedAuthorizationRevision", options.get("authorizationRevision"));
        var created = CompletableFuture.supplyAsync(() -> exchange(manager, "/tenant/members", HttpMethod.POST, request));
        try { assertThrows(java.util.concurrent.TimeoutException.class, () -> created.get(1, java.util.concurrent.TimeUnit.SECONDS)); }
        finally { release.countDown(); holder.join(); }
        assertEquals(HttpStatus.CREATED, created.get(5, java.util.concurrent.TimeUnit.SECONDS).getStatusCode());
    }

    @Test
    void 策略审计失败不能提交关系或成功版本且助手页撤销失败同样回滚() {
        var manager = login("admin"); var assistant = seed(manager); var member = member(manager); var id = (String) member.get("membershipId");
        var draft = command(manager, createPolicy(manager, assistant, List.of(id)), "preview").getBody();
        var before = exchange(manager, "/tenant/members/" + id, HttpMethod.GET, null).getBody();
        doThrow(new org.springframework.dao.DataIntegrityViolationException("合成策略审计故障")).when(policyAudits).insert(any(com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.entity.AccessPolicyAuditEntity.class));
        try { assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, command(manager, draft, "publish").getStatusCode()); }
        finally { reset(policyAudits); }
        assertEquals(0, grants(manager, id).size());
        assertEquals(before, exchange(manager, "/tenant/members/" + id, HttpMethod.GET, null).getBody());
        assertEquals("DRAFT", exchange(manager, policyPath(draft), HttpMethod.GET, null).getBody().get("status"));
        doThrow(new IllegalStateException("合成助手撤销故障")).when(sessions).revokeMembershipSessions(new MembershipId(Long.parseLong(id)));
        try { assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, memberAssistants(manager, id, List.of(Long.toString(assistant))).getStatusCode()); }
        finally { reset(sessions); }
        assertEquals(0, grants(manager, id).size());
        assertEquals(before, exchange(manager, "/tenant/members/" + id, HttpMethod.GET, null).getBody());
        assertEquals(draft.get("authorizationRevision"), exchange(manager, policyPath(draft), HttpMethod.GET, null).getBody().get("authorizationRevision"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM organization_access.member_account_operation WHERE membership_id = ? AND operation = 'ASSISTANTS'", Integer.class, Long.parseLong(id)));
    }

    @Test
    void 停用资格仍可修改助手但不启用且未发布或外租户助手不能新增() {
        var manager = login("admin"); var assistant = seed(manager); var member = member(manager); var id = (String) member.get("membershipId");
        assertEquals(HttpStatus.OK, exchange(manager, "/tenant/members/" + id + "/disable", HttpMethod.POST, Map.of("expectedVersion", member.get("version"))).getStatusCode());
        assertEquals(HttpStatus.OK, memberAssistants(manager, id, List.of(Long.toString(assistant))).getStatusCode());
        var saved = exchange(manager, "/tenant/members/" + id, HttpMethod.GET, null).getBody();
        assertEquals("DISABLED", saved.get("status")); assertEquals(member.get("conversationFeatures"), saved.get("conversationFeatures"));
        var revision = exchange(manager, "/tenant/members/" + id + "/assistants", HttpMethod.GET, null).getBody();
        assertEquals(HttpStatus.OK, memberAssistants(manager, id, List.of(Long.toString(assistant))).getStatusCode());
        assertEquals(revision, exchange(manager, "/tenant/members/" + id + "/assistants", HttpMethod.GET, null).getBody());
        var unavailable = seed(manager); jdbc.update("UPDATE assistant_delivery.assistant_version SET status = 'DRAFT', version = 0, published_at = NULL WHERE assistant_id = ?", unavailable);
        assertEquals(HttpStatus.FORBIDDEN, memberAssistants(manager, id, List.of(Long.toString(unavailable))).getStatusCode());
        assertEquals(Set.of(Long.toString(assistant)), grants(manager, id));
    }

    @Test
    void 草稿不赋权真实预览发布与成员页撤权共用唯一事实且历史不能复活授权() {
        var manager = login("admin"); var assistant = seed(manager);
        var member = member(manager); var memberId = (String) member.get("membershipId");
        var ordinary = login((String) member.get("loginName"));
        var draft = createPolicy(manager, assistant, List.of(memberId));
        assertEquals(0, grants(manager, memberId).size());
        assertThrows(com.aiplatform.foundation.shared.kernel.error.ApplicationException.class,
                () -> access.requireAssistantVersion(sessions.resolve(ordinary), assistant + 1, "conversation"));
        assertEquals(HttpStatus.CONFLICT, command(manager, draft, "publish").getStatusCode());
        var preview = command(manager, draft, "preview"); assertEquals(HttpStatus.OK, preview.getStatusCode());
        var facts = preview.getBody(); var result = (Map<?, ?>) facts.get("preview");
        assertEquals(true, result.get("passed"));
        assertEquals(0, result.get("leakCount"));
        assertEquals(2, ((Number) result.get("allowCount")).intValue());
        assertTrue(((Number) result.get("denyCount")).intValue() >= 2);
        assertEquals(0, grants(manager, memberId).size());
        var published = command(manager, facts, "publish"); assertEquals(HttpStatus.OK, published.getStatusCode());
        assertEquals(Set.of(Long.toString(assistant)), grants(manager, memberId));
        assertThrows(InvalidAuthenticatedSessionException.class, () -> sessions.resolve(ordinary));
        var current = login((String) member.get("loginName"));
        assertDoesNotThrow(() -> access.requireAssistantVersion(sessions.resolve(current), assistant + 1, "conversation"));
        assertEquals(HttpStatus.OK, memberAssistants(manager, memberId, List.of()).getStatusCode());
        assertThrows(InvalidAuthenticatedSessionException.class, () -> sessions.resolve(current));
        assertEquals(HttpStatus.CONFLICT, command(manager, published.getBody(), "publish").getStatusCode());
        assertEquals(0, grants(manager, memberId).size());
        var next = exchange(manager, policyPath(draft) + "/versions", HttpMethod.POST,
                Map.of("sourceVersionId", published.getBody().get("versionId"), "expectedVersion", published.getBody().get("version")));
        assertEquals(HttpStatus.CREATED, next.getStatusCode());
        var again = exchange(manager, policyPath(draft) + "/versions", HttpMethod.POST,
                Map.of("sourceVersionId", published.getBody().get("versionId"), "expectedVersion", published.getBody().get("version")));
        assertEquals(next.getBody().get("versionId"), again.getBody().get("versionId"));
        assertEquals(0, grants(manager, memberId).size());
        var fresh = command(manager, next.getBody(), "preview"); assertEquals(HttpStatus.OK, fresh.getStatusCode());
        assertEquals(HttpStatus.OK, command(manager, fresh.getBody(), "publish").getStatusCode());
        assertEquals(Set.of(Long.toString(assistant)), grants(manager, memberId));
        assertEquals(HttpStatus.CONFLICT, exchange(manager, versionPath(published.getBody()), HttpMethod.PUT,
                saveInput(published.getBody(), assistant, List.of(memberId))).getStatusCode());
        var history = exchange(manager, versionPath(published.getBody()), HttpMethod.GET, null);
        assertEquals("SUPERSEDED", history.getBody().get("status"));
        assertEquals(published.getBody().get("membershipIds"), history.getBody().get("membershipIds"));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM organization_access.member_assistant_authorization WHERE membership_id = ?", Integer.class, Long.parseLong(memberId)));
    }

    @Test
    void 成员功能与租户授权变化都会使旧预览失效不能使用新读取版本绕过指纹() {
        var manager = login("admin"); var assistant = seed(manager); var other = seed(manager);
        var member = member(manager); var id = (String) member.get("membershipId");
        var draft = createPolicy(manager, assistant, List.of(id));
        var preview = command(manager, draft, "preview").getBody();
        var before = exchange(manager, "/tenant/members/" + id + "/features", HttpMethod.GET, null).getBody();
        var flags = new HashMap<String, Boolean>();
        ((Map<?, ?>) before.get("conversationFeatures")).forEach((key, value) -> flags.put((String) key, (Boolean) value));
        flags.put("authorizedCitations", true);
        assertEquals(HttpStatus.OK, exchange(manager, "/tenant/members/" + id + "/features", HttpMethod.PUT,
                Map.of("conversationFeatures", flags, "expectedFeatureVersion", before.get("featureVersion"),
                        "expectedTenantFeatureVersion", before.get("tenantFeatureVersion"))).getStatusCode());
        var stale = exchange(manager, policyPath(draft), HttpMethod.GET, null).getBody();
        assertEquals(false, ((Map<?, ?>) stale.get("preview")).get("passed"));
        assertEquals(HttpStatus.CONFLICT, command(manager, stale, "publish").getStatusCode());
        var fresh = command(manager, stale, "preview").getBody();
        assertEquals(HttpStatus.OK, memberAssistants(manager, id, List.of(Long.toString(other))).getStatusCode());
        stale = exchange(manager, policyPath(draft), HttpMethod.GET, null).getBody();
        assertEquals(false, ((Map<?, ?>) stale.get("preview")).get("passed"));
        assertEquals(HttpStatus.CONFLICT, command(manager, stale, "publish").getStatusCode());
        assertEquals(Set.of(Long.toString(other)), grants(manager, id));
        assertNotNull(fresh.get("preview")); assertNotNull(preview.get("preview"));
    }

    @Test
    void 两个策略并发发布只允许一个当前预览提交且失败者没有关系或成功审计() {
        var manager = login("admin"); var assistant = seed(manager); var a = member(manager); var b = member(manager);
        var first = command(manager, createPolicy(manager, assistant, List.of((String) a.get("membershipId"))), "preview").getBody();
        var second = command(manager, createPolicy(manager, assistant, List.of((String) b.get("membershipId"))), "preview").getBody();
        var x = CompletableFuture.supplyAsync(() -> command(manager, first, "publish"));
        var y = CompletableFuture.supplyAsync(() -> command(manager, second, "publish"));
        var outcomes = List.of(x.join().getStatusCode(), y.join().getStatusCode());
        assertEquals(1, outcomes.stream().filter(HttpStatus.OK::equals).count());
        assertEquals(1, outcomes.stream().filter(HttpStatus.CONFLICT::equals).count());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM organization_access.member_assistant_authorization WHERE assistant_id = ?", Integer.class, assistant));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM organization_access.access_policy_audit WHERE action = 'PUBLISH' AND policy_id IN (?, ?)", Integer.class,
                Long.parseLong((String) first.get("policyId")), Long.parseLong((String) second.get("policyId"))));
    }

    @Test
    void 发布撤销失败必须回滚关系版本及两类审计() {
        var manager = login("admin"); var assistant = seed(manager); var member = member(manager); var id = (String) member.get("membershipId");
        var draft = command(manager, createPolicy(manager, assistant, List.of(id)), "preview").getBody();
        var before = exchange(manager, "/tenant/members/" + id, HttpMethod.GET, null).getBody();
        doThrow(new IllegalStateException("合成撤销故障")).when(sessions).revokeMembershipSessions(new MembershipId(Long.parseLong(id)));
        try { assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, command(manager, draft, "publish").getStatusCode()); }
        finally { reset(sessions); }
        assertEquals(0, grants(manager, id).size());
        var current = exchange(manager, policyPath(draft), HttpMethod.GET, null).getBody();
        assertEquals("DRAFT", current.get("status")); assertEquals(draft.get("version"), current.get("version"));
        assertEquals(draft.get("authorizationRevision"), current.get("authorizationRevision"));
        assertEquals(before, exchange(manager, "/tenant/members/" + id, HttpMethod.GET, null).getBody());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM organization_access.access_policy_audit WHERE version_id = ? AND action = 'PUBLISH'", Integer.class, Long.parseLong((String) draft.get("versionId"))));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM organization_access.member_account_operation WHERE membership_id = ? AND operation = 'ASSISTANTS'", Integer.class, Long.parseLong(id)));
    }

    @Test
    void 更换助手仅移除该策略原助手范围并保留其他助手与四项配置() {
        var manager = login("admin"); var first = seed(manager); var replacement = seed(manager); var unrelated = seed(manager);
        var a = member(manager); var b = member(manager); var aid = (String) a.get("membershipId"); var bid = (String) b.get("membershipId");
        assertEquals(HttpStatus.OK, memberAssistants(manager, aid, List.of(Long.toString(unrelated))).getStatusCode());
        var published = command(manager, command(manager, createPolicy(manager, first, List.of(aid)), "preview").getBody(), "publish").getBody();
        var draft = exchange(manager, policyPath(published) + "/versions", HttpMethod.POST,
                Map.of("sourceVersionId", published.get("versionId"), "expectedVersion", published.get("version"))).getBody();
        var saved = exchange(manager, versionPath(draft), HttpMethod.PUT, saveInput(draft, replacement, List.of(bid)));
        assertEquals(HttpStatus.OK, saved.getStatusCode());
        var preview = command(manager, saved.getBody(), "preview"); assertEquals(HttpStatus.OK, preview.getStatusCode());
        assertEquals(HttpStatus.OK, command(manager, preview.getBody(), "publish").getStatusCode());
        assertEquals(Set.of(Long.toString(unrelated)), grants(manager, aid));
        assertEquals(Set.of(Long.toString(replacement)), grants(manager, bid));
        assertEquals(a.get("conversationFeatures"), exchange(manager, "/tenant/members/" + aid, HttpMethod.GET, null).getBody().get("conversationFeatures"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM organization_access.member_assistant_authorization WHERE assistant_id = ?", Integer.class, first));
    }

    @Test
    void 管理员不可成为策略目标且普通成员不能读取或发布策略未知字段也被拒绝() {
        var manager = login("admin"); var assistant = seed(manager); var member = member(manager);
        var draft = createPolicy(manager, assistant, List.of((String) member.get("membershipId")));
        var ordinary = login((String) member.get("loginName"));
        assertEquals(HttpStatus.FORBIDDEN, exchange(ordinary, policyPath(draft), HttpMethod.GET, null).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, command(ordinary, draft, "preview").getStatusCode());
        var forged = new HashMap<>(saveInput(draft, assistant, List.of((String) member.get("membershipId")))); forged.put("tenantId", "forged");
        assertEquals(HttpStatus.BAD_REQUEST, exchange(manager, versionPath(draft), HttpMethod.PUT, forged).getStatusCode());
        var adminId = Long.toString(sessions.resolve(manager).membershipId().value());
        assertEquals(HttpStatus.FORBIDDEN, exchange(manager, versionPath(draft), HttpMethod.PUT, saveInput(draft, assistant, List.of(adminId))).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, exchange(manager, versionPath(draft), HttpMethod.PUT, saveInput(draft, assistant, List.of("999999"))).getStatusCode());
        var readOnly = exchange(manager, "/tenant/members/" + adminId + "/assistants", HttpMethod.GET, null);
        assertEquals(true, readOnly.getBody().get("readOnly"));
        assertEquals(HttpStatus.FORBIDDEN, memberAssistants(manager, adminId, List.of(Long.toString(assistant))).getStatusCode());
        var options = exchange(manager, "/tenant/access-policies/member-options?q=" + member.get("loginName"), HttpMethod.GET, null);
        assertEquals(1, ((Number) options.getBody().get("totalElements")).intValue());
        assertFalse(options.getBody().toString().contains("subjectId"));
    }

    /** 可选真实浏览器门禁连接此隔离应用和构建后的 SPA，常规后端测试不冒称执行过浏览器。 */
    private void isolatedBrowser(String code, String administrator, String prefix, long membershipId, long assistantId) {
        try {
            var directory = java.nio.file.Path.of(System.getProperty("foundation.authorization.browserDirectory")).toRealPath();
            assertTrue(directory.startsWith(java.nio.file.Path.of("/private/tmp")));
            assertEquals("rwx------", java.nio.file.attribute.PosixFilePermissions.toString(java.nio.file.Files.getPosixFilePermissions(directory)));
            var permissions = java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            var credentials = java.nio.file.Files.createTempFile(directory, "isolated-credentials-", ".json", permissions);
            var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
            java.nio.file.Files.writeString(credentials, mapper.writeValueAsString(Map.of("tenant", Map.of("tenantCode", code, "loginName", administrator, "password", PASSWORD),
                    "member", Map.of("loginName", prefix + "1", "password", PASSWORD, "membershipId", Long.toString(membershipId)),
                    "assistantId", Long.toString(assistantId), "memberPrefix", prefix)));
            var repository = java.nio.file.Path.of("").toAbsolutePath();
            while (repository != null && !java.nio.file.Files.isRegularFile(repository.resolve("scripts/development/acceptance-authorization-isolated.cjs"))) {
                repository = repository.getParent();
            }
            assertNotNull(repository, "无法定位隔离浏览器验收入口");
            var log = java.nio.file.Files.createTempFile(directory, "isolated-browser-", ".log", permissions);
            // 使用实际查询启动包的独立 JVM，保留单角色门禁并隔离 Sa-Token 的进程全局状态。
            var queryProperties = new java.util.Properties();
            queryProperties.putAll(Map.ofEntries(
                            Map.entry("spring.config.name", "authorization-query-isolated"), Map.entry("server.port", "0"),
                            Map.entry("spring.main.banner-mode", "off"), Map.entry("spring.flyway.enabled", "false"),
                            Map.entry("spring.datasource.url", POSTGRES.getJdbcUrl()), Map.entry("spring.datasource.username", POSTGRES.getUsername()),
                            Map.entry("spring.datasource.password", POSTGRES.getPassword()), Map.entry("spring.data.redis.host", REDIS.getRedisHost()),
                            Map.entry("spring.data.redis.port", Integer.toString(REDIS.getRedisPort())), Map.entry("spring.data.redis.repositories.enabled", "false"),
                            Map.entry("foundation.application-role-validation.enabled", "true"), Map.entry("foundation.query.enabled", "true"),
                            Map.entry("foundation.control-plane.enabled", "false"), Map.entry("foundation.organization-access.feature-read.enabled", "true"),
                            Map.entry("foundation.knowledge-data.persistence.enabled", "true"), Map.entry("foundation.vector-store.pgvector.enabled", "true"),
                            Map.entry("foundation.model-runtime.enabled", "true"), Map.entry("foundation.model-runtime.persistence.enabled", "true"),
                            Map.entry("foundation.model-catalog.enabled", "true"), Map.entry("foundation.persistence.snowflake.worker-id", "2"),
                            Map.entry("foundation.persistence.snowflake.datacenter-id", "1"), Map.entry("sa-token.token-name", "Authorization"),
                            Map.entry("sa-token.token-prefix", "Bearer"), Map.entry("sa-token.is-read-cookie", "false"), Map.entry("sa-token.is-read-header", "true")));
            var queryJar = repository.resolve("backend/bootstrap/query-bootstrap/target/query-bootstrap-0.1.0-SNAPSHOT.jar");
            assertTrue(java.nio.file.Files.isRegularFile(queryJar), "请先构建当前查询启动包，再执行隔离浏览器门禁");
            int queryPort;
            try (var socket = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) { queryPort = socket.getLocalPort(); }
            queryProperties.setProperty("server.port", Integer.toString(queryPort));
            queryProperties.setProperty("management.endpoints.web.exposure.include", "health");
            var queryConfiguration = java.nio.file.Files.createTempFile(directory, "isolated-query-", ".properties", permissions);
            try (var output = java.nio.file.Files.newOutputStream(queryConfiguration)) { queryProperties.store(output, null); }
            var queryLog = java.nio.file.Files.createTempFile(directory, "isolated-query-", ".log", permissions);
            var query = new ProcessBuilder(java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-jar", queryJar.toString(), "--spring.config.location=" + queryConfiguration.toUri())
                    .redirectErrorStream(true).redirectOutput(queryLog.toFile()).start();
            try {
                var health = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + queryPort + "/actuator/health"))
                        .timeout(java.time.Duration.ofSeconds(2)).GET().build();
                var client = java.net.http.HttpClient.newHttpClient(); boolean ready = false;
                var deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(60);
                while (query.isAlive() && System.nanoTime() < deadline && !ready) {
                    try { ready = client.send(health, java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode() == 200; }
                    catch (java.io.IOException pending) { /* 启动期间连接尚未就绪，原日志保留在私有目录。 */ }
                    if (!ready) Thread.sleep(100);
                }
                assertTrue(ready, "独立查询应用未就绪，启动失败证据保留在私有目录");
                var process = new ProcessBuilder("node", repository.resolve("scripts/development/acceptance-authorization-isolated.cjs").toString(),
                        "http://127.0.0.1:" + port, credentials.toString(), directory.toString(),
                        repository.resolve("frontend/apps/console-web/dist/app").toString(), "http://127.0.0.1:" + queryPort)
                        .redirectErrorStream(true).redirectOutput(log.toFile()).start();
                try {
                    assertTrue(process.waitFor(10, java.util.concurrent.TimeUnit.MINUTES), "隔离浏览器门禁超时，证据保留在独占临时目录");
                    assertEquals(0, process.exitValue(), "隔离浏览器门禁未通过，失败阶段见独占临时日志");
                } finally { if (process.isAlive()) process.destroyForcibly(); }
            } finally { query.destroy(); if (!query.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) query.destroyForcibly(); }
        } catch (java.io.IOException failure) { throw new IllegalStateException("隔离浏览器准备未成功", failure); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException("隔离浏览器门禁被中断", failure); }
    }

    private Map<String, Object> member(String manager) {
        var name = "auth-member-" + IDS.incrementAndGet();
        var options = exchange(manager, "/tenant/members/create-options", HttpMethod.GET, null).getBody();
        var response = exchange(manager, "/tenant/members", HttpMethod.POST, Map.of("loginName", name, "password", PASSWORD,
                "conversationFeatures", Map.of("assistantSelection", true, "conversation", true, "ownHistory", false, "authorizedCitations", false),
                "assistantIds", List.of(), "expectedTenantFeatureVersion", options.get("tenantFeatureVersion"), "expectedAuthorizationRevision", options.get("authorizationRevision")));
        assertEquals(HttpStatus.CREATED, response.getStatusCode()); return response.getBody();
    }
    private Map<String, Object> createPolicy(String manager, long assistant, List<String> members) {
        var options = exchange(manager, "/tenant/members/create-options", HttpMethod.GET, null).getBody();
        var response = exchange(manager, "/tenant/access-policies", HttpMethod.POST, Map.of("name", "合成访问策略-" + IDS.incrementAndGet(),
                "assistantId", Long.toString(assistant), "membershipIds", members, "expectedAuthorizationRevision", options.get("authorizationRevision"),
                "expectedTenantFeatureVersion", options.get("tenantFeatureVersion")));
        assertEquals(HttpStatus.CREATED, response.getStatusCode()); return response.getBody();
    }
    private static String policyPath(Map<String, Object> value) { return "/tenant/access-policies/" + value.get("policyId"); }
    private static String versionPath(Map<String, Object> value) { return policyPath(value) + "/versions/" + value.get("versionId"); }
    private static Map<String, Object> expected(Map<String, Object> value) {
        return Map.of("expectedVersion", value.get("version"), "expectedAuthorizationRevision", value.get("authorizationRevision"), "expectedTenantFeatureVersion", value.get("tenantFeatureVersion"));
    }
    private static Map<String, Object> saveInput(Map<String, Object> value, long assistant, List<String> ids) {
        var input = new HashMap<>(expected(value)); input.put("assistantId", Long.toString(assistant)); input.put("membershipIds", ids); return input;
    }
    private ResponseEntity<Map<String, Object>> command(String manager, Map<String, Object> value, String command) {
        return exchange(manager, versionPath(value) + "/" + command, HttpMethod.POST, expected(value));
    }
    private ResponseEntity<Map<String, Object>> memberAssistants(String manager, String id, List<String> assistants) {
        var value = exchange(manager, "/tenant/members/" + id + "/assistants", HttpMethod.GET, null).getBody();
        return exchange(manager, "/tenant/members/" + id + "/assistants", HttpMethod.PUT,
                Map.of("assistantIds", assistants, "expectedAuthorizationRevision", value.get("authorizationRevision")));
    }
    private Set<String> grants(String manager, String id) {
        var value = exchange(manager, "/tenant/members/" + id + "/assistants", HttpMethod.GET, null);
        assertEquals(HttpStatus.OK, value.getStatusCode());
        return ((List<?>) value.getBody().get("assistantIds")).stream().map(String.class::cast).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    private ResponseEntity<Map<String, Object>> exchange(String token, String path, HttpMethod method, Map<String, ?> input) {
        var headers = new HttpHeaders(); if (token != null) headers.setBearerAuth(token);
        headers.set("Idempotency-Key", "authorization-test-" + IDS.incrementAndGet());
        return http.exchange("http://localhost:" + port + "/api/v1" + path, method, new HttpEntity<>(input, headers), RESPONSE);
    }
    private String login(String name) {
        var response = exchange(null, "/auth/sessions", HttpMethod.POST, Map.of("tenantCode", "demo-store", "loginName", name, "password", PASSWORD));
        assertEquals(HttpStatus.CREATED, response.getStatusCode()); return (String) response.getBody().get("tokenValue");
    }
    private long seed(String manager) {
        long tenant = sessions.resolve(manager).tenantId().value(), base = IDS.addAndGet(20), assistant = base + 3;
        jdbc.update("INSERT INTO knowledge_data.knowledge_space (tenant_id,id,name,status,version,created_by,created_at) VALUES (?,?,?,'DRAFT',0,1,CURRENT_TIMESTAMP)", tenant, base, "合成权限知识库-" + base);
        jdbc.update("""
                INSERT INTO knowledge_data.index_version (tenant_id,knowledge_space_id,id,source_snapshot_digest,chunk_policy,
                embedding_deployment_id,embedding_deployment_revision,embedding_dimension,status,version,build_digest,chunk_count,created_by,created_at,ready_at,activated_at)
                VALUES (?,?,?,?,'fixed-800-120',?,1,8,'ACTIVE',1,?,1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, tenant, base, base + 1, "a".repeat(64), base + 2, "b".repeat(64));
        jdbc.update("INSERT INTO assistant_delivery.assistant (tenant_id,id,display_name,latest_version_number,version,created_by,created_at) VALUES (?,?,?,1,1,1,CURRENT_TIMESTAMP)", tenant, assistant, "合成权限助手-" + assistant);
        jdbc.update("""
                INSERT INTO assistant_delivery.assistant_version (tenant_id,id,assistant_id,version_number,chat_deployment_output_limit,
                knowledge_space_id,index_version_id,index_version_revision,chat_model_configuration_id,chat_model_configuration_revision,chat_catalog_entry_id,chat_catalog_entry_revision,
                embedding_model_configuration_id,embedding_model_configuration_revision,embedding_catalog_entry_id,embedding_catalog_entry_revision,embedding_dimension,
                system_instruction,max_output_tokens,retrieval_limit,minimum_score,citation_policy,refusal_policy,status,version,created_by,created_at,published_at)
                VALUES (?,?,?,1,1024,?,?,1,?,1,?,1,?,1,?,1,8,'合成权限验证',256,5,0.5,'REQUIRED','INSUFFICIENT_EVIDENCE','PUBLISHED',1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, tenant, assistant + 1, assistant, base, base + 1, base + 5, base + 6, base + 7, base + 8);
        return assistant;
    }
}
