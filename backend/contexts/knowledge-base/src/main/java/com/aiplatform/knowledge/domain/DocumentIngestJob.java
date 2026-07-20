package com.aiplatform.knowledge.domain;

import com.aiplatform.shared.kernel.DomainEntity;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.Objects;

/**
 * 文档入库任务领域实体，统一记录解析、索引、失败原因和 Trace 关联。
 */
public record DocumentIngestJob(
        TenantId tenantId,
        String jobId,
        String documentId,
        String knowledgeBaseId,
        String jobType,
        DocumentIngestStatus status,
        String traceId,
        Instant submittedAt,
        Instant updatedAt,
        String operatorId,
        String failureReason
) implements DomainEntity<String> {
    public static final String JOB_TYPE_DOCUMENT_PARSE = "DOCUMENT_PARSE";
    public static final String JOB_TYPE_DOCUMENT_INDEX = "DOCUMENT_INDEX";

    public DocumentIngestJob {
        Objects.requireNonNull(tenantId, "租户ID不能为空");
        jobId = requireText(jobId, "任务ID");
        documentId = requireText(documentId, "文档ID");
        knowledgeBaseId = requireText(knowledgeBaseId, "知识库ID");
        jobType = requireText(jobType, "任务类型");
        Objects.requireNonNull(status, "任务状态不能为空");
        traceId = requireText(traceId, "链路追踪ID");
        Objects.requireNonNull(submittedAt, "任务提交时间不能为空");
        Objects.requireNonNull(updatedAt, "任务更新时间不能为空");
        operatorId = requireText(operatorId, "操作人ID");
        if (status == DocumentIngestStatus.FAILED || status == DocumentIngestStatus.RETRY_WAIT) {
            failureReason = requireText(failureReason, "失败原因");
        } else if (failureReason == null) {
            failureReason = "";
        } else if (failureReason.isBlank()) {
            failureReason = "";
        } else {
            throw new IllegalArgumentException("非失败任务不能携带失败原因");
        }
    }

    public DocumentIngestJob(
            TenantId tenantId,
            String jobId,
            String documentId,
            String knowledgeBaseId,
            String jobType,
            String status,
            String traceId,
            Instant submittedAt,
            Instant updatedAt,
            String operatorId,
            String failureReason
    ) {
        this(
                tenantId,
                jobId,
                documentId,
                knowledgeBaseId,
                jobType,
                DocumentIngestStatus.from(status),
                traceId,
                submittedAt,
                updatedAt,
                operatorId,
                failureReason
        );
    }

    public static DocumentIngestJob documentParse(
            TenantId tenantId,
            String jobId,
            String documentId,
            String knowledgeBaseId,
            String traceId,
            Instant submittedAt,
            String operatorId
    ) {
        return create(tenantId, jobId, documentId, knowledgeBaseId, JOB_TYPE_DOCUMENT_PARSE, traceId, submittedAt, operatorId);
    }

    public static DocumentIngestJob documentIndex(
            TenantId tenantId,
            String jobId,
            String documentId,
            String knowledgeBaseId,
            String traceId,
            Instant submittedAt,
            String operatorId
    ) {
        return create(tenantId, jobId, documentId, knowledgeBaseId, JOB_TYPE_DOCUMENT_INDEX, traceId, submittedAt, operatorId);
    }

    private static DocumentIngestJob create(
            TenantId tenantId,
            String jobId,
            String documentId,
            String knowledgeBaseId,
            String jobType,
            String traceId,
            Instant submittedAt,
            String operatorId
    ) {
        return new DocumentIngestJob(
                tenantId,
                jobId,
                documentId,
                knowledgeBaseId,
                jobType,
                DocumentIngestStatus.PENDING,
                traceId,
                submittedAt,
                submittedAt,
                operatorId,
                ""
        );
    }

    public DocumentIngestJob markParsing(Instant updatedAt) {
        requireCurrent(DocumentIngestStatus.PENDING, "开始解析");
        return withStatus(DocumentIngestStatus.PARSING, updatedAt, "");
    }

    public DocumentIngestJob markIndexing(Instant updatedAt) {
        requireCurrent(DocumentIngestStatus.PENDING, "开始索引");
        return withStatus(DocumentIngestStatus.INDEXING, updatedAt, "");
    }

    public DocumentIngestJob markCompleted(Instant updatedAt) {
        if (status != DocumentIngestStatus.PARSING && status != DocumentIngestStatus.INDEXING) {
            throw new IllegalStateException("只有解析中或索引中的任务才能完成，当前状态=" + status.name());
        }
        return withStatus(DocumentIngestStatus.COMPLETED, updatedAt, "");
    }

    public DocumentIngestJob markFailed(Instant updatedAt, String failureReason) {
        if (status == DocumentIngestStatus.COMPLETED) {
            throw new IllegalStateException("已完成任务不能标记失败");
        }
        return withStatus(DocumentIngestStatus.FAILED, updatedAt, failureReason);
    }

    public DocumentIngestJob markRetryWait(Instant updatedAt, String failureReason) {
        if (status != DocumentIngestStatus.PARSING && status != DocumentIngestStatus.INDEXING) {
            throw new IllegalStateException("只有执行中的任务才能等待重试，当前状态=" + status.name());
        }
        return withStatus(DocumentIngestStatus.RETRY_WAIT, updatedAt, failureReason);
    }

    public DocumentIngestJob requeueManually(Instant updatedAt) {
        requireCurrent(DocumentIngestStatus.FAILED, "人工重排");
        return withStatus(DocumentIngestStatus.PENDING, updatedAt, "");
    }

    @Override
    public String id() {
        return jobId;
    }

    private DocumentIngestJob withStatus(DocumentIngestStatus nextStatus, Instant updatedAt, String nextFailureReason) {
        Objects.requireNonNull(nextStatus, "目标任务状态不能为空");
        Objects.requireNonNull(updatedAt, "任务状态更新时间不能为空");
        return new DocumentIngestJob(
                tenantId,
                jobId,
                documentId,
                knowledgeBaseId,
                jobType,
                nextStatus,
                traceId,
                submittedAt,
                updatedAt,
                operatorId,
                nextFailureReason
        );
    }

    private void requireCurrent(DocumentIngestStatus expected, String action) {
        if (status != expected) {
            throw new IllegalStateException(action + "要求当前任务状态为" + expected.name() + "，实际状态=" + status.name());
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
