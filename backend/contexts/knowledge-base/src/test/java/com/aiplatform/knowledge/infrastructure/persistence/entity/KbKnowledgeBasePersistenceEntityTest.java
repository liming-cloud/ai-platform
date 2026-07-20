package com.aiplatform.knowledge.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.knowledge.domain.KnowledgeBase;
import com.aiplatform.knowledge.domain.KnowledgeVisibility;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KbKnowledgeBasePersistenceEntityTest {
    @Test
    void mapsKnowledgeBaseAggregateToDatabaseColumns() {
        KnowledgeBase knowledgeBase = new KnowledgeBase(
                TenantId.of("2001"),
                "7001",
                "企业制度知识库",
                "用于制度问答",
                KnowledgeVisibility.PUBLIC,
                Set.of("1001"),
                "1001",
                "17",
                true,
                true,
                Instant.parse("2026-05-22T08:00:00Z"),
                Instant.parse("2026-05-22T08:00:00Z")
        );

        KbKnowledgeBasePersistenceEntity entity = KbKnowledgeBasePersistenceEntity.fromDomain(knowledgeBase);

        assertThat(entity.getId()).isEqualTo(7001L);
        assertThat(entity.getTenantId()).isEqualTo(2001L);
        assertThat(entity.getKbCode()).isEqualTo("KB-7001");
        assertThat(entity.getKbName()).isEqualTo("企业制度知识库");
        assertThat(entity.getDescription()).isEqualTo("用于制度问答");
        assertThat(entity.getVisibility()).isEqualTo("public");
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");
        assertThat(entity.getCreatedBy()).isEqualTo(1001L);
        assertThat(entity.getUpdatedBy()).isEqualTo(1001L);
        assertThat(entity.toDomain().createdBy()).isEqualTo("1001");
    }

    @Test
    void rejectsNonNumericOperatorIdWithChineseMessage() {
        KnowledgeBase knowledgeBase = new KnowledgeBase(
                TenantId.of("2001"),
                "7001",
                "企业制度知识库",
                "用于制度问答",
                KnowledgeVisibility.PUBLIC,
                Set.of("operator"),
                "operator",
                "17",
                true,
                true,
                Instant.parse("2026-05-22T08:00:00Z"),
                Instant.parse("2026-05-22T08:00:00Z")
        );

        assertThatThrownBy(() -> KbKnowledgeBasePersistenceEntity.fromDomain(knowledgeBase))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("创建人 ID 必须是数字型数据库主键");
    }

    @Test
    void toDomainRejectsMissingCreatedByInsteadOfEmptyPrincipalFallback() {
        KbKnowledgeBasePersistenceEntity entity = validKnowledgeBaseRow();
        entity.setCreatedBy(null);

        assertThatThrownBy(entity::toDomain)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("知识库持久化记录缺少创建人 ID");
    }

    @Test
    void toDomainNormalizesPersistenceTextBeforeKnowledgeBaseRestoration() {
        KbKnowledgeBasePersistenceEntity entity = validKnowledgeBaseRow();
        entity.setKbName(" 企业制度知识库 ");
        entity.setDescription(" 用于制度问答 ");
        entity.setVisibility(" public ");
        entity.setStatus(" ACTIVE ");

        KnowledgeBase knowledgeBase = entity.toDomain();

        assertThat(knowledgeBase.name()).isEqualTo("企业制度知识库");
        assertThat(knowledgeBase.description()).isEqualTo("用于制度问答");
        assertThat(knowledgeBase.visibility()).isEqualTo(KnowledgeVisibility.PUBLIC);
        assertThat(knowledgeBase.published()).isTrue();
    }

    @Test
    void toDomainRejectsMissingOrUnsupportedStatusInsteadOfDraftFallback() {
        KbKnowledgeBasePersistenceEntity missingStatus = validKnowledgeBaseRow();
        missingStatus.setStatus(null);
        assertThatThrownBy(missingStatus::toDomain)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("知识库持久化记录缺少知识库状态");

        KbKnowledgeBasePersistenceEntity unsupportedStatus = validKnowledgeBaseRow();
        unsupportedStatus.setStatus("ARCHIVED");
        assertThatThrownBy(unsupportedStatus::toDomain)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的知识库状态：ARCHIVED");
    }

    @Test
    void mapsDisabledKnowledgeBaseToDisabledDatabaseStatus() {
        KnowledgeBase knowledgeBase = new KnowledgeBase(
                TenantId.of("2001"),
                "7001",
                "企业制度知识库",
                "用于制度问答",
                KnowledgeVisibility.PUBLIC,
                Set.of("1001"),
                "1001",
                "17",
                true,
                false,
                Instant.parse("2026-05-22T08:00:00Z"),
                Instant.parse("2026-05-22T08:00:00Z")
        );

        KbKnowledgeBasePersistenceEntity entity = KbKnowledgeBasePersistenceEntity.fromDomain(knowledgeBase);

        assertThat(entity.getStatus()).isEqualTo("DISABLED");
        assertThat(entity.toDomain().published()).isFalse();
    }

    private static KbKnowledgeBasePersistenceEntity validKnowledgeBaseRow() {
        KbKnowledgeBasePersistenceEntity entity = new KbKnowledgeBasePersistenceEntity();
        entity.setId(7001L);
        entity.setTenantId(2001L);
        entity.setKbCode("KB-7001");
        entity.setKbName("企业制度知识库");
        entity.setDescription("用于制度问答");
        entity.setVisibility("public");
        entity.setStatus("ACTIVE");
        entity.setEmbeddingModelConfigId(17L);
        entity.setCreatedAt(Instant.parse("2026-05-22T08:00:00Z"));
        entity.setCreatedBy(1001L);
        entity.setUpdatedAt(Instant.parse("2026-05-22T08:00:00Z"));
        entity.setUpdatedBy(1001L);
        entity.setVersion(0);
        return entity;
    }
}
