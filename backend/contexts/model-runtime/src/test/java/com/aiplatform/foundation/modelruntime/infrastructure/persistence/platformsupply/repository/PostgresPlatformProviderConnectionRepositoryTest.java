package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.modelruntime.application.provider.exception.ProviderConnectionIdempotencyConflictException;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ProviderConnectionNameConflictException;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionCreation;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.domain.provider.model.AuthenticationType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import java.net.URI;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

@SpringBootTest(classes = PostgresPlatformProviderConnectionRepositoryTest.TestApplication.class)
@Testcontainers
class PostgresPlatformProviderConnectionRepositoryTest {

    private static final AtomicLong IDENTIFIERS = new AtomicLong(320000000000000100L);
    private static final Instant CREATED_AT = Instant.parse("2026-08-13T01:30:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("pgvector/pgvector:0.8.5-pg18-trixie")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private PlatformProviderConnectionRepository repository;

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
    void clearFacts() {
        jdbc.update("DELETE FROM model_runtime.platform_provider_connection_request");
        jdbc.update("DELETE FROM model_runtime.platform_provider_connection_credential");
        jdbc.update("DELETE FROM model_runtime.platform_provider_connection");
    }

    @Test
    void 平台连接与幂等事实只写入独立平台表() {
        var first = repository.create(creation("platform-request-0001", "a".repeat(64), "平台模型"));
        var replayed = repository.create(creation("platform-request-0001", "a".repeat(64), "平台模型"));

        assertFalse(first.replayed());
        assertTrue(replayed.replayed());
        assertEquals(first.connection().id(), replayed.connection().id());
        assertEquals("PLATFORM", jdbc.queryForObject("""
                SELECT scope FROM model_runtime.platform_provider_connection WHERE id = ?
                """, String.class, first.connection().id().value()));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM model_runtime.provider_connection", Long.class));
    }

    @Test
    void 预设来源和能力意图经过数据库恢复后不丢失且探测不改变绑定() {
        var created = repository.create(creation("platform-preset-roundtrip-0001", "e".repeat(64), "预设绑定" )).connection();
        var restored = repository.findById(created.id()).orElseThrow();
        assertEquals(created.preset(), restored.preset());
        var probed = restored.recordSuccessfulProbe("SYNTHETIC_PROBE_OK", CREATED_AT.plusSeconds(1));
        assertTrue(repository.update(probed, restored.version()));
        assertFalse(repository.update(probed, restored.version()));
        assertEquals(created.preset(), repository.findById(created.id()).orElseThrow().preset());
    }

    @Test
    void 平台目录按稳定排序分页且末页总数和空页保持一致() {
        for (int i = 0; i < 21; i++) {
            repository.create(creation("platform-paging-0001-" + i, "a".repeat(64), "分页连接" + i));
        }
        var first = repository.findPage(1, 20);
        var last = repository.findPage(2, 20);
        var empty = repository.findPage(3, 20);
        assertEquals(21L, first.totalElements());
        assertEquals(21L, last.totalElements());
        assertEquals(21L, empty.totalElements());
        assertEquals(20, first.items().size());
        assertEquals(1, last.items().size());
        assertTrue(empty.items().isEmpty());
        assertTrue(first.items().get(19).id().value() > last.items().get(0).id().value());
    }

    @Test
    void 同幂等键不同请求和全局重名均失败关闭() {
        repository.create(creation("platform-request-0002", "a".repeat(64), "平台模型"));

        assertThrows(
                ProviderConnectionIdempotencyConflictException.class,
                () -> repository.create(creation("platform-request-0002", "b".repeat(64), "另一模型")));
        assertThrows(
                ProviderConnectionNameConflictException.class,
                () -> repository.create(creation("platform-request-0003", "c".repeat(64), "平台模型")));
    }

    private static PlatformProviderConnectionCreation creation(
            String idempotencyKey,
            String fingerprint,
            String displayName) {
        var id = new PlatformProviderConnectionId(IDENTIFIERS.incrementAndGet());
        return new PlatformProviderConnectionCreation(
                PlatformProviderConnection.create(
                        id,
                        ProviderKind.DETERMINISTIC,
                        com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDiscoveryProfile.DETERMINISTIC_CATALOG,
                        new com.aiplatform.foundation.modelruntime.domain.platformsupply.model.preset.PlatformProviderPresetSelection("CUSTOM", "2026-10-12", "CHAT", null),
                        displayName,
                        URI.create("memory://deterministic"),
                        "本地",
                        AuthenticationType.API_KEY,
                        new CredentialReference("secret://platform-provider-connection/" + id.value()),
                        CREATED_AT),
                idempotencyKey,
                fingerprint);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @org.mybatis.spring.annotation.MapperScan(basePackageClasses = com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformProviderConnectionMapper.class)
    static class TestApplication {
        @Bean
        com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor pagination() {
            var interceptor = new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();
            var pagination = new com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor(
                    com.baomidou.mybatisplus.annotation.DbType.POSTGRE_SQL);
            pagination.setOverflow(false);
            pagination.setMaxLimit(100L);
            interceptor.addInnerInterceptor(pagination);
            return interceptor;
        }

        @Bean
        PlatformProviderConnectionRepository platformProviderConnectionRepository(
                com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformProviderConnectionMapper mapper,
                com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformProviderConnectionRequestMapper requests,
                PlatformTransactionManager transactions) {
            return new PostgresPlatformProviderConnectionRepository(mapper, requests, transactions);
        }
    }
}
