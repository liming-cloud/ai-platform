package com.aiplatform.foundation.knowledgedata.application.port.out.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.TextContentReference;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class IndexSourceSnapshotTest {

    @Test
    void 来源身份摘要由冻结来源事实决定而非仅正文摘要决定() {
        var content = new TextContentReference(
                1,
                "foundation-v1/test/content.txt",
                "a".repeat(64),
                10,
                TextContentReference.UTF8_TEXT,
                Instant.parse("2026-07-30T00:00:00Z"));
        var first = new IndexSourceSnapshot(
                new DataSourceId(220000000000000001L), content);
        var sameSnapshot = new IndexSourceSnapshot(
                new DataSourceId(220000000000000001L), content);
        var anotherSource = new IndexSourceSnapshot(
                new DataSourceId(220000000000000002L), content);
        var changedRevision = new IndexSourceSnapshot(
                IndexSourceKind.LEGACY_TEXT_CONTENT,
                new DataSourceId(220000000000000001L),
                2,
                null,
                null,
                null,
                content);

        assertEquals(first.sourceIdentityDigest(), sameSnapshot.sourceIdentityDigest());
        assertNotEquals(first.sourceIdentityDigest(), anotherSource.sourceIdentityDigest());
        assertNotEquals(first.sourceIdentityDigest(), changedRevision.sourceIdentityDigest());
    }
}
