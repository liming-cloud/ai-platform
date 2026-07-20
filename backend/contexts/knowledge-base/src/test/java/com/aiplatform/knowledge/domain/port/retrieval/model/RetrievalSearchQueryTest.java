package com.aiplatform.knowledge.domain.port.retrieval.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetrievalSearchQueryTest {
    @Test
    void normalizesRetrievalSearchFactsBeforeRepositoryUse() {
        RetrievalSearchQuery query = new RetrievalSearchQuery(
                TenantId.of("2001"),
                List.of(" 7001 ", "7001", " 7002 "),
                " 员工制度 ",
                new float[] {0.1f, 0.2f},
                " embedding-model-1 ",
                " kb-active ",
                List.of(" tenant:2001 ", "user:1001", "tenant:2001"),
                Instant.parse("2026-05-25T00:00:00Z"),
                10,
                0.65
        );

        assertThat(query.knowledgeBaseIds()).containsExactly("7001", "7002");
        assertThat(query.query()).isEqualTo("员工制度");
        assertThat(query.embeddingModel()).isEqualTo("embedding-model-1");
        assertThat(query.indexVersion()).isEqualTo("kb-active");
        assertThat(query.aclTokens()).containsExactly("tenant:2001", "user:1001");
        assertThat(query.visibleAt()).isEqualTo(Instant.parse("2026-05-25T00:00:00Z"));
        assertThat(query.queryEmbedding()).containsExactly(0.1f, 0.2f);
    }

    @Test
    void rejectsBlankKnowledgeBaseIdInsteadOfFilteringBadInput() {
        assertThatThrownBy(() -> query(List.of("7001", " "), new float[] {0.1f}, 0.65))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("知识库范围不能为空白");
    }

    @Test
    void rejectsNullKnowledgeBaseIdInsteadOfFilteringBadInput() {
        assertThatThrownBy(() -> query(Arrays.asList("7001", null), new float[] {0.1f}, 0.65))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("知识库范围不能为空白");
    }

    @Test
    void rejectsInvalidQueryEmbeddingInsteadOfPassingDamagedVectorFacts() {
        assertThatThrownBy(() -> query(List.of("7001"), new float[0], 0.65))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("查询向量不能为空");

        assertThatThrownBy(() -> query(List.of("7001"), new float[] {Float.NaN}, 0.65))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("查询向量不能包含非有限数值");

        assertThatThrownBy(() -> query(List.of("7001"), new float[] {0.1f}, Double.NaN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("相似度阈值必须在 0 到 1 之间");
    }

    @Test
    void rejectsMissingAclSnapshotOrVisibilityTimeInsteadOfRunningUnscopedSql() {
        assertThatThrownBy(() -> queryWithSecurityFacts(List.of(), Instant.parse("2026-05-25T00:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("权限快照不能为空");

        assertThatThrownBy(() -> queryWithSecurityFacts(List.of("tenant:2001", " "), Instant.parse("2026-05-25T00:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("权限快照不能为空白");

        assertThatThrownBy(() -> queryWithSecurityFacts(List.of("tenant:2001"), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("可见性校验时间不能为空");
    }

    private static RetrievalSearchQuery query(List<String> knowledgeBaseIds, float[] embedding, double scoreThreshold) {
        return new RetrievalSearchQuery(
                TenantId.of("2001"),
                knowledgeBaseIds,
                "员工制度",
                embedding,
                "embedding-model-1",
                "kb-active",
                List.of("tenant:2001", "user:1001"),
                Instant.parse("2026-05-25T00:00:00Z"),
                10,
                scoreThreshold
        );
    }

    private static RetrievalSearchQuery queryWithSecurityFacts(List<String> aclTokens, Instant visibleAt) {
        return new RetrievalSearchQuery(
                TenantId.of("2001"),
                List.of("7001"),
                "员工制度",
                new float[] {0.1f},
                "embedding-model-1",
                "kb-active",
                aclTokens,
                visibleAt,
                10,
                0.65
        );
    }
}
