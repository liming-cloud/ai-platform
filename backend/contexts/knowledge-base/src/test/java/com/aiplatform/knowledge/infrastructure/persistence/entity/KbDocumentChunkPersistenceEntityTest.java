package com.aiplatform.knowledge.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.knowledge.domain.KnowledgeDocumentChunk;
import com.aiplatform.knowledge.domain.KnowledgeDocumentChunkMetadata;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class KbDocumentChunkPersistenceEntityTest {
    @Test
    void rejectsNonNumericChunkIdWithChineseMessage() {
        KnowledgeDocumentChunk chunk = new KnowledgeDocumentChunk(
                TenantId.of("2001"),
                "chunk-main",
                "7001",
                "8001",
                1,
                "制度内容",
                "a".repeat(64),
                4,
                null,
                0,
                4,
                new KnowledgeDocumentChunkMetadata(
                        "s3://aip-knowledge/documents/8001/员工手册.txt",
                        1,
                        "员工手册.txt",
                        null,
                        null,
                        "internal",
                        "tenant",
                        List.of("tenant:2001"),
                        false,
                        Instant.parse("2026-05-22T08:00:00Z"),
                        null,
                        "embedding-model-unassigned",
                        "line:v1",
                        "kb-7001-doc-v1-draft"
                ),
                "ACTIVE",
                Instant.parse("2026-05-22T08:00:00Z"),
                Instant.parse("2026-05-22T08:00:00Z"),
                "1001"
        );

        assertThatThrownBy(() -> KbDocumentChunkPersistenceEntity.fromDomain(chunk))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("切片 ID 必须是数字型数据库主键");
    }

    @Test
    void toDomainRejectsMissingAclTokensInsteadOfUsingUnknownTenantFallback() {
        KbDocumentChunkPersistenceEntity entity = activeEntity();
        entity.setAclTokens(null);

        assertThatThrownBy(entity::toDomain)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("权限快照不能为空");
    }

    @Test
    void toDomainRejectsBlankAclTokenRowsInsteadOfFilteringBadInput() {
        KbDocumentChunkPersistenceEntity entity = activeEntity();
        entity.setAclTokens("tenant:2001\n \nrole:admin");

        assertThatThrownBy(entity::toDomain)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("权限快照不能为空白");
    }

    @Test
    void toDomainRejectsMissingRequiredFieldsInsteadOfDefaultingThem() {
        KbDocumentChunkPersistenceEntity entity = activeEntity();
        entity.setChunkNo(null);

        assertThatThrownBy(entity::toDomain)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("切片序号不能为空");

        entity = activeEntity();
        entity.setCreatedBy(null);

        assertThatThrownBy(entity::toDomain)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("创建人 ID不能为空");
    }

    private static KbDocumentChunkPersistenceEntity activeEntity() {
        KbDocumentChunkPersistenceEntity entity = new KbDocumentChunkPersistenceEntity();
        entity.setId(9001L);
        entity.setTenantId(2001L);
        entity.setKnowledgeBaseId(7001L);
        entity.setDocumentId(8001L);
        entity.setChunkNo(1);
        entity.setContent("制度内容");
        entity.setContentHash("a".repeat(64));
        entity.setSourceUri("s3://aip-knowledge/documents/8001/员工手册.txt");
        entity.setDocumentVersion(1);
        entity.setTitlePath("员工手册.txt");
        entity.setClassification("internal");
        entity.setVisibility("tenant");
        entity.setAclTokens("tenant:2001");
        entity.setAuthoritative(false);
        entity.setPublishedAt(Instant.parse("2026-05-22T08:00:00Z"));
        entity.setEmbeddingModel("embedding-model-unassigned");
        entity.setChunkStrategyVersion("line:v1");
        entity.setIndexVersion("kb-7001-doc-v1-draft");
        entity.setTokenCount(4);
        entity.setPositionStart(0);
        entity.setPositionEnd(4);
        entity.setStatus("ACTIVE");
        entity.setCreatedAt(Instant.parse("2026-05-22T08:00:00Z"));
        entity.setCreatedBy(1001L);
        entity.setUpdatedAt(Instant.parse("2026-05-22T08:00:00Z"));
        entity.setUpdatedBy(1001L);
        entity.setVersion(0);
        return entity;
    }
}
