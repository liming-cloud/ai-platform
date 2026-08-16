package com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionCode;
import java.util.Set;

/**
 * 已发布的平台固定角色模板。
 *
 * <p>权限只能由本枚举派生，禁止把个人直接授权伪装成角色绑定。</p>
 */
public enum PlatformRole {

    /** 管理平台账户、租户、模型供应链和脱敏运营摘要。 */
    PLATFORM_ADMINISTRATOR(Set.of(
            new PermissionCode("platform-session:access"),
            new PermissionCode("platform:accounts:manage"),
            new PermissionCode("tenant-governance:write"),
            new PermissionCode("model-platform:write"),
            new PermissionCode("platform-operations:read")));

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
