package com.aiplatform.approval.infrastructure.persistence.converter;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.approval.domain.model.ApprovalRiskLevel;
import com.aiplatform.approval.domain.model.ApprovalStatus;
import com.aiplatform.approval.domain.model.ApprovalTask;
import com.aiplatform.approval.infrastructure.persistence.entity.ApprTaskPersistenceEntity;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.kernel.ApprovalEvidenceEnvelope;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 审批任务领域对象与持久化实体转换器，避免 DTO 或数据库实体泄漏到应用层。
 */
@Component
public class ApprovalTaskPersistenceConverter {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    public ApprovalTask toDomain(ApprTaskPersistenceEntity entity) {
        Objects.requireNonNull(entity, "审批任务实体不能为空");
        return new ApprovalTask(
                TenantId.of(requiredLongText(entity.getTenantId(), "租户 ID")),
                requiredLongText(entity.getId(), "审批任务 ID"),
                requiredText(entity.getApprovalRequestId(), "审批请求 ID"),
                requiredText(entity.getApprovalType(), "审批类型"),
                requiredText(entity.getSourceContext(), "来源上下文"),
                requiredText(entity.getSourceResourceType(), "来源资源类型"),
                requiredLongText(entity.getSourceResourceId(), "来源资源 ID"),
                requiredText(entity.getTitle(), "审批标题"),
                ApprovalRiskLevel.valueOf(requiredText(entity.getRiskLevel(), "风险等级")),
                requiredLongText(entity.getRequesterId(), "申请人 ID"),
                entity.getApproverId() == null ? null : String.valueOf(entity.getApproverId()),
                ApprovalStatus.valueOf(requiredText(entity.getStatus(), "审批状态")),
                entity.getDueAt(),
                entity.getDecisionAt(),
                requiredText(entity.getTraceId(), "调用链 ID"),
                readEvidence(entity),
                optionalText(entity.getTargetDigest()),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                requiredInteger(entity.getVersion(), "乐观锁版本")
        );
    }

    public ApprTaskPersistenceEntity toEntity(ApprovalTask task) {
        Objects.requireNonNull(task, "审批任务不能为空");
        ApprTaskPersistenceEntity entity = new ApprTaskPersistenceEntity();
        entity.setId(parseRequiredLong(task.taskId(), "审批任务 ID"));
        entity.setTenantId(parseRequiredLong(task.tenantId().value(), "租户 ID"));
        entity.setApprovalRequestId(task.approvalRequestId());
        entity.setApprovalType(task.approvalType());
        entity.setSourceContext(task.sourceContext());
        entity.setSourceResourceType(task.sourceResourceType());
        entity.setSourceResourceId(parseRequiredLong(task.sourceResourceId(), "来源资源 ID"));
        entity.setTitle(task.title());
        entity.setRiskLevel(task.riskLevel().name());
        entity.setRequesterId(parseRequiredLong(task.requesterId(), "申请人 ID"));
        entity.setApproverId(task.approverId() == null ? null : parseRequiredLong(task.approverId(), "审批人 ID"));
        entity.setStatus(task.status().name());
        entity.setDueAt(task.dueAt());
        entity.setDecisionAt(task.decisionAt());
        entity.setTraceId(task.traceId());
        entity.setEvidenceSchema(task.evidence() == null ? null : task.evidence().schema());
        entity.setEvidenceDigest(task.evidence() == null ? null : task.evidence().digest());
        entity.setTargetDigest(task.targetDigest());
        entity.setEvidenceJson(task.evidence() == null ? null : writeEvidence(task.evidence()));
        entity.setCreatedAt(task.createdAt());
        entity.setCreatedBy(parseRequiredLong(task.requesterId(), "申请人 ID"));
        entity.setUpdatedAt(task.updatedAt());
        entity.setUpdatedBy(task.approverId() == null ? parseRequiredLong(task.requesterId(), "申请人 ID")
                : parseRequiredLong(task.approverId(), "审批人 ID"));
        entity.setVersion(task.version());
        return entity;
    }

    private static String requiredLongText(Long value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return String.valueOf(value);
    }

    private static String requiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static Integer requiredInteger(Integer value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }

    private static ApprovalEvidenceEnvelope readEvidence(ApprTaskPersistenceEntity entity) {
        if (entity.getEvidenceSchema() == null
                && entity.getEvidenceDigest() == null
                && entity.getTargetDigest() == null
                && entity.getEvidenceJson() == null) {
            return null;
        }
        if (entity.getEvidenceSchema() == null
                || entity.getEvidenceDigest() == null
                || entity.getTargetDigest() == null
                || entity.getEvidenceJson() == null) {
            throw new IllegalArgumentException("审批证据持久化字段必须同时存在");
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> facts = JSON.readValue(
                    requiredText(entity.getEvidenceJson(), "审批证据 JSON"),
                    Map.class
            );
            return new ApprovalEvidenceEnvelope(
                    requiredText(entity.getEvidenceSchema(), "审批证据 schema"),
                    requiredText(entity.getEvidenceDigest(), "审批证据摘要"),
                    facts
            );
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("审批证据 JSON 解析失败", exception);
        }
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String writeEvidence(ApprovalEvidenceEnvelope evidence) {
        try {
            return JSON.writeValueAsString(evidence.facts());
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("审批证据 JSON 序列化失败", exception);
        }
    }
}
