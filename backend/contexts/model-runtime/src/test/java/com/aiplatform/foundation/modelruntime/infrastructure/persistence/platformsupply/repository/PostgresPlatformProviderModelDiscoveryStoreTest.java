package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelDiscoveryStore;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderModelDiscovery;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderModelDiscoveryId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionCreation;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.domain.provider.model.AuthenticationType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference;
import com.aiplatform.foundation.modelruntime.domain.provider.model.DiscoveredModel;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderModelDiscoveryObservation;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
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
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest(classes = PostgresPlatformProviderModelDiscoveryStoreTest.TestApplication.class)
@Testcontainers
class PostgresPlatformProviderModelDiscoveryStoreTest {

    private static final AtomicLong IDENTIFIERS = new AtomicLong(620000000000000100L);

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:0.8.5-pg18-trixie")
            .withDatabaseName("foundation_test")
            .withUsername("foundation")
            .withPassword("foundation_test_only");

    @Autowired private PlatformProviderConnectionRepository connections;
    @Autowired private PlatformProviderModelDiscoveryStore discoveries;
    @Autowired private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/model-runtime");
    }

    @BeforeEach
    void clearFacts() {
        jdbc.update("DELETE FROM model_runtime.platform_discovered_model");
        jdbc.update("DELETE FROM model_runtime.platform_provider_model_discovery");
        jdbc.update("DELETE FROM model_runtime.platform_provider_connection_request");
        jdbc.update("DELETE FROM model_runtime.platform_provider_connection_credential");
        jdbc.update("DELETE FROM model_runtime.platform_provider_connection");
    }

    @Test
    void 平台模型发现和候选只写入平台专用表() {
        var connection = connections.create(new PlatformProviderConnectionCreation(
                PlatformProviderConnection.create(
                        new PlatformProviderConnectionId(IDENTIFIERS.incrementAndGet()),
                        ProviderKind.DETERMINISTIC,
                        "平台模型发现连接",
                        URI.create("memory://deterministic"),
                        "本地",
                        AuthenticationType.API_KEY,
                        new CredentialReference("secret://platform-provider-connection/" + IDENTIFIERS.get()),
                        Instant.parse("2026-08-13T02:00:00Z")),
                "platform-discovery-request-0001",
                "a".repeat(64))).connection();
        discoveries.record(PlatformProviderModelDiscovery.capture(
                new PlatformProviderModelDiscoveryId(IDENTIFIERS.incrementAndGet()),
                connection.id(),
                connection.version(),
                new ProviderModelDiscoveryObservation(
                        true,
                        Set.of(new DiscoveredModel(
                                "deterministic-embedding", ModelType.EMBEDDING,
                                Set.of(ModelCapability.EMBEDDING))),
                        "DETERMINISTIC_DISCOVERY_OK",
                        Duration.ZERO,
                        Instant.parse("2026-08-13T02:01:00Z")),
                Instant.parse("2026-08-13T03:01:00Z")));

        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM model_runtime.platform_provider_model_discovery", Long.class));
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM model_runtime.platform_discovered_model", Long.class));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM model_runtime.provider_model_discovery", Long.class));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
        @Bean PlatformProviderConnectionRepository platformProviderConnectionRepository(
                JdbcTemplate jdbc, PlatformTransactionManager transactions) {
            return new PostgresPlatformProviderConnectionRepository(jdbc, transactions);
        }

        @Bean PlatformProviderModelDiscoveryStore platformProviderModelDiscoveryStore(
                JdbcTemplate jdbc, PlatformTransactionManager transactions) {
            return new PostgresPlatformProviderModelDiscoveryStore(jdbc, transactions);
        }
    }
}
