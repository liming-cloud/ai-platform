package com.aiplatform.knowledge.application.result;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiplatform.knowledge.domain.DocumentIngestJob;
import com.aiplatform.knowledge.domain.DocumentIngestJobSnapshot;
import com.aiplatform.knowledge.domain.DocumentIngestStatus;
import com.aiplatform.knowledge.domain.KnowledgeDocument;
import com.aiplatform.knowledge.domain.KnowledgeDocumentStatus;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class KnowledgeDocumentListItemResultTest {
    private static final Instant NOW = Instant.parse("2026-07-18T08:00:00Z");

    @Test
    void exposesBoundedSafeParseFailureAndMarksOnlyThatTerminalStateRetryable() {
        String longSummary = "解析服务不可用".repeat(80);
        DocumentIngestJobSnapshot failedParse = snapshot(
                DocumentIngestJob.JOB_TYPE_DOCUMENT_PARSE,
                DocumentIngestStatus.FAILED,
                "AIP-KB-PARSE-FAILED",
                longSummary,
                "TRACE-KB-PARSE-001"
        );

        KnowledgeDocumentListItemResult result = KnowledgeDocumentListItemResult.from(
                document(KnowledgeDocumentStatus.FAILED), failedParse
        );

        assertThat(result.failureCode()).isEqualTo("AIP-KB-PARSE-FAILED");
        assertThat(result.failureSummary().codePointCount(0, result.failureSummary().length())).isEqualTo(240);
        assertThat(result.traceId()).isEqualTo("TRACE-KB-PARSE-001");
        assertThat(result.manualRetryEligible()).isTrue();
    }

    @Test
    void indexFailureIsAnExplicitNonRetryTerminalJobWithSafeReplacementGenerationPath() {
        DocumentIngestJobSnapshot failedIndex = snapshot(
                DocumentIngestJob.JOB_TYPE_DOCUMENT_INDEX,
                DocumentIngestStatus.FAILED,
                "AIP-KB-INDEX-FAILED",
                "向量服务不可用",
                "TRACE-KB-INDEX-001"
        );

        KnowledgeDocumentListItemResult result = KnowledgeDocumentListItemResult.from(
                document(KnowledgeDocumentStatus.FAILED), failedIndex
        );

        assertThat(result.jobType()).isEqualTo("DOCUMENT_INDEX");
        assertThat(result.jobStatus()).isEqualTo("FAILED");
        assertThat(result.manualRetryEligible()).isFalse();
    }

    @Test
    void withholdsSecretBearingSummaryAndMalformedDiagnosticIdentifiers() {
        DocumentIngestJobSnapshot unsafe = snapshot(
                DocumentIngestJob.JOB_TYPE_DOCUMENT_PARSE,
                DocumentIngestStatus.FAILED,
                "not a safe code",
                "provider authorization=Bearer-secret-value",
                "trace with spaces"
        );

        KnowledgeDocumentListItemResult result = KnowledgeDocumentListItemResult.from(
                document(KnowledgeDocumentStatus.FAILED), unsafe
        );

        assertThat(result.failureCode()).isEqualTo("AIP-KB-JOB-FAILED");
        assertThat(result.failureSummary()).isEqualTo("任务失败，详细信息仅在受控日志中可见");
        assertThat(result.traceId()).isNull();
    }

    @Test
    void nonFailureJobDoesNotExposeStaleFailureEvidence() {
        DocumentIngestJobSnapshot pending = snapshot(
                DocumentIngestJob.JOB_TYPE_DOCUMENT_INDEX,
                DocumentIngestStatus.PENDING,
                null,
                null,
                "TRACE-KB-INDEX-PENDING"
        );

        KnowledgeDocumentListItemResult result = KnowledgeDocumentListItemResult.from(
                document(KnowledgeDocumentStatus.FAILED), pending
        );

        assertThat(result.failureCode()).isNull();
        assertThat(result.failureSummary()).isNull();
        assertThat(result.manualRetryEligible()).isFalse();
    }

    @Test
    void exposesCancellationEligibilityAndPersistedTerminalEvidence() {
        DocumentIngestJobSnapshot pending = snapshot(
                DocumentIngestJob.JOB_TYPE_DOCUMENT_PARSE,
                DocumentIngestStatus.PARSING,
                null,
                null,
                "TRACE-KB-PARSE-RUNNING"
        );
        KnowledgeDocumentListItemResult running = KnowledgeDocumentListItemResult.from(
                document(KnowledgeDocumentStatus.PARSING), pending
        );
        assertThat(running.cancelEligible()).isTrue();

        DocumentIngestJobSnapshot cancelled = new DocumentIngestJobSnapshot(
                TenantId.of("2001"), "8101", "8001", "7001",
                DocumentIngestJob.JOB_TYPE_DOCUMENT_PARSE, DocumentIngestStatus.CANCELLED,
                null, null, "TRACE-KB-PARSE-RUNNING", NOW.minusSeconds(60), NOW,
                "来源文件错误", "1001", "TRACE-KB-CANCEL-001", NOW
        );
        KnowledgeDocumentListItemResult terminal = KnowledgeDocumentListItemResult.from(
                document(KnowledgeDocumentStatus.CANCELLED), cancelled
        );
        assertThat(terminal.cancelEligible()).isFalse();
        assertThat(terminal.cancellationReason()).isEqualTo("来源文件错误");
        assertThat(terminal.cancelledBy()).isEqualTo("1001");
        assertThat(terminal.cancellationTraceId()).isEqualTo("TRACE-KB-CANCEL-001");
        assertThat(terminal.cancelledAt()).isEqualTo(NOW);
    }

    private static DocumentIngestJobSnapshot snapshot(
            String jobType,
            DocumentIngestStatus status,
            String failureCode,
            String failureSummary,
            String traceId
    ) {
        return new DocumentIngestJobSnapshot(
                TenantId.of("2001"), "8101", "8001", "7001", jobType, status,
                failureCode, failureSummary, traceId, NOW.minusSeconds(60), NOW
        );
    }

    private static KnowledgeDocument document(KnowledgeDocumentStatus status) {
        return new KnowledgeDocument(
                TenantId.of("2001"), "8001", "7001", "policy.pdf", "upload",
                "s3://bucket/policy.pdf", "hash", "application/pdf", 128, 1,
                status, NOW.minusSeconds(120), NOW, "1001"
        );
    }
}
