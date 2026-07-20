package com.aiplatform.identity.domain.permissionrequest;

import com.aiplatform.shared.kernel.AggregateRoot;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.Objects;

public record PermissionRequest(
        TenantId tenantId,
        String requestId,
        String requesterId,
        String resourceType,
        String resourceId,
        String permissionCode,
        String reason,
        PermissionRequestStatus status,
        String reviewerId,
        String decisionComment,
        Instant createdAt,
        Instant updatedAt
) implements AggregateRoot<String> {
    public PermissionRequest {
        tenantId = Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        requestId = requireText(requestId, "申请 ID");
        requesterId = requireText(requesterId, "申请人 ID");
        resourceType = requireText(resourceType, "资源类型");
        resourceId = requireText(resourceId, "资源 ID");
        permissionCode = requireText(permissionCode, "权限编码");
        reason = requireText(reason, "申请原因");
        status = Objects.requireNonNull(status, "申请状态不能为空");
        reviewerId = optionalText(reviewerId);
        decisionComment = optionalText(decisionComment);
        createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
        updatedAt = Objects.requireNonNull(updatedAt, "更新时间不能为空");
        if (status == PermissionRequestStatus.PENDING && (reviewerId != null || decisionComment != null)) {
            throw new IllegalArgumentException("待处理申请不能包含决策信息");
        }
        if (status != PermissionRequestStatus.PENDING && (reviewerId == null || decisionComment == null)) {
            throw new IllegalArgumentException("已决策申请必须包含处理人和处理意见");
        }
    }

    public PermissionRequest decide(String action, String actorId, String comment, Instant decidedAt) {
        if (status != PermissionRequestStatus.PENDING) {
            throw new IllegalStateException("权限申请已处理");
        }
        String requiredActorId = requireText(actorId, "处理人 ID");
        if (requesterId.equals(requiredActorId)) {
            throw new IllegalStateException("申请人不能处理自己的权限申请");
        }
        PermissionRequestStatus next = switch (requireText(action, "决策动作").toUpperCase()) {
            case "APPROVE" -> PermissionRequestStatus.APPROVED;
            case "REJECT" -> PermissionRequestStatus.REJECTED;
            default -> throw new IllegalArgumentException("决策动作仅支持 APPROVE 或 REJECT");
        };
        return new PermissionRequest(tenantId, requestId, requesterId, resourceType, resourceId,
                permissionCode, reason, next, requiredActorId, requireText(comment, "处理意见"), createdAt, decidedAt);
    }

    @Override
    public String id() {
        return requestId;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + "不能为空");
        return value.trim();
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
