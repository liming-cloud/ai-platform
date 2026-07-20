package com.aiplatform.appstudio.domain.publish;

import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record AppPublishPreflightResult(
        TenantId tenantId,
        String applicationId,
        String publishRequestId,
        String candidateFingerprint,
        boolean passed,
        List<PublishPreflightIssue> blockers,
        List<PublishPreflightIssue> warnings,
        String traceId,
        String checkedBy,
        Instant checkedAt
) {
    public AppPublishPreflightResult {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        applicationId = requireText(applicationId, "应用 ID");
        publishRequestId = requireText(publishRequestId, "发布请求 ID");
        candidateFingerprint = requireText(candidateFingerprint, "发布候选指纹");
        if (!candidateFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("发布候选指纹必须是小写 SHA-256");
        }
        blockers = List.copyOf(Objects.requireNonNull(blockers, "阻断项不能为空"));
        warnings = List.copyOf(Objects.requireNonNull(warnings, "警告项不能为空"));
        traceId = requireText(traceId, "链路 ID");
        checkedBy = requireText(checkedBy, "检查人");
        Objects.requireNonNull(checkedAt, "检查时间不能为空");
        if (passed != blockers.isEmpty()) {
            throw new IllegalArgumentException("发布预检结果与阻断项不一致");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
