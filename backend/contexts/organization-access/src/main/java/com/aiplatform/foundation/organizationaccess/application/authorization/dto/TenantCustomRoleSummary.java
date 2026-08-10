package com.aiplatform.foundation.organizationaccess.application.authorization.dto;

import java.util.List;
import java.util.Objects;

/** 自定义角色目录的最小分页读取投影，不包含成员身份或会话状态。 */
public record TenantCustomRoleSummary(
        String id,
        String name,
        List<String> permissionCodes,
        long version,
        String status,
        long boundMemberCount) {

    public TenantCustomRoleSummary {
        id = Objects.requireNonNull(id, "自定义角色标识不能为空");
        name = Objects.requireNonNull(name, "自定义角色名称不能为空");
        permissionCodes = List.copyOf(permissionCodes);
        status = Objects.requireNonNull(status, "自定义角色状态不能为空");
        if (version < 0L || boundMemberCount < 0L) {
            throw new IllegalArgumentException("自定义角色版本和绑定成员数不能为负数");
        }
    }
}
