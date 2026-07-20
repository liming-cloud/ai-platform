package com.aiplatform.knowledge.domain;

import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.Objects;

/**
 * 文档作业查询快照。它保留持久化失败码，不把查询所需诊断事实塞回执行聚合。
 */
public record DocumentIngestJobSnapshot(
        TenantId tenantId,
        String jobId,
        String documentId,
        String knowledgeBaseId,
        String jobType,
        DocumentIngestStatus status,
        String failureCode,
        String failureSummary,
        String traceId,
        Instant submittedAt,
        Instant updatedAt,
        String cancellationReason,
        String cancelledBy,
        String cancellationTraceId,
        Instant cancelledAt,
        String rebuildId
) {
    public DocumentIngestJobSnapshot {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        jobId = requireText(jobId, "任务 ID");
        documentId = requireText(documentId, "文档 ID");
        knowledgeBaseId = requireText(knowledgeBaseId, "知识库 ID");
        jobType = requireText(jobType, "任务类型");
        Objects.requireNonNull(status, "任务状态不能为空");
        traceId = requireText(traceId, "链路追踪 ID");
        if (traceId.length() > 64) {
            throw new IllegalArgumentException("链路追踪 ID 长度不能超过 64");
        }
        Objects.requireNonNull(submittedAt, "任务提交时间不能为空");
        Objects.requireNonNull(updatedAt, "任务更新时间不能为空");
        rebuildId = hasText(rebuildId) ? rebuildId.trim() : null;
        if (status == DocumentIngestStatus.FAILED || status == DocumentIngestStatus.RETRY_WAIT) {
            failureCode = requireText(failureCode, "失败码");
            failureSummary = requireText(failureSummary, "失败摘要");
        } else {
            if (hasText(failureCode) || hasText(failureSummary)) {
                throw new IllegalArgumentException("非失败任务不能携带失败诊断");
            }
            failureCode = null;
            failureSummary = null;
        }
        if (status == DocumentIngestStatus.CANCELLED) {
            cancellationReason = requireText(cancellationReason, "取消理由");
            cancelledBy = requireText(cancelledBy, "取消人");
            cancellationTraceId = requireText(cancellationTraceId, "取消链路追踪 ID");
            Objects.requireNonNull(cancelledAt, "取消时间不能为空");
        } else {
            if (hasText(cancellationReason) || hasText(cancelledBy) || hasText(cancellationTraceId)
                    || cancelledAt != null) {
                throw new IllegalArgumentException("非取消任务不能携带取消证据");
            }
            cancellationReason = null;
            cancelledBy = null;
            cancellationTraceId = null;
            cancelledAt = null;
        }
    }

    public DocumentIngestJobSnapshot(
            TenantId tenantId,
            String jobId,
            String documentId,
            String knowledgeBaseId,
            String jobType,
            DocumentIngestStatus status,
            String failureCode,
            String failureSummary,
            String traceId,
            Instant submittedAt,
            Instant updatedAt
    ) {
        this(tenantId, jobId, documentId, knowledgeBaseId, jobType, status, failureCode, failureSummary,
                traceId, submittedAt, updatedAt, null, null, null, null, null);
    }

    public DocumentIngestJobSnapshot(
            TenantId tenantId,
            String jobId,
            String documentId,
            String knowledgeBaseId,
            String jobType,
            DocumentIngestStatus status,
            String failureCode,
            String failureSummary,
            String traceId,
            Instant submittedAt,
            Instant updatedAt,
            String cancellationReason,
            String cancelledBy,
            String cancellationTraceId,
            Instant cancelledAt
    ) {
        this(tenantId, jobId, documentId, knowledgeBaseId, jobType, status, failureCode, failureSummary,
                traceId, submittedAt, updatedAt, cancellationReason, cancelledBy, cancellationTraceId,
                cancelledAt, null);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
