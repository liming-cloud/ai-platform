package com.aiplatform.foundation.organizationaccess.domain.member.model;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionGrantSet;
import java.util.Objects;
import java.util.Set;

/**
 * 可认证租户成员的领域画像，统一维护身份、凭据与授权版本的一致性。
 */
public final class MemberAuthenticationProfile implements ValueObject {

    private final TenantId tenantId;
    private final SubjectId subjectId;
    private final MemberId membershipId;
    private final EncodedPassword encodedPassword;
    private final PermissionGrantSet grants;

    public MemberAuthenticationProfile(
            TenantId tenantId,
            SubjectId subjectId,
            MemberId membershipId,
            EncodedPassword encodedPassword,
            PermissionGrantSet grants) {
        this.tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        this.subjectId = Objects.requireNonNull(subjectId, "主体标识不能为空");
        this.membershipId = Objects.requireNonNull(membershipId, "成员标识不能为空");
        this.encodedPassword = Objects.requireNonNull(encodedPassword, "密码摘要不能为空");
        this.grants = Objects.requireNonNull(grants, "权限授权集不能为空");
    }

    public EncodedPassword encodedPassword() {
        return encodedPassword;
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
                + ", encodedPassword=" + encodedPassword
                + ", permissionVersion=" + grants.version()
                + ", permissions=" + grants.permissions() + "]";
    }
}
