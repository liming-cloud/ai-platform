package com.aiplatform.identity.application.command;

public record CreateIamTenantCommand(String actorId, String traceId, String code, String name, String planCode) {
    public CreateIamTenantCommand {
        actorId = require(actorId, "操作者 ID");
        traceId = require(traceId, "调用链 ID");
        code = require(code, "租户编码").toLowerCase();
        name = require(name, "租户名称");
        planCode = require(planCode, "套餐编码").toUpperCase();
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + "不能为空");
        return value.trim();
    }
}
