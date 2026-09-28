package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantQuota;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantQuotaMapper;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
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

/** 真实 PostgreSQL 验证配额版本条件写入及租户隔离。 */
@SpringBootTest(classes = PostgresTenantQuotaSettingsStoreTest.TestApplication.class,
        properties = "foundation.organization-access.enabled=false")
@Testcontainers
class PostgresTenantQuotaSettingsStoreTest {

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.4-alpine3.24")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword(UUID.randomUUID().toString());

    @Autowired private PostgresTenantQuotaSettingsStore store;
    @Autowired private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/organization-access");
    }

    @BeforeEach
    void tenants() {
        jdbc.update("DELETE FROM organization_access.tenant_quota");
        jdbc.update("DELETE FROM organization_access.tenant");
        for (long id : new long[]{201L, 202L}) {
            jdbc.update("INSERT INTO organization_access.tenant (tenant_id, tenant_code, display_name, status) VALUES (?, ?, '配额测试', 'DISABLED')", id, "quota-" + id);
            jdbc.update("""
                    INSERT INTO organization_access.tenant_quota
                        (tenant_id, knowledge_bases, documents_per_knowledge_base,
                         bytes_per_knowledge_base, max_file_bytes, max_text_bytes, version)
                    VALUES (?, 3, 1000, 10000000000, 50000000, 1048576, 0)
                    """, id);
        }
    }

    @Test
    void 仅更新目标租户且旧版本不能覆盖新配置() {
        assertEquals(3, store.find(201L).orElseThrow().knowledgeBases());
        var first = new TenantQuota(new TenantId(201L), 4, 1000,
                10_000_000_000L, 50_000_000L, 1_048_576L, 1L);
        assertTrue(store.replace(first, 0L));
        assertFalse(store.replace(first, 0L));
        assertEquals(4, store.find(201L).orElseThrow().knowledgeBases());
        assertEquals(1L, store.find(201L).orElseThrow().version());
        assertEquals(3, store.find(202L).orElseThrow().knowledgeBases());
        assertEquals(0L, store.find(202L).orElseThrow().version());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackageClasses = TenantQuotaMapper.class)
    static class TestApplication {
        @Bean
        PostgresTenantQuotaSettingsStore tenantQuotaSettingsStore(TenantQuotaMapper mapper) {
            return new PostgresTenantQuotaSettingsStore(mapper);
        }
    }
}
