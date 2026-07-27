package com.aiplatform.foundation.controlplane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.security.session.InvalidAuthenticatedSessionException;
import com.aiplatform.foundation.security.session.AuthenticatedSessionGateway;
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

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    private record SessionResponse(
            String tokenName,
            String tokenValue,
            long expiresInSeconds) {
    }

    private record ApiError(
            String code,
            String message,
            String traceId,
            Map<String, Object> details) {
    }
}
