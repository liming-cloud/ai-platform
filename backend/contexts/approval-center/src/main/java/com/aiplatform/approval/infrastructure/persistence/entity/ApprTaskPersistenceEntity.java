package com.aiplatform.approval.infrastructure.persistence.entity;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.approval.domain.model.ApprovalRequest;
import com.aiplatform.approval.domain.model.ApprovalStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * `appr_task` 表持久化实体，审批中心负责维护审批任务生命周期。
 */
@TableName("appr_task")
public class ApprTaskPersistenceEntity {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("approval_request_id")
    private String approvalRequestId;

    @TableField("approval_type")
    private String approvalType;

    @TableField("source_context")
    private String sourceContext;

    @TableField("source_resource_type")
    private String sourceResourceType;

    @TableField("source_resource_id")
    private Long sourceResourceId;

    @TableField("title")
    private String title;

    @TableField("risk_level")
    private String riskLevel;

    @TableField("requester_id")
    private Long requesterId;

    @TableField("approver_id")
    private Long approverId;

    @TableField("status")
    private String status;

    @TableField("due_at")
    private Instant dueAt;

    @TableField("decision_at")
    private Instant decisionAt;

    @TableField("trace_id")
    private String traceId;

    @TableField("evidence_schema")
    private String evidenceSchema;

    @TableField("evidence_digest")
    private String evidenceDigest;

    @TableField("target_digest")
    private String targetDigest;

    @TableField("evidence_json")
    private String evidenceJson;

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

    public static ApprTaskPersistenceEntity fromRequest(ApprovalRequest request, Clock clock) {
        Objects.requireNonNull(request, "审批请求不能为空");
        Instant now = Objects.requireNonNull(clock, "系统时钟不能为空").instant();
        Long requesterId = parseRequiredLong(request.requesterId(), "申请人 ID");
        ApprTaskPersistenceEntity entity = new ApprTaskPersistenceEntity();
        entity.setTenantId(parseRequiredLong(request.tenantId().value(), "租户 ID"));
        entity.setApprovalRequestId(request.approvalRequestId());
        entity.setApprovalType(request.approvalType());
        entity.setSourceContext(request.sourceContext());
        entity.setSourceResourceType(request.sourceResourceType());
        entity.setSourceResourceId(parseRequiredLong(request.sourceResourceId(), "来源资源 ID"));
        entity.setTitle(request.title());
        entity.setRiskLevel(request.riskLevel().name());
        entity.setRequesterId(requesterId);
        entity.setStatus(ApprovalStatus.PENDING.name());
        entity.setTraceId(request.traceId());
        entity.setEvidenceSchema(request.evidence().schema());
        entity.setEvidenceDigest(request.evidence().digest());
        entity.setTargetDigest(request.targetDigest());
        entity.setEvidenceJson(writeEvidence(request));
        entity.setCreatedAt(now);
        entity.setCreatedBy(requesterId);
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(requesterId);
        entity.setVersion(0);
        return entity;
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

    public String getApprovalRequestId() {
        return approvalRequestId;
    }

    public void setApprovalRequestId(String approvalRequestId) {
        this.approvalRequestId = approvalRequestId;
    }

    public String getApprovalType() {
        return approvalType;
    }

    public void setApprovalType(String approvalType) {
        this.approvalType = approvalType;
    }

    public String getSourceContext() {
        return sourceContext;
    }

    public void setSourceContext(String sourceContext) {
        this.sourceContext = sourceContext;
    }

    public String getSourceResourceType() {
        return sourceResourceType;
    }

    public void setSourceResourceType(String sourceResourceType) {
        this.sourceResourceType = sourceResourceType;
    }

    public Long getSourceResourceId() {
        return sourceResourceId;
    }

    public void setSourceResourceId(Long sourceResourceId) {
        this.sourceResourceId = sourceResourceId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Long getRequesterId() {
        return requesterId;
    }

    public void setRequesterId(Long requesterId) {
        this.requesterId = requesterId;
    }

    public Long getApproverId() {
        return approverId;
    }

    public void setApproverId(Long approverId) {
        this.approverId = approverId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public Instant getDecisionAt() {
        return decisionAt;
    }

    public void setDecisionAt(Instant decisionAt) {
        this.decisionAt = decisionAt;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getEvidenceSchema() {
        return evidenceSchema;
    }

    public void setEvidenceSchema(String evidenceSchema) {
        this.evidenceSchema = evidenceSchema;
    }

    public String getEvidenceDigest() {
        return evidenceDigest;
    }

    public void setEvidenceDigest(String evidenceDigest) {
        this.evidenceDigest = evidenceDigest;
    }

    public String getTargetDigest() {
        return targetDigest;
    }

    public void setTargetDigest(String targetDigest) {
        this.targetDigest = targetDigest;
    }

    public String getEvidenceJson() {
        return evidenceJson;
    }

    public void setEvidenceJson(String evidenceJson) {
        this.evidenceJson = evidenceJson;
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

    private static String writeEvidence(ApprovalRequest request) {
        try {
            return JSON.writeValueAsString(request.evidence().facts());
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("审批证据 JSON 序列化失败", exception);
        }
    }
}
