package com.aiplatform.identity.domain.delegation;

import java.util.List;
import java.util.Objects;

/**
 * 租户代管动作值对象，集中维护动作参数、目标资源和幂等指纹不变量。
 */
public record DelegatedTenantActionRequest(
        DelegatedTenantActionType actionType,
        String targetRoleId,
        String targetUserId,
        List<String> permissionCodes,
        String reason,
        String idempotencyKey
) {
    private static final int MAX_REASON_LENGTH = 512;
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;

    public DelegatedTenantActionRequest {
        Objects.requireNonNull(actionType, "代管动作类型不能为空");
        targetRoleId = normalizeOptional(targetRoleId);
        targetUserId = normalizeOptional(targetUserId);
        permissionCodes = normalizePermissions(permissionCodes);
        reason = requireText(reason, "代管原因", MAX_REASON_LENGTH);
        idempotencyKey = requireText(idempotencyKey, "幂等键", MAX_IDEMPOTENCY_KEY_LENGTH);
        validateActionArguments(actionType, targetRoleId, targetUserId, permissionCodes);
    }

    public String targetResourceType() {
        return switch (actionType) {
            case ASSIGN_TENANT_ADMIN -> "IAM_USER";
            case REPLACE_ROLE_PERMISSIONS -> "IAM_ROLE";
        };
    }

    public String targetResourceId() {
        return switch (actionType) {
            case ASSIGN_TENANT_ADMIN -> targetUserId;
            case REPLACE_ROLE_PERMISSIONS -> targetRoleId;
        };
    }

    public String canonicalPayload() {
        String normalizedPermissions = permissionCodes.stream()
                .map(DelegatedTenantActionRequest::lengthPrefixed)
                .reduce((left, right) -> left + right)
                .orElse("");
        return lengthPrefixed(actionType.name())
                + lengthPrefixed(targetRoleId)
                + lengthPrefixed(targetUserId)
                + lengthPrefixed(normalizedPermissions)
                + lengthPrefixed(reason)
                + lengthPrefixed(idempotencyKey);
    }

    private static void validateActionArguments(
            DelegatedTenantActionType actionType,
            String targetRoleId,
            String targetUserId,
            List<String> permissionCodes
    ) {
        switch (actionType) {
            case ASSIGN_TENANT_ADMIN -> {
                requirePresent(targetRoleId, "租户管理员角色 ID");
                requirePresent(targetUserId, "目标用户 ID");
                if (!permissionCodes.isEmpty()) {
                    throw new IllegalArgumentException("分配租户管理员动作不能同时提交权限编码");
                }
            }
            case REPLACE_ROLE_PERMISSIONS -> {
                requirePresent(targetRoleId, "目标角色 ID");
                if (targetUserId != null) {
                    throw new IllegalArgumentException("替换角色权限动作不能提交目标用户 ID");
                }
            }
        }
    }

    private static List<String> normalizePermissions(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(value -> requireText(value, "权限编码", 160))
                .distinct()
                .sorted()
                .toList();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String lengthPrefixed(String value) {
        String normalized = value == null ? "" : value;
        return normalized.length() + ":" + normalized;
    }

    private static void requirePresent(String value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
    }

    private static String requireText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过" + maxLength);
        }
        return normalized;
    }
}
