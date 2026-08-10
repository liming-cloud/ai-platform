package com.aiplatform.foundation.organizationaccess.application.member.dto;

import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import java.util.Objects;

/** 成员状态变更后的最小应用读取投影。 */
public record TenantMemberStatusView(String membershipId, String status, long permissionVersion) {

    /** 从已提交的成员资格聚合根生成无敏感读取投影。 */
    public static TenantMemberStatusView from(TenantMembership membership) {
        membership = Objects.requireNonNull(membership, "成员资格不能为空");
        return new TenantMemberStatusView(
                Long.toString(membership.id().value()),
                membership.status().name(),
                membership.permissionVersion());
    }
}
