package com.aiplatform.foundation.assistantdelivery.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.organizationaccess.api.model.MembershipId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssistantVersionTest {

    private static final TenantId TENANT =
            new TenantId(UUID.fromString("10000000-0000-0000-0000-000000000001"));
    private static final AssistantId ASSISTANT_ID =
            new AssistantId(UUID.fromString("20000000-0000-0000-0000-000000000001"));
    private static final AssistantVersionId VERSION_ID =
            new AssistantVersionId(UUID.fromString("30000000-0000-0000-0000-000000000001"));
    private static final MembershipId CREATOR =
            new MembershipId(UUID.fromString("40000000-0000-0000-0000-000000000001"));
    private static final Instant NOW = Instant.parse("2026-07-27T00:00:00Z");

    @Test
    void 应冻结模型索引和回答策略并发布为不可变快照() {
        var version = draft(512);

        var published = version.publish(NOW.plusSeconds(30));

        assertEquals(AssistantVersionStatus.PUBLISHED, published.status());
        assertEquals(1, published.version());
        assertEquals(7, published.chatDeploymentRevision());
        assertEquals(5, published.indexVersionRevision());
        assertEquals(3, published.embeddingDeploymentRevision());
        assertEquals(8, published.embeddingDimension());
        assertEquals(CitationPolicy.REQUIRED, published.citationPolicy());
        assertEquals(RefusalPolicy.INSUFFICIENT_EVIDENCE, published.refusalPolicy());
        assertThrows(
                AssistantVersionStateException.class,
                () -> published.publish(NOW.plusSeconds(60)));
    }

    @Test
    void 回答上限不得突破已发布模型上限() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AssistantVersion.draft(
                        TENANT,
                        VERSION_ID,
                        ASSISTANT_ID,
                        1,
                        UUID.randomUUID(),
                        7,
                        1024,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        5,
                        UUID.randomUUID(),
                        3,
                        8,
                        "只依据证据回答。",
                        2048,
                        5,
                        0.2,
                        CitationPolicy.REQUIRED,
                        RefusalPolicy.INSUFFICIENT_EVIDENCE,
                        CREATOR,
                        NOW));
    }

    @Test
    void 必须使用要求引用和证据不足拒答的可信策略() {
        assertThrows(
                IllegalArgumentException.class,
                () -> draftWithPolicies(
                        CitationPolicy.OPTIONAL,
                        RefusalPolicy.INSUFFICIENT_EVIDENCE));
        assertThrows(
                IllegalArgumentException.class,
                () -> draftWithPolicies(
                        CitationPolicy.REQUIRED,
                        RefusalPolicy.MODEL_DISCRETION));
    }

    private static AssistantVersion draft(int maxOutputTokens) {
        return AssistantVersion.draft(
                TENANT,
                VERSION_ID,
                ASSISTANT_ID,
                1,
                UUID.fromString("50000000-0000-0000-0000-000000000001"),
                7,
                1024,
                UUID.fromString("60000000-0000-0000-0000-000000000001"),
                UUID.fromString("70000000-0000-0000-0000-000000000001"),
                5,
                UUID.fromString("80000000-0000-0000-0000-000000000001"),
                3,
                8,
                "只依据编号证据回答，并在结论后标注引用。",
                maxOutputTokens,
                5,
                0.2,
                CitationPolicy.REQUIRED,
                RefusalPolicy.INSUFFICIENT_EVIDENCE,
                CREATOR,
                NOW);
    }

    private static AssistantVersion draftWithPolicies(
            CitationPolicy citationPolicy,
            RefusalPolicy refusalPolicy) {
        return AssistantVersion.draft(
                TENANT,
                VERSION_ID,
                ASSISTANT_ID,
                1,
                UUID.randomUUID(),
                7,
                1024,
                UUID.randomUUID(),
                UUID.randomUUID(),
                5,
                UUID.randomUUID(),
                3,
                8,
                "只依据证据回答。",
                512,
                5,
                0.2,
                citationPolicy,
                refusalPolicy,
                CREATOR,
                NOW);
    }
}
