package com.aiplatform.identity.domain.delegation;

import java.util.Locale;

/**
 * 平台管理员可执行的租户代管动作。动作集合保持封闭，避免把最高权限入口变成任意命令通道。
 */
public enum DelegatedTenantActionType {
    ASSIGN_TENANT_ADMIN,
    REPLACE_ROLE_PERMISSIONS;

    public static DelegatedTenantActionType from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("代管动作类型不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支持的代管动作类型：" + value.trim(), exception);
        }
    }
}
