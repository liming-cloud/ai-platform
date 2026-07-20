package com.aiplatform.knowledge.domain.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiplatform.knowledge.domain.KnowledgeDocumentChunkMetadata;
import com.aiplatform.knowledge.domain.retrieval.service.HybridRetrievalRanker;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class HybridRetrievalRankerTest {
    @Test
    void fusesVectorAndKeywordCandidatesWithRrfAndDeduplicatesChunks() {
        HybridRetrievalRanker ranker = new HybridRetrievalRanker();

        List<RetrievalCandidate> ranked = ranker.rank(
                List.of(
                        candidate("c1", "d1", 1, 0.91, RetrievalSignal.VECTOR),
                        candidate("c2", "d1", 2, 0.86, RetrievalSignal.VECTOR),
                        candidate("c3", "d2", 3, 0.81, RetrievalSignal.VECTOR)
                ),
                List.of(
                        candidate("c3", "d2", 1, 0.78, RetrievalSignal.KEYWORD),
                        candidate("c1", "d1", 2, 0.72, RetrievalSignal.KEYWORD),
                        candidate("c4", "d3", 3, 0.68, RetrievalSignal.KEYWORD)
                ),
                RetrievalProfile.BALANCED
        );

        assertThat(ranked).extracting(RetrievalCandidate::chunkId).containsExactly("c1", "c3", "c2", "c4");
        assertThat(ranked).extracting(RetrievalCandidate::documentId).containsExactly("d1", "d2", "d1", "d3");
    }

    @Test
    void limitsOneDocumentFromDominatingContext() {
        HybridRetrievalRanker ranker = new HybridRetrievalRanker();

        List<RetrievalCandidate> ranked = ranker.rank(
                List.of(
                        candidate("c1", "d1", 1, 0.99, RetrievalSignal.VECTOR),
                        candidate("c2", "d1", 2, 0.98, RetrievalSignal.VECTOR),
                        candidate("c3", "d1", 3, 0.97, RetrievalSignal.VECTOR),
                        candidate("c4", "d1", 4, 0.96, RetrievalSignal.VECTOR),
                        candidate("c5", "d2", 5, 0.70, RetrievalSignal.VECTOR)
                ),
                List.of(),
                RetrievalProfile.BALANCED
        );

        assertThat(ranked).extracting(RetrievalCandidate::chunkId).containsExactly("c1", "c2", "c3", "c5");
    }

    @Test
    void repeatedHitsFromOneSignalDoNotInflateFusionScore() {
        HybridRetrievalRanker ranker = new HybridRetrievalRanker();
        RetrievalCandidate repeatedVectorHit = candidate("c1", "d1", 1, 0.91, RetrievalSignal.VECTOR);

        List<RetrievalCandidate> ranked = ranker.rank(
                List.of(
                        repeatedVectorHit,
                        repeatedVectorHit,
                        candidate("c2", "d2", 2, 0.89, RetrievalSignal.VECTOR)
                ),
                List.of(candidate("c2", "d2", 1, 0.72, RetrievalSignal.KEYWORD)),
                RetrievalProfile.BALANCED
        );

        assertThat(ranked).extracting(RetrievalCandidate::chunkId).containsExactly("c2", "c1");
    }

    @Test
    void keepsSameChunkIdFromDifferentKnowledgeBasesAndIndexVersionsDistinct() {
        HybridRetrievalRanker ranker = new HybridRetrievalRanker();

        List<RetrievalCandidate> ranked = ranker.rank(
                List.of(
                        candidate("2001", "7001", "d1", "shared", "kb-7001-v1", 1, 0.90, RetrievalSignal.VECTOR),
                        candidate("2001", "7002", "d2", "shared", "kb-7002-v1", 2, 0.85, RetrievalSignal.VECTOR),
                        candidate("2001", "7001", "d1", "shared", "kb-7001-v2", 3, 0.80, RetrievalSignal.VECTOR)
                ),
                List.of(),
                RetrievalProfile.BALANCED
        );

        assertThat(ranked).hasSize(3);
        assertThat(ranked).extracting(candidate -> candidate.metadata().indexVersion())
                .containsExactly("kb-7001-v1", "kb-7002-v1", "kb-7001-v2");
    }

    @Test
    void scopesDocumentDiversityByTenantAndKnowledgeBase() {
        HybridRetrievalRanker ranker = new HybridRetrievalRanker();

        List<RetrievalCandidate> ranked = ranker.rank(
                List.of(
                        candidate("2001", "7001", "shared-doc", "a1", "kb-7001-v1", 1, 0.99, RetrievalSignal.VECTOR),
                        candidate("2001", "7001", "shared-doc", "a2", "kb-7001-v1", 2, 0.98, RetrievalSignal.VECTOR),
                        candidate("2001", "7001", "shared-doc", "a3", "kb-7001-v1", 3, 0.97, RetrievalSignal.VECTOR),
                        candidate("2001", "7002", "shared-doc", "b1", "kb-7002-v1", 4, 0.96, RetrievalSignal.VECTOR),
                        candidate("2001", "7002", "shared-doc", "b2", "kb-7002-v1", 5, 0.95, RetrievalSignal.VECTOR),
                        candidate("2001", "7002", "shared-doc", "b3", "kb-7002-v1", 6, 0.94, RetrievalSignal.VECTOR)
                ),
                List.of(),
                RetrievalProfile.BALANCED
        );

        assertThat(ranked).extracting(RetrievalCandidate::chunkId)
                .containsExactly("a1", "a2", "a3", "b1", "b2", "b3");
    }

    @Test
    void appliesVectorThresholdBeforeFusionAndIncludesTheBoundary() {
        HybridRetrievalRanker ranker = new HybridRetrievalRanker();

        List<RetrievalCandidate> ranked = ranker.rank(
                List.of(
                        candidate("below", "d1", 1, Math.nextDown(0.65), RetrievalSignal.VECTOR),
                        candidate("boundary", "d2", 2, 0.65, RetrievalSignal.VECTOR)
                ),
                List.of(candidate("keyword", "d3", 1, 0.20, RetrievalSignal.KEYWORD)),
                RetrievalProfile.BALANCED
        );

        assertThat(ranked).extracting(RetrievalCandidate::chunkId)
                .containsExactly("keyword", "boundary")
                .doesNotContain("below");
    }

    private static RetrievalCandidate candidate(String chunkId, String documentId, int rank, double score, RetrievalSignal signal) {
        return candidate("2001", "7001", documentId, chunkId, "kb-7001-doc-v1", rank, score, signal);
    }

    private static RetrievalCandidate candidate(
            String tenantId,
            String knowledgeBaseId,
            String documentId,
            String chunkId,
            String indexVersion,
            int rank,
            double score,
            RetrievalSignal signal
    ) {
        return new RetrievalCandidate(
                tenantId,
                knowledgeBaseId,
                documentId,
                chunkId,
                "制度片段",
                score,
                rank,
                signal,
                metadata(tenantId, indexVersion)
        );
    }

    private static KnowledgeDocumentChunkMetadata metadata(String tenantId, String indexVersion) {
        return new KnowledgeDocumentChunkMetadata(
                "s3://aip-knowledge/tenant/" + tenantId + "/doc.txt",
                1,
                "制度/审批",
                null,
                null,
                "internal",
                "tenant",
                List.of("tenant:" + tenantId, "role:hr"),
                false,
                Instant.parse("2026-05-22T08:00:00Z"),
                null,
                "embedding-model-unassigned",
                "heading:v1",
                indexVersion
        );
    }
}
