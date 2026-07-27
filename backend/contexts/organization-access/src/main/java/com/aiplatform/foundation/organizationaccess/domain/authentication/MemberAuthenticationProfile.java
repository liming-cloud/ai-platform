package com.aiplatform.foundation.organizationaccess.domain.authentication;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.organizationaccess.domain.authorization.PermissionGrantSet;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;
import java.util.Set;

/**
 * 可认证租户成员的领域画像，统一维护身份、凭据与授权版本的一致性。
 */
public final class MemberAuthenticationProfile {

    private final TenantId tenantId;
    private final SubjectId subjectId;
    private final MembershipId membershipId;
    private final EncodedPassword encodedPassword;
    private final PermissionGrantSet grants;

    public MemberAuthenticationProfile(
            TenantId tenantId,
            SubjectId subjectId,
            MembershipId membershipId,
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

    public MembershipId membershipId() {
        return membershipId;
    }

    /**
     * 认证通过后从一致的成员事实生成可信请求上下文。
     */
    public TrustedRequestContext trustedContext(
            AccessChannel channel,
            String traceId) {
        return new TrustedRequestContext(
                tenantId,
                subjectId,
                membershipId,
                CredentialType.PERSON,
                Objects.requireNonNull(channel, "访问渠道不能为空"),
                grants.version(),
                grants.permissions(),
                traceId);
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
