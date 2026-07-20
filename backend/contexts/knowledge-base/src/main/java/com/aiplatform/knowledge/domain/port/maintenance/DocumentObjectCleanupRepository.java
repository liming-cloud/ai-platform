package com.aiplatform.knowledge.domain.port.maintenance;

import com.aiplatform.knowledge.domain.StoredDocumentObject;
import com.aiplatform.knowledge.domain.maintenance.ClaimedDocumentObjectCleanupTask;
import com.aiplatform.knowledge.domain.maintenance.DocumentObjectCleanupOwnership;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.Optional;

/** 对象孤儿登记、claim、引用校验与 fenced 终态写入。 */
public interface DocumentObjectCleanupRepository {
    void observe(
            TenantId tenantId,
            String knowledgeBaseId,
            String documentId,
            StoredDocumentObject storedObject,
            String failureType,
            Instant observedAt,
            Instant firstAttemptAt
    );

    Optional<ClaimedDocumentObjectCleanupTask> claimNext(ClaimRequest request);

    boolean hasLiveDocumentReference(ClaimedDocumentObjectCleanupTask claimed, Instant now);

    void completeOwned(DocumentObjectCleanupOwnership ownership, Instant now);

    void retainOwned(DocumentObjectCleanupOwnership ownership, Instant now);

    void retryOwned(
            DocumentObjectCleanupOwnership ownership,
            String failureCode,
            String failureSummary,
            Instant nextRetryAt,
            Instant now
    );

    void failOwned(
            DocumentObjectCleanupOwnership ownership,
            String failureCode,
            String failureSummary,
            Instant now
    );

    record ClaimRequest(
            String workerId,
            String leaseToken,
            Instant now,
            Instant leaseUntil,
            int maxAttempts
    ) {
        public ClaimRequest {
            workerId = requireText(workerId, "清理 worker ID");
            leaseToken = requireText(leaseToken, "清理 lease token");
            if (now == null || leaseUntil == null || !leaseUntil.isAfter(now)) {
                throw new IllegalArgumentException("清理租约截止时间必须晚于当前时间");
            }
            if (maxAttempts < 1 || maxAttempts > 5) {
                throw new IllegalArgumentException("清理最大尝试次数必须在 1 到 5 之间");
            }
        }

        private static String requireText(String value, String fieldName) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(fieldName + "不能为空");
            }
            return value.trim();
        }
    }
}
