package com.aiplatform.audit.infrastructure.persistence.publisher;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.audit.infrastructure.persistence.entity.AuditOperationLogPersistenceEntity;
import com.aiplatform.audit.infrastructure.persistence.entity.AuditSecurityLogPersistenceEntity;
import com.aiplatform.audit.infrastructure.persistence.entity.RagAuthorizationDecisionLogPersistenceEntity;
import com.aiplatform.audit.infrastructure.persistence.mapper.AuditOperationLogMapper;
import com.aiplatform.audit.infrastructure.persistence.mapper.AuditSecurityLogMapper;
import com.aiplatform.audit.infrastructure.persistence.mapper.RagAuthorizationDecisionLogMapper;
import com.aiplatform.shared.audit.model.AuditEvent;
import com.aiplatform.shared.audit.port.AuditEventPublisher;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据库审计发布器，按事件类型写入安全审计或操作审计表。
 */
@Component
public class DatabaseAuditEventPublisher implements AuditEventPublisher {
    private static final String RESULT_FAILED = "FAILED";
    private static final String RESULT_PENDING_APPROVAL = "PENDING_APPROVAL";
    private static final String RESULT_SUCCESS = "SUCCESS";

    private final AuditSecurityLogMapper securityLogs;
    private final AuditOperationLogMapper operationLogs;
    private final RagAuthorizationDecisionLogMapper ragDecisions;
    private final JsonMapper objectMapper;

    @Autowired
    public DatabaseAuditEventPublisher(
            AuditSecurityLogMapper securityLogs,
            AuditOperationLogMapper operationLogs,
            RagAuthorizationDecisionLogMapper ragDecisions,
            JsonMapper objectMapper
    ) {
        this.securityLogs = Objects.requireNonNull(securityLogs, "安全审计日志 Mapper 不能为空");
        this.operationLogs = Objects.requireNonNull(operationLogs, "操作审计日志 Mapper 不能为空");
        this.ragDecisions = Objects.requireNonNull(ragDecisions, "RAG 授权决策 Mapper 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 序列化器不能为空");
    }

    DatabaseAuditEventPublisher(
            AuditSecurityLogMapper securityLogs,
            AuditOperationLogMapper operationLogs,
            JsonMapper objectMapper
    ) {
        this(securityLogs, operationLogs, entity -> 1, objectMapper);
    }

    @Override
    @Transactional
    public void publish(AuditEvent event) {
        Objects.requireNonNull(event, "审计事件不能为空");
        String resultStatus = resultStatus(event);
        String detailJson = detailJson(event);
        if (isSecurityEvent(event)) {
            if (isRagSecurityEvent(event)) {
                requiredInsertedRows(ragDecisions.insertLog(toRagDecision(event, detailJson)), "RAG 授权决策日志");
            }
            AuditSecurityLogPersistenceEntity securityLog = isRagSecurityEvent(event)
                    ? AuditSecurityLogPersistenceEntity.fromEventWithoutSubjectId(event, resultStatus, detailJson)
                    : AuditSecurityLogPersistenceEntity.fromEvent(event, resultStatus, detailJson);
            requiredInsertedRows(securityLogs.insertLog(securityLog), "安全审计日志");
            return;
        }
        requiredInsertedRows(
                operationLogs.insertLog(AuditOperationLogPersistenceEntity.fromEvent(event, resultStatus, detailJson)),
                "操作审计日志"
        );
    }

    static boolean isRagSecurityEvent(AuditEvent event) {
        return normalize(event.action()).startsWith("SECURITY_RAG_");
    }

    private static RagAuthorizationDecisionLogPersistenceEntity toRagDecision(AuditEvent event, String detailJson) {
        RagAuthorizationDecisionLogPersistenceEntity entity = new RagAuthorizationDecisionLogPersistenceEntity();
        entity.setTenantId(parseRequiredLong(event.tenantId(), "租户 ID"));
        entity.setActorId(parseRequiredLong(event.actorId(), "操作人 ID"));
        entity.setResourceType(event.resourceType());
        entity.setResourceId(event.resourceId() == null || event.resourceId().isBlank() ? "UNKNOWN" : event.resourceId());
        entity.setDecision(ragDecision(event.action()));
        Object reason = event.detailAttributes().get("reason");
        entity.setReasonCode(reason == null || String.valueOf(reason).isBlank() ? "unspecified" : String.valueOf(reason));
        entity.setTraceId(event.traceId());
        entity.setDetailsJson(detailJson);
        entity.setCreatedAt(event.createdAt());
        return entity;
    }

    private static String ragDecision(String action) {
        String normalized = normalize(action);
        if (normalized.contains("FILTERED")) {
            return "FILTERED";
        }
        if (normalized.contains("DENIED")) {
            return "DENY";
        }
        return "ALLOW";
    }

    static boolean isSecurityEvent(AuditEvent event) {
        String action = normalize(event.action());
        String resourceType = normalize(event.resourceType());
        return action.startsWith("LOGIN_")
                || action.startsWith("LOGOUT_")
                || action.startsWith("AUTH_")
                || action.startsWith("PERMISSION_")
                || action.startsWith("SECURITY_")
                || resourceType.startsWith("IAM_");
    }

    static String resultStatus(AuditEvent event) {
        String action = normalize(event.action());
        if (action.contains("FAILED") || action.contains("DENIED") || action.contains("REJECTED")) {
            return RESULT_FAILED;
        }
        if (event.approvalReferenceId() != null && !event.approvalReferenceId().isBlank()) {
            return RESULT_PENDING_APPROVAL;
        }
        return RESULT_SUCCESS;
    }

    private String detailJson(AuditEvent event) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("eventId", event.eventId());
        details.put("action", event.action());
        details.put("actorId", event.actorId());
        details.put("resourceType", event.resourceType());
        details.put("resourceId", event.resourceId());
        details.put("approvalReferenceId", event.approvalReferenceId());
        details.putAll(event.detailAttributes());
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JacksonException exception) {
            throw new IllegalStateException("审计事件详情序列化失败", exception);
        }
    }

    private static String normalize(String value) {
        return Objects.requireNonNull(value, "审计分类字段不能为空").toUpperCase(Locale.ROOT);
    }

    private static void requiredInsertedRows(int inserted, String logName) {
        if (inserted != 1) {
            throw new IllegalStateException(logName + "写入失败，影响行数：" + inserted);
        }
    }
}
