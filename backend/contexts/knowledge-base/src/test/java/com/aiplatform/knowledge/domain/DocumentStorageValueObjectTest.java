package com.aiplatform.knowledge.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.shared.kernel.TenantId;
import java.nio.charset.StandardCharsets;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class DocumentStorageValueObjectTest {
    private static final TenantId TENANT_ID = TenantId.of("2001");

    @Test
    void rejectsBlankDocumentObjectFactsWithChineseMessages() {
        assertBlankRejected("知识库ID", () -> documentObject(" ", "8001", "员工手册.txt", "text/plain", "abc123"));
        assertBlankRejected("文档ID", () -> documentObject("7001", " ", "员工手册.txt", "text/plain", "abc123"));
        assertBlankRejected("文件名", () -> documentObject("7001", "8001", " ", "text/plain", "abc123"));
        assertBlankRejected("文件MIME类型", () -> documentObject("7001", "8001", "员工手册.txt", " ", "abc123"));
        assertBlankRejected("文件内容哈希", () -> documentObject("7001", "8001", "员工手册.txt", "text/plain", " "));
    }

    @Test
    void rejectsEmptyDocumentObjectContentInsteadOfPersistingEmptyObject() {
        assertThatThrownBy(() -> new DocumentObject(
                TENANT_ID,
                "7001",
                "8001",
                "员工手册.txt",
                "text/plain",
                "abc123",
                new byte[0]
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文件内容不能为空");
    }

    @Test
    void defensivelyCopiesDocumentObjectContent() {
        byte[] content = "制度内容".getBytes(StandardCharsets.UTF_8);

        DocumentObject object = new DocumentObject(
                TENANT_ID,
                " 7001 ",
                " 8001 ",
                " 员工手册.txt ",
                " text/plain ",
                " abc123 ",
                content
        );
        content[0] = 0;

        assertThat(object.knowledgeBaseId()).isEqualTo("7001");
        assertThat(object.documentId()).isEqualTo("8001");
        assertThat(object.fileName()).isEqualTo("员工手册.txt");
        assertThat(object.contentType()).isEqualTo("text/plain");
        assertThat(object.contentHash()).isEqualTo("abc123");
        assertThat(object.content()).isEqualTo("制度内容".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void rejectsBlankStoredDocumentObjectFactsWithChineseMessages() {
        assertBlankRejected("对象存储桶", () -> new StoredDocumentObject(" ", "documents/8001.txt", "s3://aip/documents/8001.txt"));
        assertBlankRejected("对象存储Key", () -> new StoredDocumentObject("aip", " ", "s3://aip/documents/8001.txt"));
        assertBlankRejected("文档来源地址", () -> new StoredDocumentObject("aip", "documents/8001.txt", " "));
    }

    @Test
    void rejectsBlankSourceContentFactsWithChineseMessages() {
        assertBlankRejected("文档来源地址", () -> new DocumentSourceContent(" ", "制度内容".getBytes(StandardCharsets.UTF_8)));
        assertBlankRejected("文档源内容", () -> new DocumentSourceContent("s3://aip/documents/8001.txt", new byte[0]));
    }

    private static void assertBlankRejected(String fieldName, ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(fieldName + "不能为空");
    }

    private static DocumentObject documentObject(
            String knowledgeBaseId,
            String documentId,
            String fileName,
            String contentType,
            String contentHash
    ) {
        return new DocumentObject(
                TENANT_ID,
                knowledgeBaseId,
                documentId,
                fileName,
                contentType,
                contentHash,
                "制度内容".getBytes(StandardCharsets.UTF_8)
        );
    }
}
