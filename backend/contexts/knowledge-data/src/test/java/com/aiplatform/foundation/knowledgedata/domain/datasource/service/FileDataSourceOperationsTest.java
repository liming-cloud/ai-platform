package com.aiplatform.foundation.knowledgedata.domain.datasource.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceStateException;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceStatus;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceType;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SyncArtifact;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SyncArtifactId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.quality.QualityReport;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.FileSourceObjectReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.SourceRevision;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FileDataSourceOperationsTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final KnowledgeSpaceId SPACE = new KnowledgeSpaceId(210000000000000001L);
    private static final DataSourceId SOURCE_ID = new DataSourceId(230000000000000001L);
    private static final CreatorId CREATOR = new CreatorId(4921825928971026434L);
    private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");

    private final FileDataSourceSynchronization synchronization = new FileDataSourceSynchronization();
    private final FileSyncArtifactPublishing publishing = new FileSyncArtifactPublishing();

    @Test
    void 应以当前文件修订完成同步并冻结质量结论() {
        var source = readySource().startSynchronization(2);
        var completion = synchronization.complete(
                source,
                fileRevision(source),
                new SyncArtifactId(240000000000000001L),
                "a".repeat(64),
                1,
                NOW);

        assertEquals(GovernedDataSourceStatus.READY, completion.completedSource().status());
        assertEquals(4, completion.completedSource().version());
        assertEquals(source.id(), completion.artifact().dataSourceId());
        assertEquals("file-integrity-v1", completion.qualityReport().ruleSetVersion());
    }

    @Test
    void 应拒绝不属于来源的文件修订() {
        var source = readySource().startSynchronization(2);
        var otherSource = readySource(new DataSourceId(230000000000000002L)).startSynchronization(2);

        assertThrows(DataSourceStateException.class, () -> synchronization.complete(
                source,
                fileRevision(otherSource),
                new SyncArtifactId(240000000000000002L),
                "a".repeat(64),
                1,
                NOW));
    }

    @Test
    void 应拒绝未处于同步中的来源() {
        var source = readySource();

        assertThrows(DataSourceStateException.class, () -> synchronization.complete(
                source,
                fileRevision(source),
                new SyncArtifactId(240000000000000003L),
                "a".repeat(64),
                1,
                NOW));
    }

    @Test
    void 应拒绝以非FILE来源开始文件同步() {
        var source = GovernedDataSource.create(
                TENANT,
                SPACE,
                SOURCE_ID,
                GovernedDataSourceType.OBJECT_STORAGE,
                "受控对象范围",
                CREATOR,
                NOW);

        assertThrows(DataSourceStateException.class, () -> synchronization.begin(source, 0));
    }

    @Test
    void 应拒绝阻断质量结论的产物发布() {
        var source = readySource();
        var artifact = artifact(source);

        assertThrows(DataSourceStateException.class, () -> publishing.publish(
                source,
                artifact,
                QualityReport.blocked(artifact, "file-integrity-v1", 0, 1),
                NOW));
    }

    @Test
    void 应拒绝不属于来源的产物发布() {
        var source = readySource();
        var otherArtifact = artifact(readySource(new DataSourceId(230000000000000002L)));

        assertThrows(DataSourceStateException.class, () -> publishing.publish(
                source,
                otherArtifact,
                QualityReport.passed(otherArtifact, "file-integrity-v1"),
                NOW));
    }

    private static GovernedDataSource readySource() {
        return readySource(SOURCE_ID);
    }

    private static GovernedDataSource readySource(DataSourceId id) {
        return GovernedDataSource.create(
                        TENANT, SPACE, id, GovernedDataSourceType.FILE, "受控文件", CREATOR, NOW)
                .startProbe(0)
                .completeProbe(1);
    }

    private static SourceRevision fileRevision(GovernedDataSource source) {
        return SourceRevision.file(
                source,
                new FileSourceObjectReference(
                        "foundation-v1/tenants/1/knowledge-data/files/2/1/content.txt",
                        "a".repeat(64),
                        1,
                        FileSourceObjectReference.UTF8_TEXT),
                CREATOR,
                NOW);
    }

    private static SyncArtifact artifact(GovernedDataSource source) {
        return SyncArtifact.create(
                new SyncArtifactId(240000000000000004L),
                source.tenantId(),
                source.knowledgeSpaceId(),
                source.id(),
                source.currentRevision(),
                "a".repeat(64),
                1,
                NOW);
    }
}
