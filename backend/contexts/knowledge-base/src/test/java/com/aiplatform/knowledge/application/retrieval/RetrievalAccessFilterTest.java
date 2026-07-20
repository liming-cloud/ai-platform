package com.aiplatform.knowledge.domain.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.knowledge.domain.KnowledgeDocumentChunkMetadata;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetrievalAccessFilterTest {
    @Test
    void requiresTenantKnowledgeBaseAndAclTokens() {
        assertThatThrownBy(() -> new RetrievalAccessFilter(
                "",
                List.of("7001"),
                List.of("tenant:2001"),
                Instant.parse("2026-05-24T00:00:00Z")
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("租户");

        assertThatThrownBy(() -> new RetrievalAccessFilter(
                "2001",
                List.of(),
                List.of("tenant:2001"),
                Instant.parse("2026-05-24T00:00:00Z")
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("知识库");

        assertThatThrownBy(() -> new RetrievalAccessFilter(
                "2001",
                List.of("7001"),
                List.of(),
                Instant.parse("2026-05-24T00:00:00Z")
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("权限");
    }

    @Test
    void normalizesRetrievalAccessFactsBeforeAclMatching() {
        RetrievalAccessFilter filter = new RetrievalAccessFilter(
                " 2001 ",
                List.of(" 7001 ", "7001"),
                List.of(" role:hr ", "role:hr", " tenant:2001 "),
                Instant.parse("2026-05-24T00:00:00Z")
        );

        assertThat(filter.tenantId()).isEqualTo("2001");
        assertThat(filter.knowledgeBaseIds()).containsExactly("7001");
        assertThat(filter.aclTokens()).containsExactly("role:hr", "tenant:2001");
        assertThat(filter.allows(candidate("2001", "7001", metadata(List.of("role:hr"), null)))).isTrue();
    }

    @Test
    void rejectsBlankAclTokensInsteadOfFilteringBadInput() {
        assertThatThrownBy(() -> new RetrievalAccessFilter(
                "2001",
                List.of("7001"),
                List.of("tenant:2001", " "),
                Instant.parse("2026-05-24T00:00:00Z")
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("权限不能为空白");
    }

    @Test
    void rejectsNullAclTokensInsteadOfFilteringBadInput() {
        assertThatThrownBy(() -> new RetrievalAccessFilter(
                "2001",
                List.of("7001"),
                Arrays.asList("tenant:2001", null),
                Instant.parse("2026-05-24T00:00:00Z")
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("权限不能为空白");
    }

    @Test
    void rejectsBlankKnowledgeBaseIdsInsteadOfFilteringBadInput() {
        assertThatThrownBy(() -> new RetrievalAccessFilter(
                "2001",
                List.of("7001", " "),
                List.of("tenant:2001"),
                Instant.parse("2026-05-24T00:00:00Z")
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("知识库不能为空白");
    }

    @Test
    void filtersExpiredOrUnauthorizedChunksBeforePromptAssembly() {
        RetrievalAccessFilter filter = new RetrievalAccessFilter(
                "2001",
                List.of("7001"),
                List.of("tenant:2001", "role:hr"),
                Instant.parse("2026-05-24T00:00:00Z")
        );

        assertThat(filter.allows(candidate("2001", "7001", metadata(List.of("role:hr"), null)))).isTrue();
        assertThat(filter.allows(candidate("2002", "7001", metadata(List.of("role:hr"), null)))).isFalse();
        assertThat(filter.allows(candidate("2001", "7002", metadata(List.of("role:hr"), null)))).isFalse();
        assertThat(filter.allows(candidate("2001", "7001", metadata(List.of("role:finance"), null)))).isFalse();
        assertThat(filter.allows(candidate("2001", "7001", metadata(
                List.of("role:hr"),
                Instant.parse("2026-05-23T23:59:59Z")
        )))).isFalse();
    }

    @Test
    void failsClosedForUnknownVisibilityAndSensitiveClassificationWithoutClearance() {
        RetrievalAccessFilter ordinary = new RetrievalAccessFilter(
                "2001",
                List.of("7001"),
                List.of("tenant:2001", "kb:7001"),
                Instant.parse("2026-05-24T00:00:00Z")
        );
        RetrievalAccessFilter cleared = new RetrievalAccessFilter(
                "2001",
                List.of("7001"),
                List.of("tenant:2001", "kb:7001", "classification:confidential"),
                Instant.parse("2026-05-24T00:00:00Z")
        );

        assertThat(ordinary.allows(candidate("2001", "7001", metadata(
                "confidential", "tenant", List.of("kb:7001"), null
        )))).isFalse();
        assertThat(cleared.allows(candidate("2001", "7001", metadata(
                "confidential", "tenant", List.of("kb:7001"), null
        )))).isTrue();
        assertThat(cleared.allows(candidate("2001", "7001", metadata(
                "internal", "unexpected", List.of("kb:7001"), null
        )))).isFalse();
    }

    @Test
    void privateVisibilityRequiresMatchingOwnerOrUserAclInsteadOfTenantAcl() {
        RetrievalAccessFilter filter = new RetrievalAccessFilter(
                "2001",
                List.of("7001"),
                List.of("tenant:2001", "kb:7001", "user:1001", "owner:1001"),
                Instant.parse("2026-05-24T00:00:00Z")
        );

        assertThat(filter.allows(candidate("2001", "7001", metadata(
                "internal", "private", List.of("tenant:2001", "kb:7001"), null
        )))).isFalse();
        assertThat(filter.allows(candidate("2001", "7001", metadata(
                "internal", "private", List.of("owner:1001", "kb:7001"), null
        )))).isTrue();
        assertThat(filter.allows(candidate("2001", "7001", metadata(
                "internal", "private", List.of("user:2002", "kb:7001"), null
        )))).isFalse();
    }

    private static RetrievalCandidate candidate(String tenantId, String knowledgeBaseId, KnowledgeDocumentChunkMetadata metadata) {
        return new RetrievalCandidate(
                tenantId,
                knowledgeBaseId,
                "8001",
                "9001",
                "制度片段",
                0.9,
                1,
                RetrievalSignal.VECTOR,
                metadata
        );
    }

    private static KnowledgeDocumentChunkMetadata metadata(List<String> aclTokens, Instant expiresAt) {
        return metadata("internal", "tenant", aclTokens, expiresAt);
    }

    private static KnowledgeDocumentChunkMetadata metadata(
            String classification,
            String visibility,
            List<String> aclTokens,
            Instant expiresAt
    ) {
        return new KnowledgeDocumentChunkMetadata(
                "s3://aip-knowledge/tenant/2001/doc.txt",
                1,
                "制度/审批",
                null,
                null,
                classification,
                visibility,
                aclTokens,
                false,
                Instant.parse("2026-05-22T08:00:00Z"),
                expiresAt,
                "embedding-model-unassigned",
                "heading:v1",
                "kb-7001-doc-v1"
        );
    }
}
