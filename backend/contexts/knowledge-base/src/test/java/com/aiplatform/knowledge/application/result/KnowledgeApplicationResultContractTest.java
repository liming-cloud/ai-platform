package com.aiplatform.knowledge.application.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class KnowledgeApplicationResultContractTest {
    @Test
    void createKnowledgeBaseResultNormalizesRequiredFactsBeforeResponse() {
        CreateKnowledgeBaseResult result = new CreateKnowledgeBaseResult(" 7001 ", " ACTIVE ");

        assertThat(result.knowledgeBaseId()).isEqualTo("7001");
        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    @Test
    void uploadKnowledgeDocumentResultNormalizesRequiredFactsBeforeResponse() {
        UploadKnowledgeDocumentResult result = new UploadKnowledgeDocumentResult(" 8001 ", " 8101 ", " UPLOADED ");

        assertThat(result.documentId()).isEqualTo("8001");
        assertThat(result.ingestJobId()).isEqualTo("8101");
        assertThat(result.status()).isEqualTo("UPLOADED");
    }

    @Test
    void parseKnowledgeDocumentResultNormalizesRequiredFactsBeforeResponse() {
        ParseKnowledgeDocumentResult result = new ParseKnowledgeDocumentResult(" 8001 ", " 8101 ", " CHUNKED ", 3);

        assertThat(result.documentId()).isEqualTo("8001");
        assertThat(result.ingestJobId()).isEqualTo("8101");
        assertThat(result.status()).isEqualTo("CHUNKED");
    }

    @Test
    void rejectsBlankApplicationResultFactsInsteadOfNullOnlyValidation() {
        assertBlankRejected("知识库ID", () -> new CreateKnowledgeBaseResult(" ", "ACTIVE"));
        assertBlankRejected("知识库状态", () -> new CreateKnowledgeBaseResult("7001", " "));
        assertBlankRejected("文档ID", () -> new UploadKnowledgeDocumentResult(" ", "8101", "UPLOADED"));
        assertBlankRejected("入库任务ID", () -> new UploadKnowledgeDocumentResult("8001", " ", "UPLOADED"));
        assertBlankRejected("上传状态", () -> new UploadKnowledgeDocumentResult("8001", "8101", " "));
        assertBlankRejected("解析状态", () -> new ParseKnowledgeDocumentResult("8001", "8101", " ", 1));
    }

    @Test
    void rejectsNegativeParseChunkCountWithChineseMessage() {
        assertThatThrownBy(() -> new ParseKnowledgeDocumentResult("8001", "8101", "CHUNKED", -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("解析切片数量不能为负数");
    }

    private static void assertBlankRejected(String fieldName, ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(fieldName + "不能为空");
    }
}
