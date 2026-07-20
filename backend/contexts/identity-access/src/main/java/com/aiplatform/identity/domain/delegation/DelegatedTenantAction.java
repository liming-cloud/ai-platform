package com.aiplatform.identity.domain.delegation;

import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.Objects;

/**
 * 已落库的租户代管动作事实。该记录只追加，不承载后续权限读取。
 */
public record DelegatedTenantAction(
        String actionId,
        TenantId tenantId,
        String operatorId,
        DelegatedTenantActionType actionType,
        String targetResourceType,
        String targetResourceId,
        String reason,
        String requestHash,
        String auditEventId,
        String status,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";

    public DelegatedTenantAction {
        actionId = normalizeOptional(actionId);
        Objects.requireNonNull(tenantId, "被代管租户 ID 不能为空");
        operatorId = requireText(operatorId, "代管操作者 ID");
        Objects.requireNonNull(actionType, "代管动作类型不能为空");
        targetResourceType = requireText(targetResourceType, "目标资源类型");
        targetResourceId = requireText(targetResourceId, "目标资源 ID");
        reason = requireText(reason, "代管原因");
        requestHash = requireText(requestHash, "请求哈希");
        auditEventId = requireText(auditEventId, "审计事件 ID");
        status = requireText(status, "代管动作状态");
        if (!STATUS_SUCCEEDED.equals(status)) {
            throw new IllegalArgumentException("不支持的代管动作状态：" + status);
        }
        Objects.requireNonNull(createdAt, "代管动作创建时间不能为空");
        Objects.requireNonNull(updatedAt, "代管动作更新时间不能为空");
        if (version < 0) {
            throw new IllegalArgumentException("代管动作版本不能为负数");
        }
    }

    public static DelegatedTenantAction succeeded(
            TenantId tenantId,
            String operatorId,
            DelegatedTenantActionRequest request,
            String requestHash,
            String auditEventId,
            Instant occurredAt
    ) {
        return new DelegatedTenantAction(
                null,
                tenantId,
                operatorId,
                request.actionType(),
                request.targetResourceType(),
                request.targetResourceId(),
                request.reason(),
                requestHash,
                auditEventId,
                STATUS_SUCCEEDED,
                occurredAt,
                occurredAt,
                0
        );
    }

    public DelegatedTenantAction withActionId(String persistedActionId) {
        return new DelegatedTenantAction(
                requireText(persistedActionId, "代管动作 ID"),
                tenantId,
                operatorId,
                actionType,
                targetResourceType,
                targetResourceId,
                reason,
                requestHash,
                auditEventId,
                status,
                createdAt,
                updatedAt,
                version
        );
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
