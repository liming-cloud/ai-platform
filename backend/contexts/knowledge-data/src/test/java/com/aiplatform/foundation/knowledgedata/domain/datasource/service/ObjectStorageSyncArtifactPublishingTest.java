package com.aiplatform.foundation.knowledgedata.domain.datasource.service;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceStateException;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceType;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SyncArtifact;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SyncArtifactId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.TextContentReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.quality.QualityReport;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ObjectStorageSyncArtifactPublishingTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final KnowledgeSpaceId SPACE = new KnowledgeSpaceId(210000000000000001L);
    private static final DataSourceId SOURCE_ID = new DataSourceId(230000000000000001L);
    private static final Instant NOW = Instant.parse("2026-07-30T07:00:00Z");

    @Test
    void 对象存储同步产物质量不是通过时禁止发布() {
        var source = readySource();
        var artifact = artifact(source);

        assertThrows(DataSourceStateException.class,
                () -> new ObjectStorageSynchronization.ArtifactPublishing().publish(
                        source,
                        artifact,
                        QualityReport.warned(artifact, "object-storage-text-v1", 1),
                        NOW));
    }

    private static GovernedDataSource readySource() {
        return GovernedDataSource.create(
                        TENANT,
                        SPACE,
                        SOURCE_ID,
                        GovernedDataSourceType.OBJECT_STORAGE,
                        "平台对象来源",
                        new CreatorId(4921825928971026434L),
                        NOW)
                .startProbe(0)
                .completeProbe(1)
                .startSynchronization(2)
                .completeSynchronization(3);
    }

    private static SyncArtifact artifact(GovernedDataSource source) {
        return SyncArtifact.create(
                new SyncArtifactId(240000000000000001L),
                TENANT,
                SPACE,
                source.id(),
                source.currentRevision(),
                new TextContentReference(
                        1,
                        "foundation-v1/tenants/1/knowledge-data/artifacts/object-storage.txt",
                        "a".repeat(64),
                        1,
                        TextContentReference.UTF8_TEXT,
                        NOW),
                1,
                NOW);
    }
}
