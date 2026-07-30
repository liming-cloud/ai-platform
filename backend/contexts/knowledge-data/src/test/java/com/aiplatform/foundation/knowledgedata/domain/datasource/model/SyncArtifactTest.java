package com.aiplatform.foundation.knowledgedata.domain.datasource.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.knowledgedata.domain.datasource.quality.QualityReport;
import com.aiplatform.foundation.knowledgedata.domain.datasource.quality.QualityStatus;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SyncArtifactTest {

    private static final TenantId TENANT_ID = new TenantId(100000000000000001L);
    private static final KnowledgeSpaceId SPACE_ID = new KnowledgeSpaceId(210000000000000001L);
    private static final DataSourceId SOURCE_ID = new DataSourceId(220000000000000002L);
    private static final Instant SYNCHRONIZED_AT = Instant.parse("2026-07-29T01:00:00Z");

    @Test
    void 同步产物冻结来源修订和内容摘要() {
        var artifact = SyncArtifact.create(
                new SyncArtifactId(230000000000000001L),
                TENANT_ID,
                SPACE_ID,
                SOURCE_ID,
                3,
                content(),
                42,
                SYNCHRONIZED_AT);

        assertEquals(3, artifact.sourceRevision());
        assertEquals("b".repeat(64), artifact.contentDigest());
        assertEquals(content(), artifact.content());
        assertEquals(42, artifact.recordCount());
        assertEquals(SYNCHRONIZED_AT, artifact.synchronizedAt());
    }

    @Test
    void 质量阻断结论不能被视为可发布产物() {
        var artifact = artifact();
        var blocked = QualityReport.blocked(artifact, "quality-rules-v1", 2, 1);

        assertEquals(QualityStatus.BLOCK, blocked.status());
        assertThrows(DataSourceStateException.class, blocked::requirePublishable);
    }

    @Test
    void 通过或警告质量结论允许进入后续发布判断() {
        var artifact = artifact();

        assertEquals(artifact, QualityReport.passed(artifact, "quality-rules-v1").requirePublishable());
        assertEquals(artifact, QualityReport.warned(artifact, "quality-rules-v1", 1).requirePublishable());
    }

    @Test
    void 同步产物拒绝无效修订摘要和计数() {
        assertThrows(IllegalArgumentException.class, () -> SyncArtifact.create(
                new SyncArtifactId(230000000000000001L),
                TENANT_ID,
                SPACE_ID,
                SOURCE_ID,
                0,
                content(),
                42,
                SYNCHRONIZED_AT));
        assertThrows(NullPointerException.class, () -> SyncArtifact.create(
                new SyncArtifactId(230000000000000001L),
                TENANT_ID,
                SPACE_ID,
                SOURCE_ID,
                3,
                null,
                -1,
                SYNCHRONIZED_AT));
    }

    private static SyncArtifact artifact() {
        return SyncArtifact.create(
                new SyncArtifactId(230000000000000001L),
                TENANT_ID,
                SPACE_ID,
                SOURCE_ID,
                3,
                content(),
                42,
                SYNCHRONIZED_AT);
    }

    private static TextContentReference content() {
        return new TextContentReference(
                3,
                "foundation-v1/tenants/100000000000000001/knowledge-data/artifacts/230000000000000001.txt",
                "b".repeat(64),
                42,
                TextContentReference.UTF8_TEXT,
                SYNCHRONIZED_AT);
    }
}
