package com.aiplatform.foundation.organizationaccess.domain.member.model;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionGrantSet;
import java.util.Objects;
import java.util.Set;

/**
 * 已验证主体在指定租户内的成员资格读取投影。
 *
 * <p>本投影不保存凭据摘要；主体凭据只属于 {@code SubjectIdentity} 聚合根，
 * 避免租户成员资格复制或轮换全局凭据。</p>
 */
public final class MemberAuthenticationProfile implements ValueObject {

    private final TenantId tenantId;
    private final SubjectId subjectId;
    private final MemberId membershipId;
    private final PermissionGrantSet grants;

    public MemberAuthenticationProfile(
            TenantId tenantId,
            SubjectId subjectId,
            MemberId membershipId,
            PermissionGrantSet grants) {
        this.tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        this.subjectId = Objects.requireNonNull(subjectId, "主体标识不能为空");
        this.membershipId = Objects.requireNonNull(membershipId, "成员标识不能为空");
        this.grants = Objects.requireNonNull(grants, "权限授权集不能为空");
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public SubjectId subjectId() {
        return subjectId;
    }

    public MemberId membershipId() {
        return membershipId;
    }

    public long permissionVersion() {
        return grants.version();
    }

    public Set<PermissionCode> permissions() {
        return grants.permissions();
    }

    @Override
    public String toString() {
        return "MemberAuthenticationProfile[tenantId=" + tenantId
                + ", subjectId=" + subjectId
                + ", membershipId=" + membershipId
                + ", permissionVersion=" + grants.version()
                + ", permissions=" + grants.permissions() + "]";
    }
}
