package com.aiplatform.knowledge.domain;

import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.Objects;

/**
 * 一次任务执行的租约与 fencing 身份。旧 worker 不得借此身份完成已被重领的任务。
 */
public record DocumentIngestJobOwnership(
        TenantId tenantId,
        String jobId,
        String leaseOwner,
        String leaseToken,
        Instant leaseUntil,
        long fenceVersion
) {
    public DocumentIngestJobOwnership {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        jobId = requireText(jobId, "任务 ID");
        leaseOwner = requireText(leaseOwner, "租约 owner");
        leaseToken = requireText(leaseToken, "租约 token");
        Objects.requireNonNull(leaseUntil, "租约截止时间不能为空");
        if (fenceVersion < 1) {
            throw new IllegalArgumentException("fence version 必须大于 0");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
