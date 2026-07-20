package com.aiplatform.knowledge.application.retrieval.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.knowledge.domain.retrieval.RetrievalSignal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetrieveKnowledgeResultTest {
    @Test
    void rejectsMissingChunksInsteadOfEmptyFallback() {
        assertThatThrownBy(() -> new RetrieveKnowledgeResult(false, null, "无可用知识", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("检索切片不能为空");
    }

    @Test
    void rejectsNoAnswerResultWithoutReason() {
        assertThatThrownBy(() -> new RetrieveKnowledgeResult(false, List.of(), null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("无答案原因不能为空");
    }

    @Test
    void rejectsAnswerableResultWithoutChunks() {
        assertThatThrownBy(() -> new RetrieveKnowledgeResult(true, List.of(), "", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("可回答结果必须包含检索切片");
    }

    @Test
    void rejectsNoAnswerResultWithChunks() {
        assertThatThrownBy(() -> new RetrieveKnowledgeResult(false, List.of(chunk()), "无可用知识", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("无答案结果不能携带检索切片");
    }

    @Test
    void normalizesNoAnswerReasonAndClearsAnswerableNoAnswerReason() {
        RetrieveKnowledgeResult noAnswer = new RetrieveKnowledgeResult(false, List.of(), " 未召回知识 ", null, null, null);
        RetrieveKnowledgeResult answerable = new RetrieveKnowledgeResult(true, List.of(chunkWithSpaces()), " ", null, null, " 未指定回答模型 ");

        assertThat(noAnswer.noAnswerReason()).isEqualTo("未召回知识");
        assertThat(answerable.noAnswerReason()).isNull();
        assertThat(answerable.generatedAnswer()).isNull();
        assertThat(answerable.answerModelCode()).isNull();
        assertThat(answerable.answerGenerationReason()).isEqualTo("未指定回答模型");
        assertThat(answerable.chunks()).singleElement().satisfies(chunk -> {
            assertThat(chunk.knowledgeBaseId()).isEqualTo("7001");
            assertThat(chunk.documentId()).isEqualTo("8001");
            assertThat(chunk.chunkId()).isEqualTo("9001");
            assertThat(chunk.content()).isEqualTo("员工制度内容");
            assertThat(chunk.sourceUri()).isEqualTo("s3://aip-knowledge/doc.txt");
            assertThat(chunk.titlePath()).isEqualTo("员工制度");
            assertThat(chunk.tableCellRange()).isEqualTo("A1:B2");
            assertThat(chunk.indexVersion()).isEqualTo("kb-7001-active");
        });
    }

    @Test
    void supportsGeneratedAnswerWithModelCode() {
        RetrieveKnowledgeResult result = new RetrieveKnowledgeResult(
                true,
                List.of(chunk()),
                null,
                "差旅报销上限 2000 元。",
                "gpt-4.1-mini",
                null
        );

        assertThat(result.generatedAnswer()).contains("2000 元");
        assertThat(result.answerModelCode()).isEqualTo("gpt-4.1-mini");
        assertThat(result.answerGenerationReason()).isNull();
    }

    @Test
    void preservesAuthorizationSnapshotAndFilteringCountsAcrossAnswerDecoration() {
        RetrievalAuthorizationSnapshot snapshot = RetrievalAuthorizationSnapshot.of(
                List.of("business-user"),
                List.of("knowledge-base:retrieval:query"),
                List.of("7001", "7002"),
                List.of("7001")
        );

        RetrieveKnowledgeResult result = RetrieveKnowledgeResult.answerable(List.of(chunk()))
                .withSecurityEvidence(snapshot, 3, 1)
                .withAnswerGenerationReason("未指定回答模型");

        assertThat(result.authorizationSnapshot()).isEqualTo(snapshot);
        assertThat(result.filteredCount()).isEqualTo(3);
        assertThat(result.filteredCitationCount()).isEqualTo(1);
    }

    @Test
    void deduplicatesOnlyCompleteEvidenceIdentityAtTheResultBoundary() {
        RetrievedKnowledgeChunk original = chunk();
        RetrievedKnowledgeChunk anotherTenant = chunk("2002", "7001", "8001", "9001", "kb-7001-active");
        RetrievedKnowledgeChunk anotherKnowledgeBase = chunk("2001", "7002", "8001", "9001", "kb-7001-active");
        RetrievedKnowledgeChunk anotherIndexVersion = chunk("2001", "7001", "8001", "9001", "kb-7001-v2");

        RetrieveKnowledgeResult result = RetrieveKnowledgeResult.answerable(List.of(
                original,
                original,
                anotherTenant,
                anotherKnowledgeBase,
                anotherIndexVersion
        ));

        assertThat(result.chunks()).containsExactly(
                original,
                anotherTenant,
                anotherKnowledgeBase,
                anotherIndexVersion
        );
    }

    @Test
    void rejectsInvalidFilteringCounts() {
        assertThatThrownBy(() -> new RetrieveKnowledgeResult(
                false,
                List.of(),
                "无可用知识",
                null,
                null,
                null,
                RetrievalAuthorizationSnapshot.empty(),
                0,
                1
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("过滤计数不合法");
    }

    @Test
    void rejectsGeneratedAnswerWithoutModelCode() {
        assertThatThrownBy(() -> new RetrieveKnowledgeResult(true, List.of(chunk()), null, "答案", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("生成答案时必须携带回答模型编码");
    }

    @Test
    void rejectsNullChunkOrInvalidChunkScoreInsteadOfLeakingDamagedRetrievalEvidence() {
        List<RetrievedKnowledgeChunk> chunks = new ArrayList<>();
        chunks.add(null);

        assertThatThrownBy(() -> new RetrieveKnowledgeResult(true, chunks, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("检索切片不能为空");

        assertThatThrownBy(() -> new RetrievedKnowledgeChunk(
                "2001",
                "7001",
                "8001",
                "9001",
                "员工制度内容",
                Double.NaN,
                RetrievalSignal.VECTOR,
                "s3://aip-knowledge/doc.txt",
                "员工制度",
                1,
                null,
                true,
                "kb-7001-active"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("score必须在 0 到 1 之间");
    }

    private static RetrievedKnowledgeChunk chunk() {
        return chunk("2001", "7001", "8001", "9001", "kb-7001-active");
    }

    private static RetrievedKnowledgeChunk chunk(
            String tenantId,
            String knowledgeBaseId,
            String documentId,
            String chunkId,
            String indexVersion
    ) {
        return new RetrievedKnowledgeChunk(
                tenantId,
                knowledgeBaseId,
                documentId,
                chunkId,
                "员工制度内容",
                0.95,
                RetrievalSignal.VECTOR,
                "s3://aip-knowledge/doc.txt",
                "员工制度",
                1,
                null,
                true,
                indexVersion
        );
    }

    private static RetrievedKnowledgeChunk chunkWithSpaces() {
        return new RetrievedKnowledgeChunk(
                " 2001 ",
                " 7001 ",
                " 8001 ",
                " 9001 ",
                " 员工制度内容 ",
                0.95,
                RetrievalSignal.VECTOR,
                " s3://aip-knowledge/doc.txt ",
                " 员工制度 ",
                1,
                " A1:B2 ",
                true,
                " kb-7001-active "
        );
    }
}
