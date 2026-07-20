package com.aiplatform.knowledge.domain.port;

import com.aiplatform.knowledge.domain.DocumentIngestJob;
import com.aiplatform.knowledge.domain.DocumentIngestJobSnapshot;
import com.aiplatform.knowledge.domain.ClaimedDocumentIngestJob;
import com.aiplatform.knowledge.domain.DocumentIndexJobTarget;
import com.aiplatform.knowledge.domain.DocumentIngestJobOwnership;
import com.aiplatform.knowledge.domain.DocumentIngestJobCancellation;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 文档入库任务仓储端口，应用层必须按租户边界读取任务事实状态。
 */
public interface DocumentIngestJobRepository {
    String nextJobId();

    DocumentIngestJob save(DocumentIngestJob job);

    DocumentIngestJob saveIndexJob(DocumentIngestJob job, DocumentIndexJobTarget target);

    Optional<DocumentIngestJob> findById(TenantId tenantId, String jobId);

    Optional<DocumentIngestJob> findLatestByDocumentId(
            TenantId tenantId,
            String knowledgeBaseId,
            String documentId
    );

    List<DocumentIngestJobSnapshot> findLatestSnapshots(
            TenantId tenantId,
            String knowledgeBaseId,
            List<String> documentIds
    );

    Optional<DocumentIngestJob> findByRebuildDocument(TenantId tenantId, String rebuildId, String documentId);

    Optional<ClaimedDocumentIngestJob> claimNext(ClaimRequest request);

    Optional<DocumentIngestJobCancellation> cancelLatest(
            TenantId tenantId,
            String knowledgeBaseId,
            String documentId,
            String reason,
            String operatorId,
            String traceId,
            Instant now
    );

    DocumentIngestJob retryFailedManually(TenantId tenantId, String jobId, Instant now, String operatorId);

    void requireOwned(DocumentIngestJobOwnership ownership, Instant now);

    /**
     * 在不改变 fence version 的前提下续租当前 ownership。
     *
     * <p>续租必须同时匹配 tenant、job、owner、token 与 fence，且旧租约仍有效；否则失败关闭。</p>
     */
    void renewOwned(DocumentIngestJobOwnership ownership, Instant leaseUntil, Instant now);

    void completeOwned(DocumentIngestJobOwnership ownership, Instant now);

    void retryOwned(
            DocumentIngestJobOwnership ownership,
            String failureCode,
            String failureSummary,
            Instant nextRetryAt,
            Instant now
    );

    void failOwned(
            DocumentIngestJobOwnership ownership,
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
            if (workerId == null || workerId.isBlank()) {
                throw new IllegalArgumentException("worker ID 不能为空");
            }
            if (leaseToken == null || leaseToken.isBlank()) {
                throw new IllegalArgumentException("租约 token 不能为空");
            }
            if (now == null || leaseUntil == null || !leaseUntil.isAfter(now)) {
                throw new IllegalArgumentException("租约截止时间必须晚于 claim 时间");
            }
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("最大尝试次数必须大于 0");
            }
            workerId = workerId.trim();
            leaseToken = leaseToken.trim();
        }
    }
}
