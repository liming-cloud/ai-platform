package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelDiscoveryStore;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelDiscoveryLookup;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelCandidateConfirmationStore;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformDiscoveredModelCandidate;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderModelDiscovery;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderModelDiscoveryId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformProviderModelDiscoveryObservation;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionCreation;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.domain.provider.model.AuthenticationType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
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
    @Autowired private PlatformProviderModelDiscoveryLookup discoveryLookup;
    @Autowired private PlatformProviderModelCandidateConfirmationStore confirmations;
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
        jdbc.update("DELETE FROM model_runtime.platform_model_candidate_confirmation");
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
                new PlatformProviderModelDiscoveryObservation(
                        true,
                        Set.of(PlatformDiscoveredModelCandidate.confirmedByProvider(
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

    @Test
    void 候选分页只返回当前连接修订且未过期的安全发现结果() {
        var connection = connections.create(new PlatformProviderConnectionCreation(
                PlatformProviderConnection.create(
                        new PlatformProviderConnectionId(IDENTIFIERS.incrementAndGet()),
                        ProviderKind.DETERMINISTIC,
                        "平台候选分页连接",
                        URI.create("memory://deterministic"),
                        "本地",
                        AuthenticationType.API_KEY,
                        new CredentialReference("secret://platform-provider-connection/" + IDENTIFIERS.get()),
                        Instant.parse("2026-08-13T02:00:00Z")),
                "platform-discovery-page-request-0001",
                "b".repeat(64))).connection();
        discoveries.record(PlatformProviderModelDiscovery.capture(
                new PlatformProviderModelDiscoveryId(IDENTIFIERS.incrementAndGet()),
                connection.id(), connection.version(), new PlatformProviderModelDiscoveryObservation(
                        true,
                        Set.of(
                                PlatformDiscoveredModelCandidate.confirmedByProvider("deterministic-chat", ModelType.CHAT,
                                        Set.of(ModelCapability.CHAT)),
                                PlatformDiscoveredModelCandidate.confirmedByProvider("deterministic-embedding", ModelType.EMBEDDING,
                                        Set.of(ModelCapability.EMBEDDING))),
                        "DETERMINISTIC_DISCOVERY_OK", Duration.ZERO,
                        Instant.parse("2026-08-13T02:01:00Z")),
                Instant.parse("2026-08-13T03:01:00Z")));

        var page = discoveryLookup.findCurrentCandidates(connection.id(), connection.version(),
                new PageQuery(1, 1), Instant.parse("2026-08-13T02:30:00Z"));

        assertEquals(2L, page.totalElements());
        assertEquals(1, page.items().size());
        assertEquals("deterministic-chat", page.items().getFirst().modelCode());
    }

    @Test
    void 候选分页只返回同一连接修订最新成功发现() {
        var connection = connections.create(new PlatformProviderConnectionCreation(
                PlatformProviderConnection.create(
                        new PlatformProviderConnectionId(IDENTIFIERS.incrementAndGet()), ProviderKind.DETERMINISTIC,
                        "平台当前候选连接", URI.create("memory://deterministic"), "本地", AuthenticationType.API_KEY,
                        new CredentialReference("secret://platform-provider-connection/" + IDENTIFIERS.get()),
                        Instant.parse("2026-08-13T02:00:00Z")), "platform-discovery-current-request-0001", "c".repeat(64))).connection();
        discoveries.record(PlatformProviderModelDiscovery.capture(
                new PlatformProviderModelDiscoveryId(IDENTIFIERS.incrementAndGet()), connection.id(), connection.version(),
                new PlatformProviderModelDiscoveryObservation(true, Set.of(PlatformDiscoveredModelCandidate.confirmedByProvider("retired-model", ModelType.CHAT,
                        Set.of(ModelCapability.CHAT))), "DETERMINISTIC_DISCOVERY_OK", Duration.ZERO,
                        Instant.parse("2026-08-13T02:01:00Z")), Instant.parse("2026-08-13T03:01:00Z")));
        discoveries.record(PlatformProviderModelDiscovery.capture(
                new PlatformProviderModelDiscoveryId(IDENTIFIERS.incrementAndGet()), connection.id(), connection.version(),
                new PlatformProviderModelDiscoveryObservation(true, Set.of(PlatformDiscoveredModelCandidate.confirmedByProvider("current-model", ModelType.EMBEDDING,
                        Set.of(ModelCapability.EMBEDDING))), "DETERMINISTIC_DISCOVERY_OK", Duration.ZERO,
                        Instant.parse("2026-08-13T02:02:00Z")), Instant.parse("2026-08-13T03:02:00Z")));

        var page = discoveryLookup.findCurrentCandidates(connection.id(), connection.version(), new PageQuery(1, 20),
                Instant.parse("2026-08-13T02:30:00Z"));

        assertEquals(1L, page.totalElements());
        assertEquals("current-model", page.items().getFirst().modelCode());
    }

    @Test
    void 待确认候选可安全分页但不能作为部署候选读取() {
        var connection = connections.create(new PlatformProviderConnectionCreation(
                PlatformProviderConnection.create(
                        new PlatformProviderConnectionId(IDENTIFIERS.incrementAndGet()),
                        ProviderKind.OPENAI_COMPATIBLE,
                        "OpenAI-compatible 平台连接",
                        URI.create("https://models.example.test/v1"),
                        "global",
                        AuthenticationType.API_KEY,
                        new CredentialReference("secret://platform-provider-connection/" + IDENTIFIERS.get()),
                        Instant.parse("2026-08-24T02:00:00Z")),
                "platform-discovery-unclassified-request-0001",
                "d".repeat(64))).connection();
        discoveries.record(PlatformProviderModelDiscovery.capture(
                new PlatformProviderModelDiscoveryId(IDENTIFIERS.incrementAndGet()),
                connection.id(),
                connection.version(),
                new PlatformProviderModelDiscoveryObservation(
                        true,
                        Set.of(PlatformDiscoveredModelCandidate.requiresAdministratorConfirmation(
                                "embedding-looking-name")),
                        "OPENAI_COMPATIBLE_DISCOVERY_OK",
                        Duration.ofMillis(8),
                        Instant.parse("2026-08-24T02:01:00Z")),
                Instant.parse("2026-08-24T03:01:00Z")));

        var page = discoveryLookup.findCurrentCandidates(
                connection.id(), connection.version(), new PageQuery(1, 20),
                Instant.parse("2026-08-24T02:30:00Z"));

        assertEquals(1L, page.totalElements());
        assertNull(page.items().getFirst().modelType());
        assertTrue(page.items().getFirst().capabilities().isEmpty());
        assertTrue(discoveryLookup.findCurrentCandidate(
                connection.id(), connection.version(), "embedding-looking-name",
                ModelType.EMBEDDING, Instant.parse("2026-08-24T02:30:00Z")).isEmpty());

        var confirmed = confirmations.confirmCurrentCandidate(
                connection.id(), connection.version(), "embedding-looking-name", ModelType.EMBEDDING,
                102L, Instant.parse("2026-08-24T02:31:00Z"), Instant.parse("2026-08-24T02:30:00Z"));

        assertEquals(ModelType.EMBEDDING, confirmed.modelType());
        assertEquals(Set.of(ModelCapability.EMBEDDING), confirmed.capabilities());
        assertEquals("CONFIRMED_BY_ADMIN", confirmed.classification().name());
        assertTrue(discoveryLookup.findCurrentCandidate(
                connection.id(), connection.version(), "embedding-looking-name",
                ModelType.EMBEDDING, Instant.parse("2026-08-24T02:32:00Z")).isPresent());
        assertEquals(102L, jdbc.queryForObject(
                "SELECT confirmed_by_subject_id FROM model_runtime.platform_model_candidate_confirmation WHERE model_code = ?",
                Long.class, "embedding-looking-name"));
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
