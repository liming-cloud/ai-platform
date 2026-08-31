package com.aiplatform.foundation.controlplane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.security.session.InvalidAuthenticatedSessionException;
import com.aiplatform.foundation.security.session.AuthenticatedSessionGateway;
import com.aiplatform.foundation.security.session.PlatformSessionGateway;
import com.redis.testcontainers.RedisContainer;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("demo")
@Testcontainers
class AuthenticationApiTest {

    private static final char[] DEMO_PASSWORD = "Demo@2026!".toCharArray();

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("pgvector/pgvector:0.8.5-pg18-trixie")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Container
    private static final RedisContainer REDIS = new RedisContainer("redis:8.8.1-trixie");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private AuthenticatedSessionGateway sessions;

    @Autowired
    private PlatformSessionGateway platformSessions;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getRedisHost);
        registry.add("spring.data.redis.port", REDIS::getRedisPort);
    }

    @Test
    void 应通过接口登录恢复可信上下文并注销() {
        var response = http.postForEntity(
                url("/api/v1/auth/sessions"),
                Map.of(
                        "tenantCode", "demo-store",
                        "loginName", "admin",
                        "password", new String(DEMO_PASSWORD)),
                SessionResponse.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        var token = body.tokenValue();
        assertFalse(token.isBlank());
        assertEquals(
                100000000000000001L,
                sessions.resolve(token).tenantId().value());

        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        var logout = http.exchange(
                url("/api/v1/auth/sessions/current"),
                HttpMethod.DELETE,
                new HttpEntity<>(headers),
                Void.class);

        assertEquals(HttpStatus.NO_CONTENT, logout.getStatusCode());
        assertThrows(InvalidAuthenticatedSessionException.class, () -> sessions.resolve(token));
    }

    @Test
    void 错误密码和未知账号应返回相同错误契约() {
        var wrongPassword = loginFailure("admin", "wrong");
        var unknownMember = loginFailure("missing", "wrong");

        assertEquals(HttpStatus.UNAUTHORIZED, wrongPassword.getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, unknownMember.getStatusCode());
        assertNotNull(wrongPassword.getBody());
        assertNotNull(unknownMember.getBody());
        assertEquals("ORGANIZATION_ACCESS.AUTHENTICATION.0001", wrongPassword.getBody().code());
        assertEquals("ORGANIZATION_ACCESS.AUTHENTICATION.0001", unknownMember.getBody().code());
        assertFalse(wrongPassword.getBody().message().contains("密码"));
        assertFalse(unknownMember.getBody().message().contains("账号"));
    }

    @Test
    void 平台管理员应通过独立入口取得不含租户的治理会话() {
        var response = http.postForEntity(
                url("/api/v1/platform/auth/sessions"),
                Map.of(
                        "loginName", "platform-admin",
                        "password", new String(DEMO_PASSWORD)),
                SessionResponse.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        var context = platformSessions.resolve(body.tokenValue());
        assertEquals(4921825928971026436L, context.subjectId().value());
        assertFalse(context.permissions().isEmpty());

        var tenantSession = http.postForEntity(
                url("/api/v1/auth/sessions"),
                Map.of(
                        "tenantCode", "demo-store",
                        "loginName", "admin",
                        "password", new String(DEMO_PASSWORD)),
                SessionResponse.class);
        assertNotNull(tenantSession.getBody());
        assertThrows(InvalidAuthenticatedSessionException.class,
                () -> platformSessions.resolve(tenantSession.getBody().tokenValue()));
    }

    @Test
    void 统一浏览器登录应只写同源安全Cookie而不在响应体返回令牌() {
        var csrf = browserCsrf();
        var response = http.exchange(
                url("/api/v1/auth/browser-sessions"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "tenantCode", "demo-store",
                                "loginName", "admin",
                                "password", new String(DEMO_PASSWORD)),
                        csrf.headers()),
                String.class);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertTrue(response.getBody() == null || response.getBody().isEmpty());
        var cookies = response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE);
        var tenantCookie = cookies.stream()
                .filter(cookie -> cookie.startsWith("__Host-foundation-tenant-session="))
                .findFirst()
                .orElseThrow();
        assertTrue(tenantCookie.contains("Path=/")
                && tenantCookie.contains("Secure")
                && tenantCookie.contains("HttpOnly")
                && tenantCookie.contains("SameSite=Lax"));
        assertTrue(cookies.stream().anyMatch(cookie -> cookie.startsWith("__Host-foundation-csrf=")
                && cookie.contains("Path=/")
                && cookie.contains("Secure")
                && cookie.contains("SameSite=Lax")
                && !cookie.contains("HttpOnly")));
    }

    @Test
    void 统一浏览器登录应拒绝跨站写入() {
        var headers = new HttpHeaders();
        headers.setOrigin("https://untrusted.example");
        headers.set("Sec-Fetch-Site", "cross-site");

        var response = http.exchange(
                url("/api/v1/auth/browser-sessions"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "tenantCode", "demo-store",
                                "loginName", "admin",
                                "password", new String(DEMO_PASSWORD)),
                        headers),
                String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void 持有浏览器会话的写入缺少CSRF防护值应失败关闭() {
        var csrf = browserCsrf();
        var login = http.exchange(
                url("/api/v1/auth/browser-sessions"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "loginName", "platform-admin",
                                "password", new String(DEMO_PASSWORD)),
                        csrf.headers()),
                String.class);
        assertEquals(HttpStatus.NO_CONTENT, login.getStatusCode());
        var platformCookie = login.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream()
                .filter(value -> value.startsWith("__Host-foundation-platform-session="))
                .findFirst()
                .map(value -> value.substring(0, value.indexOf(';')))
                .orElseThrow();
        var csrfCookie = login.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream()
                .filter(value -> value.startsWith("__Host-foundation-csrf="))
                .findFirst()
                .map(value -> value.substring(0, value.indexOf(';')))
                .orElseThrow();
        var headers = sameOriginHeaders();
        headers.set(HttpHeaders.COOKIE, platformCookie + "; " + csrfCookie);

        var response = http.exchange(
                url("/api/v1/platform/tenants"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "tenantCode", "csrf-tenant-" + System.nanoTime(),
                                "displayName", "CSRF 防护租户",
                                "initialAdministratorSubjectId", "4921825928971026436"),
                        headers),
                String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void 浏览器会话能力清单应只投影已验证租户范围且不泄露身份或令牌() {
        var browserLogin = browserLogin("demo-store", "admin");
        var headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, browserLogin.sessionCookie());

        var response = http.exchange(
                url("/api/v1/auth/browser-session?workspaceCategory=PLATFORM_TENANT_GOVERNANCE"),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(java.util.List.of("TENANT"), response.getBody().get("workspaceCategories"));
        assertEquals(java.util.List.of("TENANT_ADMINISTRATOR"), response.getBody().get("displayRoles"));
        assertTrue(((java.util.List<?>) response.getBody().get("capabilityCodes"))
                .containsAll(java.util.List.of("knowledge:read", "assistant:query")));
        assertEquals(3600, response.getBody().get("maximumSessionAgeSeconds"));
        assertFalse(response.getBody().containsKey("token"));
        assertFalse(response.getBody().containsKey("subjectId"));
        assertFalse(response.getBody().containsKey("membershipId"));
        assertFalse(response.getBody().containsKey("tenantId"));
        assertFalse(response.getBody().containsKey("permissions"));
    }

    @Test
    void 租户管理员绑定普通用户角色后应撤销目标成员的旧会话并更新权限投影() {
        var targetSession = http.postForEntity(
                url("/api/v1/auth/sessions"),
                Map.of(
                        "tenantCode", "demo-store",
                        "loginName", "tenant-user",
                        "password", new String(DEMO_PASSWORD)),
                SessionResponse.class);
        assertEquals(HttpStatus.CREATED, targetSession.getStatusCode());
        assertNotNull(targetSession.getBody());
        var managerToken = tenantLogin();

        var response = http.exchange(
                url("/api/v1/tenant/members/3382738893456539652/role-bindings"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of("role", "TENANT_USER"),
                        bearer(managerToken)),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("TENANT_USER", response.getBody().get("role"));
        assertEquals("3382738893456539652", response.getBody().get("membershipId"));
        assertThrows(InvalidAuthenticatedSessionException.class,
                () -> sessions.resolve(targetSession.getBody().tokenValue()));

        var renewedSession = http.postForEntity(
                url("/api/v1/auth/sessions"),
                Map.of(
                        "tenantCode", "demo-store",
                        "loginName", "tenant-user",
                        "password", new String(DEMO_PASSWORD)),
                SessionResponse.class);
        assertEquals(HttpStatus.CREATED, renewedSession.getStatusCode());
        assertNotNull(renewedSession.getBody());
        assertTrue(sessions.resolve(renewedSession.getBody().tokenValue())
                .hasPermission(new com.aiplatform.foundation.security.identity.PermissionCode("knowledge:read")));
        assertTrue(sessions.resolve(renewedSession.getBody().tokenValue())
                .hasPermission(new com.aiplatform.foundation.security.identity.PermissionCode("assistant:query")));
    }

    @Test
    void 租户管理员不得提升降级或停用其他租户管理员() {
        var bind = http.exchange(
                url("/api/v1/tenant/members/3382738893456539652/role-bindings"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of("role", "TENANT_ADMINISTRATOR"),
                        bearer(tenantLogin())),
                ApiError.class);
        assertEquals(HttpStatus.FORBIDDEN, bind.getStatusCode());
        assertNotNull(bind.getBody());
        assertEquals("ORGANIZATION_ACCESS.AUTHORIZATION.0002", bind.getBody().code());

        var revoke = http.exchange(
                url("/api/v1/tenant/members/3382738893456539652/role-bindings/TENANT_ADMINISTRATOR"),
                HttpMethod.DELETE,
                new HttpEntity<>(bearer(tenantLogin())),
                ApiError.class);
        assertEquals(HttpStatus.FORBIDDEN, revoke.getStatusCode());
        assertNotNull(revoke.getBody());
        assertEquals("ORGANIZATION_ACCESS.AUTHORIZATION.0002", revoke.getBody().code());

        var disable = http.exchange(
                url("/api/v1/tenant/members/3382738893456539650/disable"),
                HttpMethod.POST,
                new HttpEntity<>(bearer(tenantLogin())),
                ApiError.class);
        assertEquals(HttpStatus.FORBIDDEN, disable.getStatusCode());
        assertNotNull(disable.getBody());
        assertEquals("ORGANIZATION_ACCESS.AUTHORIZATION.0002", disable.getBody().code());
        assertFalse(disable.getBody().message().contains("TENANT_ADMINISTRATOR"));
        assertFalse(disable.getBody().message().contains("3382738893456539650"));
    }

    @Test
    void 三角色产品不再发布租户自定义角色接口() {
        var response = http.exchange(
                url("/api/v1/tenant/custom-roles"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "name", "模型观察员",
                                "permissions", java.util.List.of("model:read")),
                        bearer(tenantLogin())),
                String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        var binding = http.exchange(
                url("/api/v1/tenant/members/3382738893456539652/custom-role-bindings"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of("customRoleId", "9001"),
                        bearer(tenantLogin())),
                String.class);
        assertEquals(HttpStatus.NOT_FOUND, binding.getStatusCode());
    }

    @Test
    void 租户访问管理员停用成员时应由令牌确定范围并撤销目标会话() {
        var targetMembershipId = 3382738893456539660L;
        jdbc.update("""
                INSERT INTO organization_access.tenant_membership
                    (membership_id, tenant_id, subject_id, membership_role, status, permission_version, version)
                VALUES (?, 100000000000000001, 4921825928971026438, 'TENANT_USER', 'ENABLED', 0, 0)
                ON CONFLICT (tenant_id, membership_id) DO UPDATE
                    SET status = 'ENABLED', permission_version = 0, version = 0
                """, targetMembershipId);
        var targetSession = tenantLoginWithName("tenant-admin-candidate");
        var managerToken = tenantLogin();
        var directory = http.exchange(
                url("/api/v1/tenant/members?pageNumber=1&pageSize=100"),
                HttpMethod.GET,
                new HttpEntity<>(bearer(managerToken)),
                Map.class);
        assertEquals(HttpStatus.OK, directory.getStatusCode());
        assertNotNull(directory.getBody());
        assertEquals(1, directory.getBody().get("pageNumber"));
        assertEquals(100, directory.getBody().get("pageSize"));
        assertTrue(((java.util.List<?>) directory.getBody().get("items")).stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .anyMatch(item -> Long.toString(targetMembershipId).equals(item.get("membershipId"))
                        && "tenant-admin-candidate".equals(item.get("loginName"))));

        var response = http.exchange(
                url("/api/v1/tenant/members/" + targetMembershipId + "/disable"),
                HttpMethod.POST,
                new HttpEntity<>(bearer(managerToken)),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(Long.toString(targetMembershipId), response.getBody().get("membershipId"));
        assertEquals("DISABLED", response.getBody().get("status"));
        assertEquals(1, response.getBody().get("permissionVersion"));
        assertThrows(InvalidAuthenticatedSessionException.class,
                () -> sessions.resolve(targetSession));
        assertEquals(HttpStatus.UNAUTHORIZED,
                tenantLoginWithNameResponse("tenant-admin-candidate").getStatusCode());
    }

    @Test
    void 租户访问管理员建立成员时应只接受主体标识且写入普通成员资格() {
        var subjectId = 4921825928971026440L;
        jdbc.update("DELETE FROM organization_access.tenant_membership WHERE subject_id = ?", subjectId);
        jdbc.update("""
                INSERT INTO organization_access.subject_identity
                    (subject_id, login_name, password_hash, status, version)
                VALUES (?, 'member-establishment-http', 'valid-hash', 'ACTIVE', 0)
                ON CONFLICT (subject_id) DO UPDATE
                    SET login_name = EXCLUDED.login_name, status = 'ACTIVE'
                """, subjectId);
        var managerToken = tenantLogin();

        var response = http.exchange(
                url("/api/v1/tenant/members"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("subjectId", Long.toString(subjectId)), bearer(managerToken)),
                Map.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ENABLED", response.getBody().get("status"));
        assertEquals(0, response.getBody().get("permissionVersion"));
        assertEquals("TENANT_USER", jdbc.queryForObject("""
                SELECT membership_role FROM organization_access.tenant_membership
                WHERE tenant_id = 100000000000000001 AND subject_id = ?
                """, String.class, subjectId));
    }

    @Test
    void 双向人员范围冲突必须由真实HTTP安全拒绝() {
        var tenantConflict = http.exchange(
                url("/api/v1/tenant/members"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of("subjectId", "4921825928971026436"),
                        bearer(tenantLogin())),
                ApiError.class);
        assertEquals(HttpStatus.CONFLICT, tenantConflict.getStatusCode());
        assertNotNull(tenantConflict.getBody());
        assertEquals("ORGANIZATION_ACCESS.CONFLICT.0002", tenantConflict.getBody().code());
        assertFalse(tenantConflict.getBody().message().contains("4921825928971026436"));

        var platformConflict = http.exchange(
                url("/api/v1/platform/accounts/4921825928971026434/role-bindings"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of("role", "PLATFORM_ADMINISTRATOR"),
                        bearer(platformLogin())),
                ApiError.class);
        assertEquals(HttpStatus.CONFLICT, platformConflict.getStatusCode());
        assertNotNull(platformConflict.getBody());
        assertEquals("ORGANIZATION_ACCESS.CONFLICT.0002", platformConflict.getBody().code());
        assertFalse(platformConflict.getBody().message().contains("4921825928971026434"));

        var provisioningConflict = http.exchange(
                url("/api/v1/platform/tenants"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "tenantCode", "scope-conflict-" + System.nanoTime(),
                                "displayName", "人员范围冲突租户",
                                "initialAdministratorSubjectId", "4921825928971026436"),
                        bearer(platformLogin())),
                ApiError.class);
        assertEquals(HttpStatus.CONFLICT, provisioningConflict.getStatusCode());
        assertNotNull(provisioningConflict.getBody());
        assertEquals("ORGANIZATION_ACCESS.CONFLICT.0002", provisioningConflict.getBody().code());
    }

    @Test
    void 浏览器会话能力清单应投影平台治理范围且隔离失效的另一范围会话() {
        var platformSessionCookie = browserPlatformLogin("platform-admin");
        var headers = new HttpHeaders();
        headers.set(
                HttpHeaders.COOKIE,
                "__Host-foundation-tenant-session=invalid-session; " + platformSessionCookie);

        var response = http.exchange(
                url("/api/v1/auth/browser-session"),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(
                java.util.List.of("PLATFORM_TENANT_GOVERNANCE"),
                response.getBody().get("workspaceCategories"));
        assertEquals(
                java.util.List.of("PLATFORM_ADMINISTRATOR"), response.getBody().get("displayRoles"));
        assertTrue(response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream()
                .anyMatch(cookie -> cookie.startsWith("__Host-foundation-tenant-session=")
                        && cookie.contains("Max-Age=0")));
        assertFalse(response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream()
                .anyMatch(cookie -> cookie.startsWith("__Host-foundation-platform-session=")
                        && cookie.contains("Max-Age=0")));
    }

    @Test
    void 失效浏览器范围会话应被拒绝并定向清除Cookie() {
        var headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, "__Host-foundation-tenant-session=invalid-session");

        var response = http.exchange(
                url("/api/v1/auth/browser-session"),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertTrue(response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream()
                .anyMatch(cookie -> cookie.startsWith("__Host-foundation-tenant-session=")
                        && cookie.contains("Max-Age=0")
                        && cookie.contains("HttpOnly")));
    }

    @Test
    void 浏览器注销应撤销当前范围会话并清除整个会话族Cookie() {
        var csrf = browserCsrf();
        var login = http.exchange(
                url("/api/v1/auth/browser-sessions"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "tenantCode", "demo-store",
                                "loginName", "admin",
                                "password", new String(DEMO_PASSWORD)),
                        csrf.headers()),
                String.class);
        assertEquals(HttpStatus.NO_CONTENT, login.getStatusCode());
        var tenantCookie = cookiePair(login.getHeaders(), "__Host-foundation-tenant-session=");
        var csrfCookie = cookiePair(login.getHeaders(), "__Host-foundation-csrf=");
        var headers = sameOriginHeaders();
        headers.set(HttpHeaders.COOKIE, tenantCookie + "; " + csrfCookie);
        headers.set("X-CSRF-Token", csrfCookie.substring(csrfCookie.indexOf('=') + 1));

        var logout = http.exchange(
                url("/api/v1/auth/browser-session"),
                HttpMethod.DELETE,
                new HttpEntity<>(headers),
                String.class);

        assertEquals(HttpStatus.NO_CONTENT, logout.getStatusCode());
        assertTrue(logout.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream()
                .anyMatch(cookie -> cookie.startsWith("__Host-foundation-tenant-session=")
                        && cookie.contains("Max-Age=0") && cookie.contains("HttpOnly")));
        assertTrue(logout.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream()
                .anyMatch(cookie -> cookie.startsWith("__Host-foundation-platform-session=")
                        && cookie.contains("Max-Age=0") && cookie.contains("HttpOnly")));
        assertTrue(logout.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream()
                .anyMatch(cookie -> cookie.startsWith("__Host-foundation-csrf=")
                        && cookie.contains("Max-Age=0") && !cookie.contains("HttpOnly")));

        var expired = new HttpHeaders();
        expired.set(HttpHeaders.COOKIE, tenantCookie);
        var capability = http.exchange(
                url("/api/v1/auth/browser-session"),
                HttpMethod.GET,
                new HttpEntity<>(expired),
                String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, capability.getStatusCode());
    }

    @Test
    void 平台会话应建立并激活租户且新租户随后可取得租户会话() {
        var platformToken = platformLogin();
        var tenantCode = "api-tenant-" + System.nanoTime();
        var created = http.exchange(
                url("/api/v1/platform/tenants"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "tenantCode", tenantCode,
                                "displayName", "接口创建租户",
                                "initialAdministratorSubjectId", "4921825928971026438"),
                        bearer(platformToken)),
                TenantLifecycleResponse.class);

        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertNotNull(created.getBody());
        assertEquals("PROVISIONING", created.getBody().status());
        assertEquals(HttpStatus.UNAUTHORIZED, tenantLogin(tenantCode).getStatusCode());

        var activated = http.exchange(
                url("/api/v1/platform/tenants/" + created.getBody().tenantId() + "/lifecycle/activate"),
                HttpMethod.POST,
                new HttpEntity<>(bearer(platformToken)),
                TenantLifecycleResponse.class);
        assertEquals(HttpStatus.OK, activated.getStatusCode());
        assertNotNull(activated.getBody());
        assertEquals("ACTIVE", activated.getBody().status());

        assertEquals(HttpStatus.CREATED, tenantLogin(tenantCode).getStatusCode());

        var suspended = http.exchange(
                url("/api/v1/platform/tenants/" + created.getBody().tenantId() + "/lifecycle/suspend"),
                HttpMethod.POST,
                new HttpEntity<>(bearer(platformToken)),
                TenantLifecycleResponse.class);
        assertEquals(HttpStatus.OK, suspended.getStatusCode());
        assertNotNull(suspended.getBody());
        assertEquals("SUSPENDED", suspended.getBody().status());
        assertEquals(HttpStatus.UNAUTHORIZED, tenantLogin(tenantCode).getStatusCode());

        var restored = http.exchange(
                url("/api/v1/platform/tenants/" + created.getBody().tenantId() + "/lifecycle/restore"),
                HttpMethod.POST,
                new HttpEntity<>(bearer(platformToken)),
                TenantLifecycleResponse.class);
        assertEquals(HttpStatus.OK, restored.getStatusCode());
        assertNotNull(restored.getBody());
        assertEquals("ACTIVE", restored.getBody().status());
        assertEquals(HttpStatus.CREATED, tenantLogin(tenantCode).getStatusCode());
    }

    @Test
    void 平台治理会话应分页读取不含受限字段的生命周期投影() {
        var platformToken = platformLogin();
        var tenantCode = "projection-tenant-" + System.nanoTime();
        var created = http.exchange(
                url("/api/v1/platform/tenants"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "tenantCode", tenantCode,
                                "displayName", "读取投影租户",
                                "initialAdministratorSubjectId", "4921825928971026438"),
                        bearer(platformToken)),
                TenantLifecycleResponse.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());

        var response = http.exchange(
                url("/api/v1/platform/tenants?pageNumber=1&pageSize=100"),
                HttpMethod.GET,
                new HttpEntity<>(bearer(platformToken)),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().get("pageNumber"));
        assertEquals(100, response.getBody().get("pageSize"));
        var summary = ((java.util.List<?>) response.getBody().get("items")).stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .filter(item -> tenantCode.equals(item.get("tenantCode")))
                .findFirst()
                .orElseThrow();
        assertEquals("读取投影租户", summary.get("displayName"));
        assertEquals("PROVISIONING", summary.get("status"));
        assertEquals(0, summary.get("version"));
        assertNotNull(summary.get("createdAt"));
        assertNotNull(summary.get("lastChangedAt"));
        assertFalse(summary.containsKey("initialAdministratorSubjectId"));
        assertFalse(summary.containsKey("permissions"));
    }

    @Test
    void 平台应独占租户管理员全生命周期并撤销受影响主体会话() {
        var assignSubjectId = 4921825928971026501L;
        var replacementSubjectId = 4921825928971026502L;
        seedActiveSubject(assignSubjectId, "platform-assigned-admin");
        seedActiveSubject(replacementSubjectId, "platform-replacement-admin");
        var platformToken = platformLogin();
        var tenantCode = "administrator-lifecycle-" + System.nanoTime();
        var created = http.exchange(
                url("/api/v1/platform/tenants"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "tenantCode", tenantCode,
                                "displayName", "管理员生命周期租户",
                                "initialAdministratorSubjectId", "4921825928971026438"),
                        bearer(platformToken)),
                TenantLifecycleResponse.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertNotNull(created.getBody());
        var tenantId = created.getBody().tenantId();
        var activated = http.exchange(
                url("/api/v1/platform/tenants/" + tenantId + "/lifecycle/activate"),
                HttpMethod.POST,
                new HttpEntity<>(bearer(platformToken)),
                TenantLifecycleResponse.class);
        assertEquals(HttpStatus.OK, activated.getStatusCode());
        var oldSession = tenantLogin(tenantCode, "tenant-admin-candidate");

        var candidates = http.exchange(
                url("/api/v1/platform/tenants/" + tenantId
                        + "/administrators/candidates?q=platform-&pageNumber=1&pageSize=20"),
                HttpMethod.GET,
                new HttpEntity<>(bearer(platformToken)),
                Map.class);
        assertEquals(HttpStatus.OK, candidates.getStatusCode());
        assertNotNull(candidates.getBody());
        var candidateItems = (java.util.List<?>) candidates.getBody().get("items");
        assertTrue(candidateItems.stream().filter(Map.class::isInstance).map(Map.class::cast)
                .anyMatch(item -> Long.toString(assignSubjectId).equals(item.get("accountReference"))));
        assertTrue(candidateItems.stream().filter(Map.class::isInstance).map(Map.class::cast)
                .anyMatch(item -> Long.toString(replacementSubjectId).equals(item.get("accountReference"))));

        var assigned = http.exchange(
                url("/api/v1/platform/tenants/" + tenantId + "/administrators"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of("accountReference", Long.toString(assignSubjectId)),
                        bearer(platformToken)),
                Map.class);
        assertEquals(HttpStatus.CREATED, assigned.getStatusCode());
        assertNotNull(assigned.getBody());
        assertEquals("platform-assigned-admin", assigned.getBody().get("loginName"));
        assertFalse(assigned.getBody().containsKey("subjectId"));
        assertFalse(assigned.getBody().containsKey("passwordHash"));

        var directory = http.exchange(
                url("/api/v1/platform/tenants/" + tenantId
                        + "/administrators?pageNumber=1&pageSize=20"),
                HttpMethod.GET,
                new HttpEntity<>(bearer(platformToken)),
                Map.class);
        assertEquals(HttpStatus.OK, directory.getStatusCode());
        assertNotNull(directory.getBody());
        var oldAdministrator = ((java.util.List<?>) directory.getBody().get("items")).stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .filter(item -> "tenant-admin-candidate".equals(item.get("loginName")))
                .findFirst()
                .orElseThrow();
        var oldMembershipReference = oldAdministrator.get("membershipReference").toString();

        var replaced = http.exchange(
                url("/api/v1/platform/tenants/" + tenantId + "/administrators/"
                        + oldMembershipReference + "/replace"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "expectedVersion", oldAdministrator.get("version"),
                                "replacementAccountReference", Long.toString(replacementSubjectId)),
                        bearer(platformToken)),
                Map.class);
        assertEquals(HttpStatus.OK, replaced.getStatusCode());
        assertNotNull(replaced.getBody());
        assertEquals("platform-replacement-admin", replaced.getBody().get("loginName"));
        assertThrows(InvalidAuthenticatedSessionException.class, () -> sessions.resolve(oldSession));
        assertEquals(HttpStatus.UNAUTHORIZED,
                tenantLoginStatus(tenantCode, "tenant-admin-candidate"));
        assertEquals(HttpStatus.CREATED,
                tenantLoginResponse(tenantCode, "platform-replacement-admin").getStatusCode());

        var restored = http.exchange(
                url("/api/v1/platform/tenants/" + tenantId + "/administrators/"
                        + oldMembershipReference + "/restore"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("expectedVersion", 1), bearer(platformToken)),
                Map.class);
        assertEquals(HttpStatus.OK, restored.getStatusCode());
        assertNotNull(restored.getBody());
        assertEquals("ENABLED", restored.getBody().get("status"));
        var restoredSession = tenantLogin(tenantCode, "tenant-admin-candidate");

        var disabled = http.exchange(
                url("/api/v1/platform/tenants/" + tenantId + "/administrators/"
                        + oldMembershipReference + "/disable"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("expectedVersion", 2), bearer(platformToken)),
                Map.class);
        assertEquals(HttpStatus.OK, disabled.getStatusCode());
        assertNotNull(disabled.getBody());
        assertEquals("DISABLED", disabled.getBody().get("status"));
        assertThrows(InvalidAuthenticatedSessionException.class, () -> sessions.resolve(restoredSession));

        var tenantDenied = http.exchange(
                url("/api/v1/platform/tenants/" + tenantId
                        + "/administrators?pageNumber=1&pageSize=20"),
                HttpMethod.GET,
                new HttpEntity<>(bearer(tenantLogin(tenantCode, "platform-assigned-admin"))),
                ApiError.class);
        assertEquals(HttpStatus.UNAUTHORIZED, tenantDenied.getStatusCode());
    }

    @Test
    void 租户会话与伪造当前主体字段不得取得平台租户治理入口() {
        var tenantToken = tenantLogin();
        var denied = http.exchange(
                url("/api/v1/platform/tenants"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "tenantCode", "forged-tenant",
                                "displayName", "伪造租户",
                                "initialAdministratorSubjectId", "4921825928971026436",
                                "subjectId", "4921825928971026436"),
                        bearer(tenantToken)),
                ApiError.class);
        assertEquals(HttpStatus.UNAUTHORIZED, denied.getStatusCode());

        var rejected = http.exchange(
                url("/api/v1/platform/tenants"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "tenantCode", "forged-tenant",
                                "displayName", "伪造租户",
                                "initialAdministratorSubjectId", "4921825928971026436",
                                "subjectId", "4921825928971026436"),
                        bearer(platformLogin())),
                ApiError.class);
        assertEquals(HttpStatus.BAD_REQUEST, rejected.getStatusCode());
    }

    @Test
    void 新增平台管理员可读取并建立租户生命周期() {
        var platformToken = platformLogin("platform-operator");
        var denied = http.exchange(
                url("/api/v1/platform/tenants?pageNumber=1&pageSize=20"),
                HttpMethod.GET,
                new HttpEntity<>(bearer(platformToken)),
                Map.class);

        assertEquals(HttpStatus.OK, denied.getStatusCode());

        var writeDenied = http.exchange(
                url("/api/v1/platform/tenants"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "tenantCode", "operator-denied-" + System.nanoTime(),
                                "displayName", "无权限租户",
                                "initialAdministratorSubjectId", "4921825928971026438"),
                        bearer(platformToken)),
                Map.class);

        assertEquals(HttpStatus.CREATED, writeDenied.getStatusCode());
    }

    @Test
    void 新增平台管理员登录后获得完整平台能力() {
        var headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, browserPlatformLogin("platform-operator"));

        var capability = http.exchange(
                url("/api/v1/auth/browser-session"),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        assertEquals(HttpStatus.OK, capability.getStatusCode());
        assertNotNull(capability.getBody());
        assertEquals(
                java.util.List.of("PLATFORM_TENANT_GOVERNANCE"),
                capability.getBody().get("workspaceCategories"));
        assertEquals(
                java.util.List.of("PLATFORM_ADMINISTRATOR"), capability.getBody().get("displayRoles"));
        assertTrue(((java.util.List<?>) capability.getBody().get("capabilityCodes"))
                .contains("platform-session:access"));
        assertTrue(((java.util.List<?>) capability.getBody().get("capabilityCodes"))
                .contains("tenant-governance:write"));
        assertTrue(((java.util.List<?>) capability.getBody().get("capabilityCodes"))
                .contains("platform:accounts:manage"));
    }

    private org.springframework.http.ResponseEntity<ApiError> loginFailure(
            String loginName,
            String password) {
        return http.postForEntity(
                url("/api/v1/auth/sessions"),
                Map.of(
                        "tenantCode", "demo-store",
                        "loginName", loginName,
                        "password", password),
                ApiError.class);
    }

    private String platformLogin() {
        return platformLogin("platform-admin");
    }

    private String platformLogin(String loginName) {
        var response = http.postForEntity(
                url("/api/v1/platform/auth/sessions"),
                Map.of(
                        "loginName", loginName,
                        "password", new String(DEMO_PASSWORD)),
                SessionResponse.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody().tokenValue();
    }

    private String tenantLogin() {
        return tenantLoginWithName("admin");
    }

    private String tenantLoginWithName(String loginName) {
        var response = http.postForEntity(
                url("/api/v1/auth/sessions"),
                Map.of(
                        "tenantCode", "demo-store",
                        "loginName", loginName,
                        "password", new String(DEMO_PASSWORD)),
                SessionResponse.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody().tokenValue();
    }

    private org.springframework.http.ResponseEntity<String> tenantLoginWithNameResponse(
            String loginName) {
        return http.postForEntity(
                url("/api/v1/auth/sessions"),
                Map.of(
                        "tenantCode", "demo-store",
                        "loginName", loginName,
                        "password", new String(DEMO_PASSWORD)),
                String.class);
    }

    private org.springframework.http.ResponseEntity<String> tenantLogin(String tenantCode) {
        return http.postForEntity(
                url("/api/v1/auth/sessions"),
                Map.of(
                        "tenantCode", tenantCode,
                        "loginName", "tenant-admin-candidate",
                        "password", new String(DEMO_PASSWORD)),
                String.class);
    }

    private String tenantLogin(String tenantCode, String loginName) {
        var response = tenantLoginResponse(tenantCode, loginName);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody().tokenValue();
    }

    private org.springframework.http.ResponseEntity<SessionResponse> tenantLoginResponse(
            String tenantCode,
            String loginName) {
        return http.postForEntity(
                url("/api/v1/auth/sessions"),
                Map.of(
                        "tenantCode", tenantCode,
                        "loginName", loginName,
                        "password", new String(DEMO_PASSWORD)),
                SessionResponse.class);
    }

    private org.springframework.http.HttpStatusCode tenantLoginStatus(
            String tenantCode,
            String loginName) {
        return http.postForEntity(
                        url("/api/v1/auth/sessions"),
                        Map.of(
                                "tenantCode", tenantCode,
                                "loginName", loginName,
                                "password", new String(DEMO_PASSWORD)),
                        String.class)
                .getStatusCode();
    }

    private void seedActiveSubject(long subjectId, String loginName) {
        jdbc.update("""
                INSERT INTO organization_access.subject_identity
                    (subject_id, login_name, password_hash, status, version)
                SELECT ?, ?, password_hash, 'ACTIVE', 0
                FROM organization_access.subject_identity
                WHERE subject_id = 4921825928971026438
                ON CONFLICT (subject_id) DO UPDATE
                    SET login_name = EXCLUDED.login_name,
                        password_hash = EXCLUDED.password_hash,
                        status = 'ACTIVE',
                        version = 0
                """, subjectId, loginName);
    }

    private static HttpHeaders bearer(String token) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private HttpHeaders sameOriginHeaders() {
        var headers = new HttpHeaders();
        headers.setOrigin("http://127.0.0.1:" + port);
        headers.set("Sec-Fetch-Site", "same-origin");
        return headers;
    }

    private BrowserCsrf browserCsrf() {
        var response = http.getForEntity(url("/api/v1/auth/browser-csrf"), String.class);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        var cookie = response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream()
                .filter(value -> value.startsWith("__Host-foundation-csrf="))
                .findFirst()
                .orElseThrow();
        var pair = cookie.substring(0, cookie.indexOf(';'));
        var headers = sameOriginHeaders();
        headers.set(HttpHeaders.COOKIE, pair);
        headers.set("X-CSRF-Token", pair.substring(pair.indexOf('=') + 1));
        return new BrowserCsrf(headers);
    }

    private BrowserLogin browserLogin(String tenantCode, String loginName) {
        var csrf = browserCsrf();
        var response = http.exchange(
                url("/api/v1/auth/browser-sessions"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "tenantCode", tenantCode,
                                "loginName", loginName,
                                "password", new String(DEMO_PASSWORD)),
                        csrf.headers()),
                String.class);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        var sessionCookie = response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream()
                .filter(value -> value.startsWith("__Host-foundation-tenant-session="))
                .findFirst()
                .map(value -> value.substring(0, value.indexOf(';')))
                .orElseThrow();
        return new BrowserLogin(sessionCookie);
    }

    private String browserPlatformLogin(String loginName) {
        var csrf = browserCsrf();
        var response = http.exchange(
                url("/api/v1/auth/browser-sessions"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "loginName", loginName,
                                "password", new String(DEMO_PASSWORD)),
                        csrf.headers()),
                String.class);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        return response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream()
                .filter(value -> value.startsWith("__Host-foundation-platform-session="))
                .findFirst()
                .map(value -> value.substring(0, value.indexOf(';')))
                .orElseThrow();
    }

    private static String cookiePair(HttpHeaders headers, String cookiePrefix) {
        return headers.getOrEmpty(HttpHeaders.SET_COOKIE).stream()
                .filter(value -> value.startsWith(cookiePrefix))
                .findFirst()
                .map(value -> value.substring(0, value.indexOf(';')))
                .orElseThrow();
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    private record SessionResponse(
            String tokenName,
            String tokenValue,
            long expiresInSeconds) {
    }

    private record BrowserCsrf(HttpHeaders headers) {
    }

    private record BrowserLogin(String sessionCookie) {
    }

    private record ApiError(
            String code,
            String message,
            String traceId,
            Map<String, Object> details) {
    }

    private record TenantLifecycleResponse(
            String tenantId,
            String tenantCode,
            String displayName,
            String initialAdministratorSubjectId,
            String status,
            long version) {
    }
}
