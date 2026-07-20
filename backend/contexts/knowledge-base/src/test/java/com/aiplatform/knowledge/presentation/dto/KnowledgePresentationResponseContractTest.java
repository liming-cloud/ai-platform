package com.aiplatform.knowledge.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.knowledge.domain.retrieval.RetrievalSignal;
import com.aiplatform.knowledge.presentation.retrieval.response.RetrievalAuthorizationSnapshotResponse;
import com.aiplatform.knowledge.presentation.indexing.response.KnowledgeDocumentIndexResponse;
import com.aiplatform.knowledge.presentation.retrieval.response.RetrieveKnowledgeResponse;
import com.aiplatform.knowledge.presentation.retrieval.response.RetrievedKnowledgeChunkResponse;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class KnowledgePresentationResponseContractTest {
    @Test
    void createKnowledgeBaseResponseNormalizesRequiredFactsBeforeApiResponse() {
        KnowledgeBaseResponse response = new KnowledgeBaseResponse(" 7001 ", " ACTIVE ");

        assertThat(response.knowledgeBaseId()).isEqualTo("7001");
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void uploadKnowledgeDocumentResponseNormalizesRequiredFactsBeforeApiResponse() {
        KnowledgeDocumentResponse response = new KnowledgeDocumentResponse(" 8001 ", " 8101 ", " UPLOADED ");

        assertThat(response.documentId()).isEqualTo("8001");
        assertThat(response.ingestJobId()).isEqualTo("8101");
        assertThat(response.status()).isEqualTo("UPLOADED");
    }

    @Test
    void parseKnowledgeDocumentResponseNormalizesRequiredFactsBeforeApiResponse() {
        KnowledgeDocumentParseResponse response = new KnowledgeDocumentParseResponse(" 8001 ", " 8101 ", " CHUNKED ", 3);

        assertThat(response.documentId()).isEqualTo("8001");
        assertThat(response.ingestJobId()).isEqualTo("8101");
        assertThat(response.status()).isEqualTo("CHUNKED");
    }

    @Test
    void indexKnowledgeDocumentResponseNormalizesRequiredFactsAndFailureReason() {
        KnowledgeDocumentIndexResponse response = new KnowledgeDocumentIndexResponse(
                " 8001 ",
                " 8201 ",
                " FAILED ",
                0,
                " 向量化失败 "
        );

        assertThat(response.documentId()).isEqualTo("8001");
        assertThat(response.ingestJobId()).isEqualTo("8201");
        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.failureReason()).isEqualTo("向量化失败");
    }

    @Test
    void retrievalResponseNormalizesChunksAndNoAnswerReasonBeforeApiResponse() {
        RetrieveKnowledgeResponse answerable = new RetrieveKnowledgeResponse(true, List.of(chunkWithSpaces()), " ", null, null, " 未指定回答模型 ");
        RetrieveKnowledgeResponse noAnswer = new RetrieveKnowledgeResponse(false, List.of(), " 未召回可用知识 ", null, null, null);

        assertThat(answerable.noAnswerReason()).isNull();
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
        assertThat(noAnswer.noAnswerReason()).isEqualTo("未召回可用知识");
    }

    @Test
    void retrievalResponseNormalizesGeneratedAnswerAndModelCode() {
        RetrieveKnowledgeResponse response = new RetrieveKnowledgeResponse(
                true,
                List.of(chunk()),
                null,
                " 差旅报销上限 2000 元。 ",
                " gpt-4.1-mini ",
                null
        );

        assertThat(response.generatedAnswer()).isEqualTo("差旅报销上限 2000 元。");
        assertThat(response.answerModelCode()).isEqualTo("gpt-4.1-mini");
        assertThat(response.answerGenerationReason()).isNull();
    }

    @Test
    void httpResponsesAreIndistinguishableAcrossInternalFilteringCounts() {
        RetrievalAuthorizationSnapshotResponse snapshot = new RetrievalAuthorizationSnapshotResponse(
                List.of("business-user"),
                List.of("knowledge-base:retrieval:query"),
                List.of("7001"),
                List.of("7001")
        );
        RetrieveKnowledgeResponse withoutFiltering = new RetrieveKnowledgeResponse(
                true, List.of(chunk()), null, null, null, "未指定回答模型", snapshot, 0, 0
        );
        RetrieveKnowledgeResponse withFiltering = new RetrieveKnowledgeResponse(
                true, List.of(chunk()), null, null, null, "未指定回答模型", snapshot, 9, 3
        );

        assertThat(withFiltering).isEqualTo(withoutFiltering);
        assertThat(withFiltering.filteredCount()).isZero();
        assertThat(withFiltering.filteredCitationCount()).isZero();
    }

    @Test
    void rejectsBlankPresentationResponseFactsInsteadOfNullOnlyValidation() {
        assertBlankRejected("知识库ID", () -> new KnowledgeBaseResponse(" ", "ACTIVE"));
        assertBlankRejected("知识库状态", () -> new KnowledgeBaseResponse("7001", " "));
        assertBlankRejected("文档ID", () -> new KnowledgeDocumentResponse(" ", "8101", "UPLOADED"));
        assertBlankRejected("入库任务ID", () -> new KnowledgeDocumentResponse("8001", " ", "UPLOADED"));
        assertBlankRejected("上传状态", () -> new KnowledgeDocumentResponse("8001", "8101", " "));
        assertBlankRejected("解析状态", () -> new KnowledgeDocumentParseResponse("8001", "8101", " ", 1));
        assertBlankRejected("索引状态", () -> new KnowledgeDocumentIndexResponse("8001", "8201", " ", 0, ""));
        assertBlankRejected("失败原因", () -> new KnowledgeDocumentIndexResponse("8001", "8201", "FAILED", 0, " "));
        assertBlankRejected("切片ID", () -> new RetrievedKnowledgeChunkResponse(
                "7001", "8001", " ", "员工制度内容", 0.91, RetrievalSignal.VECTOR,
                "s3://aip-knowledge/doc.txt", "员工制度", 1, null, true, "kb-7001-active"
        ));
        assertBlankRejected("表格单元格范围", () -> new RetrievedKnowledgeChunkResponse(
                "7001", "8001", "9001", "员工制度内容", 0.91, RetrievalSignal.VECTOR,
                "s3://aip-knowledge/doc.txt", "员工制度", 1, " ", true, "kb-7001-active"
        ));
        assertBlankRejected("无答案原因", () -> new RetrieveKnowledgeResponse(false, List.of(), " ", null, null, null));
    }

    @Test
    void rejectsInvalidPresentationResponseCountsWithChineseMessages() {
        assertThatThrownBy(() -> new KnowledgeDocumentParseResponse("8001", "8101", "CHUNKED", -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("解析切片数量不能为负数");

        assertThatThrownBy(() -> new KnowledgeDocumentIndexResponse("8001", "8201", "AVAILABLE", -1, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("索引切片数量不能为负数");

        assertThatThrownBy(() -> new RetrievedKnowledgeChunkResponse(
                "7001", "8001", "9001", "员工制度内容", Double.NaN, RetrievalSignal.VECTOR,
                "s3://aip-knowledge/doc.txt", "员工制度", 1, null, true, "kb-7001-active"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("召回分数必须在 0 到 1 之间");

        assertThatThrownBy(() -> new RetrievedKnowledgeChunkResponse(
                "7001", "8001", "9001", "员工制度内容", 0.91, RetrievalSignal.VECTOR,
                "s3://aip-knowledge/doc.txt", "员工制度", 0, null, true, "kb-7001-active"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("段落编号必须为正数");
    }

    @Test
    void rejectsNonFailedIndexResponseWithFailureReason() {
        assertThatThrownBy(() -> new KnowledgeDocumentIndexResponse("8001", "8201", "AVAILABLE", 2, "向量化失败"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("非失败索引响应不能携带失败原因");
    }

    @Test
    void rejectsInconsistentRetrievalResponseState() {
        assertThatThrownBy(() -> new RetrieveKnowledgeResponse(true, List.of(), "", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("可回答响应必须包含检索切片");

        assertThatThrownBy(() -> new RetrieveKnowledgeResponse(true, List.of(chunk()), "命中不足", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("可回答响应不能携带无答案原因");

        assertThatThrownBy(() -> new RetrieveKnowledgeResponse(false, List.of(chunk()), "未召回", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("无答案响应不能携带检索切片");

        assertThatThrownBy(() -> new RetrieveKnowledgeResponse(true, List.of(chunk()), null, "答案", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("生成答案时必须携带回答模型编码");
    }

    @Test
    void rejectsDamagedRetrievalResponseCollectionsWithChineseMessages() {
        ArrayList<RetrievedKnowledgeChunkResponse> chunks = new ArrayList<>();
        chunks.add(null);

        assertThatThrownBy(() -> new RetrieveKnowledgeResponse(true, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("检索切片响应不能为空");
        assertThatThrownBy(() -> new RetrieveKnowledgeResponse(true, chunks, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("检索切片响应不能为空");
    }

    private static void assertBlankRejected(String fieldName, ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(fieldName + "不能为空");
    }

    private static RetrievedKnowledgeChunkResponse chunk() {
        return new RetrievedKnowledgeChunkResponse(
                "7001",
                "8001",
                "9001",
                "员工制度内容",
                0.91,
                RetrievalSignal.VECTOR,
                "s3://aip-knowledge/doc.txt",
                "员工制度",
                1,
                null,
                true,
                "kb-7001-active"
        );
    }

    private static RetrievedKnowledgeChunkResponse chunkWithSpaces() {
        return new RetrievedKnowledgeChunkResponse(
                " 7001 ",
                " 8001 ",
                " 9001 ",
                " 员工制度内容 ",
                0.91,
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
