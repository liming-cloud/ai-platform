package com.aiplatform.foundation.knowledgedata.domain.datasource.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.knowledgedata.domain.datasource.quality.QualityReport;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PublishedSyncArtifactTest {

    @Test
    void 只允许发布质量非阻断的同步产物() {
        var artifact = artifact();
        var published = PublishedSyncArtifact.create(
                artifact, QualityReport.warned(artifact, "file-integrity-v1", 1),
                Instant.parse("2026-07-29T08:00:00Z"));

        assertEquals(artifact, published.artifact());
        assertEquals("WARN", published.qualityStatus().name());
        assertThrows(DataSourceStateException.class, () -> PublishedSyncArtifact.create(
                artifact, QualityReport.blocked(artifact, "file-integrity-v1", 0, 1),
                Instant.parse("2026-07-29T08:00:00Z")));
    }

    private static SyncArtifact artifact() {
        return SyncArtifact.create(new SyncArtifactId(240000000000000001L),
                new TenantId(100000000000000001L), new KnowledgeSpaceId(210000000000000001L),
                new DataSourceId(230000000000000001L), 1, new TextContentReference(
                        1,
                        "foundation-v1/tenants/1/knowledge-data/artifacts/240000000000000001.txt",
                        "a".repeat(64),
                        1,
                        TextContentReference.UTF8_TEXT,
                        Instant.parse("2026-07-29T07:00:00Z")), 1,
                Instant.parse("2026-07-29T07:00:00Z"));
    }
}
