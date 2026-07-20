package com.aiplatform.knowledge.infrastructure.persistence.entity;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.knowledge.domain.DocumentIngestJob;
import com.aiplatform.knowledge.domain.ClaimedDocumentIngestJob;
import com.aiplatform.knowledge.domain.DocumentIndexJobTarget;
import com.aiplatform.knowledge.domain.DocumentIngestJobOwnership;
import com.aiplatform.knowledge.domain.DocumentIngestStatus;
import com.aiplatform.knowledge.domain.DocumentIngestJobSnapshot;
import com.aiplatform.shared.kernel.TenantId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.util.Objects;

/**
 * `kb_embedding_job` 表持久化实体，记录文档解析和向量化任务。
 */
@TableName("kb_embedding_job")
public class KbEmbeddingJobPersistenceEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;

    @TableField("document_id")
    private Long documentId;

    @TableField("job_type")
    private String jobType;

    @TableField("status")
    private String status;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("next_retry_at")
    private Instant nextRetryAt;

    @TableField("failure_code")
    private String failureCode;

    @TableField("failure_reason")
    private String failureReason;

    @TableField("cancel_reason")
    private String cancelReason;

    @TableField("cancelled_by")
    private Long cancelledBy;

    @TableField("cancel_trace_id")
    private String cancelTraceId;

    @TableField("cancelled_at")
    private Instant cancelledAt;

    @TableField("lease_owner")
    private String leaseOwner;

    @TableField("lease_token")
    private String leaseToken;

    @TableField("lease_until")
    private Instant leaseUntil;

    @TableField("rebuild_id")
    private Long rebuildId;

    @TableField("target_model_config_id")
    private Long targetModelConfigId;

    @TableField("target_embedding_model")
    private String targetEmbeddingModel;

    @TableField("index_version")
    private String indexVersion;

    @TableField("operator_type")
    private String operatorType;

    @TableField("trace_id")
    private String traceId;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("created_by")
    private Long createdBy;

    @TableField("updated_at")
    private Instant updatedAt;

    @TableField("updated_by")
    private Long updatedBy;

    @TableField("deleted_at")
    private Instant deletedAt;

    @TableField("version")
    private Integer version;

    public static KbEmbeddingJobPersistenceEntity fromDomain(DocumentIngestJob job) {
        return fromDomain(job, null);
    }

    public static KbEmbeddingJobPersistenceEntity fromDomain(
            DocumentIngestJob job,
            DocumentIndexJobTarget target
    ) {
        Objects.requireNonNull(job, "文档入库任务不能为空");
        KbEmbeddingJobPersistenceEntity entity = new KbEmbeddingJobPersistenceEntity();
        entity.setId(parseRequiredLong(job.jobId(), "任务 ID"));
        entity.setTenantId(parseRequiredLong(job.tenantId().value(), "租户 ID"));
        entity.setKnowledgeBaseId(parseRequiredLong(job.knowledgeBaseId(), "知识库 ID"));
        entity.setDocumentId(parseRequiredLong(job.documentId(), "文档 ID"));
        entity.setJobType(job.jobType());
        entity.setStatus(job.status().name());
        entity.setRetryCount(0);
        entity.setNextRetryAt(job.status() == DocumentIngestStatus.PENDING
                || job.status() == DocumentIngestStatus.RETRY_WAIT ? job.submittedAt() : null);
        entity.setFailureReason(job.status() == DocumentIngestStatus.FAILED
                || job.status() == DocumentIngestStatus.RETRY_WAIT ? job.failureReason() : null);
        entity.setTraceId(job.traceId());
        entity.setCreatedAt(job.submittedAt());
        entity.setCreatedBy(parseRequiredLong(job.operatorId(), "操作人 ID"));
        entity.setUpdatedAt(job.updatedAt());
        entity.setUpdatedBy(parseRequiredLong(job.operatorId(), "操作人 ID"));
        entity.setVersion(0);
        if (target != null) {
            entity.setRebuildId(parseRequiredLong(target.rebuildId(), "重建计划 ID"));
            entity.setTargetModelConfigId(parseRequiredLong(target.modelConfigId(), "模型配置 ID"));
            entity.setTargetEmbeddingModel(target.embeddingModel());
            entity.setIndexVersion(target.indexVersion());
            entity.setOperatorType(target.operatorType());
        }
        return entity;
    }

    public DocumentIngestJob toDomain() {
        return new DocumentIngestJob(
                TenantId.of(requiredLongText(tenantId, "租户 ID")),
                requiredLongText(id, "任务 ID"),
                requiredLongText(documentId, "文档 ID"),
                requiredLongText(knowledgeBaseId, "知识库 ID"),
                requiredText(jobType, "任务类型"),
                requiredText(status, "任务状态"),
                requiredText(traceId, "链路追踪 ID"),
                requiredInstant(createdAt, "任务创建时间"),
                requiredInstant(updatedAt, "任务更新时间"),
                requiredLongText(createdBy, "操作人 ID"),
                failureReason
        );
    }

    public DocumentIngestJobSnapshot toSnapshot() {
        return new DocumentIngestJobSnapshot(
                TenantId.of(requiredLongText(tenantId, "租户 ID")),
                requiredLongText(id, "任务 ID"),
                requiredLongText(documentId, "文档 ID"),
                requiredLongText(knowledgeBaseId, "知识库 ID"),
                requiredText(jobType, "任务类型"),
                DocumentIngestStatus.from(requiredText(status, "任务状态")),
                failureCode,
                failureReason,
                requiredText(traceId, "链路追踪 ID"),
                requiredInstant(createdAt, "任务创建时间"),
                requiredInstant(updatedAt, "任务更新时间"),
                cancelReason,
                cancelledBy == null ? null : String.valueOf(cancelledBy),
                cancelTraceId,
                cancelledAt,
                rebuildId == null ? null : String.valueOf(rebuildId)
        );
    }

    public ClaimedDocumentIngestJob toClaimedJob() {
        DocumentIngestJob job = toDomain();
        DocumentIndexJobTarget target = null;
        if (DocumentIngestJob.JOB_TYPE_DOCUMENT_INDEX.equals(job.jobType())) {
            target = new DocumentIndexJobTarget(
                    requiredLongText(rebuildId, "重建计划 ID"),
                    requiredLongText(targetModelConfigId, "模型配置 ID"),
                    requiredText(targetEmbeddingModel, "目标 Embedding 模型"),
                    requiredText(indexVersion, "索引版本"),
                    requiredText(operatorType, "操作人类型")
            );
        }
        return new ClaimedDocumentIngestJob(
                job,
                requiredNonNegative(retryCount, "任务尝试次数"),
                new DocumentIngestJobOwnership(
                        job.tenantId(),
                        job.jobId(),
                        requiredText(leaseOwner, "租约 owner"),
                        requiredText(leaseToken, "租约 token"),
                        requiredInstant(leaseUntil, "租约截止时间"),
                        requiredNonNegative(version, "fence version")
                ),
                target
        );
    }

    private static String requiredLongText(Long value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException("文档入库任务持久化记录缺少" + fieldName);
        }
        return String.valueOf(value);
    }

    private static String requiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("文档入库任务持久化记录缺少" + fieldName);
        }
        return value.trim();
    }

    private static Instant requiredInstant(Instant value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException("文档入库任务持久化记录缺少" + fieldName);
        }
        return value;
    }

    private static int requiredNonNegative(Integer value, String fieldName) {
        if (value == null || value < 0) {
            throw new IllegalStateException("文档入库任务持久化记录缺少" + fieldName);
        }
        return value;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getJobType() {
        return jobType;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(Instant nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public Long getCancelledBy() {
        return cancelledBy;
    }

    public void setCancelledBy(Long cancelledBy) {
        this.cancelledBy = cancelledBy;
    }

    public String getCancelTraceId() {
        return cancelTraceId;
    }

    public void setCancelTraceId(String cancelTraceId) {
        this.cancelTraceId = cancelTraceId;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String getLeaseOwner() {
        return leaseOwner;
    }

    public void setLeaseOwner(String leaseOwner) {
        this.leaseOwner = leaseOwner;
    }

    public String getLeaseToken() {
        return leaseToken;
    }

    public void setLeaseToken(String leaseToken) {
        this.leaseToken = leaseToken;
    }

    public Instant getLeaseUntil() {
        return leaseUntil;
    }

    public void setLeaseUntil(Instant leaseUntil) {
        this.leaseUntil = leaseUntil;
    }

    public Long getRebuildId() {
        return rebuildId;
    }

    public void setRebuildId(Long rebuildId) {
        this.rebuildId = rebuildId;
    }

    public Long getTargetModelConfigId() {
        return targetModelConfigId;
    }

    public void setTargetModelConfigId(Long targetModelConfigId) {
        this.targetModelConfigId = targetModelConfigId;
    }

    public String getTargetEmbeddingModel() {
        return targetEmbeddingModel;
    }

    public void setTargetEmbeddingModel(String targetEmbeddingModel) {
        this.targetEmbeddingModel = targetEmbeddingModel;
    }

    public String getIndexVersion() {
        return indexVersion;
    }

    public void setIndexVersion(String indexVersion) {
        this.indexVersion = indexVersion;
    }

    public String getOperatorType() {
        return operatorType;
    }

    public void setOperatorType(String operatorType) {
        this.operatorType = operatorType;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
