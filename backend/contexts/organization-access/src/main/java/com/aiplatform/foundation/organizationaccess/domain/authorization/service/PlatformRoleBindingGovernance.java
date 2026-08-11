package com.aiplatform.foundation.organizationaccess.domain.authorization.service;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform.PlatformRole;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform.PlatformRoleBinding;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform.PlatformRoleBindingId;
import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import java.time.Instant;
import java.util.Objects;

/** 平台固定角色绑定的跨账户不变量。 */
public final class PlatformRoleBindingGovernance {

    private PlatformRoleBindingGovernance() {
    }

    /**
     * 只允许为已启用账户绑定尚未持有的固定平台角色。
     *
     * @throws IllegalStateException 当目标账户未启用或角色已绑定时拒绝。
     */
    public static PlatformRoleBinding bind(
            SubjectIdentity target,
            boolean hasActiveBinding,
            PlatformRoleBindingId bindingId,
            PlatformRole role,
            Instant occurredAt) {
        target = Objects.requireNonNull(target, "目标账户不能为空");
        if (!target.isActive()) {
            throw new IllegalStateException("未启用账户不能绑定平台角色");
        }
        if (hasActiveBinding) {
            throw new IllegalStateException("目标账户已拥有该平台角色");
        }
        return PlatformRoleBinding.bind(
                Objects.requireNonNull(bindingId, "平台角色绑定标识不能为空"),
                target.id(),
                Objects.requireNonNull(role, "平台角色不能为空"),
                Objects.requireNonNull(occurredAt, "角色绑定时间不能为空"));
    }

    /**
     * 撤销一项活动绑定，同时保护最后一个有效账户管理员。
     *
     * @throws IllegalStateException 当绑定已撤销，或撤销会移除最后一个有效账户管理员时拒绝。
     */
    public static PlatformRoleBinding revoke(
            SubjectIdentity target,
            PlatformRoleBinding binding,
            long otherActiveAccountAdministrators,
            Instant occurredAt) {
        target = Objects.requireNonNull(target, "目标账户不能为空");
        binding = Objects.requireNonNull(binding, "平台角色绑定不能为空");
        if (!target.id().equals(binding.subjectId())) {
            throw new IllegalArgumentException("平台角色绑定不属于目标账户");
        }
        if (!binding.isActive()) {
            throw new IllegalStateException("平台角色绑定已撤销");
        }
        if (otherActiveAccountAdministrators < 0L) {
            throw new IllegalArgumentException("其他有效账户管理员数量不能为负数");
        }
        if (target.isActive()
                && binding.role() == PlatformRole.PLATFORM_ACCOUNT_ADMINISTRATOR
                && otherActiveAccountAdministrators == 0L) {
            throw new IllegalStateException("不能撤销最后一个有效账户管理员角色");
        }
        return binding.revoke(Objects.requireNonNull(occurredAt, "角色撤销时间不能为空"));
    }
}
