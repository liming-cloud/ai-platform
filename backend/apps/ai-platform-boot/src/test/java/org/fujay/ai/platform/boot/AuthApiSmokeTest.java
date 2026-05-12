package org.fujay.ai.platform.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.username=ai_platform",
            "spring.datasource.password=ai_platform_dev",
            "spring.data.redis.host=localhost",
            "spring.data.redis.port=6379",
            "ai.platform.secret.master-key=test-master-key-change-me",
            "ai.platform.model.bootstrap.openai.enabled=false"
        })
class AuthApiSmokeTest {

    private static final String TEST_SCHEMA = "ai_platform_test_" + UUID.randomUUID().toString().replace("-", "");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void registerDatabaseProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () -> "jdbc:postgresql://localhost:5432/ai_platform?currentSchema=" + TEST_SCHEMA);
        registry.add("spring.flyway.schemas", () -> TEST_SCHEMA);
        registry.add("spring.flyway.default-schema", () -> TEST_SCHEMA);
        registry.add("spring.flyway.create-schemas", () -> "true");
    }

    @Test
    void corsPreflightAllowsTenantStatusPatchFromConsole() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin("http://localhost:5173");
        headers.setAccessControlRequestMethod(HttpMethod.PATCH);

        ResponseEntity<Void> response = restTemplate.exchange(
                url("/api/admin/tenants/1/status"), HttpMethod.OPTIONS, new HttpEntity<>(headers), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo("http://localhost:5173");
        assertThat(response.getHeaders().getAccessControlAllowMethods()).contains(HttpMethod.PATCH);
    }

    @Test
    void authFlowUsesRealSessionAndTenantBoundary() {
        ResponseEntity<JsonNode> loginResponse = restTemplate.postForEntity(
                url("/api/auth/login"),
                Map.of(
                        "tenantCode", "demo",
                        "username", "admin",
                        "password", "AiPlatform2026!"),
                JsonNode.class);

        assertSuccess(loginResponse);
        JsonNode loginData = loginResponse.getBody().path("data");
        assertThat(loginData.path("tokenName").asText()).isEqualTo("satoken");
        assertThat(loginData.path("tokenValue").asText()).isNotBlank();
        assertThat(loginData.path("tenantId").asText()).matches("\\d+");
        assertThat(loginData.path("tenantCode").asText()).isEqualTo("demo");

        HttpHeaders headers = new HttpHeaders();
        headers.set(loginData.path("tokenName").asText(), loginData.path("tokenValue").asText());
        HttpEntity<Void> authenticated = new HttpEntity<>(headers);

        ResponseEntity<JsonNode> meResponse =
                restTemplate.exchange(url("/api/auth/me"), HttpMethod.GET, authenticated, JsonNode.class);
        assertSuccess(meResponse);
        assertThat(meResponse.getBody().path("data").path("username").asText()).isEqualTo("admin");
        assertThat(meResponse.getBody().path("data").path("roles").findValuesAsText("roleCode"))
                .contains("PLATFORM_ADMIN");
        assertThat(meResponse.getBody().path("data").path("permissions").findValuesAsText("permissionCode"))
                .contains(
                        "platform:tenant:write",
                        "rbac:permission:write",
                        "rbac:menu:write",
                        "model:platform-catalog:write",
                        "model:tenant-config:write",
                        "model:invocation:execute");
        assertThat(meResponse.getBody().path("data").path("menus").findValuesAsText("menuCode"))
                .contains("platform.tenants", "platform.roles", "platform.permissions", "platform.menus", "tenant.models");

        ResponseEntity<JsonNode> passwordChangeResponse = restTemplate.exchange(
                url("/api/auth/password"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of("oldPassword", "AiPlatform2026!", "newPassword", "AiPlatform2026!Changed"),
                        headers),
                JsonNode.class);
        assertSuccess(passwordChangeResponse);

        ResponseEntity<JsonNode> changedPasswordLoginResponse = restTemplate.postForEntity(
                url("/api/auth/login"),
                Map.of(
                        "tenantCode", "demo",
                        "username", "admin",
                        "password", "AiPlatform2026!Changed"),
                JsonNode.class);
        assertSuccess(changedPasswordLoginResponse);

        ResponseEntity<JsonNode> currentTenantResponse =
                restTemplate.exchange(url("/api/tenants/current"), HttpMethod.GET, authenticated, JsonNode.class);
        assertSuccess(currentTenantResponse);
        assertThat(currentTenantResponse.getBody().path("data").path("tenantName").asText()).isEqualTo("演示租户");

        ResponseEntity<JsonNode> adminTenantsResponse =
                restTemplate.exchange(url("/api/admin/tenants"), HttpMethod.GET, authenticated, JsonNode.class);
        assertSuccess(adminTenantsResponse);
        String tenantId = adminTenantsResponse.getBody().path("data").get(0).path("tenantId").asText();
        assertThat(adminTenantsResponse.getBody().path("data").get(0).path("tenantCode").asText()).isEqualTo("demo");

        ResponseEntity<JsonNode> disabledTenantResponse = restTemplate.exchange(
                url("/api/admin/tenants/" + tenantId + "/status"),
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("status", "DISABLED"), headers),
                JsonNode.class);
        assertSuccess(disabledTenantResponse);
        assertThat(disabledTenantResponse.getBody().path("data").path("status").asText()).isEqualTo("DISABLED");

        ResponseEntity<JsonNode> enabledTenantResponse = restTemplate.exchange(
                url("/api/admin/tenants/" + tenantId + "/status"),
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("status", "ENABLED"), headers),
                JsonNode.class);
        assertSuccess(enabledTenantResponse);
        assertThat(enabledTenantResponse.getBody().path("data").path("status").asText()).isEqualTo("ENABLED");

        ResponseEntity<JsonNode> createdTenantResponse = restTemplate.exchange(
                url("/api/admin/tenants"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "tenantCode", "acme",
                                "tenantName", "Acme 租户",
                                "adminUsername", "tenant_admin",
                                "adminDisplayName", "Acme 管理员",
                                "adminPassword", "Tenant2026!",
                                "status", "ENABLED"),
                        headers),
                JsonNode.class);
        assertSuccess(createdTenantResponse);
        String acmeTenantId = createdTenantResponse.getBody().path("data").path("tenantId").asText();
        assertThat(createdTenantResponse.getBody().path("data").path("tenantCode").asText()).isEqualTo("acme");
        assertThat(acmeTenantId).matches("\\d+");

        ResponseEntity<JsonNode> updatedTenantResponse = restTemplate.exchange(
                url("/api/admin/tenants/" + acmeTenantId),
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("tenantName", "Acme 企业租户", "status", "ENABLED"), headers),
                JsonNode.class);
        assertSuccess(updatedTenantResponse);
        assertThat(updatedTenantResponse.getBody().path("data").path("tenantName").asText()).isEqualTo("Acme 企业租户");

        ResponseEntity<JsonNode> acmeLoginResponse = restTemplate.postForEntity(
                url("/api/auth/login"),
                Map.of(
                        "tenantCode", "acme",
                        "username", "tenant_admin",
                        "password", "Tenant2026!"),
                JsonNode.class);
        assertSuccess(acmeLoginResponse);
        JsonNode acmeLoginData = acmeLoginResponse.getBody().path("data");
        HttpHeaders acmeHeaders = new HttpHeaders();
        acmeHeaders.set(acmeLoginData.path("tokenName").asText(), acmeLoginData.path("tokenValue").asText());
        ResponseEntity<JsonNode> acmeMeResponse =
                restTemplate.exchange(url("/api/auth/me"), HttpMethod.GET, new HttpEntity<>(acmeHeaders), JsonNode.class);
        assertSuccess(acmeMeResponse);
        assertThat(acmeMeResponse.getBody().path("data").path("menus").findValuesAsText("menuCode"))
                .contains("platform.tenants", "platform.roles", "platform.permissions", "platform.menus", "tenant.models");

        ResponseEntity<JsonNode> modelOptionsResponse =
                restTemplate.exchange(url("/api/model-providers/options"), HttpMethod.GET, authenticated, JsonNode.class);
        assertSuccess(modelOptionsResponse);
        assertThat(modelOptionsResponse.getBody().path("data").path("providerTypes").findValuesAsText("providerType"))
                .contains("OPENAI", "OPENAI_COMPATIBLE");
        assertThat(modelOptionsResponse.getBody().path("data").path("modelOptions").findValuesAsText("modelCode"))
                .contains("o3", "tencent/Hunyuan-MT-7B");

        ResponseEntity<JsonNode> platformCatalogResponse =
                restTemplate.exchange(url("/api/admin/model-catalog"), HttpMethod.GET, authenticated, JsonNode.class);
        assertSuccess(platformCatalogResponse);
        assertThat(platformCatalogResponse.getBody().path("data").path("providerTypes").findValuesAsText("providerType"))
                .contains("OPENAI_COMPATIBLE");

        ResponseEntity<JsonNode> platformProviderTypeResponse = restTemplate.exchange(
                url("/api/admin/model-catalog/provider-types"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "providerType", "OPENAI_COMPATIBLE",
                                "displayName", "硅基流动",
                                "defaultBaseUrl", "https://api.siliconflow.cn",
                                "status", "ENABLED",
                                "sortOrder", 30),
                        headers),
                JsonNode.class);
        assertSuccess(platformProviderTypeResponse);
        String providerTypeId = platformProviderTypeResponse.getBody().path("data").path("providerTypeId").asText();
        assertThat(platformProviderTypeResponse.getBody().path("data").path("displayName").asText()).isEqualTo("硅基流动");

        ResponseEntity<JsonNode> platformModelOptionResponse = restTemplate.exchange(
                url("/api/admin/model-catalog/model-options"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "providerTypeId", providerTypeId,
                                "modelCode", "Qwen/Qwen2.5-7B-Instruct",
                                "displayName", "Qwen2.5-7B 免费对话模型",
                                "capabilityType", "CHAT",
                                "contextWindow", 32768,
                                "maxOutputTokens", 2048,
                                "temperature", 0.2,
                                "topP", 0.7,
                                "status", "ENABLED",
                                "sortOrder", 31),
                        headers),
                JsonNode.class);
        assertSuccess(platformModelOptionResponse);
        assertThat(platformModelOptionResponse.getBody().path("data").path("modelCode").asText())
                .isEqualTo("Qwen/Qwen2.5-7B-Instruct");

        ResponseEntity<JsonNode> updatedModelOptionsResponse =
                restTemplate.exchange(url("/api/model-providers/options"), HttpMethod.GET, authenticated, JsonNode.class);
        assertSuccess(updatedModelOptionsResponse);
        assertThat(updatedModelOptionsResponse.getBody().path("data").path("providerTypes").findValuesAsText("displayName"))
                .contains("硅基流动");
        assertThat(updatedModelOptionsResponse.getBody().path("data").path("modelOptions").findValuesAsText("modelCode"))
                .contains("Qwen/Qwen2.5-7B-Instruct");

        ResponseEntity<JsonNode> rolesResponse =
                restTemplate.exchange(url("/api/admin/roles"), HttpMethod.GET, authenticated, JsonNode.class);
        assertSuccess(rolesResponse);
        JsonNode roleData = rolesResponse.getBody().path("data");
        String platformAdminRoleId = "";
        for (JsonNode roleNode : roleData.path("roles")) {
            if ("PLATFORM_ADMIN".equals(roleNode.path("roleCode").asText())) {
                platformAdminRoleId = roleNode.path("roleId").asText();
            }
        }
        assertThat(platformAdminRoleId).isNotBlank();
        assertThat(roleData.path("roles").get(0).path("permissions").findValuesAsText("permissionCode"))
                .contains(
                        "platform:tenant:write",
                        "rbac:permission:write",
                        "rbac:menu:write",
                        "model:platform-catalog:write",
                        "model:tenant-config:write",
                        "model:invocation:execute");
        assertThat(roleData.path("roles").get(0).path("menus").findValuesAsText("menuCode"))
                .contains("platform.tenants", "platform.roles", "platform.permissions", "platform.menus", "tenant.models");

        ResponseEntity<JsonNode> savedPermissionResponse = restTemplate.exchange(
                url("/api/admin/permissions"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "permissionCode", "report:dataset:read",
                                "permissionName", "查看报表数据集",
                                "status", "ENABLED"),
                        headers),
                JsonNode.class);
        assertSuccess(savedPermissionResponse);
        assertThat(savedPermissionResponse.getBody().path("data").path("permissionCode").asText())
                .isEqualTo("report:dataset:read");

        ResponseEntity<JsonNode> savedMenuResponse = restTemplate.exchange(
                url("/api/admin/menus"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "menuCode", "tenant.reports",
                                "menuName", "报表中心",
                                "parentCode", "tenant.root",
                                "routePath", "/tenant/reports",
                                "componentKey", "TenantReports",
                                "platformScope", "TENANT",
                                "sortOrder", 30,
                                "status", "ENABLED"),
                        headers),
                JsonNode.class);
        assertSuccess(savedMenuResponse);
        assertThat(savedMenuResponse.getBody().path("data").path("menuCode").asText()).isEqualTo("tenant.reports");

        ResponseEntity<JsonNode> savedRoleResponse = restTemplate.exchange(
                url("/api/admin/roles"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "roleCode", "REPORT_VIEWER",
                                "roleName", "报表查看员",
                                "status", "ENABLED"),
                        headers),
                JsonNode.class);
        assertSuccess(savedRoleResponse);
        String reportRoleId = savedRoleResponse.getBody().path("data").path("roleId").asText();
        assertThat(savedRoleResponse.getBody().path("data").path("roleCode").asText()).isEqualTo("REPORT_VIEWER");

        ResponseEntity<JsonNode> reportRoleBindingResponse = restTemplate.exchange(
                url("/api/admin/roles/" + reportRoleId + "/bindings"),
                HttpMethod.PUT,
                new HttpEntity<>(
                        Map.of(
                                "permissionCodes", List.of("report:dataset:read"),
                                "menuCodes", List.of("tenant.root", "tenant.reports")),
                        headers),
                JsonNode.class);
        assertSuccess(reportRoleBindingResponse);
        assertThat(reportRoleBindingResponse.getBody().path("data").path("permissions").findValuesAsText("permissionCode"))
                .containsExactly("report:dataset:read");
        assertThat(reportRoleBindingResponse.getBody().path("data").path("menus").findValuesAsText("menuCode"))
                .contains("tenant.reports");

        ResponseEntity<JsonNode> updatedRolesResponse =
                restTemplate.exchange(url("/api/admin/roles"), HttpMethod.GET, authenticated, JsonNode.class);
        assertSuccess(updatedRolesResponse);
        assertThat(updatedRolesResponse.getBody().path("data").path("roles").findValuesAsText("roleCode"))
                .contains("REPORT_VIEWER");
        assertThat(updatedRolesResponse.getBody().path("data").path("permissions").findValuesAsText("permissionCode"))
                .contains("report:dataset:read");
        assertThat(updatedRolesResponse.getBody().path("data").path("menus").findValuesAsText("menuCode"))
                .contains("tenant.reports");

        ResponseEntity<JsonNode> roleBindingResponse = restTemplate.exchange(
                url("/api/admin/roles/" + platformAdminRoleId + "/bindings"),
                HttpMethod.PUT,
                new HttpEntity<>(
                        Map.of(
                                "permissionCodes",
                                List.of(
                                        "platform:tenant:read",
                                        "platform:tenant:write",
                                        "rbac:role:write",
                                        "rbac:permission:write",
                                        "rbac:menu:write",
                                        "model:tenant-config:read",
                                        "model:tenant-config:write",
                                        "model:invocation:execute"),
                                "menuCodes",
                                List.of(
                                        "platform.root",
                                        "platform.tenants",
                                        "platform.roles",
                                        "platform.permissions",
                                        "platform.menus",
                                        "tenant.root",
                                        "tenant.models")),
                        headers),
                JsonNode.class);
        assertThat(roleBindingResponse.getStatusCode())
                .as(() -> String.valueOf(roleBindingResponse.getBody()))
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(roleBindingResponse.getBody().path("code").asText()).isEqualTo("COMMON-400");

        ResponseEntity<JsonNode> protectedAdminRolesResponse =
                restTemplate.exchange(url("/api/admin/roles"), HttpMethod.GET, authenticated, JsonNode.class);
        assertSuccess(protectedAdminRolesResponse);
        assertThat(protectedAdminRolesResponse.getBody().path("data").path("roles").findValuesAsText("roleCode"))
                .contains("PLATFORM_ADMIN");

        ResponseEntity<JsonNode> providerResponse = restTemplate.exchange(
                url("/api/model-providers"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "providerName", "OpenAI",
                                "providerType", "OPENAI",
                                "baseUrl", "官方默认",
                                "apiKey", "sk-test-secret",
                                "budgetUsd", 5,
                                "models", List.of(Map.of(
                                        "modelCode", "o3",
                                        "displayName", "o3",
                                        "capabilityType", "CHAT",
                                        "contextWindow", 200000,
                                        "maxOutputTokens", 1024,
                                        "reasoningEffort", "medium"))),
                        headers),
                JsonNode.class);
        assertSuccess(providerResponse);
        JsonNode providerData = providerResponse.getBody().path("data");
        assertThat(providerData.path("providerName").asText()).isEqualTo("OpenAI");
        assertThat(providerData.path("apiKeyLast4").asText()).isEqualTo("cret");
        assertThat(providerData.toString()).doesNotContain("sk-test-secret");

        ResponseEntity<JsonNode> providerListResponse =
                restTemplate.exchange(url("/api/model-providers"), HttpMethod.GET, authenticated, JsonNode.class);
        assertSuccess(providerListResponse);
        assertThat(providerListResponse.getBody().path("data").get(0).path("models").get(0).path("modelCode").asText())
                .isEqualTo("o3");

        ResponseEntity<JsonNode> forbiddenResponse = restTemplate.exchange(
                url("/api/tenants/1"), HttpMethod.GET, authenticated, JsonNode.class);
        assertThat(forbiddenResponse.getStatusCode())
                .as(() -> String.valueOf(forbiddenResponse.getBody()))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forbiddenResponse.getBody().path("code").asText()).isEqualTo("COMMON-403");
        assertThat(forbiddenResponse.getBody().path("message").asText()).isEqualTo("没有权限访问该资源");

        ResponseEntity<JsonNode> logoutResponse =
                restTemplate.exchange(url("/api/auth/logout"), HttpMethod.POST, authenticated, JsonNode.class);
        assertSuccess(logoutResponse);
    }

    private void assertSuccess(ResponseEntity<JsonNode> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("code").asText()).isEqualTo("0");
        assertThat(response.getBody().path("message").asText()).isEqualTo("成功");
        assertThat(response.getBody().path("traceId").asText()).isNotBlank();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
