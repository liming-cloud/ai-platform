package com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntry;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfiguration;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationAccessGrant;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationAccessGrantId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationScope;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationRepository;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationAccessRepository;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.sql.Timestamp;
import java.time.Instant;
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

@SpringBootTest(classes = PostgresModelConfigurationRepositoryTest.TestApplication.class)
@Testcontainers
class PostgresModelConfigurationRepositoryTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:0.8.5-pg18-trixie")
            .withDatabaseName("foundation_test").withUsername("foundation").withPassword("foundation_test_only");

    @Autowired private ModelConfigurationRepository repository;
    @Autowired private ModelConfigurationAccessRepository accessRepository;
    @Autowired private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/model-runtime");
    }

    @BeforeEach
    void prepareCatalog() {
        jdbc.update("DELETE FROM model_runtime.model_configuration_access_grant");
        jdbc.update("DELETE FROM model_runtime.model_configuration_credential");
        jdbc.update("DELETE FROM model_runtime.model_configuration");
        jdbc.update("DELETE FROM model_runtime.controlled_model_catalog_entry");
        jdbc.update("""
                INSERT INTO model_runtime.controlled_model_catalog_entry
                (id, provider_kind, endpoint, model_code, model_type, status, version, created_at)
                VALUES (3001, 'OPENAI_COMPATIBLE', 'https://example.com/v1', 'qwen-plus', 'CHAT', 'PUBLISHED', 1, ?)
                """, Timestamp.from(Instant.parse("2026-07-28T00:00:00Z")));
    }

    @Test
    void 保存租户配置并读取已发布目录() {
        var catalog = repository.findCatalogEntry(new ControlledModelCatalogEntryId(3001L)).orElseThrow();
        assertEquals("qwen-plus", catalog.modelCode());
        repository.save(ModelConfiguration.create(
                new ModelConfigurationId(4001L), ModelConfigurationScope.TENANT,
                new TenantId(2001L), null, "租户模型", catalog.id(),
                Instant.parse("2026-07-28T00:00:00Z")));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM model_runtime.model_configuration", Integer.class));
        var configuration = repository.findConfiguration(new ModelConfigurationId(4001L)).orElseThrow();
        assertEquals(ModelConfigurationScope.TENANT, configuration.scope());
        assertEquals(2001L, configuration.tenantId().value());
    }

    @Test
    void 保存并发布受控目录条目时使用乐观版本更新() {
        var draft = ControlledModelCatalogEntry.create(
                new ControlledModelCatalogEntryId(3002L),
                com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind.OPENAI_COMPATIBLE,
                java.net.URI.create("https://example.com/v1"),
                "qwen-embedding", com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType.EMBEDDING,
                Instant.parse("2026-07-28T00:00:00Z"));
        repository.saveCatalogEntry(draft);
        repository.updateCatalogEntry(draft.publish());

        var published = repository.findCatalogEntry(new ControlledModelCatalogEntryId(3002L)).orElseThrow();
        assertEquals("PUBLISHED", published.status().name());
        assertEquals(1L, published.version());
    }

    @Test
    void 活动授权撤销后不再被授权查询命中() {
        repository.save(ModelConfiguration.create(
                new ModelConfigurationId(4001L), ModelConfigurationScope.PLATFORM,
                null, null, "平台模型", new ControlledModelCatalogEntryId(3001L),
                Instant.parse("2026-07-28T00:00:00Z")));
        var active = ModelConfigurationAccessGrant.grant(
                new ModelConfigurationAccessGrantId(5001L), new ModelConfigurationId(4001L),
                new TenantId(2001L), null, Instant.parse("2026-07-28T00:00:00Z"));
        accessRepository.save(active);
        assertEquals(true, accessRepository.hasActiveGrant(
                new ModelConfigurationId(4001L), new TenantId(2001L),
                new com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationOwnerId(6001L)));

        accessRepository.update(active.revoke(Instant.parse("2026-07-28T00:01:00Z")));
        assertEquals(false, accessRepository.hasActiveGrant(
                new ModelConfigurationId(4001L), new TenantId(2001L),
                new com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationOwnerId(6001L)));
    }

    @SpringBootConfiguration @EnableAutoConfiguration
    @MapperScan("com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration")
    static class TestApplication {
        @Bean ModelConfigurationRepository repository(ModelConfigurationMapper mapper) {
            return new PostgresModelConfigurationRepository(mapper);
        }
        @Bean ModelConfigurationAccessRepository accessRepository(ModelConfigurationAccessMapper mapper) {
            return new PostgresModelConfigurationAccessRepository(mapper);
        }
    }
}
