package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.aiplatform.foundation.modelruntime.application.provider.exception.ProviderConnectionConcurrentModificationException;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionCreation;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionPublicationRepository;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.domain.provider.model.*;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformProviderConnectionPublicationMapper;
import java.net.URI;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(classes = PostgresPlatformProviderConnectionPublicationRepositoryTest.TestApplication.class)
@Testcontainers
class PostgresPlatformProviderConnectionPublicationRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");
    private static final Duration VALIDITY = Duration.ofHours(24);
    private static final AtomicReference<Instant> CURRENT = new AtomicReference<>(NOW);
    private static final AtomicLong IDS = new AtomicLong(320000000000000100L);
    @Container private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:0.8.5-pg18-trixie")
            .withDatabaseName("foundation_test").withUsername("foundation").withPassword("foundation_test_only");
    @Autowired private PlatformProviderConnectionRepository connections;
    @Autowired private PlatformProviderConnectionPublicationRepository publications;
    @Autowired private JdbcTemplate jdbc;

    @DynamicPropertySource static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/model-runtime");
    }

    @BeforeEach void clearFacts() {
        CURRENT.set(NOW);
        jdbc.update("DELETE FROM model_runtime.platform_provider_connection_publication");
        jdbc.update("DELETE FROM model_runtime.platform_provider_connection_request");
        jdbc.update("DELETE FROM model_runtime.platform_provider_connection");
    }

    @Test void 发布和重放返回真实原事实且不改变验证状态或创建模型() {
        var connection = verified();
        // PostgreSQL 时间精度与 JVM 不同；首次响应也必须使用实际持久化事实。
        CURRENT.set(NOW.plusNanos(123456789));
        var firstId = IDS.incrementAndGet();
        var first = publications.publish(connection.id(), connection.version(), firstId, 101L, VALIDITY);
        var replayed = publications.publish(connection.id(), connection.version(), IDS.incrementAndGet(), 102L, VALIDITY);
        assertEquals(firstId, first.id());
        assertEquals(first, replayed);
        assertEquals(first, publications.find(connection.id(), connection.version()).orElseThrow());
        assertEquals(1L, count("platform_provider_connection_publication"));
        assertEquals(0L, count("platform_model_deployment"));
        assertEquals(0L, count("model_configuration"));
        assertEquals(connection.version(), connections.findById(connection.id()).orElseThrow().version());
        assertEquals(ProviderConnectionStatus.VERIFIED, connections.findById(connection.id()).orElseThrow().status());
    }

    @Test void 新探测不借用历史发布且下一发布生成独立连续修订() {
        var connection = verified();
        var first = publications.publish(connection.id(), connection.version(), IDS.incrementAndGet(), 101L, VALIDITY);
        var next = connection.recordSuccessfulProbe("CONNECTION_OK", NOW.plusSeconds(1));
        assertTrue(connections.update(next, connection.version()));
        CURRENT.set(NOW.plusSeconds(1));
        assertTrue(publications.find(next.id(), next.version()).isEmpty());
        assertThrows(ProviderConnectionConcurrentModificationException.class,
                () -> publications.publish(next.id(), connection.version(), IDS.incrementAndGet(), 101L, VALIDITY));
        var second = publications.publish(next.id(), next.version(), IDS.incrementAndGet(), 101L, VALIDITY);
        assertEquals(2L, second.revision());
        assertEquals(next.version(), second.connectionRevision());
        assertEquals(first, publications.find(first.connectionId(), first.connectionRevision()).orElseThrow());
    }

    @Test void 探测过期后不能重放或产生新发布() {
        var connection = verified();
        publications.publish(connection.id(), connection.version(), IDS.incrementAndGet(), 101L, VALIDITY);
        CURRENT.set(NOW.plus(VALIDITY));
        assertThrows(ProviderConnectionStateException.class,
                () -> publications.publish(connection.id(), connection.version(), IDS.incrementAndGet(), 102L, VALIDITY));
        assertEquals(1L, count("platform_provider_connection_publication"));
    }

    @Test void 并发重复发布只有一条事实且操作者不会被后来的请求覆盖() throws Exception {
        var connection = verified();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> { start.await(); return publications.publish(connection.id(), connection.version(), IDS.incrementAndGet(), 101L, VALIDITY); });
            var second = executor.submit(() -> { start.await(); return publications.publish(connection.id(), connection.version(), IDS.incrementAndGet(), 102L, VALIDITY); });
            start.countDown();
            assertEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }
        assertEquals(1L, count("platform_provider_connection_publication"));
    }

    @Test void 发布写入失败不能留下发布或改变连接() {
        var connection = verified();
        jdbc.execute("CREATE FUNCTION model_runtime.reject_synthetic_publication() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION '合成写入失败'; END $$");
        jdbc.execute("CREATE TRIGGER reject_synthetic_publication BEFORE INSERT ON model_runtime.platform_provider_connection_publication FOR EACH ROW EXECUTE FUNCTION model_runtime.reject_synthetic_publication()");
        try {
            assertThrows(org.springframework.dao.DataAccessException.class,
                    () -> publications.publish(connection.id(), connection.version(), IDS.incrementAndGet(), 101L, VALIDITY));
            assertEquals(0L, count("platform_provider_connection_publication"));
            assertEquals(connection.version(), connections.findById(connection.id()).orElseThrow().version());
        } finally {
            jdbc.execute("DROP TRIGGER reject_synthetic_publication ON model_runtime.platform_provider_connection_publication");
            jdbc.execute("DROP FUNCTION model_runtime.reject_synthetic_publication()");
        }
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM model_runtime." + table, Long.class);
    }

    private PlatformProviderConnection verified() {
        var id = new PlatformProviderConnectionId(IDS.incrementAndGet());
        var connection = PlatformProviderConnection.create(
                id,
                ProviderKind.DETERMINISTIC,
                com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDiscoveryProfile.DETERMINISTIC_CATALOG,
                new com.aiplatform.foundation.modelruntime.domain.platformsupply.model.preset.PlatformProviderPresetSelection("CUSTOM", "2026-10-12", "CHAT", null),
                "合成连接" + id.value(),
                URI.create("memory://synthetic"),
                "local",
                AuthenticationType.API_KEY,
                new CredentialReference("secret://platform-provider-connection/" + id.value()),
                NOW);
        connections.create(new PlatformProviderConnectionCreation(connection, "synthetic-connection-" + id.value(), "a".repeat(64)));
        var verified = connection.recordSuccessfulProbe("CONNECTION_OK", NOW);
        assertTrue(connections.update(verified, connection.version()));
        return verified;
    }

    @SpringBootConfiguration @EnableAutoConfiguration
    @MapperScan(basePackageClasses = PlatformProviderConnectionPublicationMapper.class)
    static class TestApplication {
        @Bean PlatformProviderConnectionRepository connections(com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformProviderConnectionMapper mapper,
                com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformProviderConnectionRequestMapper requests, PlatformTransactionManager transactions) {
            return new PostgresPlatformProviderConnectionRepository(mapper, requests, transactions);
        }
        @Bean PlatformProviderConnectionPublicationRepository publications(PlatformProviderConnectionPublicationMapper mapper,
                PlatformProviderConnectionRepository connections, PlatformTransactionManager transactions) {
            var clock = new Clock() {
                @Override public ZoneId getZone() { return ZoneOffset.UTC; }
                @Override public Clock withZone(ZoneId zone) { return this; }
                @Override public Instant instant() { return CURRENT.get(); }
            };
            return new PostgresPlatformProviderConnectionPublicationRepository(mapper, connections, transactions, clock);
        }
    }
}
