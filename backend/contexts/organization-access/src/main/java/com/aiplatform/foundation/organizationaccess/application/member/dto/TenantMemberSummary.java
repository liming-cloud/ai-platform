package com.aiplatform.foundation.organizationaccess.application.member.dto;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 租户成员目录的最小安全读取投影，不包含凭据、会话或完整权限集合。 */
public record TenantMemberSummary(
        String membershipId,
        String loginName,
        String membershipRole,
        String status,
        long permissionVersion,
        List<String> builtInRoles,
        Instant createdAt,
        long version,
        long assistantCount) {

    /** 校验目录投影的稳定展示字段。 */
    public TenantMemberSummary {
        membershipId = requireText(membershipId, "成员标识");
        loginName = requireText(loginName, "登录名");
        membershipRole = requireText(membershipRole, "成员角色");
        status = requireText(status, "成员状态");
        if (permissionVersion < 0L) {
            throw new IllegalArgumentException("成员权限修订不能为负数");
        }
        builtInRoles = List.copyOf(Objects.requireNonNull(builtInRoles, "内置角色集合不能为空"));
        if (builtInRoles.stream().anyMatch(role -> role == null || role.isBlank())) {
            throw new IllegalArgumentException("内置角色集合不能包含空白角色");
        }
        createdAt = Objects.requireNonNull(createdAt, "成员创建时间不能为空");
    }

    private static String requireText(String value, String name) {
        value = Objects.requireNonNull(value, name + "不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + "不能为空白");
        }
        return value;
    }
}
