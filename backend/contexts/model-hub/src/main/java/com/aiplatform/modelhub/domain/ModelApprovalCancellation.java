package com.aiplatform.modelhub.domain;

import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.kernel.ValueObject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * 模型审批撤销事实。它只描述 model-hub 已请求的撤销，不冒充 approval-center 已完成状态迁移。
 */
public record ModelApprovalCancellation(
        TenantId tenantId,
        String approvalRequestId,
        String sourceResourceType,
        String sourceResourceId,
        String operatorId,
        String reason,
        String traceId,
        Instant requestedAt
) implements ValueObject {
    public ModelApprovalCancellation {
        tenantId = Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        approvalRequestId = requireText(approvalRequestId, "审批请求 ID");
        sourceResourceType = requireText(sourceResourceType, "审批资源类型");
        sourceResourceId = requireText(sourceResourceId, "审批资源 ID");
        operatorId = requireText(operatorId, "撤销操作人 ID");
        reason = requireText(reason, "审批撤销理由");
        if (reason.length() > 500) {
            throw new IllegalArgumentException("审批撤销理由不能超过 500 个字符");
        }
        if (reason.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("审批撤销理由不能包含控制字符");
        }
        traceId = requireText(traceId, "调用链 ID");
        requestedAt = Objects.requireNonNull(requestedAt, "审批撤销请求时间不能为空")
                .truncatedTo(ChronoUnit.MICROS);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
