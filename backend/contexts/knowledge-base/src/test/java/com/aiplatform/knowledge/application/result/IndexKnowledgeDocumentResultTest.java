package com.aiplatform.knowledge.application.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class IndexKnowledgeDocumentResultTest {
    @Test
    void availableResultAllowsEmptyFailureReason() {
        IndexKnowledgeDocumentResult result = new IndexKnowledgeDocumentResult(
                " 8001 ",
                " 8201 ",
                " AVAILABLE ",
                2,
                "   "
        );

        assertThat(result.documentId()).isEqualTo("8001");
        assertThat(result.ingestJobId()).isEqualTo("8201");
        assertThat(result.status()).isEqualTo("AVAILABLE");
        assertThat(result.failureReason()).isEmpty();
    }

    @Test
    void rejectsFailedIndexResultWithoutFailureReasonInsteadOfEmptyFallback() {
        assertThatThrownBy(() -> new IndexKnowledgeDocumentResult(
                "8001",
                "8201",
                "FAILED",
                0,
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("失败原因不能为空");
    }

    @Test
    void failedIndexResultNormalizesStatusAndFailureReason() {
        IndexKnowledgeDocumentResult result = new IndexKnowledgeDocumentResult(
                " 8001 ",
                " 8201 ",
                " FAILED ",
                0,
                " 向量化失败 "
        );

        assertThat(result.documentId()).isEqualTo("8001");
        assertThat(result.ingestJobId()).isEqualTo("8201");
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.failureReason()).isEqualTo("向量化失败");
    }

    @Test
    void rejectsNonFailedIndexResultWithFailureReason() {
        assertThatThrownBy(() -> new IndexKnowledgeDocumentResult(
                "8001",
                "8201",
                "AVAILABLE",
                2,
                "向量化失败"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("非失败索引结果不能携带失败原因");
    }
}
