package com.aiplatform.knowledge.domain.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.knowledge.domain.KnowledgeDocumentChunkMetadata;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetrievalCandidateTest {
    @Test
    void normalizesCandidateIdsBeforeAccessAndRankingUse() {
        RetrievalCandidate candidate = new RetrievalCandidate(
                " 2001 ",
                " 7001 ",
                " 8001 ",
                " 9001 ",
                " 制度片段 ",
                0.91,
                1,
                RetrievalSignal.VECTOR,
                metadata()
        );

        assertThat(candidate.tenantId()).isEqualTo("2001");
        assertThat(candidate.knowledgeBaseId()).isEqualTo("7001");
        assertThat(candidate.documentId()).isEqualTo("8001");
        assertThat(candidate.chunkId()).isEqualTo("9001");
        assertThat(candidate.content()).isEqualTo(" 制度片段 ");
    }

    @Test
    void rejectsNonFiniteScoresInsteadOfRankingCorruptedCandidates() {
        assertThatThrownBy(() -> new RetrievalCandidate(
                "2001",
                "7001",
                "8001",
                "9001",
                "制度片段",
                Double.NaN,
                1,
                RetrievalSignal.VECTOR,
                metadata()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("召回分数必须是有限数字");

        assertThatThrownBy(() -> new RetrievalCandidate(
                "2001",
                "7001",
                "8001",
                "9001",
                "制度片段",
                Double.POSITIVE_INFINITY,
                1,
                RetrievalSignal.VECTOR,
                metadata()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("召回分数必须是有限数字");
    }

    private static KnowledgeDocumentChunkMetadata metadata() {
        return new KnowledgeDocumentChunkMetadata(
                "s3://aip-knowledge/tenant/2001/doc.txt",
                1,
                "制度/审批",
                null,
                null,
                "internal",
                "tenant",
                List.of("tenant:2001", "role:hr"),
                false,
                Instant.parse("2026-05-22T08:00:00Z"),
                null,
                "embedding-model-unassigned",
                "heading:v1",
                "kb-7001-doc-v1"
        );
    }
}
