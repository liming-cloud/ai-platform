package com.aiplatform.knowledge.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class KnowledgeDocumentTest {
    private static final TenantId TENANT_ID = TenantId.of("2001");
    private static final Instant NOW = Instant.parse("2026-05-22T08:00:00Z");

    @Test
    void rejectsBlankRequiredDocumentFactsInsteadOfInvalidAggregateRoot() {
        assertBlankRejected("文档ID", () -> document(" ", "7001", "员工手册.pdf", "object-storage",
                "s3://aip-knowledge/doc.pdf", "abc123", "application/pdf", "1001"));
        assertBlankRejected("知识库ID", () -> document("8001", " ", "员工手册.pdf", "object-storage",
                "s3://aip-knowledge/doc.pdf", "abc123", "application/pdf", "1001"));
        assertBlankRejected("文档名称", () -> document("8001", "7001", " ", "object-storage",
                "s3://aip-knowledge/doc.pdf", "abc123", "application/pdf", "1001"));
        assertBlankRejected("文档来源类型", () -> document("8001", "7001", "员工手册.pdf", " ",
                "s3://aip-knowledge/doc.pdf", "abc123", "application/pdf", "1001"));
        assertBlankRejected("文档来源地址", () -> document("8001", "7001", "员工手册.pdf", "object-storage",
                " ", "abc123", "application/pdf", "1001"));
        assertBlankRejected("文档内容哈希", () -> document("8001", "7001", "员工手册.pdf", "object-storage",
                "s3://aip-knowledge/doc.pdf", " ", "application/pdf", "1001"));
        assertBlankRejected("文档内容类型", () -> document("8001", "7001", "员工手册.pdf", "object-storage",
                "s3://aip-knowledge/doc.pdf", "abc123", " ", "1001"));
        assertBlankRejected("文档上传人", () -> document("8001", "7001", "员工手册.pdf", "object-storage",
                "s3://aip-knowledge/doc.pdf", "abc123", "application/pdf", " "));
    }

    @Test
    void normalizesRequiredDocumentFactsInsideAggregateRoot() {
        KnowledgeDocument document = document(
                " 8001 ",
                " 7001 ",
                " 员工手册.pdf ",
                " object-storage ",
                " s3://aip-knowledge/doc.pdf ",
                " abc123 ",
                " application/pdf ",
                " 1001 "
        );

        assertThat(document.documentId()).isEqualTo("8001");
        assertThat(document.knowledgeBaseId()).isEqualTo("7001");
        assertThat(document.fileName()).isEqualTo("员工手册.pdf");
        assertThat(document.sourceType()).isEqualTo("object-storage");
        assertThat(document.sourceUri()).isEqualTo("s3://aip-knowledge/doc.pdf");
        assertThat(document.contentHash()).isEqualTo("abc123");
        assertThat(document.contentType()).isEqualTo("application/pdf");
        assertThat(document.uploadedBy()).isEqualTo("1001");
    }

    private static void assertBlankRejected(String fieldName, ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(fieldName + "不能为空");
    }

    private static KnowledgeDocument document(
            String documentId,
            String knowledgeBaseId,
            String fileName,
            String sourceType,
            String sourceUri,
            String contentHash,
            String contentType,
            String uploadedBy
    ) {
        return new KnowledgeDocument(
                TENANT_ID,
                documentId,
                knowledgeBaseId,
                fileName,
                sourceType,
                sourceUri,
                contentHash,
                contentType,
                42,
                1,
                KnowledgeDocumentStatus.UPLOADED,
                NOW,
                NOW,
                uploadedBy
        );
    }
}
