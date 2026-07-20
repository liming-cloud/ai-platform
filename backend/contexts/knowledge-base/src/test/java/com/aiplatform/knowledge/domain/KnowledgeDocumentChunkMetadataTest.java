package com.aiplatform.knowledge.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeDocumentChunkMetadataTest {
    @Test
    void normalizesMetadataFactsBeforeRagUse() {
        KnowledgeDocumentChunkMetadata metadata = new KnowledgeDocumentChunkMetadata(
                " s3://aip-knowledge/tenant/2001/doc.txt ",
                1,
                " 员工手册.txt ",
                null,
                " A1:B2 ",
                " internal ",
                " tenant ",
                List.of(" tenant:2001 "),
                false,
                Instant.parse("2026-05-22T08:00:00Z"),
                null,
                " embedding-model-unassigned ",
                " line:v1 ",
                " kb-7001-doc-v1-draft "
        );

        assertThat(metadata.sourceUri()).isEqualTo("s3://aip-knowledge/tenant/2001/doc.txt");
        assertThat(metadata.titlePath()).isEqualTo("员工手册.txt");
        assertThat(metadata.tableCellRange()).isEqualTo("A1:B2");
        assertThat(metadata.classification()).isEqualTo("internal");
        assertThat(metadata.visibility()).isEqualTo("tenant");
        assertThat(metadata.aclTokens()).containsExactly("tenant:2001");
        assertThat(metadata.embeddingModel()).isEqualTo("embedding-model-unassigned");
        assertThat(metadata.chunkStrategyVersion()).isEqualTo("line:v1");
        assertThat(metadata.indexVersion()).isEqualTo("kb-7001-doc-v1-draft");
    }

    @Test
    void requiresExplicitClassificationAndVisibility() {
        assertThatThrownBy(() -> metadata(" ", "tenant"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("知识密级不能为空");
        assertThatThrownBy(() -> metadata("internal", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("知识可见性不能为空");
        assertThatThrownBy(() -> new KnowledgeDocumentChunkMetadata(
                "s3://aip-knowledge/tenant/2001/doc.txt",
                1,
                "员工手册.txt",
                null,
                " ",
                "internal",
                "tenant",
                List.of("tenant:2001"),
                false,
                Instant.parse("2026-05-22T08:00:00Z"),
                null,
                "embedding-model-unassigned",
                "line:v1",
                "kb-7001-doc-v1-draft"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("表格单元格范围不能为空");
    }

    @Test
    void rejectsBlankAclTokensInsteadOfFilteringBadInput() {
        assertThatThrownBy(() -> new KnowledgeDocumentChunkMetadata(
                "s3://aip-knowledge/tenant/2001/doc.txt",
                1,
                "员工手册.txt",
                null,
                null,
                "internal",
                "tenant",
                List.of("tenant:2001", " "),
                false,
                Instant.parse("2026-05-22T08:00:00Z"),
                null,
                "embedding-model-unassigned",
                "line:v1",
                "kb-7001-doc-v1-draft"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("权限快照不能为空白");
    }

    @Test
    void doesNotExposeDefaultDraftFactory() {
        assertThat(Arrays.stream(KnowledgeDocumentChunkMetadata.class.getDeclaredMethods()).map(Method::getName))
                .doesNotContain("defaultDraft");
    }

    private static KnowledgeDocumentChunkMetadata metadata(String classification, String visibility) {
        return new KnowledgeDocumentChunkMetadata(
                "s3://aip-knowledge/tenant/2001/doc.txt",
                1,
                "员工手册.txt",
                null,
                null,
                classification,
                visibility,
                List.of("tenant:2001"),
                false,
                Instant.parse("2026-05-22T08:00:00Z"),
                null,
                "embedding-model-unassigned",
                "line:v1",
                "kb-7001-doc-v1-draft"
        );
    }
}
