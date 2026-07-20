package com.aiplatform.contracts.capability.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeRetrievalResponseTest {
    @Test
    void rejectsMissingRetrievalCollectionsInsteadOfEmptyFallback() {
        assertThatThrownBy(() -> new KnowledgeRetrievalResponse(false, null, List.of(), "无可用知识"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("引用列表不能为空");

        assertThatThrownBy(() -> new KnowledgeRetrievalResponse(false, List.of(), null, "无可用知识"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("召回证据不能为空");
    }

    @Test
    void rejectsNoAnswerResultWithoutReason() {
        assertThatThrownBy(() -> new KnowledgeRetrievalResponse(false, List.of(), List.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("无答案原因不能为空");
    }

    @Test
    void rejectsAnswerableResultWithNoAnswerReason() {
        assertThatThrownBy(() -> new KnowledgeRetrievalResponse(
                true,
                List.of(citation()),
                List.of(evidence()),
                "无可用知识"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("可回答结果不能携带无答案原因");
    }

    @Test
    void rejectsAnswerableResultWithoutCitationOrEvidence() {
        assertThatThrownBy(() -> new KnowledgeRetrievalResponse(true, List.of(), List.of(evidence()), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("可回答结果必须携带引用列表");

        assertThatThrownBy(() -> new KnowledgeRetrievalResponse(true, List.of(citation()), List.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("可回答结果必须携带召回证据");
    }

    @Test
    void normalizesNoAnswerReasonAndClearsAnswerableNoAnswerReason() {
        KnowledgeRetrievalResponse noAnswer = new KnowledgeRetrievalResponse(false, List.of(), List.of(), " 未召回知识 ");
        KnowledgeRetrievalResponse answerable = new KnowledgeRetrievalResponse(true, List.of(citation()), List.of(evidence()), " ");

        assertThat(noAnswer.noAnswerReason()).isEqualTo("未召回知识");
        assertThat(answerable.noAnswerReason()).isNull();
    }

    @Test
    void capabilityResponsesAreIndistinguishableAcrossInternalFilteringCounts() {
        KnowledgeRetrievalResponse withoutFiltering = new KnowledgeRetrievalResponse(
                true,
                List.of(citation()),
                List.of(evidence()),
                null,
                0
        );
        KnowledgeRetrievalResponse withFiltering = new KnowledgeRetrievalResponse(
                true,
                List.of(citation()),
                List.of(evidence()),
                null,
                7
        );

        assertThat(withFiltering).isEqualTo(withoutFiltering);
        assertThat(withFiltering.filteredCitationCount()).isZero();
    }

    @Test
    void rejectsNullCitationOrEvidenceElementInsteadOfLeakingDamagedRetrievalFacts() {
        List<KnowledgeRetrievalCitation> citations = new ArrayList<>();
        citations.add(null);
        List<KnowledgeRetrievalEvidence> evidences = new ArrayList<>();
        evidences.add(null);

        assertThatThrownBy(() -> new KnowledgeRetrievalResponse(true, citations, List.of(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("引用列表不能为空");

        assertThatThrownBy(() -> new KnowledgeRetrievalResponse(true, List.of(citation()), evidences, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("召回证据不能为空");
    }

    private static KnowledgeRetrievalCitation citation() {
        return new KnowledgeRetrievalCitation("kb-1", "doc-1", "chunk-1", "制度手册", "片段", "p1", 0.9);
    }

    private static KnowledgeRetrievalEvidence evidence() {
        return new KnowledgeRetrievalEvidence("kb-1", "doc-1", "chunk-1", 0.9, "vector", "idx-v1");
    }
}
