package com.aiplatform.contracts.capability.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class KnowledgeRetrievalEvidenceTest {
    @Test
    void normalizesRetrievalEvidenceFacts() {
        KnowledgeRetrievalEvidence evidence = new KnowledgeRetrievalEvidence(
                " kb-1 ",
                " doc-1 ",
                " chunk-1 ",
                0.82,
                " vector ",
                " index-v1 "
        );

        assertThat(evidence.knowledgeBaseId()).isEqualTo("kb-1");
        assertThat(evidence.documentId()).isEqualTo("doc-1");
        assertThat(evidence.chunkId()).isEqualTo("chunk-1");
        assertThat(evidence.retrievalSignal()).isEqualTo("vector");
        assertThat(evidence.indexVersion()).isEqualTo("index-v1");
    }

    @Test
    void rejectsInvalidRetrievalEvidenceScore() {
        assertThatThrownBy(() -> new KnowledgeRetrievalEvidence(
                "kb-1",
                "doc-1",
                "chunk-1",
                Double.POSITIVE_INFINITY,
                "vector",
                "index-v1"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("召回分数必须在 0 到 1 之间");
    }
}
