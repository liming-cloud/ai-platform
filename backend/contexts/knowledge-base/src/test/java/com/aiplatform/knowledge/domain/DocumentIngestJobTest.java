package com.aiplatform.knowledge.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class DocumentIngestJobTest {
    private static final Instant NOW = Instant.parse("2026-05-25T00:00:00Z");

    @Test
    void enforcesParseLifecycleTransitions() {
        DocumentIngestJob pending = DocumentIngestJob.documentParse(
                TenantId.of("2001"),
                "8101",
                "8001",
                "7001",
                "trace-001",
                NOW,
                "1001"
        );

        DocumentIngestJob parsing = pending.markParsing(NOW.plusSeconds(1));
        DocumentIngestJob completed = parsing.markCompleted(NOW.plusSeconds(2));

        assertThat(pending.status()).isEqualTo(DocumentIngestStatus.PENDING);
        assertThat(parsing.status()).isEqualTo(DocumentIngestStatus.PARSING);
        assertThat(completed.status()).isEqualTo(DocumentIngestStatus.COMPLETED);
        assertThatThrownBy(() -> pending.markCompleted(NOW.plusSeconds(3)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("解析中或索引中");
        assertThatThrownBy(() -> completed.markFailed(NOW.plusSeconds(4), "失败原因"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已完成任务不能标记失败");
    }

    @Test
    void enforcesIndexLifecycleTransitions() {
        DocumentIngestJob pending = DocumentIngestJob.documentIndex(
                TenantId.of("2001"),
                "8201",
                "8001",
                "7001",
                "trace-002",
                NOW,
                "1001"
        );

        DocumentIngestJob indexing = pending.markIndexing(NOW.plusSeconds(1));
        DocumentIngestJob failed = indexing.markFailed(NOW.plusSeconds(2), "向量化服务未配置");

        assertThat(indexing.status()).isEqualTo(DocumentIngestStatus.INDEXING);
        assertThat(failed.status()).isEqualTo(DocumentIngestStatus.FAILED);
        assertThat(failed.failureReason()).isEqualTo("向量化服务未配置");
        assertThatThrownBy(() -> pending.markParsing(NOW.plusSeconds(3)).markIndexing(NOW.plusSeconds(4)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("开始索引要求当前任务状态为PENDING");
    }

    @Test
    void recognizesDurableRetryWaitStateWithoutRevivingTerminalFailure() {
        assertThat(DocumentIngestStatus.from(" retry_wait ")).isEqualTo(DocumentIngestStatus.RETRY_WAIT);
        assertThat(DocumentIngestStatus.from("failed")).isEqualTo(DocumentIngestStatus.FAILED);
    }

    @Test
    void rejectsFailedJobWithoutFailureReasonInsteadOfEmptyFallback() {
        DocumentIngestJob indexing = DocumentIngestJob.documentIndex(
                TenantId.of("2001"),
                "8201",
                "8001",
                "7001",
                "trace-002",
                NOW,
                "1001"
        ).markIndexing(NOW.plusSeconds(1));

        assertThatThrownBy(() -> indexing.markFailed(NOW.plusSeconds(2), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("失败原因不能为空");
    }

    @Test
    void rejectsNonFailedJobWithFailureReason() {
        assertThatThrownBy(() -> new DocumentIngestJob(
                TenantId.of("2001"),
                "8101",
                "8001",
                "7001",
                "DOCUMENT_PARSE",
                "PENDING",
                "trace-001",
                NOW,
                NOW,
                "1001",
                "等待解析"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("非失败任务不能携带失败原因");
    }

    @Test
    void normalizesTaskFactsAndFailureReasonInsideEntity() {
        DocumentIngestJob failed = new DocumentIngestJob(
                TenantId.of("2001"),
                " 8101 ",
                " 8001 ",
                " 7001 ",
                " DOCUMENT_PARSE ",
                " failed ",
                " trace-001 ",
                NOW,
                NOW,
                " 1001 ",
                " 解析失败 "
        );
        DocumentIngestJob pending = new DocumentIngestJob(
                TenantId.of("2001"),
                " 8102 ",
                " 8001 ",
                " 7001 ",
                " DOCUMENT_INDEX ",
                DocumentIngestStatus.PENDING,
                " trace-002 ",
                NOW,
                NOW,
                " 1001 ",
                " "
        );

        assertThat(failed.jobId()).isEqualTo("8101");
        assertThat(failed.documentId()).isEqualTo("8001");
        assertThat(failed.knowledgeBaseId()).isEqualTo("7001");
        assertThat(failed.jobType()).isEqualTo("DOCUMENT_PARSE");
        assertThat(failed.status()).isEqualTo(DocumentIngestStatus.FAILED);
        assertThat(failed.traceId()).isEqualTo("trace-001");
        assertThat(failed.operatorId()).isEqualTo("1001");
        assertThat(failed.failureReason()).isEqualTo("解析失败");
        assertThat(pending.failureReason()).isEmpty();
    }

    @Test
    void rejectsBlankRequiredTaskFactsInsteadOfInvalidIngestJob() {
        assertBlankRejected("任务ID", () -> job(" ", "8001", "7001", "DOCUMENT_PARSE", "trace-001", "1001"));
        assertBlankRejected("文档ID", () -> job("8101", " ", "7001", "DOCUMENT_PARSE", "trace-001", "1001"));
        assertBlankRejected("知识库ID", () -> job("8101", "8001", " ", "DOCUMENT_PARSE", "trace-001", "1001"));
        assertBlankRejected("任务类型", () -> job("8101", "8001", "7001", " ", "trace-001", "1001"));
        assertBlankRejected("链路追踪ID", () -> job("8101", "8001", "7001", "DOCUMENT_PARSE", " ", "1001"));
        assertBlankRejected("操作人ID", () -> job("8101", "8001", "7001", "DOCUMENT_PARSE", "trace-001", " "));
    }

    private static void assertBlankRejected(String fieldName, ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(fieldName + "不能为空");
    }

    private static DocumentIngestJob job(
            String jobId,
            String documentId,
            String knowledgeBaseId,
            String jobType,
            String traceId,
            String operatorId
    ) {
        return new DocumentIngestJob(
                TenantId.of("2001"),
                jobId,
                documentId,
                knowledgeBaseId,
                jobType,
                DocumentIngestStatus.PENDING,
                traceId,
                NOW,
                NOW,
                operatorId,
                ""
        );
    }
}
