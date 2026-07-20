package com.aiplatform.knowledge.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeDocumentChunkTest {
    private static final Instant NOW = Instant.parse("2026-05-22T08:00:00Z");

    @Test
    void normalizesChunkFactsBeforeIndexAndRetrievalUse() {
        KnowledgeDocumentChunk chunk = new KnowledgeDocumentChunk(
                TenantId.of("2001"),
                " 9001 ",
                " 7001 ",
                " 8001 ",
                1,
                " 制度内容 ",
                " " + "a".repeat(64) + " ",
                4,
                null,
                0,
                6,
                metadata(),
                " ACTIVE ",
                NOW,
                NOW,
                " 1001 "
        );

        assertThat(chunk.chunkId()).isEqualTo("9001");
        assertThat(chunk.id()).isEqualTo("9001");
        assertThat(chunk.knowledgeBaseId()).isEqualTo("7001");
        assertThat(chunk.documentId()).isEqualTo("8001");
        assertThat(chunk.content()).isEqualTo(" 制度内容 ");
        assertThat(chunk.contentHash()).isEqualTo("a".repeat(64));
        assertThat(chunk.status()).isEqualTo("ACTIVE");
        assertThat(chunk.operatorId()).isEqualTo("1001");
    }

    @Test
    void rejectsBlankChunkFactsInsteadOfKeepingInvalidMetadata() {
        assertThatThrownBy(() -> chunk(" ", "9001", "1001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("切片 ID不能为空");
        assertThatThrownBy(() -> chunk("9001", " ", "1001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("切片状态不能为空");
        assertThatThrownBy(() -> chunk("9001", "ACTIVE", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("操作人 ID不能为空");
    }

    private static KnowledgeDocumentChunk chunk(String chunkId, String status, String operatorId) {
        return new KnowledgeDocumentChunk(
                TenantId.of("2001"),
                chunkId,
                "7001",
                "8001",
                1,
                "制度内容",
                "a".repeat(64),
                4,
                null,
                0,
                4,
                metadata(),
                status,
                NOW,
                NOW,
                operatorId
        );
    }

    private static KnowledgeDocumentChunkMetadata metadata() {
        return new KnowledgeDocumentChunkMetadata(
                "s3://aip-knowledge/tenant/2001/doc.txt",
                1,
                "员工手册.txt",
                null,
                null,
                "internal",
                "tenant",
                List.of("tenant:2001"),
                false,
                NOW,
                null,
                "embedding-model-unassigned",
                "line:v1",
                "kb-7001-doc-v1-draft"
        );
    }
}
