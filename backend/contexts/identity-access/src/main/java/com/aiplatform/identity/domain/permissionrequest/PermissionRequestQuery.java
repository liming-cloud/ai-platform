package com.aiplatform.identity.domain.permissionrequest;

import com.aiplatform.shared.kernel.TenantId;
import java.util.Objects;

/**
 * 权限申请分页查询值对象。租户范围始终来自服务端上下文，状态为空表示全部状态。
 */
public record PermissionRequestQuery(
        TenantId tenantId,
        PermissionRequestStatus status,
        int page,
        int pageSize
) {
    public PermissionRequestQuery {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        if (page < 1) {
            throw new IllegalArgumentException("页码必须大于 0");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("每页条数必须为 1 到 100");
        }
    }

    public long offset() {
        return Math.multiplyExact((long) page - 1, pageSize);
    }
}
