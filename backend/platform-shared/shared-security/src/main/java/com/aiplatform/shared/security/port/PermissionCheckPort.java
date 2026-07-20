package com.aiplatform.shared.security.port;

import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.model.PermissionCode;

/**
 * 权限校验共享端口，用于业务上下文在服务端主体和租户范围内执行资源授权判断。
 */
public interface PermissionCheckPort {
    boolean canAccess(
            TenantId tenantId,
            String actorId,
            PermissionCode permissionCode,
            String resourceType,
            String resourceId
    );
}
