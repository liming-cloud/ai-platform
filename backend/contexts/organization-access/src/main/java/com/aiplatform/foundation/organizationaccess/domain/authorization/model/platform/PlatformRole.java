package com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionCode;
import java.util.Set;

/**
 * 已发布的平台固定角色模板。
 *
 * <p>权限只能由本枚举派生，禁止把个人直接授权伪装成角色绑定。</p>
 */
public enum PlatformRole {

    /** 管理全局人员账户及其平台角色。 */
    PLATFORM_ACCOUNT_ADMINISTRATOR(Set.of(
            new PermissionCode("platform-session:access"),
            new PermissionCode("platform:accounts:manage"))),

    /** 管理租户生命周期。 */
    PLATFORM_TENANT_GOVERNOR(Set.of(
            new PermissionCode("platform-session:access"),
            new PermissionCode("tenant-governance:write"))),

    /** 只允许进入平台治理控制台。 */
    PLATFORM_OPERATOR(Set.of(new PermissionCode("platform-session:access")));

    private final Set<PermissionCode> permissions;

    PlatformRole(Set<PermissionCode> permissions) {
        this.permissions = Set.copyOf(permissions);
    }

    /** 返回由固定模板派生的平台权限。 */
    public Set<PermissionCode> permissions() {
        return permissions;
    }

    /** 仅接受已发布的平台角色代码。 */
    public static PlatformRole from(String code) {
        try {
            return valueOf(code);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("平台角色尚未发布", exception);
        }
    }
}
