package com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 由活动平台角色绑定派生的认证读取投影。
 *
 * <p>本投影不接受个人权限输入，权限始终来自已发布角色模板。</p>
 */
public record PlatformRoleAuthorization(
        SubjectId subjectId,
        long permissionVersion,
        Set<PlatformRole> roles) implements ValueObject {

    private static final PermissionCode SESSION_ACCESS = new PermissionCode("platform-session:access");

    public PlatformRoleAuthorization {
        subjectId = Objects.requireNonNull(subjectId, "主体标识不能为空");
        if (permissionVersion < 0L) {
            throw new IllegalArgumentException("平台权限版本不能为负数");
        }
        roles = Set.copyOf(Objects.requireNonNull(roles, "活动平台角色不能为空"));
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("活动平台角色不能为空");
        }
        if (!permissions(roles).contains(SESSION_ACCESS)) {
            throw new IllegalArgumentException("平台角色缺少会话准入权限");
        }
    }

    /** 返回所有活动绑定按模板并集派生的权限。 */
    public Set<PermissionCode> permissions() {
        return permissions(roles);
    }

    private static Set<PermissionCode> permissions(Set<PlatformRole> roles) {
        return roles.stream()
                .flatMap(role -> role.permissions().stream())
                .collect(Collectors.toUnmodifiableSet());
    }
}
