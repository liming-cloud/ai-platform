package com.aiplatform.knowledge.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.knowledge.domain.DocumentIngestJob;
import com.aiplatform.knowledge.domain.DocumentIngestStatus;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class KbEmbeddingJobPersistenceEntityTest {
    @Test
    void mapsDocumentIngestJobToDatabaseColumns() {
        DocumentIngestJob job = new DocumentIngestJob(
                TenantId.of("2001"),
                "8101",
                "8001",
                "7001",
                "DOCUMENT_PARSE",
                "PENDING",
                "TRACE-KB-DOC-001",
                Instant.parse("2026-05-22T08:00:00Z"),
                Instant.parse("2026-05-22T08:00:00Z"),
                "1001",
                ""
        );

        KbEmbeddingJobPersistenceEntity entity = KbEmbeddingJobPersistenceEntity.fromDomain(job);

        assertThat(entity.getId()).isEqualTo(8101L);
        assertThat(entity.getTenantId()).isEqualTo(2001L);
        assertThat(entity.getKnowledgeBaseId()).isEqualTo(7001L);
        assertThat(entity.getDocumentId()).isEqualTo(8001L);
        assertThat(entity.getJobType()).isEqualTo("DOCUMENT_PARSE");
        assertThat(entity.getStatus()).isEqualTo("PENDING");
        assertThat(entity.getFailureReason()).isNull();
        assertThat(entity.getTraceId()).isEqualTo("TRACE-KB-DOC-001");
        assertThat(entity.getCreatedBy()).isEqualTo(1001L);
        assertThat(entity.toDomain().failureReason()).isEmpty();
    }

    @Test
    void rejectsNonNumericJobIdWithChineseMessage() {
        DocumentIngestJob job = new DocumentIngestJob(
                TenantId.of("2001"),
                "job-main",
                "8001",
                "7001",
                "DOCUMENT_PARSE",
                "PENDING",
                "TRACE-KB-DOC-001",
                Instant.parse("2026-05-22T08:00:00Z"),
                Instant.parse("2026-05-22T08:00:00Z"),
                "1001",
                ""
        );

        assertThatThrownBy(() -> KbEmbeddingJobPersistenceEntity.fromDomain(job))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("任务 ID 必须是数字型数据库主键");
    }

    @Test
    void toDomainRejectsMissingRequiredFieldsInsteadOfDefaultingThem() {
        assertMissingJobField(entity -> entity.setTraceId(null), "链路追踪 ID");
        assertMissingJobField(entity -> entity.setCreatedBy(null), "操作人 ID");
        assertMissingJobField(entity -> entity.setTenantId(null), "租户 ID");
        assertMissingJobField(entity -> entity.setDocumentId(null), "文档 ID");
    }

    @Test
    void toDomainNormalizesPersistenceTextBeforeJobRestoration() {
        KbEmbeddingJobPersistenceEntity entity = validJobRow();
        entity.setJobType(" DOCUMENT_PARSE ");
        entity.setStatus(" PENDING ");
        entity.setTraceId(" TRACE-KB-DOC-001 ");
        entity.setFailureReason(" ");

        DocumentIngestJob job = entity.toDomain();

        assertThat(job.jobType()).isEqualTo("DOCUMENT_PARSE");
        assertThat(job.status()).isEqualTo(DocumentIngestStatus.PENDING);
        assertThat(job.traceId()).isEqualTo("TRACE-KB-DOC-001");
        assertThat(job.failureReason()).isEmpty();
    }

    @Test
    void toDomainRejectsFailedJobMissingFailureReasonInsteadOfEmptyFallback() {
        KbEmbeddingJobPersistenceEntity entity = validJobRow();
        entity.setStatus("FAILED");
        entity.setFailureReason(null);

        assertThatThrownBy(entity::toDomain)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("失败原因不能为空");
    }

    @Test
    void toDomainRejectsNonFailedJobWithFailureReason() {
        KbEmbeddingJobPersistenceEntity entity = validJobRow();
        entity.setFailureReason("等待解析");

        assertThatThrownBy(entity::toDomain)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("非失败任务不能携带失败原因");
    }

    private static void assertMissingJobField(
            Consumer<KbEmbeddingJobPersistenceEntity> mutation,
            String fieldName
    ) {
        KbEmbeddingJobPersistenceEntity entity = validJobRow();
        mutation.accept(entity);

        assertThatThrownBy(entity::toDomain)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("文档入库任务持久化记录缺少" + fieldName);
    }

    private static KbEmbeddingJobPersistenceEntity validJobRow() {
        KbEmbeddingJobPersistenceEntity entity = new KbEmbeddingJobPersistenceEntity();
        entity.setId(8101L);
        entity.setTenantId(2001L);
        entity.setKnowledgeBaseId(7001L);
        entity.setDocumentId(8001L);
        entity.setJobType("DOCUMENT_PARSE");
        entity.setStatus("PENDING");
        entity.setRetryCount(0);
        entity.setTraceId("TRACE-KB-DOC-001");
        entity.setCreatedAt(Instant.parse("2026-05-22T08:00:00Z"));
        entity.setCreatedBy(1001L);
        entity.setUpdatedAt(Instant.parse("2026-05-22T08:00:00Z"));
        entity.setUpdatedBy(1001L);
        entity.setVersion(0);
        return entity;
    }
}
