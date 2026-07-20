package com.aiplatform.knowledge.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * 原子 claim 返回的任务快照、尝试次数和可校验 ownership。
 */
public record ClaimedDocumentIngestJob(
        DocumentIngestJob job,
        int attemptCount,
        DocumentIngestJobOwnership ownership,
        DocumentIndexJobTarget indexTarget
) {
    public ClaimedDocumentIngestJob {
        Objects.requireNonNull(job, "任务不能为空");
        Objects.requireNonNull(ownership, "任务 ownership 不能为空");
        if (attemptCount < 1) {
            throw new IllegalArgumentException("任务尝试次数必须大于 0");
        }
        if (!job.tenantId().equals(ownership.tenantId()) || !job.jobId().equals(ownership.jobId())) {
            throw new IllegalArgumentException("任务事实与租约 ownership 不一致");
        }
        if (DocumentIngestJob.JOB_TYPE_DOCUMENT_INDEX.equals(job.jobType()) && indexTarget == null) {
            throw new IllegalArgumentException("索引任务缺少冻结候选事实");
        }
        if (DocumentIngestJob.JOB_TYPE_DOCUMENT_PARSE.equals(job.jobType()) && indexTarget != null) {
            throw new IllegalArgumentException("解析任务不能携带索引候选事实");
        }
    }

    public Optional<DocumentIndexJobTarget> optionalIndexTarget() {
        return Optional.ofNullable(indexTarget);
    }
}
