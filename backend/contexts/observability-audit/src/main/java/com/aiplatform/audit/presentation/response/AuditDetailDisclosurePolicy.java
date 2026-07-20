package com.aiplatform.audit.presentation.response;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 安全审计详情的外部披露边界。数据库中的内部诊断字段不会自动成为浏览器合同。
 */
@Component
public final class AuditDetailDisclosurePolicy {
    private static final TypeReference<Map<String, Object>> DETAIL_TYPE = new TypeReference<>() { };
    private static final Set<String> DISCLOSED_FIELDS = Set.of(
            "eventId", "action", "actorId", "resourceType", "resourceId", "approvalReferenceId",
            "reason", "invitationId", "deliveryStatus", "status", "featureCode", "enabled",
            "permissionCodes", "targetTenantId", "targetUserId", "requestId", "roleId", "menuId",
            "providerId", "modelConfigId", "applicationId", "sessionId", "runId", "knowledgeBaseId",
            "documentId", "decision", "resultStatus", "failureCode", "retryCount", "attemptCount",
            "sourceContext", "sourceResourceType", "sourceResourceId"
    );
    private static final List<String> SENSITIVE_KEY_FRAGMENTS = List.of(
            "secret", "password", "passwd", "credential", "token", "authorization", "cookie",
            "apikey", "api_key", "prompt", "content", "answer", "payload", "cipher", "privatekey"
    );
    private static final int MAX_TEXT_LENGTH = 512;
    private static final int MAX_LIST_ITEMS = 50;

    private final JsonMapper objectMapper;

    public AuditDetailDisclosurePolicy(JsonMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 序列化器不能为空");
    }

    public String disclose(String detailJson) {
        if (detailJson == null || detailJson.isBlank()) {
            return "{}";
        }
        try {
            Map<String, Object> source = objectMapper.readValue(detailJson, DETAIL_TYPE);
            Map<String, Object> disclosed = new LinkedHashMap<>();
            source.forEach((key, value) -> {
                Object safeValue = safeValue(value);
                if (isDisclosedKey(key) && safeValue != null) {
                    disclosed.put(key, safeValue);
                }
            });
            return objectMapper.writeValueAsString(disclosed);
        } catch (RuntimeException ignored) {
            return "{}";
        }
    }

    private static boolean isDisclosedKey(String key) {
        if (key == null || !DISCLOSED_FIELDS.contains(key)) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        return SENSITIVE_KEY_FRAGMENTS.stream().noneMatch(normalized::contains);
    }

    private static Object safeValue(Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof String text) {
            return text.length() <= MAX_TEXT_LENGTH ? text : text.substring(0, MAX_TEXT_LENGTH);
        }
        if (value instanceof List<?> list && list.size() <= MAX_LIST_ITEMS
                && list.stream().allMatch(AuditDetailDisclosurePolicy::isScalar)) {
            return List.copyOf(list);
        }
        return null;
    }

    private static boolean isScalar(Object value) {
        return value == null || value instanceof String || value instanceof Number || value instanceof Boolean;
    }
}
