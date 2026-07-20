package com.aiplatform.knowledge.application.result;

import com.aiplatform.knowledge.domain.KnowledgeDocument;
import com.aiplatform.knowledge.domain.DocumentIngestJob;
import com.aiplatform.knowledge.domain.DocumentIngestJobSnapshot;
import com.aiplatform.knowledge.domain.DocumentIngestStatus;
import com.aiplatform.knowledge.domain.KnowledgeDocumentStatus;
import java.time.Instant;

public record KnowledgeDocumentListItemResult(
        String knowledgeBaseId,
        String documentId,
        String fileName,
        String status,
        String jobId,
        String jobType,
        String jobStatus,
        String failureCode,
        String failureSummary,
        String traceId,
        boolean manualRetryEligible,
        String cancellationReason,
        String cancelledBy,
        String cancellationTraceId,
        Instant cancelledAt,
        boolean cancelEligible
) {
    public KnowledgeDocumentListItemResult {
        knowledgeBaseId = requireText(knowledgeBaseId, "知识库ID");
        documentId = requireText(documentId, "文档ID");
        fileName = requireText(fileName, "文档名称");
        status = requireText(status, "文档状态");
        jobId = optionalText(jobId);
        jobType = optionalText(jobType);
        jobStatus = optionalText(jobStatus);
        failureCode = optionalText(failureCode);
        failureSummary = optionalText(failureSummary);
        traceId = optionalText(traceId);
        cancellationReason = optionalText(cancellationReason);
        cancelledBy = optionalText(cancelledBy);
        cancellationTraceId = optionalText(cancellationTraceId);
        if (manualRetryEligible && (jobId == null || jobType == null || jobStatus == null)) {
            throw new IllegalArgumentException("可人工重试的文档必须携带可信作业事实");
        }
        if (cancelEligible && (jobId == null || jobType == null || jobStatus == null)) {
            throw new IllegalArgumentException("可取消文档必须携带可信作业事实");
        }
        if ("CANCELLED".equals(jobStatus)) {
            if (cancellationReason == null || cancelledBy == null || cancellationTraceId == null || cancelledAt == null) {
                throw new IllegalArgumentException("已取消文档必须携带完整取消证据");
            }
        } else if (cancellationReason != null || cancelledBy != null || cancellationTraceId != null || cancelledAt != null) {
            throw new IllegalArgumentException("非取消文档不能携带取消证据");
        }
    }

    public KnowledgeDocumentListItemResult(
            String knowledgeBaseId,
            String documentId,
            String fileName,
            String status,
            String jobId,
            String jobType,
            String jobStatus,
            String failureCode,
            String failureSummary,
            String traceId,
            boolean manualRetryEligible
    ) {
        this(knowledgeBaseId, documentId, fileName, status, jobId, jobType, jobStatus, failureCode,
                failureSummary, traceId, manualRetryEligible, null, null, null, null, false);
    }

    public static KnowledgeDocumentListItemResult from(KnowledgeDocument document) {
        return from(document, null);
    }

    public static KnowledgeDocumentListItemResult from(
            KnowledgeDocument document,
            DocumentIngestJobSnapshot latestJob
    ) {
        if (latestJob == null) {
            return new KnowledgeDocumentListItemResult(
                    document.knowledgeBaseId(), document.documentId(), document.fileName(),
                    document.status().name(), null, null, null, null, null, null, false,
                    null, null, null, null, false
            );
        }
        boolean retryEligible = document.status() == com.aiplatform.knowledge.domain.KnowledgeDocumentStatus.FAILED
                && latestJob.status() == DocumentIngestStatus.FAILED
                && DocumentIngestJob.JOB_TYPE_DOCUMENT_PARSE.equals(latestJob.jobType());
        boolean cancellationEligible = document.cancellable()
                && switch (latestJob.status()) {
                    case PENDING, RETRY_WAIT, PARSING, INDEXING -> true;
                    default -> false;
                };
        boolean cancelled = document.status() == KnowledgeDocumentStatus.CANCELLED
                && latestJob.status() == DocumentIngestStatus.CANCELLED;
        return new KnowledgeDocumentListItemResult(
                document.knowledgeBaseId(),
                document.documentId(),
                document.fileName(),
                document.status().name(),
                latestJob.jobId(),
                latestJob.jobType(),
                latestJob.status().name(),
                KnowledgeDocumentJobDisclosurePolicy.failureCode(latestJob),
                KnowledgeDocumentJobDisclosurePolicy.failureSummary(latestJob),
                KnowledgeDocumentJobDisclosurePolicy.traceId(latestJob),
                retryEligible,
                cancelled ? latestJob.cancellationReason() : null,
                cancelled ? latestJob.cancelledBy() : null,
                cancelled ? latestJob.cancellationTraceId() : null,
                cancelled ? latestJob.cancelledAt() : null,
                cancellationEligible
        );
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
