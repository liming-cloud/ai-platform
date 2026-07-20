package com.aiplatform.appstudio.domain.release;

import com.aiplatform.appstudio.domain.AiApplication;
import com.aiplatform.appstudio.domain.ApplicationPublicationStatus;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 已激活或历史发布版本的不可变快照。配置和有效用户受众均在发布时冻结。
 */
public record AiApplicationRelease(
        String releaseId,
        TenantId tenantId,
        String applicationId,
        long version,
        String sourceReleaseId,
        String requestId,
        ApplicationReleaseOperation operation,
        AiApplication snapshot,
        Set<String> allowedUserIds,
        String publishedBy,
        String reason,
        String traceId,
        Instant publishedAt
) {
    public AiApplicationRelease {
        releaseId = requireText(releaseId, "发布版本 ID");
        tenantId = requireValue(tenantId, "租户 ID");
        applicationId = requireText(applicationId, "应用 ID");
        if (version < 1) {
            throw new IllegalArgumentException("发布版本号必须大于 0");
        }
        requestId = requireText(requestId, "发布请求 ID");
        operation = requireValue(operation, "发布操作");
        snapshot = requireValue(snapshot, "应用发布快照");
        if (!tenantId.equals(snapshot.tenantId()) || !applicationId.equals(snapshot.applicationId())) {
            throw new IllegalArgumentException("应用发布快照必须归属相同租户和应用");
        }
        if (snapshot.publicationStatus() != ApplicationPublicationStatus.PUBLISHED) {
            throw new IllegalArgumentException("应用发布快照必须处于 PUBLISHED 状态");
        }
        sourceReleaseId = normalizeOptionalText(sourceReleaseId);
        if (operation == ApplicationReleaseOperation.ROLLBACK && sourceReleaseId == null) {
            throw new IllegalArgumentException("回滚版本必须记录来源版本 ID");
        }
        if (operation == ApplicationReleaseOperation.PUBLISH && sourceReleaseId != null) {
            throw new IllegalArgumentException("普通发布版本不得记录回滚来源");
        }
        allowedUserIds = requireValue(allowedUserIds, "发布受众").stream()
                .map(value -> requireText(value, "发布受众用户 ID"))
                .collect(Collectors.toUnmodifiableSet());
        publishedBy = requireText(publishedBy, "发布人 ID");
        reason = requireText(reason, "发布原因");
        traceId = requireText(traceId, "Trace ID");
        publishedAt = requireValue(publishedAt, "发布时间");
    }

    public boolean allows(String userId) {
        return allowedUserIds.contains(requireText(userId, "用户 ID"));
    }

    private static String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static <T> T requireValue(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }
}
