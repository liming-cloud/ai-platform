package com.aiplatform.foundation.knowledgedata.domain.datasource.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceStateException;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceStatus;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceType;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SyncArtifactId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.TextContentReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.quality.QualityStatus;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.SourceRevision;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetCsvDefinition;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetCsvReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetFieldMapping;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetLogicalType;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetRowFilter;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetSensitivityLevel;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class StructuredDatasetCsvSynchronizationTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final KnowledgeSpaceId SPACE = new KnowledgeSpaceId(210000000000000001L);
    private static final DataSourceId SOURCE_ID = new DataSourceId(230000000000000001L);
    private static final CreatorId CREATOR = new CreatorId(4921825928971026434L);
    private static final Instant NOW = Instant.parse("2026-07-30T10:30:00Z");

    private final StructuredDatasetCsvSynchronization synchronization =
            new StructuredDatasetCsvSynchronization();

    @Test
    void 应以当前结构化数据集修订完成同步并冻结通过质量结论() {
        var ready = readySource(StructuredDatasetRowFilter.ALL_ROWS);
        var syncing = synchronization.begin(ready, ready.version());

        var completion = synchronization.complete(
                syncing,
                revision(ready, StructuredDatasetRowFilter.ALL_ROWS),
                new SyncArtifactId(240000000000000001L),
                normalizedContent("a"),
                2,
                0,
                NOW);

        assertEquals(GovernedDataSourceStatus.READY, completion.completedSource().status());
        assertEquals(4, completion.completedSource().version());
        assertEquals(QualityStatus.PASS, completion.qualityReport().status());
        assertEquals("dataset-csv-v1", completion.qualityReport().ruleSetVersion());
        assertEquals(2, completion.artifact().recordCount());
    }

    @Test
    void 非空内容过滤跳过空行时应冻结告警质量结论() {
        var ready = readySource(StructuredDatasetRowFilter.NON_BLANK_CONTENT);
        var syncing = synchronization.begin(ready, ready.version());

        var completion = synchronization.complete(
                syncing,
                revision(ready, StructuredDatasetRowFilter.NON_BLANK_CONTENT),
                new SyncArtifactId(240000000000000002L),
                normalizedContent("b"),
                1,
                3,
                NOW);

        assertEquals(QualityStatus.WARN, completion.qualityReport().status());
        assertEquals(3, completion.qualityReport().warningCount());
    }

    @Test
    void 通过或告警质量结论的当前数据集产物可被显式发布() {
        var ready = readySource(StructuredDatasetRowFilter.NON_BLANK_CONTENT);
        var syncing = synchronization.begin(ready, ready.version());
        var completion = synchronization.complete(syncing, revision(ready, StructuredDatasetRowFilter.NON_BLANK_CONTENT),
                new SyncArtifactId(240000000000000005L), normalizedContent("e"), 1, 1, NOW);
        var publication = new StructuredDatasetSyncArtifactPublishing().publish(
                completion.completedSource(), completion.artifact(), completion.qualityReport(), NOW);
        assertEquals(GovernedDataSourceStatus.READY, publication.publishedSource().status());
        assertEquals(QualityStatus.WARN, publication.publication().qualityStatus());
    }

    @Test
    void 所有行策略不得跳过空内容行() {
        var ready = readySource(StructuredDatasetRowFilter.ALL_ROWS);
        var syncing = synchronization.begin(ready, ready.version());

        assertThrows(DataSourceStateException.class, () -> synchronization.complete(
                syncing,
                revision(ready, StructuredDatasetRowFilter.ALL_ROWS),
                new SyncArtifactId(240000000000000003L),
                normalizedContent("c"),
                1,
                1,
                NOW));
    }

    @Test
    void 应拒绝非结构化数据集来源和不属于来源的修订() {
        var file = GovernedDataSource.create(
                TENANT, SPACE, SOURCE_ID, GovernedDataSourceType.FILE, "文件", CREATOR, NOW);
        assertThrows(DataSourceStateException.class, () -> synchronization.begin(file, 0));

        var ready = readySource(StructuredDatasetRowFilter.ALL_ROWS);
        var syncing = synchronization.begin(ready, ready.version());
        var other = readySource(new DataSourceId(230000000000000002L), StructuredDatasetRowFilter.ALL_ROWS);
        assertThrows(DataSourceStateException.class, () -> synchronization.complete(
                syncing,
                revision(other, StructuredDatasetRowFilter.ALL_ROWS),
                new SyncArtifactId(240000000000000004L),
                normalizedContent("d"),
                1,
                0,
                NOW));
    }

    private static GovernedDataSource readySource(StructuredDatasetRowFilter filter) {
        return readySource(SOURCE_ID, filter);
    }

    private static GovernedDataSource readySource(DataSourceId id, StructuredDatasetRowFilter filter) {
        return GovernedDataSource.create(
                        TENANT,
                        SPACE,
                        id,
                        GovernedDataSourceType.STRUCTURED_DATASET,
                        "受控结构化数据集",
                        CREATOR,
                        NOW)
                .startProbe(0)
                .completeProbe(1);
    }

    private static SourceRevision revision(
            GovernedDataSource source, StructuredDatasetRowFilter filter) {
        return SourceRevision.structuredDataset(
                source,
                new StructuredDatasetCsvDefinition(
                        new StructuredDatasetCsvReference(
                                "foundation-v1/tenants/1/knowledge-data/datasets/2/1/data.csv",
                                "a".repeat(64),
                                32),
                        "csv-mapping-v1",
                        "content",
                        "title",
                        List.of(
                                new StructuredDatasetFieldMapping(
                                        "content",
                                        StructuredDatasetLogicalType.TEXT,
                                        StructuredDatasetSensitivityLevel.PUBLIC),
                                new StructuredDatasetFieldMapping(
                                        "title",
                                        StructuredDatasetLogicalType.TEXT,
                                        StructuredDatasetSensitivityLevel.PUBLIC)),
                        filter),
                CREATOR,
                NOW);
    }

    private static TextContentReference normalizedContent(String character) {
        return new TextContentReference(
                1,
                "foundation-v1/tenants/1/knowledge-data/artifacts/" + character + ".txt",
                character.repeat(64),
                1,
                TextContentReference.UTF8_TEXT,
                NOW);
    }
}
