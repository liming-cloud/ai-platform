package com.aiplatform.foundation.organizationaccess.domain.authorization.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import java.util.Objects;
import java.util.Set;

/**
 * 带单调版本的不可变成员权限授权集。
 */
public final class PermissionGrantSet implements ValueObject {

    private final long version;
    private final Set<PermissionCode> permissions;

    public PermissionGrantSet(long version, Set<PermissionCode> permissions) {
        if (version < 0) {
            throw new IllegalArgumentException("权限版本不能为负数");
        }
        Objects.requireNonNull(permissions, "权限集合不能为空");
        if (permissions.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("权限集合不能包含空值");
        }
        this.version = version;
        this.permissions = Set.copyOf(permissions);
    }

    public long version() {
        return version;
    }

    public Set<PermissionCode> permissions() {
        return permissions;
    }
}
