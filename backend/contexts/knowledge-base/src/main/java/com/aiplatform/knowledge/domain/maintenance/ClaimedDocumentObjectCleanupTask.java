package com.aiplatform.knowledge.domain.maintenance;

import com.aiplatform.shared.kernel.TenantId;
import java.util.Objects;

/** 已认领的对象清理事实；bucket/key 必须始终与 tenant/knowledge-base/document 一起传递。 */
public record ClaimedDocumentObjectCleanupTask(
        TenantId tenantId,
        String cleanupId,
        String knowledgeBaseId,
        String documentId,
        String bucket,
        String objectKey,
        String sourceUri,
        String observationFailureType,
        int attemptCount,
        DocumentObjectCleanupOwnership ownership
) {
    public ClaimedDocumentObjectCleanupTask {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        cleanupId = requireText(cleanupId, "清理任务 ID");
        knowledgeBaseId = requireText(knowledgeBaseId, "知识库 ID");
        documentId = requireText(documentId, "文档 ID");
        bucket = requireText(bucket, "对象 bucket");
        objectKey = requireText(objectKey, "对象 key");
        sourceUri = requireText(sourceUri, "对象 URI");
        observationFailureType = requireText(observationFailureType, "观测失败类型");
        if (attemptCount < 1 || attemptCount > 6) {
            throw new IllegalArgumentException("清理尝试次数必须在 1 到 6 之间");
        }
        Objects.requireNonNull(ownership, "清理 ownership 不能为空");
        if (!tenantId.equals(ownership.tenantId()) || !cleanupId.equals(ownership.cleanupId())) {
            throw new IllegalArgumentException("清理任务与 ownership 身份不一致");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
