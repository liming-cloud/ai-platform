package com.aiplatform.knowledge.application.retrieval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.knowledge.application.retrieval.result.RetrievedKnowledgeChunk;
import com.aiplatform.knowledge.domain.KnowledgeDocumentChunkMetadata;
import com.aiplatform.knowledge.domain.retrieval.RetrievalCandidate;
import com.aiplatform.knowledge.domain.retrieval.RetrievalSignal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeAnswerGroundingApplicationServiceTest {
    private final KnowledgeAnswerGroundingApplicationService groundingService = new KnowledgeAnswerGroundingApplicationService();

    @Test
    void keepsModelAnswerWhenPreciseEvidenceFactsArePreserved() {
        String answer = groundingService.stabilize(
                "员工制度",
                List.of(chunk("# 企业报销制度\n\n差旅报销上限为 2000 元。审批通过后由财务在 3 个工作日内打款。")),
                "结论：差旅报销上限为 2000 元，审批通过后由财务在 3 个工作日内打款。"
        );

        assertThat(answer).contains("2000 元");
        assertThat(answer).doesNotContain("证据出处：\n[E1]");
    }

    @Test
    void fallsBackToExtractiveAnswerWhenModelAnswerLosesPreciseFacts() {
        String answer = groundingService.stabilize(
                "员工制度",
                List.of(chunk("# 企业报销制度\n\n差旅报销上限为 2000 元。审批通过后由财务在 3 个工作日内打款。")),
                "结论：差旅报销上限为 5 元，审批后 3 天打款。"
        );

        assertThat(answer).contains("证据出处");
        assertThat(answer).contains("2000 元");
        assertThat(answer).contains("3 个工作日");
    }

    @Test
    void rejectsBlankGeneratedAnswer() {
        assertThatThrownBy(() -> groundingService.stabilize("员工制度", List.of(chunk("差旅报销上限为 2000 元。")), "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模型回答不能为空");
    }

    private static RetrievedKnowledgeChunk chunk(String content) {
        return RetrievedKnowledgeChunk.from(new RetrievalCandidate(
                "2001",
                "7001",
                "d1",
                "c1",
                content,
                0.91,
                1,
                RetrievalSignal.VECTOR,
                new KnowledgeDocumentChunkMetadata(
                        "s3://aip-knowledge/doc.txt",
                        1,
                        "员工制度",
                        null,
                        null,
                        "internal",
                        "tenant",
                        List.of("kb:7001"),
                        false,
                        Instant.parse("2026-05-20T00:00:00Z"),
                        null,
                        "embedding-model-1",
                        "heading:v1",
                        "kb-7001-active"
                )
        ));
    }
}
