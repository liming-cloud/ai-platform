package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceOperation;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceStateException;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceStatus;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceType;
import com.aiplatform.foundation.knowledgedata.domain.datasource.quality.QualityReport;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.FileSourceObjectReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.ObjectStorageObjectCollection;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.PlatformObjectRangeId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.PlatformObjectReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.SourceRevision;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetCsvDefinition;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetCsvReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetFieldMapping;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetLogicalType;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetRowFilter;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetSensitivityLevel;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SyncArtifactId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.GovernedDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.domain.datasource.service.FileDataSourceSynchronization;
import com.aiplatform.foundation.knowledgedata.domain.datasource.service.FileSynchronizationCompletion;
import com.aiplatform.foundation.knowledgedata.domain.datasource.service.FileSyncArtifactPublication;
import com.aiplatform.foundation.knowledgedata.domain.datasource.service.FileSyncArtifactPublishing;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.GovernedFileDataSourceReadPort;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.GovernedFileDataSourceReadMapper;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.GovernedDataSourceMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.ObjectStorageObjectMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.PublishedSyncArtifactMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.QualityReportMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.SourceRevisionMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.SyncArtifactMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.StructuredDatasetFieldMapper;
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
    private static final FileDataSourceSynchronization SYNCHRONIZATION = new FileDataSourceSynchronization();
    private static final FileSyncArtifactPublishing PUBLISHING = new FileSyncArtifactPublishing();

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

    @Autowired
    private PublishedSyncArtifactMapper publicationMapper;

    @Autowired
    private GovernedFileDataSourceReadPort reads;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/knowledge-data");
    }

    @BeforeEach
    void prepareFacts() {
        jdbc.update("DELETE FROM knowledge_data.published_sync_artifact");
        jdbc.update("DELETE FROM knowledge_data.quality_report");
        jdbc.update("DELETE FROM knowledge_data.sync_artifact");
        jdbc.update("DELETE FROM knowledge_data.source_revision_object");
        jdbc.update("DELETE FROM knowledge_data.source_revision_dataset_field");
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
    void 对象存储来源范围选择和冻结对象集合按原顺序持久化() {
        var source = objectStorageSource(STORE_TENANT, nextSourceId(), "平台对象集合");
        var revision = SourceRevision.objectStorage(
                source,
                new ObjectStorageObjectCollection(
                        new PlatformObjectRangeId("rng_7c90d7a8716e4a79"),
                        7,
                        java.util.List.of(
                                new PlatformObjectReference(
                                        "obj_26d372bcbd4245b7",
                                        "a".repeat(64),
                                        128,
                                        "text/plain; charset=utf-8",
                                        CREATED_AT),
                                new PlatformObjectReference(
                                        "obj_6a6256645ef6487f",
                                        "b".repeat(64),
                                        256,
                                        "text/markdown",
                                        CREATED_AT.plusSeconds(30)))),
                CREATOR,
                CREATED_AT);

        repository.register(source, revision);

        var stored = repository.findRevision(STORE_TENANT, SPACE, source.id(), 1).orElseThrow();
        assertEquals(revision.definitionDigest(), stored.definitionDigest());
        assertEquals(revision.objectStorageObjects(), stored.objectStorageObjects());
        assertFalse(repository.findRevision(CAMPUS_TENANT, SPACE, source.id(), 1).isPresent());
        assertEquals(2, count("source_revision_object", STORE_TENANT));
    }

    @Test
    void 结构化数据集私有CSV与字段映射按原顺序持久化() {
        var source = structuredDatasetSource(STORE_TENANT, nextSourceId(), "客服知识库");
        var definition = new StructuredDatasetCsvDefinition(
                new StructuredDatasetCsvReference(
                        "foundation-v1/tenants/100000000000000001/knowledge-data/datasets/1/1/data.csv",
                        "a".repeat(64),
                        128),
                "csv-mapping-v1",
                "content",
                "title",
                java.util.List.of(
                        new StructuredDatasetFieldMapping(
                                "content", StructuredDatasetLogicalType.TEXT,
                                StructuredDatasetSensitivityLevel.PUBLIC),
                        new StructuredDatasetFieldMapping(
                                "title", StructuredDatasetLogicalType.TEXT,
                                StructuredDatasetSensitivityLevel.INTERNAL)),
                StructuredDatasetRowFilter.NON_BLANK_CONTENT);
        var revision = SourceRevision.structuredDataset(source, definition, CREATOR, CREATED_AT);

        repository.register(source, revision);

        var stored = repository.findRevision(STORE_TENANT, SPACE, source.id(), 1).orElseThrow();
        assertEquals(revision.definitionDigest(), stored.definitionDigest());
        assertEquals(definition, stored.structuredDataset());
        assertFalse(repository.findRevision(CAMPUS_TENANT, SPACE, source.id(), 1).isPresent());
        assertEquals(2, count("source_revision_dataset_field", STORE_TENANT));
    }

    @Test
    void 同步产物和质量结论在同一事务提交且跨租户不可见() {
        var source = source(STORE_TENANT, nextSourceId(), "校园文件");
        var sourceRevision = revision(source, "a".repeat(64));
        repository.register(source, sourceRevision);
        var syncing = startSynchronization(source);
        var completion = complete(syncing, sourceRevision);

        repository.completeSynchronization(completion);

        var completed = completion.completedSource();
        var artifact = completion.artifact();
        var report = completion.qualityReport();
        assertEquals(GovernedDataSourceStatus.READY, completed.status());
        assertEquals(4, completed.version());
        assertEquals(
                artifact.contentDigest(),
                repository.findArtifact(STORE_TENANT, SPACE, artifact.id())
                        .orElseThrow()
                        .contentDigest());
        assertEquals(
                artifact.content(),
                repository.findArtifact(STORE_TENANT, SPACE, artifact.id())
                        .orElseThrow()
                        .content());
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
    void 阻断质量结论不能形成发布操作结果且数据库不写入发布事实() {
        var source = source(STORE_TENANT, nextSourceId(), "发布质量复核文件");
        var sourceRevision = revision(source, "a".repeat(64));
        repository.register(source, sourceRevision);
        var syncing = startSynchronization(source);
        var completion = complete(syncing, sourceRevision);
        repository.completeSynchronization(completion);

        assertThrows(
                DataSourceStateException.class,
                () -> PUBLISHING.publish(
                        completion.completedSource(),
                        completion.artifact(),
                        QualityReport.blocked(completion.artifact(), "file-integrity-v1", 0, 1),
                        CREATED_AT.plusSeconds(120)));

        var stored = repository.findSource(STORE_TENANT, SPACE, source.id()).orElseThrow();
        assertEquals(GovernedDataSourceStatus.READY, stored.status());
        assertEquals(completion.completedSource().version(), stored.version());
        assertEquals(0, count("published_sync_artifact", STORE_TENANT));
    }

    @Test
    void 发布会冻结同步产物质量事实并递增来源修订() {
        var source = source(STORE_TENANT, nextSourceId(), "发布快照文件");
        var sourceRevision = revision(source, "a".repeat(64));
        repository.register(source, sourceRevision);
        var syncing = startSynchronization(source);
        var completion = complete(syncing, sourceRevision);
        repository.completeSynchronization(completion);
        var publication = publish(completion, CREATED_AT.plusSeconds(120));

        repository.publishArtifact(publication);

        var published = publication.publishedSource();
        var artifact = completion.artifact();
        var report = completion.qualityReport();
        assertEquals(GovernedDataSourceStatus.READY, published.status());
        assertEquals(completion.completedSource().version() + 1, published.version());
        var stored = publicationMapper.findByArtifact(
                STORE_TENANT.value(), SPACE.value(), artifact.id().value());
        assertEquals(source.id().value(), stored.getDataSourceId());
        assertEquals(published.version(), stored.getSourceVersion());
        assertEquals(report.status().name(), stored.getQualityStatus());
        assertEquals(report.ruleSetVersion(), stored.getRuleSetVersion());
        assertEquals(publication.publication().publishedAt(), stored.getPublishedAt());
        assertEquals(
                null,
                publicationMapper.findByArtifact(
                        CAMPUS_TENANT.value(), SPACE.value(), artifact.id().value()));
        assertEquals(1, count("published_sync_artifact", STORE_TENANT));
    }

    @Test
    void FILE列表只返回当前租户的最新发布产物摘要() {
        var source = source(STORE_TENANT, nextSourceId(), "可重建文件");
        var sourceRevision = revision(source, "a".repeat(64));
        repository.register(source, sourceRevision);
        var firstSync = startSynchronization(source);
        var firstCompletion = complete(firstSync, sourceRevision);
        repository.completeSynchronization(firstCompletion);
        var firstPublication = publish(firstCompletion, CREATED_AT.plusSeconds(120));
        repository.publishArtifact(firstPublication);
        var firstPublished = firstPublication.publishedSource();
        var secondSync = firstPublished.startSynchronization(firstPublished.version());
        assertTrue(repository.update(secondSync, firstPublished.version()));
        var secondCompletion = complete(secondSync, sourceRevision);
        repository.completeSynchronization(secondCompletion);
        var secondPublication = publish(secondCompletion, CREATED_AT.plusSeconds(240));
        repository.publishArtifact(secondPublication);
        var secondArtifact = secondCompletion.artifact();

        var page = reads.list(STORE_TENANT, SPACE, new com.aiplatform.foundation.shared.contract.paging.PageQuery(1, 20));

        assertEquals(1, page.totalElements());
        var item = page.items().getFirst();
        assertEquals(source.id().toString(), item.dataSourceId());
        assertEquals(secondArtifact.id().toString(), item.publishedArtifactId());
        assertEquals("PASS", item.publishedQualityStatus());
        assertEquals("可重建文件", item.displayName());
        assertEquals(0, reads.list(CAMPUS_TENANT, SPACE,
                new com.aiplatform.foundation.shared.contract.paging.PageQuery(1, 20)).totalElements());
    }

    @Test
    void 不属于来源的修订不能形成同步完成结果且数据库保持同步中状态() {
        var source = source(STORE_TENANT, nextSourceId(), "回滚文件");
        var sourceRevision = revision(source, "a".repeat(64));
        repository.register(source, sourceRevision);
        var syncing = startSynchronization(source);
        var another = source(STORE_TENANT, nextSourceId(), "其他文件");

        assertThrows(DataSourceStateException.class, () -> SYNCHRONIZATION.complete(
                syncing,
                revision(another, "c".repeat(64)),
                new SyncArtifactId(ARTIFACT_IDS.incrementAndGet()),
                content(revision(another, "c".repeat(64))),
                3,
                CREATED_AT.plusSeconds(60)));

        assertEquals(0, count("sync_artifact", STORE_TENANT));
        assertEquals(0, count("quality_report", STORE_TENANT));
        var stored = repository.findSource(STORE_TENANT, SPACE, source.id()).orElseThrow();
        assertEquals(GovernedDataSourceStatus.SYNCING, stored.status());
        assertEquals(syncing.version(), stored.version());
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
        var sourceRevision = revision(source, "a".repeat(64));
        repository.register(source, sourceRevision);
        var syncing = startSynchronization(source);
        var completion = complete(syncing, sourceRevision);
        var tasks = new ArrayList<Callable<Boolean>>();
        for (var index = 0; index < 2; index++) {
            tasks.add(() -> {
                try {
                    repository.completeSynchronization(completion);
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

    private static GovernedDataSource objectStorageSource(TenantId tenantId, DataSourceId id, String name) {
        return GovernedDataSource.create(
                tenantId,
                SPACE,
                id,
                GovernedDataSourceType.OBJECT_STORAGE,
                name,
                CREATOR,
                CREATED_AT);
    }

    private static GovernedDataSource structuredDatasetSource(TenantId tenantId, DataSourceId id, String name) {
        return GovernedDataSource.create(
                tenantId,
                SPACE,
                id,
                GovernedDataSourceType.STRUCTURED_DATASET,
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

    private static FileSynchronizationCompletion complete(
            GovernedDataSource syncing, SourceRevision revision) {
        return SYNCHRONIZATION.complete(
                syncing,
                revision,
                new SyncArtifactId(ARTIFACT_IDS.incrementAndGet()),
                content(revision),
                3,
                CREATED_AT.plusSeconds(60));
    }

    private static com.aiplatform.foundation.knowledgedata.domain.datasource.model.TextContentReference content(
            SourceRevision revision) {
        var file = revision.fileObject();
        return new com.aiplatform.foundation.knowledgedata.domain.datasource.model.TextContentReference(
                Math.toIntExact(revision.revision()),
                file.objectKey(),
                file.contentDigest(),
                file.byteSize(),
                com.aiplatform.foundation.knowledgedata.domain.datasource.model.TextContentReference.UTF8_TEXT,
                revision.createdAt());
    }

    private static FileSyncArtifactPublication publish(
            FileSynchronizationCompletion completion, Instant publishedAt) {
        return PUBLISHING.publish(
                completion.completedSource(),
                completion.artifact(),
                completion.qualityReport(),
                publishedAt);
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
                ObjectStorageObjectMapper objectStorageObjectMapper,
                StructuredDatasetFieldMapper structuredDatasetFieldMapper,
                SyncArtifactMapper artifactMapper,
                QualityReportMapper qualityMapper,
                PublishedSyncArtifactMapper publicationMapper,
                PlatformTransactionManager transactions) {
            return new PostgresGovernedDataSourceRepository(
                    sourceMapper,
                    revisionMapper,
                    objectStorageObjectMapper,
                    structuredDatasetFieldMapper,
                    artifactMapper,
                    qualityMapper,
                    publicationMapper,
                    transactions);
        }

        @Bean
        GovernedFileDataSourceReadPort governedFileDataSourceReadPort(
                GovernedFileDataSourceReadMapper mapper) {
            return new PostgresGovernedFileDataSourceReadAdapter(mapper);
        }
    }
}
