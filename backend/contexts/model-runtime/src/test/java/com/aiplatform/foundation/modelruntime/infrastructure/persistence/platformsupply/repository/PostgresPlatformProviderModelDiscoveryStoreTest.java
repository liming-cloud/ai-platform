package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformProviderConnectionMapper;
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
    @Autowired private PlatformProviderConnectionMapper connectionMapper;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/model-runtime");
    }

    @BeforeEach
    void clearFacts() {
        jdbc.update("DELETE FROM model_runtime.platform_provider_connection_publication");
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
                        com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDiscoveryProfile.DETERMINISTIC_CATALOG,
                        new com.aiplatform.foundation.modelruntime.domain.platformsupply.model.preset.PlatformProviderPresetSelection("CUSTOM", "2026-10-12", "CHAT", null),
                        "平台模型发现连接",
                        URI.create("memory://deterministic"),
                        "本地",
                        AuthenticationType.API_KEY,
                        new CredentialReference("secret://platform-provider-connection/" + IDENTIFIERS.get()),
                        Instant.parse("2026-08-13T02:00:00Z")),
                "platform-discovery-request-0001",
                "a".repeat(64))).connection();
        connection = recordDiscovery(connection, PlatformProviderModelDiscovery.capture(
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
        var committed = connections.findById(connection.id()).orElseThrow();
        assertEquals(1L, committed.version());
        assertEquals(Instant.parse("2026-08-13T02:01:00Z"), committed.lastProbedAt());
        assertTrue(committed.lastProbeSucceeded());
        assertEquals(committed.version(), jdbc.queryForObject(
                "SELECT platform_provider_connection_revision FROM model_runtime.platform_provider_model_discovery", Long.class));
    }

    @Test
    void 候选分页只返回当前连接修订且未过期的安全发现结果() {
        var connection = connections.create(new PlatformProviderConnectionCreation(
                PlatformProviderConnection.create(
                        new PlatformProviderConnectionId(IDENTIFIERS.incrementAndGet()),
                        ProviderKind.DETERMINISTIC,
                        com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDiscoveryProfile.DETERMINISTIC_CATALOG,
                        new com.aiplatform.foundation.modelruntime.domain.platformsupply.model.preset.PlatformProviderPresetSelection("CUSTOM", "2026-10-12", "CHAT", null),
                        "平台候选分页连接",
                        URI.create("memory://deterministic"),
                        "本地",
                        AuthenticationType.API_KEY,
                        new CredentialReference("secret://platform-provider-connection/" + IDENTIFIERS.get()),
                        Instant.parse("2026-08-13T02:00:00Z")),
                "platform-discovery-page-request-0001",
                "b".repeat(64))).connection();
        connection = recordDiscovery(connection, PlatformProviderModelDiscovery.capture(
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
    void 候选分页只返回当前连接修订且不混入先前发现() {
        var connection = connections.create(new PlatformProviderConnectionCreation(
                PlatformProviderConnection.create(
                        new PlatformProviderConnectionId(IDENTIFIERS.incrementAndGet()),
                        ProviderKind.DETERMINISTIC,
                        com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDiscoveryProfile.DETERMINISTIC_CATALOG,
                        new com.aiplatform.foundation.modelruntime.domain.platformsupply.model.preset.PlatformProviderPresetSelection("CUSTOM", "2026-10-12", "CHAT", null),
                        "平台当前候选连接",
                        URI.create("memory://deterministic"),
                        "本地",
                        AuthenticationType.API_KEY,
                        new CredentialReference("secret://platform-provider-connection/" + IDENTIFIERS.get()),
                        Instant.parse("2026-08-13T02:00:00Z")), "platform-discovery-current-request-0001", "c".repeat(64))).connection();
        connection = recordDiscovery(connection, PlatformProviderModelDiscovery.capture(
                new PlatformProviderModelDiscoveryId(IDENTIFIERS.incrementAndGet()), connection.id(), connection.version(),
                new PlatformProviderModelDiscoveryObservation(true, Set.of(PlatformDiscoveredModelCandidate.confirmedByProvider("retired-model", ModelType.CHAT,
                        Set.of(ModelCapability.CHAT))), "DETERMINISTIC_DISCOVERY_OK", Duration.ZERO,
                        Instant.parse("2026-08-13T02:01:00Z")), Instant.parse("2026-08-13T03:01:00Z")));
        connection = recordDiscovery(connection, PlatformProviderModelDiscovery.capture(
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
                        com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDiscoveryProfile.OPENAI_MODELS_API,
                        new com.aiplatform.foundation.modelruntime.domain.platformsupply.model.preset.PlatformProviderPresetSelection("CUSTOM", "2026-10-12", "CHAT", null),
                        "OpenAI-compatible 平台连接",
                        URI.create("https://models.example.test/v1"),
                        "global",
                        AuthenticationType.API_KEY,
                        new CredentialReference("secret://platform-provider-connection/" + IDENTIFIERS.get()),
                        Instant.parse("2026-08-24T02:00:00Z")),
                "platform-discovery-unclassified-request-0001",
                "d".repeat(64))).connection();
        connection = recordDiscovery(connection, PlatformProviderModelDiscovery.capture(
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

    @Test
    void 发现根写入失败必须回滚连接验证且外部调用不持有事务() {
        var connection = connections.create(new PlatformProviderConnectionCreation(
                PlatformProviderConnection.create(new PlatformProviderConnectionId(IDENTIFIERS.incrementAndGet()),
                        ProviderKind.DETERMINISTIC,
                        com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDiscoveryProfile.DETERMINISTIC_CATALOG,
                        new com.aiplatform.foundation.modelruntime.domain.platformsupply.model.preset.PlatformProviderPresetSelection(
                                "CUSTOM", "2026-10-12", "CHAT", null), "发现回滚连接",
                        URI.create("memory://deterministic"), "本地", AuthenticationType.API_KEY,
                        new CredentialReference("secret://platform-provider-connection/" + IDENTIFIERS.get()),
                        Instant.parse("2026-10-12T02:00:00Z")), "discovery-rollback-request", "e".repeat(64))).connection();
        var discoveryId = IDENTIFIERS.incrementAndGet();
        // 仅在隔离测试数据库制造主键冲突，证明真实 adapter 的最终回滚可见性。
        jdbc.update("INSERT INTO model_runtime.platform_provider_model_discovery "
                        + "(id,platform_provider_connection_id,platform_provider_connection_revision,"
                        + "succeeded,result_code,elapsed_millis,discovered_at,expires_at) "
                        + "VALUES (?,?,0,FALSE,'EXISTING_FAILURE',0,?,NULL)", discoveryId,
                connection.id().value(), java.sql.Timestamp.from(Instant.parse("2026-10-12T02:01:00Z")));
        var service = new com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformProviderModelDiscoveryService(
                connections, java.util.List.of(new com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelDiscoverer() {
                    @Override public com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind providerKind() {
                        return com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind.DETERMINISTIC;
                    }
                    @Override public com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformModelDiscoveryProfile discoveryProfile() {
                        return com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformModelDiscoveryProfile.DETERMINISTIC_CATALOG;
                    }
                    @Override public com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformProviderModelDiscoveryObservation discover(
                            com.aiplatform.foundation.modelruntime.application.extension.facade.request.PlatformProviderModelDiscoveryRequest request) {
                        assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
                        return new com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformProviderModelDiscoveryObservation(
                                true, Set.of(com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformDiscoveredModelCandidate.confirmedByProvider(
                                        "rollback-model", com.aiplatform.foundation.modelruntime.application.extension.facade.ModelType.CHAT,
                                        Set.of(com.aiplatform.foundation.modelruntime.application.extension.facade.ModelCapability.CHAT))),
                                "DISCOVERY_OK", Duration.ZERO, Instant.parse("2026-10-12T02:02:00Z"));
                    }
                }), discoveries, java.time.Clock.fixed(Instant.parse("2026-10-12T02:02:00Z"), java.time.ZoneOffset.UTC),
                () -> discoveryId, Duration.ofHours(1));
        var context = new com.aiplatform.foundation.security.identity.PlatformRequestContext(
                new com.aiplatform.foundation.security.identity.SubjectId(102L),
                com.aiplatform.foundation.security.identity.CredentialType.PERSON,
                com.aiplatform.foundation.security.identity.AccessChannel.CONSOLE, 1L,
                Set.of(com.aiplatform.foundation.security.identity.PlatformPermissions.SESSION_ACCESS,
                        com.aiplatform.foundation.security.identity.PlatformPermissions.MODEL_PLATFORM_WRITE), "trc_discovery_rollback");

        assertThrows(org.springframework.dao.DuplicateKeyException.class, () -> service.discover(
                new com.aiplatform.foundation.modelruntime.application.platformsupply.command.DiscoverPlatformProviderModelsCommand(
                        context, connection.id().toString(), connection.version(), Duration.ofSeconds(3))));

        var unchanged = connections.findById(connection.id()).orElseThrow();
        assertEquals(connection.version(), unchanged.version());
        assertEquals(connection.status(), unchanged.status());
        assertNull(unchanged.lastProbedAt());
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM model_runtime.platform_provider_model_discovery", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM model_runtime.platform_discovered_model", Long.class));
    }

    private PlatformProviderConnection recordDiscovery(PlatformProviderConnection connection,
            PlatformProviderModelDiscovery discovery) {
        var next = discovery.succeeded()
                ? connection.recordSuccessfulProbe(discovery.resultCode(), discovery.discoveredAt())
                : connection.recordFailedProbe(discovery.resultCode(), discovery.discoveredAt());
        discoveries.record(next, connection.version(), PlatformProviderModelDiscovery.capture(
                discovery.id(), next.id(), next.version(), new PlatformProviderModelDiscoveryObservation(
                        discovery.succeeded(), discovery.models(), discovery.resultCode(),
                        discovery.elapsed(), discovery.discoveredAt()), discovery.expiresAt()));
        return next;
    }

    @Test
    void 停用后的迟到提交被真实仓储拒绝且不留下发现事实() {
        var connection = createDraftConnection("迟到发现连接");
        var at = Instant.parse("2026-10-12T02:02:00Z");
        var verified = connection.recordSuccessfulProbe("DISCOVERY_OK", at);
        var discovery = PlatformProviderModelDiscovery.capture(
                new PlatformProviderModelDiscoveryId(IDENTIFIERS.incrementAndGet()), connection.id(), verified.version(),
                new PlatformProviderModelDiscoveryObservation(true, Set.of(
                        PlatformDiscoveredModelCandidate.confirmedByProvider("late-model", ModelType.CHAT,
                                Set.of(ModelCapability.CHAT))), "DISCOVERY_OK", Duration.ZERO, at),
                at.plusSeconds(3600));
        assertTrue(connections.update(connection.disable(), connection.version()));

        assertThrows(com.aiplatform.foundation.modelruntime.application.provider.exception.ProviderConnectionConcurrentModificationException.class,
                () -> discoveries.record(verified, connection.version(), discovery));

        assertEquals(com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionStatus.DISABLED,
                connections.findById(connection.id()).orElseThrow().status());
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM model_runtime.platform_provider_model_discovery", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM model_runtime.platform_discovered_model", Long.class));
    }

    @Test
    void 发现事实绑定错误修订必须在任何写入前拒绝() {
        var connection = createDraftConnection("错误绑定连接");
        var at = Instant.parse("2026-10-12T02:02:00Z");
        var verified = connection.recordSuccessfulProbe("DISCOVERY_OK", at);
        var discovery = PlatformProviderModelDiscovery.capture(
                new PlatformProviderModelDiscoveryId(IDENTIFIERS.incrementAndGet()), connection.id(), connection.version(),
                new PlatformProviderModelDiscoveryObservation(true, Set.of(
                        PlatformDiscoveredModelCandidate.confirmedByProvider("bound-model", ModelType.CHAT,
                                Set.of(ModelCapability.CHAT))), "DISCOVERY_OK", Duration.ZERO, at),
                at.plusSeconds(3600));

        assertThrows(IllegalArgumentException.class, () -> discoveries.record(verified, connection.version(), discovery));

        assertEquals(connection.version(), connections.findById(connection.id()).orElseThrow().version());
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM model_runtime.platform_provider_model_discovery", Long.class));
    }

    @Test
    void 配置事实按真实凭据槽及当前发布修订批量返回() {
        var connection = createDraftConnection("配置事实连接");
        var other = createDraftConnection("未配置连接");
        var ids = Set.of(connection.id().value(), other.id().value());
        assertTrue(connectionMapper.selectFacts(ids).stream().noneMatch(value -> value.credentialConfigured()
                || value.discoveryCredentialsConfigured() || value.published()));
        for (var purpose : java.util.List.of("INFERENCE_API_KEY", "DISCOVERY_ACCESS_KEY_ID", "DISCOVERY_SECRET_ACCESS_KEY")) {
            // 无法解密的合成密文仍只能证明槽已配置；该查询不得尝试读取或解密它。
            jdbc.update("INSERT INTO model_runtime.platform_provider_connection_credential "
                            + "(platform_provider_connection_id,credential_purpose,algorithm,master_key_version,"
                            + "data_key_nonce,encrypted_data_key,ciphertext_nonce,ciphertext,fingerprint,rotated_at) "
                            + "VALUES (?,?,'AES-256-GCM','synthetic',?,?,?,?,'synthetic',?)",
                    connection.id().value(), purpose, new byte[12], new byte[16], new byte[12], new byte[16],
                    java.sql.Timestamp.from(Instant.parse("2026-10-12T02:01:00Z")));
            var fact = connectionMapper.selectFacts(ids).stream().filter(value -> value.connectionId() == connection.id().value()).findFirst().orElseThrow();
            assertTrue(fact.credentialConfigured());
            assertEquals(purpose.equals("DISCOVERY_SECRET_ACCESS_KEY"), fact.discoveryCredentialsConfigured());
        }
        var at = Instant.parse("2026-10-12T02:02:00Z");
        var verified = connection.recordSuccessfulProbe("DISCOVERY_OK", at);
        assertTrue(connections.update(verified, connection.version()));
        jdbc.update("INSERT INTO model_runtime.platform_provider_connection_publication "
                        + "(id,connection_id,revision,connection_revision,published_by_subject_id,probed_at,published_at) "
                        + "VALUES (?,?,1,?,102,?,?)", IDENTIFIERS.incrementAndGet(), connection.id().value(), verified.version(),
                java.sql.Timestamp.from(at), java.sql.Timestamp.from(at.plusSeconds(1)));
        assertTrue(connectionMapper.selectFacts(Set.of(connection.id().value())).getFirst().published());
        assertTrue(connections.update(verified.disable(), verified.version()));
        assertFalse(connectionMapper.selectFacts(Set.of(connection.id().value())).getFirst().published());
        assertFalse(connectionMapper.selectFacts(Set.of(other.id().value())).getFirst().credentialConfigured());
    }

    private PlatformProviderConnection createDraftConnection(String name) {
        var id = new PlatformProviderConnectionId(IDENTIFIERS.incrementAndGet());
        return connections.create(new PlatformProviderConnectionCreation(PlatformProviderConnection.create(
                id, ProviderKind.DETERMINISTIC,
                com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDiscoveryProfile.DETERMINISTIC_CATALOG,
                new com.aiplatform.foundation.modelruntime.domain.platformsupply.model.preset.PlatformProviderPresetSelection(
                        "CUSTOM", "2026-10-12", "CHAT", null), name, URI.create("memory://deterministic"), "本地",
                AuthenticationType.API_KEY, new CredentialReference("secret://platform-provider-connection/" + id.value()),
                Instant.parse("2026-10-12T02:00:00Z")), "discovery-request-" + id.value(), "f".repeat(64))).connection();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @org.mybatis.spring.annotation.MapperScan(basePackageClasses = com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformProviderConnectionMapper.class)
    static class TestApplication {
        @Bean PlatformProviderConnectionRepository platformProviderConnectionRepository(
                com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformProviderConnectionMapper mapper,
                com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformProviderConnectionRequestMapper requests, PlatformTransactionManager transactions) {
            return new PostgresPlatformProviderConnectionRepository(mapper, requests, transactions);
        }

        @Bean PlatformProviderModelDiscoveryStore platformProviderModelDiscoveryStore(
                JdbcTemplate jdbc, PlatformTransactionManager transactions,
                PlatformProviderConnectionRepository connections) {
            return new PostgresPlatformProviderModelDiscoveryStore(jdbc, transactions, connections);
        }
    }
}
