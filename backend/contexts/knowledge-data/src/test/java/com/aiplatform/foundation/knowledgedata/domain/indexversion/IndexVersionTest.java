package com.aiplatform.foundation.knowledgedata.domain.indexversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.domain.model.KnowledgeSpaceId;
import com.aiplatform.foundation.security.identity.MembershipId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class IndexVersionTest {

    private static final TenantId TENANT_ID =
            new TenantId(100000000000000001L);
    private static final KnowledgeSpaceId SPACE_ID =
            new KnowledgeSpaceId(210000000000000001L);
    private static final IndexVersionId INDEX_ID =
            new IndexVersionId(230000000000000001L);
    private static final MembershipId MEMBER_ID =
            new MembershipId(1843651857942052866L);
    private static final Instant CREATED_AT = Instant.parse("2026-07-27T00:00:00Z");
    private static final EmbeddingConfiguration EMBEDDING =
            new EmbeddingConfiguration(
                    320000000000000001L,
                    1,
                    128);

    @Test
    void 构建就绪激活和替代形成不可逆状态链() {
        var building = IndexVersion.start(
                TENANT_ID,
                SPACE_ID,
                INDEX_ID,
                "a".repeat(64),
                "paragraph-window-v1",
                EMBEDDING,
                MEMBER_ID,
                CREATED_AT);

        var ready = building.markReady(
                "b".repeat(64),
                8,
                CREATED_AT.plusSeconds(10));
        var active = ready.activate(CREATED_AT.plusSeconds(20));
        var superseded = active.supersede(CREATED_AT.plusSeconds(30));

        assertEquals(IndexVersionStatus.BUILDING, building.status());
        assertEquals(IndexVersionStatus.READY, ready.status());
        assertEquals(IndexVersionStatus.ACTIVE, active.status());
        assertEquals(IndexVersionStatus.SUPERSEDED, superseded.status());
        assertEquals(3, superseded.version());
        assertEquals(8, superseded.chunkCount());
        assertEquals("b".repeat(64), superseded.buildDigest());
    }

    @Test
    void 失败版本和已替代版本不能重新激活() {
        var building = IndexVersion.start(
                TENANT_ID,
                SPACE_ID,
                INDEX_ID,
                "a".repeat(64),
                "paragraph-window-v1",
                EMBEDDING,
                MEMBER_ID,
                CREATED_AT);
        var failed = building.fail("EMBEDDING_DIMENSION_MISMATCH");
        var superseded = building
                .markReady("b".repeat(64), 1, CREATED_AT.plusSeconds(1))
                .activate(CREATED_AT.plusSeconds(2))
                .supersede(CREATED_AT.plusSeconds(3));

        assertEquals(IndexVersionStatus.FAILED, failed.status());
        assertEquals("EMBEDDING_DIMENSION_MISMATCH", failed.failureCode());
        assertThrows(
                IndexVersionStateException.class,
                () -> failed.activate(CREATED_AT.plusSeconds(4)));
        assertThrows(
                IndexVersionStateException.class,
                () -> superseded.activate(CREATED_AT.plusSeconds(4)));
    }

    @Test
    void 构建摘要和分块数量不合法时不能进入就绪() {
        var building = IndexVersion.start(
                TENANT_ID,
                SPACE_ID,
                INDEX_ID,
                "a".repeat(64),
                "paragraph-window-v1",
                EMBEDDING,
                MEMBER_ID,
                CREATED_AT);

        assertThrows(
                IllegalArgumentException.class,
                () -> building.markReady("not-a-digest", 1, CREATED_AT));
        assertThrows(
                IllegalArgumentException.class,
                () -> building.markReady("b".repeat(64), 0, CREATED_AT));
    }
}
