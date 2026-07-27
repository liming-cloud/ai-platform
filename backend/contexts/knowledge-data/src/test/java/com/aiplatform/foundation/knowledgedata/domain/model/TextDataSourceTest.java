package com.aiplatform.foundation.knowledgedata.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.security.identity.MembershipId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TextDataSourceTest {

    private static final TenantId TENANT_ID =
            new TenantId(100000000000000001L);
    private static final KnowledgeSpaceId SPACE_ID =
            new KnowledgeSpaceId(210000000000000001L);
    private static final DataSourceId SOURCE_ID =
            new DataSourceId(220000000000000001L);
    private static final MembershipId CREATOR_ID =
            new MembershipId(4921825928971026434L);
    private static final Instant CREATED_AT = Instant.parse("2026-07-27T00:00:00Z");

    @Test
    void 新建文本数据源从无内容草稿开始() {
        var source = TextDataSource.create(
                TENANT_ID,
                SPACE_ID,
                SOURCE_ID,
                "门店商品说明",
                CREATOR_ID,
                CREATED_AT);

        assertEquals(DataSourceStatus.DRAFT, source.status());
        assertEquals(0, source.version());
        assertEquals(0, source.contentVersion());
        assertEquals(0, source.publishedContentVersion());
        assertEquals(null, source.currentContent());
    }

    @Test
    void 登记连续内容版本后进入内容就绪状态() {
        var source = draft();
        var content = content(1);

        var ready = source.attach(content);

        assertEquals(DataSourceStatus.CONTENT_READY, ready.status());
        assertEquals(1, ready.version());
        assertEquals(1, ready.contentVersion());
        assertEquals(content, ready.currentContent());
    }

    @Test
    void 发布当前内容后固化发布版本() {
        var ready = draft().attach(content(1));

        var published = ready.publish();

        assertEquals(DataSourceStatus.PUBLISHED, published.status());
        assertEquals(2, published.version());
        assertEquals(1, published.contentVersion());
        assertEquals(1, published.publishedContentVersion());
    }

    @Test
    void 无内容不能发布且内容版本必须连续() {
        assertThrows(IllegalStateException.class, () -> draft().publish());
        assertThrows(
                IllegalArgumentException.class,
                () -> draft().attach(content(2)));
    }

    @Test
    void 已发布数据源上传新内容后重新进入内容就绪状态() {
        var published = draft().attach(content(1)).publish();

        var ready = published.attach(content(2));

        assertEquals(DataSourceStatus.CONTENT_READY, ready.status());
        assertEquals(2, ready.contentVersion());
        assertEquals(1, ready.publishedContentVersion());
        assertEquals(3, ready.version());
    }

    @Test
    void 内容就绪状态必须存在尚未发布的新版本() {
        assertThrows(IllegalArgumentException.class, () -> TextDataSource.reconstitute(
                TENANT_ID,
                SPACE_ID,
                SOURCE_ID,
                "门店商品说明",
                DataSourceStatus.CONTENT_READY,
                2,
                1,
                1,
                content(1),
                CREATOR_ID,
                CREATED_AT));
    }

    private static TextDataSource draft() {
        return TextDataSource.create(
                TENANT_ID,
                SPACE_ID,
                SOURCE_ID,
                "门店商品说明",
                CREATOR_ID,
                CREATED_AT);
    }

    private static TextContentReference content(int version) {
        return new TextContentReference(
                version,
                "foundation-v1/tenant/space/source/" + version + "/content.txt",
                "a".repeat(64),
                128,
                "text/plain; charset=UTF-8",
                CREATED_AT.plusSeconds(version));
    }
}
