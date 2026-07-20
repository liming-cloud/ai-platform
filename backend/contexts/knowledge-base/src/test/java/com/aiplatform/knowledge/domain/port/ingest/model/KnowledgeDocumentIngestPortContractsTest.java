package com.aiplatform.knowledge.domain.port.ingest.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeDocumentIngestPortContractsTest {
    @Test
    void chunkDraftPreservesEvidenceContentButRejectsBlankContent() {
        KnowledgeDocumentChunkDraft draft = new KnowledgeDocumentChunkDraft(" 制度内容 ", 4, null, 0, 6);

        assertThat(draft.content()).isEqualTo(" 制度内容 ");
        assertThatThrownBy(() -> new KnowledgeDocumentChunkDraft(" ", 4, null, 0, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("切片内容不能为空");
    }

    @Test
    void processedDocumentNormalizesProcessorAndStrategyBeforeDomainUse() {
        ProcessedKnowledgeDocument processed = new ProcessedKnowledgeDocument(
                " line-text ",
                " line:v1 ",
                List.of(new KnowledgeDocumentChunkDraft("制度内容", 4, null, 0, 4))
        );

        assertThat(processed.processorName()).isEqualTo("line-text");
        assertThat(processed.chunkStrategyVersion()).isEqualTo("line:v1");
    }

    @Test
    void processedDocumentRejectsInvalidChunkCollections() {
        assertThatThrownBy(() -> new ProcessedKnowledgeDocument("line-text", "line:v1", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("切片草稿列表不能为空");
        assertThatThrownBy(() -> new ProcessedKnowledgeDocument(
                "line-text",
                "line:v1",
                Arrays.asList(new KnowledgeDocumentChunkDraft("制度内容", 4, null, 0, 4), null)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("切片草稿不能为空");
    }
}
