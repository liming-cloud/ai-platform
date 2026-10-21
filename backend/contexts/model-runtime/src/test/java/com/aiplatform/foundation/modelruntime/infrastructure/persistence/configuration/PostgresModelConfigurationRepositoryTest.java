package com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.modelruntime.application.configuration.exception.ControlledModelCatalogConflictException;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntry;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfiguration;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationAccessGrant;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationAccessGrantId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationScope;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelRuntimeCapabilities;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationRepository;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationAccessRepository;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeploymentId;
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
        jdbc.execute("TRUNCATE TABLE model_runtime.platform_provider_connection CASCADE");
        insertVerifiedConnection(610000000000000001L, 2L);
        insertPublishedDeployment(
                620000000000000001L,
                3L,
                610000000000000001L,
                2L,
                Instant.parse("2030-07-28T00:00:00Z"));
        jdbc.update("""
                INSERT INTO model_runtime.controlled_model_catalog_entry
                (id, provider_kind, endpoint, model_code, model_type, context_window, output_limit,
                 embedding_dimension, source_platform_deployment_id, source_platform_deployment_revision,
                 status, version, created_at)
                VALUES (3001, 'OPENAI_COMPATIBLE', 'https://example.com/v1', 'test-chat-model', 'CHAT', 32768, 4096,
                        NULL, 620000000000000001, 3, 'PUBLISHED', 1, ?)
                """, Timestamp.from(Instant.parse("2026-07-28T00:00:00Z")));
    }

    @Test
    void 保存租户配置并读取已发布目录() {
        var catalog = repository.findCatalogEntry(new ControlledModelCatalogEntryId(3001L)).orElseThrow();
        assertEquals("test-chat-model", catalog.modelCode());
        assertEquals(4_096, catalog.capabilities().outputLimit());
        repository.save(ModelConfiguration.create(
                new ModelConfigurationId(4001L), ModelConfigurationScope.TENANT,
                new TenantId(2001L), null, "租户模型", catalog.id(),
                Instant.parse("2026-07-28T00:00:00Z")));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM model_runtime.model_configuration", Integer.class));
        var configuration = repository.findConfiguration(new ModelConfigurationId(4001L)).orElseThrow();
        assertEquals(ModelConfigurationScope.TENANT, configuration.scope());
        assertEquals(2001L, configuration.tenantId().value());
        repository.updateConfiguration(configuration.activate());
        assertEquals("ACTIVE", repository.findConfiguration(new ModelConfigurationId(4001L))
                .orElseThrow().status().name());
    }

    @Test
    void 历史已发布状态没有完整供应事实时不能回退旧全局目录() {
        jdbc.update("""
                INSERT INTO model_runtime.controlled_model_catalog_entry
                (id, provider_kind, endpoint, model_code, model_type, context_window, output_limit,
                 embedding_dimension, status, version, created_at)
                VALUES (3004, 'OPENAI_COMPATIBLE', 'https://example.com/v1', 'draft-chat', 'CHAT', 32768, 4096,
                        NULL, 'DRAFT', 0, ?)
                """, Timestamp.from(Instant.parse("2026-07-28T00:00:01Z")));
        jdbc.update("""
                INSERT INTO model_runtime.controlled_model_catalog_entry
                (id, provider_kind, endpoint, model_code, model_type, context_window, output_limit,
                 embedding_dimension, source_platform_deployment_id, source_platform_deployment_revision,
                 status, version, created_at)
                VALUES (3005, 'OPENAI_COMPATIBLE', 'https://example.com/v1', 'stale-revision', 'CHAT', 32768, 4096,
                        NULL, 620000000000000001, 2, 'PUBLISHED', 1, ?)
                """, Timestamp.from(Instant.parse("2026-07-28T00:00:02Z")));
        insertPublishedDeployment(
                620000000000000002L,
                1L,
                610000000000000001L,
                2L,
                Instant.parse("2020-07-28T00:00:00Z"));
        jdbc.update("""
                INSERT INTO model_runtime.controlled_model_catalog_entry
                (id, provider_kind, endpoint, model_code, model_type, context_window, output_limit,
                 embedding_dimension, source_platform_deployment_id, source_platform_deployment_revision,
                 status, version, created_at)
                VALUES (3006, 'OPENAI_COMPATIBLE', 'https://example.com/v1', 'expired-verification', 'CHAT', 32768, 4096,
                        NULL, 620000000000000002, 1, 'PUBLISHED', 1, ?)
                """, Timestamp.from(Instant.parse("2026-07-28T00:00:03Z")));
        insertVerifiedConnection(610000000000000002L, 3L);
        insertPublishedDeployment(
                620000000000000003L,
                1L,
                610000000000000002L,
                2L,
                Instant.parse("2030-07-28T00:00:00Z"));
        jdbc.update("""
                INSERT INTO model_runtime.controlled_model_catalog_entry
                (id, provider_kind, endpoint, model_code, model_type, context_window, output_limit,
                 embedding_dimension, source_platform_deployment_id, source_platform_deployment_revision,
                 status, version, created_at)
                VALUES (3007, 'OPENAI_COMPATIBLE', 'https://example.com/v1', 'revised-connection', 'CHAT', 32768, 4096,
                        NULL, 620000000000000003, 1, 'PUBLISHED', 1, ?)
                """, Timestamp.from(Instant.parse("2026-07-28T00:00:04Z")));

        var page = repository.findPublishedCatalogPage(new TenantId(2001L), 1, 20);

        assertEquals(0, page.totalElements());
        assertEquals(java.util.List.of(), page.items().stream()
                .map(value -> value.id().value())
                .toList());
    }

    private void insertVerifiedConnection(long id, long version) {
        jdbc.update("""
                INSERT INTO model_runtime.platform_provider_connection
                (id, scope, provider_kind, display_name, endpoint, region, authentication_type,
                 credential_reference, status, version, last_probe_succeeded, last_probe_code,
                 last_probed_at, created_at)
                VALUES (?, 'PLATFORM', 'DETERMINISTIC', ?, 'https://example.com/v1', 'local', 'API_KEY',
                        ?, 'VERIFIED', ?, TRUE, 'ok', ?, ?)
                """, id, "平台连接-" + id, "secret://platform-provider-connection/" + id, version,
                Timestamp.from(Instant.parse("2026-07-28T00:00:00Z")),
                Timestamp.from(Instant.parse("2026-07-28T00:00:00Z")));
    }

    private void insertPublishedDeployment(
            long id,
            long version,
            long connectionId,
            long connectionRevision,
            Instant verificationExpiresAt) {
        jdbc.update("""
                INSERT INTO model_runtime.platform_model_deployment
                (id, scope, platform_provider_connection_id, platform_provider_connection_revision,
                 model_code, display_name, model_type, capabilities, context_window, output_limit,
                 embedding_dimension, last_verification_id, verification_expires_at, status, version, created_at)
                VALUES (?, 'PLATFORM', ?, ?, ?, ?, 'CHAT', ARRAY['CHAT'], 32768, 4096,
                        NULL, NULL, NULL, 'DRAFT', ?, ?)
                """, id, connectionId, connectionRevision, "model-" + id, "平台部署-" + id, version,
                Timestamp.from(Instant.parse("2026-07-28T00:00:00Z")));
        var verificationId = id + 10_000L;
        jdbc.update("""
                INSERT INTO model_runtime.platform_model_capability_verification
                (id, platform_model_deployment_id, deployment_revision, platform_provider_connection_id,
                 platform_provider_connection_revision, model_code, declared_capabilities, observed_capabilities,
                 succeeded, result_code, elapsed_millis, probed_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ARRAY['CHAT'], ARRAY['CHAT'], TRUE, 'ok', 1, ?, ?)
                """, verificationId, id, version, connectionId, connectionRevision, "model-" + id,
                Timestamp.from(verificationExpiresAt.minusSeconds(60)), Timestamp.from(verificationExpiresAt));
        jdbc.update("""
                UPDATE model_runtime.platform_model_deployment
                SET last_verification_id = ?, verification_expires_at = ?, status = 'PUBLISHED'
                WHERE id = ?
                """, verificationId, Timestamp.from(verificationExpiresAt), id);
    }

    @Test
    void 保存并发布受控目录条目时使用乐观版本更新() {
        var deploymentId = new PlatformModelDeploymentId(620000000000000008L);
        var draft = ControlledModelCatalogEntry.createFromPlatformDeployment(
                new ControlledModelCatalogEntryId(3002L),
                deploymentId,
                3L,
                com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind.OPENAI_COMPATIBLE,
                java.net.URI.create("https://example.com/v1"),
                "qwen-embedding", com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType.EMBEDDING,
                ModelRuntimeCapabilities.embedding(8_192, 1_024),
                Instant.parse("2026-07-28T00:00:00Z"));
        repository.saveCatalogEntry(draft);
        repository.updateCatalogEntry(draft.publish());

        var published = repository.findCatalogEntry(new ControlledModelCatalogEntryId(3002L)).orElseThrow();
        assertEquals("PUBLISHED", published.status().name());
        assertEquals(1L, published.version());
        assertEquals(1_024, published.capabilities().embeddingDimension());
        assertEquals(deploymentId, published.sourceDeploymentId());
        assertEquals(3L, published.sourceDeploymentRevision());
    }

    @Test
    void 相同平台部署修订的第二份目录返回稳定冲突而不是数据库异常() {
        var duplicate = ControlledModelCatalogEntry.createFromPlatformDeployment(
                new ControlledModelCatalogEntryId(3008L),
                new PlatformModelDeploymentId(620000000000000001L),
                3L,
                com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind.OPENAI_COMPATIBLE,
                java.net.URI.create("https://example.com/v1"),
                "test-chat-model",
                com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType.CHAT,
                ModelRuntimeCapabilities.chat(32_768, 4_096),
                Instant.parse("2026-07-28T00:00:00Z"));

        assertThrows(ControlledModelCatalogConflictException.class,
                () -> repository.saveCatalogEntry(duplicate));
    }

    @Test
    void 保存确定性目录快照时允许受控内存端点() {
        var entry = ControlledModelCatalogEntry.createFromPlatformDeployment(
                new ControlledModelCatalogEntryId(3008L),
                new PlatformModelDeploymentId(620000000000000009L),
                3L,
                com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind.DETERMINISTIC,
                java.net.URI.create("memory://deterministic"),
                "deterministic-embedding",
                com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType.EMBEDDING,
                ModelRuntimeCapabilities.embedding(8_192, 1_024),
                Instant.parse("2026-08-14T00:00:00Z"));

        repository.saveCatalogEntry(entry);

        assertEquals("memory://deterministic", repository.findCatalogEntry(entry.id()).orElseThrow().endpoint().toString());
    }

    @Test
    void 缺少能力快照的历史目录条目必须失效关闭() {
        jdbc.update("""
                INSERT INTO model_runtime.controlled_model_catalog_entry
                (id, provider_kind, endpoint, model_code, model_type, status, version, created_at)
                VALUES (3003, 'OPENAI_COMPATIBLE', 'https://example.com/v1', 'legacy-chat', 'CHAT', 'PUBLISHED', 1, ?)
                """, Timestamp.from(Instant.parse("2026-07-28T00:00:00Z")));

        assertThrows(IllegalStateException.class, () ->
                repository.findCatalogEntry(new ControlledModelCatalogEntryId(3003L)));
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
        insertHistoricalConfigurationGrant(active);
        assertEquals(true, accessRepository.hasActiveGrant(
                new ModelConfigurationId(4001L), new TenantId(2001L),
                new com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationOwnerId(6001L)));

        accessRepository.update(active.revoke(Instant.parse("2026-07-28T00:01:00Z")));
        assertEquals(false, accessRepository.hasActiveGrant(
                new ModelConfigurationId(4001L), new TenantId(2001L),
                new com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationOwnerId(6001L)));
    }

    @Test
    void 分页只返回当前成员的个人和租户配置以及已授权的平台配置() {
        var tenant = new TenantId(2001L);
        var member = new com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationOwnerId(6001L);
        var catalog = new ControlledModelCatalogEntryId(3001L);
        var createdAt = Instant.parse("2026-07-28T00:00:00Z");
        repository.save(ModelConfiguration.create(
                new ModelConfigurationId(4001L), ModelConfigurationScope.PERSONAL,
                tenant, member, "我的模型", catalog, createdAt));
        repository.save(ModelConfiguration.create(
                new ModelConfigurationId(4002L), ModelConfigurationScope.PERSONAL,
                tenant,
                new com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationOwnerId(6002L),
                "他人模型", catalog, createdAt));
        repository.save(ModelConfiguration.create(
                new ModelConfigurationId(4003L), ModelConfigurationScope.TENANT,
                tenant, null, "租户模型", catalog, createdAt));
        repository.save(ModelConfiguration.create(
                new ModelConfigurationId(4004L), ModelConfigurationScope.PLATFORM,
                null, null, "已授权平台模型", catalog, createdAt));
        repository.save(ModelConfiguration.create(
                new ModelConfigurationId(4005L), ModelConfigurationScope.PLATFORM,
                null, null, "未授权平台模型", catalog, createdAt));
        insertHistoricalConfigurationGrant(ModelConfigurationAccessGrant.grant(
                new ModelConfigurationAccessGrantId(5001L), new ModelConfigurationId(4004L),
                tenant, null, createdAt));

        var page = repository.findVisiblePage(tenant, member, 1, 20);

        assertEquals(3, page.totalElements());
        assertEquals(
                java.util.Set.of(4001L, 4003L, 4004L),
                page.items().stream().map(value -> value.id().value()).collect(java.util.stream.Collectors.toSet()));
    }

    /** 历史关系只验旧存储读取与撤销，不把它补造为新供应授权；正式提交先证明拒绝。 */
    private void insertHistoricalConfigurationGrant(ModelConfigurationAccessGrant grant) {
        assertThrows(IllegalStateException.class, () -> accessRepository.save(grant));
        jdbc.update("INSERT INTO model_runtime.model_configuration_access_grant (id, model_configuration_id, grantee_tenant_id, grantee_membership_id, status, version, created_at, revoked_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                grant.id().value(), grant.modelConfigurationId().value(), grant.granteeTenantId().value(),
                grant.granteeMembershipId() == null ? null : grant.granteeMembershipId().value(), grant.status().name(),
                grant.version(), Timestamp.from(grant.createdAt()), grant.revokedAt() == null ? null : Timestamp.from(grant.revokedAt()));
    }

    @SpringBootConfiguration @EnableAutoConfiguration
    @MapperScan("com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration")
    static class TestApplication {
        @Bean com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor pagination() {
            var result = new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();
            result.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor(com.baomidou.mybatisplus.annotation.DbType.POSTGRE_SQL));
            return result;
        }
        @Bean ModelConfigurationRepository repository(
                ControlledModelCatalogEntryMapper catalogMapper,
                ModelConfigurationMapper mapper, org.springframework.transaction.PlatformTransactionManager transactions) {
            return new PostgresModelConfigurationRepository(catalogMapper, mapper,
                    () -> new com.aiplatform.foundation.security.identity.TenantActivityFacts() {
                        public void requireActive(java.util.Set<Long> ids) { if (!ids.equals(java.util.Set.of(2001L))) throw new IllegalArgumentException("合成活动租户不匹配"); }
                        public boolean isActive(long id) { return id == 2001L; }
                    }, () -> (tenant, feature) -> { if (tenant.value() != 2001L || !feature.equals("modelGovernance")) throw new IllegalStateException("合成功能不可用"); }, transactions);
        }
        @Bean ModelConfigurationAccessRepository accessRepository(ModelConfigurationAccessMapper mapper,
                ModelConfigurationRepository repository, org.springframework.transaction.PlatformTransactionManager transactions) {
            return new PostgresModelConfigurationAccessRepository(mapper, repository, transactions);
        }
    }
}
