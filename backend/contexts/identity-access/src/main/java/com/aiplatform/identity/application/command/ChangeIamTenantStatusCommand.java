package com.aiplatform.identity.application.command;

import com.aiplatform.shared.kernel.TenantId;
import java.util.Objects;

public record ChangeIamTenantStatusCommand(TenantId tenantId, String actorId, String traceId, String status, String reason) {
    public ChangeIamTenantStatusCommand {
        tenantId = Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        actorId = require(actorId, "操作者 ID");
        traceId = require(traceId, "调用链 ID");
        status = require(status, "租户状态").toUpperCase();
        reason = IamChangeReason.normalize(reason, "状态变更原因");
        if (!status.equals("ACTIVE") && !status.equals("DISABLED")) {
            throw new IllegalArgumentException("租户状态只支持 ACTIVE 或 DISABLED");
        }
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + "不能为空");
        return value.trim();
    }
}
