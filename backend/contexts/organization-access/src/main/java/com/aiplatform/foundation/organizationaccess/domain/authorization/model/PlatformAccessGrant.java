package com.aiplatform.foundation.organizationaccess.domain.authorization.model;

import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import java.util.Objects;
import java.util.Set;

/**
 * 已启用主体持有的平台租户治理授权读取事实。
 *
 * <p>它不包含租户、成员、正文或凭据。授权管理生命周期不在 ADR-0043 首条切片范围内，
 * 因而本对象是认证读取投影而不是可由应用层修改的聚合。</p>
 */
public record PlatformAccessGrant(
        SubjectId subjectId,
        long permissionVersion,
        Set<PermissionCode> permissions) implements ValueObject {

    private static final PermissionCode TENANT_GOVERNANCE_WRITE =
            new PermissionCode("tenant-governance:write");

    public PlatformAccessGrant {
        Objects.requireNonNull(subjectId, "主体标识不能为空");
        if (permissionVersion < 0L) {
            throw new IllegalArgumentException("平台权限版本不能为负数");
        }
        permissions = Set.copyOf(Objects.requireNonNull(permissions, "平台权限集合不能为空"));
        if (!permissions.contains(TENANT_GOVERNANCE_WRITE)) {
            throw new IllegalArgumentException("平台授权缺少租户治理权限");
        }
    }
}
