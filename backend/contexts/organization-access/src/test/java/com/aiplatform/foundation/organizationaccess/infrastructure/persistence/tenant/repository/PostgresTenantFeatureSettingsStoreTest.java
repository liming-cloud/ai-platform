package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantFeature;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantFeatureSettings;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantFeatureSettingsService;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantFeatureSettingsStore;
import com.aiplatform.foundation.security.identity.TenantFeatureAvailability;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.EnumMap;
import java.util.UUID;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** 用真实 PostgreSQL 检查租户功能版本条件和跨租户隔离。 */
@SpringBootTest(classes = PostgresTenantFeatureSettingsStoreTest.TestApplication.class,
        properties = {"foundation.organization-access.enabled=false",
                "foundation.organization-access.feature-read.enabled=true"})
@Testcontainers
class PostgresTenantFeatureSettingsStoreTest {
    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-alpine3.24")
            .withDatabaseName("foundation_test").withUsername("foundation")
            .withPassword(UUID.randomUUID().toString());

    @Autowired private TenantFeatureSettingsStore store;
    @Autowired private TenantFeatureAvailability reader;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TenantFeatureSettingsService service;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/organization-access");
    }

    @BeforeEach
    void tenants() {
        jdbc.update("DELETE FROM organization_access.tenant");
        for (long id : new long[]{201L, 202L}) {
            jdbc.update("INSERT INTO organization_access.tenant (tenant_id, tenant_code, display_name, status) VALUES (?, ?, '功能测试', 'DISABLED')", id, "feature-" + id);
            jdbc.update("""
                    INSERT INTO organization_access.tenant_feature_settings
                        (tenant_id, knowledge_management, model_governance, assistant_delivery,
                         evaluation_governance, assistant_selection, conversation, own_history,
                         authorized_citations, version)
                    VALUES (?, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, 0)
                    """, id);
        }
    }

    @Test
    void 只更新目标租户且旧版本不能覆盖() {
        var initial = store.find(201L).orElseThrow();
        assertTrue(initial.allows(TenantFeature.KNOWLEDGE_MANAGEMENT));
        var features = new EnumMap<>(initial.availableFeatures());
        features.put(TenantFeature.KNOWLEDGE_MANAGEMENT, false);
        var changed = new TenantFeatureSettings(new TenantId(201L), features, 1L);
        assertTrue(store.replace(changed, 0L));
        assertFalse(store.replace(changed, 0L));
        assertFalse(store.find(201L).orElseThrow().allows(TenantFeature.KNOWLEDGE_MANAGEMENT));
        assertEquals(1L, store.find(201L).orElseThrow().version());
        assertTrue(store.find(202L).orElseThrow().allows(TenantFeature.KNOWLEDGE_MANAGEMENT));
    }

    @Test
    void 会话撤销失败时数据库更新必须回滚() {
        var initial = store.find(201L).orElseThrow();
        var features = new java.util.HashMap<>(
                com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto.TenantFeatureSettingsResult
                        .from(initial).availableFeatures());
        features.put("knowledgeManagement", false);
        var governor = new PlatformRequestContext(new SubjectId(4921825928971026436L),
                CredentialType.PERSON, AccessChannel.CONSOLE, 1L,
                Set.of(PlatformPermissions.SESSION_ACCESS, PlatformPermissions.TENANT_GOVERNANCE_WRITE),
                "trc_feature_rollback");

        assertThrows(IllegalStateException.class, () -> service.update(governor, 201L, features, 0L));

        assertTrue(store.find(201L).orElseThrow().allows(TenantFeature.KNOWLEDGE_MANAGEMENT));
        assertEquals(0L, store.find(201L).orElseThrow().version());
    }

    @Test
    void 查询进程只读检查器以同一数据库事实拒绝关闭功能() {
        reader.requireAvailable(new TenantId(201L), "knowledgeManagement");
        var current = store.find(201L).orElseThrow();
        var values = new EnumMap<>(current.availableFeatures());
        values.put(TenantFeature.KNOWLEDGE_MANAGEMENT, false);
        assertTrue(store.replace(new TenantFeatureSettings(new TenantId(201L), values, 1), 0));

        assertThrows(com.aiplatform.foundation.shared.kernel.error.ApplicationException.class,
                () -> reader.requireAvailable(new TenantId(201L), "knowledgeManagement"));
        reader.requireAvailable(new TenantId(202L), "knowledgeManagement");
        assertThrows(com.aiplatform.foundation.shared.kernel.error.ApplicationException.class,
                () -> reader.requireAvailable(new TenantId(203L), "knowledgeManagement"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
        @Bean
        TenantFeatureSettingsService tenantFeatureSettingsService(TenantFeatureSettingsStore store) {
            return new TenantFeatureSettingsService(store, tenantId -> { throw new IllegalStateException("撤销失败"); });
        }
    }
}
