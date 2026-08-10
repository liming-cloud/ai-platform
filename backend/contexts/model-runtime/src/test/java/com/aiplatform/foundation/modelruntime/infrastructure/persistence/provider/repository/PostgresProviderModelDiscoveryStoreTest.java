package com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.modelruntime.application.provider.port.out.ProviderModelDiscoveryStore;
import com.aiplatform.foundation.modelruntime.application.deployment.port.out.ProviderModelDiscoveryLookup;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.DiscoveredModel;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderModelDiscovery;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderModelDiscoveryId;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderModelDiscoveryObservation;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(classes = PostgresProviderModelDiscoveryStoreTest.TestApplication.class)
@Testcontainers
class PostgresProviderModelDiscoveryStoreTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final ProviderConnectionId CONNECTION =
            new ProviderConnectionId(310000000000000001L);
    private static final Instant DISCOVERED_AT = Instant.parse("2026-08-10T03:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("pgvector/pgvector:0.8.5-pg18-trixie")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private ProviderModelDiscoveryStore store;

    @Autowired
    private ProviderModelDiscoveryLookup lookup;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/model-runtime");
    }

    @BeforeEach
    void 清理并准备供应商连接() {
        jdbc.execute("TRUNCATE TABLE model_runtime.provider_discovered_model, "
                + "model_runtime.provider_model_discovery");
        jdbc.update("DELETE FROM model_runtime.provider_connection_request");
        jdbc.update("DELETE FROM model_runtime.provider_connection");
        jdbc.update(
                """
                INSERT INTO model_runtime.provider_connection (
                    tenant_id, id, provider_kind, display_name, endpoint, region,
                    authentication_type, credential_reference, status, version,
                    last_probe_succeeded, last_probe_code, last_probed_at, created_at
                ) VALUES (?, ?, 'DETERMINISTIC', '演示连接', 'memory://deterministic', '本地',
                    'API_KEY', 'secret://demo/provider', 'VERIFIED', 1,
                    TRUE, 'DETERMINISTIC_OK', ?, ?)
                """,
                TENANT.value(),
                CONNECTION.value(),
                Timestamp.from(DISCOVERED_AT),
                Timestamp.from(DISCOVERED_AT));
    }

    @Test
    void 应原子写入不可变发现事实和候选模型明细() {
        store.record(discovery(340000000000000001L, Set.of(
                new DiscoveredModel(
                        "deterministic-chat", ModelType.CHAT, Set.of(ModelCapability.CHAT)),
                new DiscoveredModel(
                        "deterministic-embedding", ModelType.EMBEDDING,
                        Set.of(ModelCapability.EMBEDDING)))));

        assertEquals(1, count("model_runtime.provider_model_discovery"));
        assertEquals(2, count("model_runtime.provider_discovered_model"));
    }

    @Test
    void 候选明细写入失败时不得留下孤立发现根() {
        var conflictingModels = Set.of(
                new DiscoveredModel(
                        "duplicate-chat", ModelType.CHAT, Set.of(ModelCapability.CHAT)),
                new DiscoveredModel(
                        "duplicate-chat", ModelType.CHAT,
                        Set.of(ModelCapability.CHAT, ModelCapability.STREAMING_CHAT)));

        assertThrows(DataIntegrityViolationException.class,
                () -> store.record(discovery(340000000000000002L, conflictingModels)));

        assertEquals(0, count("model_runtime.provider_model_discovery"));
        assertEquals(0, count("model_runtime.provider_discovered_model"));
    }

    @Test
    void 只返回当前连接修订未过期的精确候选模型() {
        store.record(discovery(340000000000000003L, Set.of(
                new DiscoveredModel(
                        "deterministic-chat", ModelType.CHAT, Set.of(ModelCapability.CHAT)))));

        assertEquals(
                "deterministic-chat",
                lookup.findCurrentCandidate(
                                TENANT,
                                CONNECTION,
                                1,
                                "deterministic-chat",
                                ModelType.CHAT,
                                DISCOVERED_AT.plusSeconds(1))
                        .orElseThrow()
                        .modelCode());
        assertEquals(
                0,
                lookup.findCurrentCandidate(
                                TENANT,
                                CONNECTION,
                                1,
                                "deterministic-chat",
                                ModelType.CHAT,
                                DISCOVERED_AT.plus(Duration.ofHours(25)))
                        .stream()
                        .count());
    }

    private static ProviderModelDiscovery discovery(long id, Set<DiscoveredModel> models) {
        return ProviderModelDiscovery.capture(
                new ProviderModelDiscoveryId(id),
                TENANT,
                CONNECTION,
                1,
                new ProviderModelDiscoveryObservation(
                        true,
                        models,
                        "DETERMINISTIC_DISCOVERY_OK",
                        Duration.ofMillis(10),
                        DISCOVERED_AT),
                DISCOVERED_AT.plus(Duration.ofHours(24)));
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean
        PostgresProviderModelDiscoveryStore providerModelDiscoveryStore(
                JdbcTemplate jdbc,
                PlatformTransactionManager transactions) {
            return new PostgresProviderModelDiscoveryStore(jdbc, transactions);
        }
    }
}
