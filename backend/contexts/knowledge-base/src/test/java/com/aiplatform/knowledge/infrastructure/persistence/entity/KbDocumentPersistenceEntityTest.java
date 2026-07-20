package com.aiplatform.knowledge.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.knowledge.domain.KnowledgeDocument;
import com.aiplatform.knowledge.domain.KnowledgeDocumentStatus;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class KbDocumentPersistenceEntityTest {
    @Test
    void mapsKnowledgeDocumentAggregateToDatabaseColumns() {
        KnowledgeDocument document = new KnowledgeDocument(
                TenantId.of("2001"),
                "8001",
                "7001",
                "员工手册.pdf",
                "upload",
                "local://knowledge-bases/7001/documents/8001/员工手册.pdf",
                "4b227777d4dd1fc61c6f884f48641d02b4d121d3fd328cb08b5531fcacdabf8a",
                "application/pdf",
                12L,
                1,
                KnowledgeDocumentStatus.UPLOADED,
                Instant.parse("2026-05-22T08:00:00Z"),
                Instant.parse("2026-05-22T08:00:00Z"),
                "1001"
        );

        KbDocumentPersistenceEntity entity = KbDocumentPersistenceEntity.fromDomain(document);

        assertThat(entity.getId()).isEqualTo(8001L);
        assertThat(entity.getTenantId()).isEqualTo(2001L);
        assertThat(entity.getKnowledgeBaseId()).isEqualTo(7001L);
        assertThat(entity.getDocumentName()).isEqualTo("员工手册.pdf");
        assertThat(entity.getSourceType()).isEqualTo("upload");
        assertThat(entity.getSourceUri()).isEqualTo("local://knowledge-bases/7001/documents/8001/员工手册.pdf");
        assertThat(entity.getContentHash()).isEqualTo("4b227777d4dd1fc61c6f884f48641d02b4d121d3fd328cb08b5531fcacdabf8a");
        assertThat(entity.getMimeType()).isEqualTo("application/pdf");
        assertThat(entity.getFileSizeBytes()).isEqualTo(12L);
        assertThat(entity.getParseStatus()).isEqualTo("UPLOADED");
        assertThat(entity.getVersionNo()).isEqualTo(1);
        assertThat(entity.getCreatedBy()).isEqualTo(1001L);
    }

    @Test
    void rejectsNonNumericDocumentIdWithChineseMessage() {
        KnowledgeDocument document = new KnowledgeDocument(
                TenantId.of("2001"),
                "doc-main",
                "7001",
                "员工手册.pdf",
                "upload",
                "local://knowledge-bases/7001/documents/doc-main/员工手册.pdf",
                "4b227777d4dd1fc61c6f884f48641d02b4d121d3fd328cb08b5531fcacdabf8a",
                "application/pdf",
                12L,
                1,
                KnowledgeDocumentStatus.UPLOADED,
                Instant.parse("2026-05-22T08:00:00Z"),
                Instant.parse("2026-05-22T08:00:00Z"),
                "1001"
        );

        assertThatThrownBy(() -> KbDocumentPersistenceEntity.fromDomain(document))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文档 ID 必须是数字型数据库主键");
    }

    @Test
    void toDomainRejectsMissingRequiredFieldsInsteadOfDefaultingThem() {
        assertMissingDocumentField(entity -> entity.setSourceUri(null), "文档来源地址");
        assertMissingDocumentField(entity -> entity.setMimeType(null), "文档 MIME 类型");
        assertMissingDocumentField(entity -> entity.setFileSizeBytes(null), "文档大小");
        assertMissingDocumentField(entity -> entity.setVersionNo(null), "文档业务版本号");
        assertMissingDocumentField(entity -> entity.setCreatedBy(null), "上传人 ID");
    }

    @Test
    void toDomainNormalizesPersistenceTextBeforeAggregateRestoration() {
        KbDocumentPersistenceEntity entity = validDocumentRow();
        entity.setDocumentName(" 员工手册.pdf ");
        entity.setSourceType(" upload ");
        entity.setSourceUri(" local://knowledge-bases/7001/documents/8001/员工手册.pdf ");
        entity.setContentHash(" 4b227777d4dd1fc61c6f884f48641d02b4d121d3fd328cb08b5531fcacdabf8a ");
        entity.setMimeType(" text/plain ");
        entity.setParseStatus(" UPLOADED ");

        KnowledgeDocument document = entity.toDomain();

        assertThat(document.fileName()).isEqualTo("员工手册.pdf");
        assertThat(document.sourceType()).isEqualTo("upload");
        assertThat(document.sourceUri()).isEqualTo("local://knowledge-bases/7001/documents/8001/员工手册.pdf");
        assertThat(document.contentHash()).isEqualTo("4b227777d4dd1fc61c6f884f48641d02b4d121d3fd328cb08b5531fcacdabf8a");
        assertThat(document.contentType()).isEqualTo("text/plain");
        assertThat(document.status()).isEqualTo(KnowledgeDocumentStatus.UPLOADED);
    }

    private static void assertMissingDocumentField(
            Consumer<KbDocumentPersistenceEntity> mutation,
            String fieldName
    ) {
        KbDocumentPersistenceEntity entity = validDocumentRow();
        mutation.accept(entity);

        assertThatThrownBy(entity::toDomain)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("知识文档持久化记录缺少" + fieldName);
    }

    private static KbDocumentPersistenceEntity validDocumentRow() {
        KbDocumentPersistenceEntity entity = new KbDocumentPersistenceEntity();
        entity.setId(8001L);
        entity.setTenantId(2001L);
        entity.setKnowledgeBaseId(7001L);
        entity.setDocumentName("员工手册.pdf");
        entity.setSourceType("upload");
        entity.setSourceUri("local://knowledge-bases/7001/documents/8001/员工手册.pdf");
        entity.setContentHash("4b227777d4dd1fc61c6f884f48641d02b4d121d3fd328cb08b5531fcacdabf8a");
        entity.setMimeType("application/pdf");
        entity.setFileSizeBytes(12L);
        entity.setParseStatus("UPLOADED");
        entity.setVersionNo(1);
        entity.setCreatedAt(Instant.parse("2026-05-22T08:00:00Z"));
        entity.setCreatedBy(1001L);
        entity.setUpdatedAt(Instant.parse("2026-05-22T08:00:00Z"));
        entity.setUpdatedBy(1001L);
        entity.setVersion(0);
        return entity;
    }
}
