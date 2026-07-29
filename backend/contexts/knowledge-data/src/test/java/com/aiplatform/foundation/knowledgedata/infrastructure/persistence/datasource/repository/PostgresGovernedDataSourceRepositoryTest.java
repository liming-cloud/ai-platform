package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceOperation;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceStateException;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.FileSourceObjectReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceStatus;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceType;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.QualityReport;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SourceRevision;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SyncArtifact;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SyncArtifactId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.GovernedDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.GovernedDataSourceMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.QualityReportMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.SourceRevisionMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.SyncArtifactMapper;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.Callable;
import java.util.ArrayList;
import java.util.concurrent.Executors;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 通过真实 PostgreSQL 验证受治理数据源、来源修订、同步产物与质量结论的原子事实边界。
 */
@SpringBootTest(classes = PostgresGovernedDataSourceRepositoryTest.TestApplication.class)
@Testcontainers
class PostgresGovernedDataSourceRepositoryTest {

    private static final TenantId STORE_TENANT = new TenantId(100000000000000001L);
    private static final TenantId CAMPUS_TENANT = new TenantId(100000000000000002L);
    private static final KnowledgeSpaceId SPACE = new KnowledgeSpaceId(210000000000000001L);
    private static final CreatorId CREATOR = new CreatorId(4921825928971026434L);
    private static final Instant CREATED_AT = Instant.parse("2026-07-29T02:00:00Z");
    private static final AtomicLong SOURCE_IDS = new AtomicLong(230000000000000000L);
    private static final AtomicLong ARTIFACT_IDS = new AtomicLong(240000000000000000L);

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("pgvector/pgvector:0.8.5-pg18-trixie")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private GovernedDataSourceRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/knowledge-data");
    }

    @BeforeEach
    void prepareFacts() {
        jdbc.update("DELETE FROM knowledge_data.quality_report");
        jdbc.update("DELETE FROM knowledge_data.sync_artifact");
        jdbc.update("DELETE FROM knowledge_data.source_revision");
        jdbc.update("DELETE FROM knowledge_data.governed_data_source");
        jdbc.update("DELETE FROM knowledge_data.knowledge_space");
        insertSpace(STORE_TENANT);
        insertSpace(CAMPUS_TENANT);
    }

    @Test
    void 登记来源与首个不可变修订并按租户隔离读取() {
        var source = source(STORE_TENANT, nextSourceId(), "门店文件");
        var revision = revision(source, "a".repeat(64));

        repository.register(source, revision);

        var stored = repository.findSource(STORE_TENANT, SPACE, source.id());
        assertTrue(stored.isPresent());
        assertEquals(GovernedDataSourceStatus.DRAFT, stored.orElseThrow().status());
        assertEquals(1, stored.orElseThrow().currentRevision());
        assertEquals(
                revision.definitionDigest(),
                repository.findRevision(STORE_TENANT, SPACE, source.id(), 1)
                        .orElseThrow()
                        .definitionDigest());
        assertEquals(
                revision.fileObject(),
                repository.findRevision(STORE_TENANT, SPACE, source.id(), 1)
                        .orElseThrow()
                        .fileObject());
        assertFalse(repository.findSource(CAMPUS_TENANT, SPACE, source.id()).isPresent());
        assertEquals(1, count("governed_data_source", STORE_TENANT));
        assertEquals(1, count("source_revision", STORE_TENANT));
    }

    @Test
    void 同步产物和质量结论在同一事务提交且跨租户不可见() {
        var source = source(STORE_TENANT, nextSourceId(), "校园文件");
        repository.register(source, revision(source, "a".repeat(64)));
        var syncing = startSynchronization(source);
        var artifact = artifact(source, "b".repeat(64));
        var report = QualityReport.warned(artifact, "file-quality-v1", 2);

        var completed = repository.completeSynchronization(artifact, report, syncing.version());

        assertEquals(GovernedDataSourceStatus.READY, completed.status());
        assertEquals(4, completed.version());
        assertEquals(
                artifact.contentDigest(),
                repository.findArtifact(STORE_TENANT, SPACE, artifact.id())
                        .orElseThrow()
                        .contentDigest());
        assertEquals(
                report.status(),
                repository.findQualityReport(STORE_TENANT, SPACE, artifact.id())
                        .orElseThrow()
                        .status());
        assertFalse(repository.findArtifact(CAMPUS_TENANT, SPACE, artifact.id()).isPresent());
        assertEquals(1, count("sync_artifact", STORE_TENANT));
        assertEquals(1, count("quality_report", STORE_TENANT));
    }

    @Test
    void 质量结论不属于待登记产物时整体回滚() {
        var source = source(STORE_TENANT, nextSourceId(), "回滚文件");
        repository.register(source, revision(source, "a".repeat(64)));
        var syncing = startSynchronization(source);
        var artifact = artifact(source, "b".repeat(64));
        var unrelated = artifact(source, "c".repeat(64));

        assertThrows(IllegalArgumentException.class, () -> repository.completeSynchronization(
                artifact,
                QualityReport.passed(unrelated, "file-quality-v1"),
                syncing.version()));

        assertFalse(repository.findArtifact(STORE_TENANT, SPACE, artifact.id()).isPresent());
        assertFalse(repository.findQualityReport(STORE_TENANT, SPACE, artifact.id()).isPresent());
        var stored = repository.findSource(STORE_TENANT, SPACE, source.id()).orElseThrow();
        assertEquals(GovernedDataSourceStatus.SYNCING, stored.status());
        assertEquals(syncing.version(), stored.version());
        assertEquals(0, count("sync_artifact", STORE_TENANT));
        assertEquals(0, count("quality_report", STORE_TENANT));
    }

    @Test
    void 失败操作被持久化且重试只能恢复对应运行状态() {
        var source = source(STORE_TENANT, nextSourceId(), "恢复文件");
        repository.register(source, revision(source, "a".repeat(64)));
        var probing = source.startProbe(0);
        var failed = probing.failProbe(1);

        assertTrue(repository.update(probing, 0));
        assertTrue(repository.update(failed, 1));
        var storedFailure = repository.findSource(STORE_TENANT, SPACE, source.id()).orElseThrow();
        assertEquals(GovernedDataSourceStatus.FAILED, storedFailure.status());
        assertEquals(DataSourceOperation.PROBE, storedFailure.failedOperation());

        var retried = storedFailure.retry(2);
        assertTrue(repository.update(retried, 2));
        var storedRetry = repository.findSource(STORE_TENANT, SPACE, source.id()).orElseThrow();
        assertEquals(GovernedDataSourceStatus.PROBING, storedRetry.status());
        assertEquals(null, storedRetry.failedOperation());
    }

    @Test
    void 并发完成同步只有一个提交且失败方不留下重复产物() throws Exception {
        var source = source(STORE_TENANT, nextSourceId(), "并发同步文件");
        repository.register(source, revision(source, "a".repeat(64)));
        var syncing = startSynchronization(source);
        var artifact = artifact(source, "b".repeat(64));
        var report = QualityReport.passed(artifact, "file-quality-v1");
        var tasks = new ArrayList<Callable<Boolean>>();
        for (var index = 0; index < 2; index++) {
            tasks.add(() -> {
                try {
                    repository.completeSynchronization(artifact, report, syncing.version());
                    return true;
                } catch (DataSourceStateException exception) {
                    return false;
                }
            });
        }

        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(tasks);
            assertEquals(1, results.stream().filter(result -> {
                try {
                    return result.get();
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }).count());
        }

        var stored = repository.findSource(STORE_TENANT, SPACE, source.id()).orElseThrow();
        assertEquals(GovernedDataSourceStatus.READY, stored.status());
        assertEquals(4, stored.version());
        assertEquals(1, count("sync_artifact", STORE_TENANT));
        assertEquals(1, count("quality_report", STORE_TENANT));
    }

    @Test
    void 并发状态更新只有一个提交且失败方不覆盖最终事实() throws Exception {
        var source = source(STORE_TENANT, nextSourceId(), "并发恢复文件");
        repository.register(source, revision(source, "a".repeat(64)));
        var probing = source.startProbe(0);
        var tasks = new ArrayList<Callable<Boolean>>();
        for (var index = 0; index < 2; index++) {
            tasks.add(() -> repository.update(probing, 0));
        }

        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(tasks);
            assertEquals(1, results.stream().filter(result -> {
                try {
                    return result.get();
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }).count());
        }

        var stored = repository.findSource(STORE_TENANT, SPACE, source.id()).orElseThrow();
        assertEquals(GovernedDataSourceStatus.PROBING, stored.status());
        assertEquals(1, stored.version());
    }

    private static GovernedDataSource source(TenantId tenantId, DataSourceId id, String name) {
        return GovernedDataSource.create(
                tenantId,
                SPACE,
                id,
                GovernedDataSourceType.FILE,
                name,
                CREATOR,
                CREATED_AT);
    }

    private static SourceRevision revision(GovernedDataSource source, String digest) {
        return SourceRevision.file(
                source,
                new FileSourceObjectReference(
                        "foundation-v1/tenants/%d/knowledge-data/files/%d/1/content.txt".formatted(
                                source.tenantId().value(), source.id().value()),
                        digest,
                        12,
                        "text/plain; charset=utf-8"),
                CREATOR,
                CREATED_AT);
    }

    private GovernedDataSource startSynchronization(GovernedDataSource source) {
        var probing = source.startProbe(0);
        assertTrue(repository.update(probing, 0));
        var ready = probing.completeProbe(1);
        assertTrue(repository.update(ready, 1));
        var syncing = ready.startSynchronization(2);
        assertTrue(repository.update(syncing, 2));
        return syncing;
    }

    private static DataSourceId nextSourceId() {
        return new DataSourceId(SOURCE_IDS.incrementAndGet());
    }

    private static SyncArtifact artifact(GovernedDataSource source, String digest) {
        return SyncArtifact.create(
                new SyncArtifactId(ARTIFACT_IDS.incrementAndGet()),
                STORE_TENANT,
                SPACE,
                source.id(),
                1,
                digest,
                3,
                CREATED_AT.plusSeconds(60));
    }

    private void insertSpace(TenantId tenantId) {
        jdbc.update(
                """
                INSERT INTO knowledge_data.knowledge_space (
                    tenant_id, id, name, status, version, created_by, created_at
                ) VALUES (?, ?, ?, 'DRAFT', 0, ?, ?)
                """,
                tenantId.value(),
                SPACE.value(),
                "测试空间-" + tenantId.value(),
                CREATOR.value(),
                CREATED_AT.atOffset(java.time.ZoneOffset.UTC));
    }

    private int count(String table, TenantId tenantId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM knowledge_data." + table + " WHERE tenant_id = ?",
                Integer.class,
                tenantId.value());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan("com.aiplatform.foundation.knowledgedata.infrastructure.persistence")
    static class TestApplication {

        @Bean
        GovernedDataSourceRepository governedDataSourceRepository(
                GovernedDataSourceMapper sourceMapper,
                SourceRevisionMapper revisionMapper,
                SyncArtifactMapper artifactMapper,
                QualityReportMapper qualityMapper,
                PlatformTransactionManager transactions) {
            return new PostgresGovernedDataSourceRepository(
                    sourceMapper,
                    revisionMapper,
                    artifactMapper,
                    qualityMapper,
                    transactions);
        }
    }
}
