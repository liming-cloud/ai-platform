package com.aiplatform.knowledge.presentation.dto;

import com.aiplatform.knowledge.application.result.KnowledgeDocumentListItemResult;
import java.time.Instant;

public record KnowledgeDocumentListItemResponse(
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
    public KnowledgeDocumentListItemResponse {
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
    }

    public static KnowledgeDocumentListItemResponse from(KnowledgeDocumentListItemResult result) {
        return new KnowledgeDocumentListItemResponse(
                result.knowledgeBaseId(),
                result.documentId(),
                result.fileName(),
                result.status(),
                result.jobId(),
                result.jobType(),
                result.jobStatus(),
                result.failureCode(),
                result.failureSummary(),
                result.traceId(),
                result.manualRetryEligible(),
                result.cancellationReason(),
                result.cancelledBy(),
                result.cancellationTraceId(),
                result.cancelledAt(),
                result.cancelEligible()
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
