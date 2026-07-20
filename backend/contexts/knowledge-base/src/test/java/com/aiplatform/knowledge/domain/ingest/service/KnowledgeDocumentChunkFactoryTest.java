package com.aiplatform.knowledge.domain.ingest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.knowledge.domain.KnowledgeDocument;
import com.aiplatform.knowledge.domain.KnowledgeDocumentChunk;
import com.aiplatform.knowledge.domain.KnowledgeDocumentStatus;
import com.aiplatform.knowledge.domain.port.ingest.model.KnowledgeDocumentChunkDraft;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class KnowledgeDocumentChunkFactoryTest {
    private static final Instant NOW = Instant.parse("2026-05-22T08:00:00Z");

    @Test
    void normalizesChunkCreationFactsBeforeMetadataAndAclUse() {
        KnowledgeDocumentChunk chunk = new KnowledgeDocumentChunkFactory().createChunk(
                document(),
                new KnowledgeDocumentChunkDraft("制度内容", 4, null, 0, 4),
                " 9001 ",
                1,
                " 1001 ",
                " line:v1 ",
                NOW
        );

        assertThat(chunk.chunkId()).isEqualTo("9001");
        assertThat(chunk.operatorId()).isEqualTo("1001");
        assertThat(chunk.metadata().aclTokens()).contains("owner:1001");
        assertThat(chunk.metadata().chunkStrategyVersion()).isEqualTo("line:v1");
    }

    @Test
    void rejectsBlankChunkCreationFactsBeforeCreatingChunk() {
        KnowledgeDocumentChunkFactory factory = new KnowledgeDocumentChunkFactory();
        KnowledgeDocumentChunkDraft draft = new KnowledgeDocumentChunkDraft("制度内容", 4, null, 0, 4);

        assertThatThrownBy(() -> factory.createChunk(document(), draft, " ", 1, "1001", "line:v1", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("切片 ID不能为空");
        assertThatThrownBy(() -> factory.createChunk(document(), draft, "9001", 1, " ", "line:v1", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("操作人 ID不能为空");
        assertThatThrownBy(() -> factory.createChunk(document(), draft, "9001", 1, "1001", " ", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("切块策略版本不能为空");
    }

    private static KnowledgeDocument document() {
        return new KnowledgeDocument(
                TenantId.of("2001"),
                "8001",
                "7001",
                "员工手册.txt",
                "upload",
                "s3://aip-knowledge/tenant/2001/doc.txt",
                "d".repeat(64),
                "text/plain",
                28L,
                1,
                KnowledgeDocumentStatus.UPLOADED,
                NOW,
                NOW,
                "1001"
        );
    }
}
