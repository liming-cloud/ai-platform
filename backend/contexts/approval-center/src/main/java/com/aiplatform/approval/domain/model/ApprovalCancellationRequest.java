package com.aiplatform.approval.domain.model;

import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.kernel.ValueObject;
import java.time.Instant;
import java.util.Objects;

/** 持久化审批撤销请求；任务尚未到达时保留为 PENDING，避免 MQ 乱序丢失撤销事实。 */
public record ApprovalCancellationRequest(
        TenantId tenantId,
        String eventId,
        String approvalRequestId,
        String sourceContext,
        String sourceResourceType,
        String sourceResourceId,
        String actorId,
        String reason,
        String traceId,
        Instant requestedAt
) implements ValueObject {
    public ApprovalCancellationRequest {
        tenantId = Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        eventId = requireText(eventId, "审批撤销事件 ID");
        approvalRequestId = requireText(approvalRequestId, "审批请求 ID");
        sourceContext = requireText(sourceContext, "审批来源上下文");
        sourceResourceType = requireText(sourceResourceType, "审批资源类型");
        sourceResourceId = requireText(sourceResourceId, "审批资源 ID");
        actorId = requireText(actorId, "审批撤销操作人 ID");
        reason = requireText(reason, "审批撤销理由");
        if (reason.length() > 500) {
            throw new IllegalArgumentException("审批撤销理由不能超过 500 个字符");
        }
        if (reason.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("审批撤销理由不能包含控制字符");
        }
        traceId = requireText(traceId, "调用链 ID");
        requestedAt = Objects.requireNonNull(requestedAt, "审批撤销请求时间不能为空");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
