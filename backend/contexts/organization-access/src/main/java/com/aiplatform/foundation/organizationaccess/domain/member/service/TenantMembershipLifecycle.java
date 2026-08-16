package com.aiplatform.foundation.organizationaccess.domain.member.service;

import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipRole;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipStatus;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;

/**
 * 维护成员状态变更与租户访问管理连续性的无状态领域服务。
 *
 * <p>“最后一个访问管理员”需要检查同租户其他成员的有效权限，故不属于单一成员聚合根；受锁目录读取由应用层
 * 提供为明确事实，本服务只裁决不变量。</p>
 */
public final class TenantMembershipLifecycle {

    private TenantMembershipLifecycle() {
    }

    /**
     * 停用成员；若目标拥有访问管理能力，必须保留至少一个其他有效访问管理员。
     *
     * @throws IllegalArgumentException 当其他有效访问管理员数量为负数时拒绝。
     * @throws IllegalStateException 当操作会停用租户最后一个访问管理员时拒绝。
     */
    public static TenantMembership disable(
            TenantMembership membership,
            boolean hasAccessManage,
            long otherEnabledAccessManagers) {
        membership = Objects.requireNonNull(membership, "成员资格不能为空");
        if (otherEnabledAccessManagers < 0L) {
            throw new IllegalArgumentException("其他有效访问管理员数量不能为负数");
        }
        if (membership.status()
                == com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipStatus.DISABLED) {
            return membership;
        }
        if (hasAccessManage && otherEnabledAccessManagers == 0L) {
            throw new IllegalStateException("不能停用租户最后一个访问管理员");
        }
        return membership.disable();
    }

    /**
     * 启用成员；启用不会恢复已撤销会话，后续认证必须重新计算有效权限。
     */
    public static TenantMembership enable(TenantMembership membership) {
        return Objects.requireNonNull(membership, "成员资格不能为空").enable();
    }

    /** 建立无隐式业务权限的普通启用成员资格。 */
    public static TenantMembership establish(
            MemberId id, TenantId tenantId, SubjectId subjectId, Instant createdAt) {
        return TenantMembership.reconstitute(
                Objects.requireNonNull(id, "成员标识不能为空"),
                Objects.requireNonNull(tenantId, "租户标识不能为空"),
                Objects.requireNonNull(subjectId, "主体标识不能为空"),
                TenantMembershipRole.TENANT_USER,
                TenantMembershipStatus.ENABLED,
                0L,
                0L,
                Objects.requireNonNull(createdAt, "成员建立时间不能为空"));
    }
}
