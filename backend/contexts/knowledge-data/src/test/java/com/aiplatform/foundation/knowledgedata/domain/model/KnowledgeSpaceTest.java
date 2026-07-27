package com.aiplatform.foundation.knowledgedata.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.security.identity.MembershipId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class KnowledgeSpaceTest {

    private static final TenantId TENANT_ID =
            new TenantId(100000000000000001L);
    private static final MembershipId CREATOR_ID =
            new MembershipId(4921825928971026434L);
    private static final Instant CREATED_AT = Instant.parse("2026-07-27T00:00:00Z");

    @Test
    void 创建草稿知识空间并保留明确租户边界() {
        var space = KnowledgeSpace.create(
                TENANT_ID,
                new KnowledgeSpaceId(210000000000000001L),
                "门店商品知识",
                "供门店机器人回答商品问题",
                CREATOR_ID,
                CREATED_AT);

        assertEquals(TENANT_ID, space.tenantId());
        assertEquals("门店商品知识", space.name());
        assertEquals("供门店机器人回答商品问题", space.description());
        assertEquals(KnowledgeSpaceStatus.DRAFT, space.status());
        assertEquals(0, space.version());
        assertEquals(CREATED_AT, space.createdAt());
    }

    @Test
    void 拒绝首尾空白和控制字符名称() {
        assertThrows(IllegalArgumentException.class, () -> KnowledgeSpace.create(
                TENANT_ID,
                new KnowledgeSpaceId(210000000000000002L),
                " 门店商品知识",
                null,
                CREATOR_ID,
                CREATED_AT));
        assertThrows(IllegalArgumentException.class, () -> KnowledgeSpace.create(
                TENANT_ID,
                new KnowledgeSpaceId(210000000000000003L),
                "门店\n商品知识",
                null,
                CREATOR_ID,
                CREATED_AT));
    }

    @Test
    void 拒绝超过页面预算的名称和说明() {
        assertThrows(IllegalArgumentException.class, () -> KnowledgeSpace.create(
                TENANT_ID,
                new KnowledgeSpaceId(210000000000000004L),
                "知".repeat(81),
                null,
                CREATOR_ID,
                CREATED_AT));
        assertThrows(IllegalArgumentException.class, () -> KnowledgeSpace.create(
                TENANT_ID,
                new KnowledgeSpaceId(210000000000000005L),
                "校园知识",
                "说".repeat(501),
                CREATOR_ID,
                CREATED_AT));
    }
}
