package com.aiplatform.identity.application.command;

import com.aiplatform.shared.kernel.TenantId;
import java.util.Objects;

public record UpdateIamTenantCommand(TenantId tenantId, String actorId, String traceId, String name, String planCode) {
    public UpdateIamTenantCommand {
        tenantId = Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        actorId = require(actorId, "操作者 ID");
        traceId = require(traceId, "调用链 ID");
        name = require(name, "租户名称");
        planCode = require(planCode, "套餐编码").toUpperCase();
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + "不能为空");
        return value.trim();
    }
}
