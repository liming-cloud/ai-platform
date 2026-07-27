package com.aiplatform.foundation.organizationaccess.application.authentication;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import java.util.Objects;
import java.util.Set;

/**
 * 认证所需的最小成员事实。
 */
public final class AuthenticatedMember {

    private final TenantId tenantId;
    private final SubjectId subjectId;
    private final MembershipId membershipId;
    private final String encodedPassword;
    private final long permissionVersion;
    private final Set<PermissionCode> permissions;

    public AuthenticatedMember(
            TenantId tenantId,
            SubjectId subjectId,
            MembershipId membershipId,
            String encodedPassword,
            long permissionVersion,
            Set<PermissionCode> permissions) {
        this.tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        this.subjectId = Objects.requireNonNull(subjectId, "主体标识不能为空");
        this.membershipId = Objects.requireNonNull(membershipId, "成员标识不能为空");
        this.encodedPassword = requireEncodedPassword(encodedPassword);
        if (permissionVersion < 0) {
            throw new IllegalArgumentException("权限版本不能为负数");
        }
        this.permissionVersion = permissionVersion;
        this.permissions = Set.copyOf(Objects.requireNonNull(permissions, "权限集合不能为空"));
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

    public String encodedPassword() {
        return encodedPassword;
    }

    public long permissionVersion() {
        return permissionVersion;
    }

    public Set<PermissionCode> permissions() {
        return permissions;
    }

    @Override
    public String toString() {
        return "AuthenticatedMember[tenantId=" + tenantId
                + ", subjectId=" + subjectId
                + ", membershipId=" + membershipId
                + ", encodedPassword=[已脱敏], permissionVersion=" + permissionVersion
                + ", permissions=" + permissions + "]";
    }

    private static String requireEncodedPassword(String value) {
        Objects.requireNonNull(value, "密码摘要不能为空");
        if (value.isBlank() || value.length() > 256) {
            throw new IllegalArgumentException("密码摘要格式不正确");
        }
        return value;
    }
}
