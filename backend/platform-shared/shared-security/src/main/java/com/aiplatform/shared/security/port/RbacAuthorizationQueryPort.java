package com.aiplatform.shared.security.port;

import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.model.PermissionCode;
import java.util.Set;

/**
 * RBAC 授权查询端口，供 Sa-Token 和业务上下文从服务端权限目录恢复角色与权限快照。
 */
public interface RbacAuthorizationQueryPort {
    Set<String> findRoleCodes(TenantId tenantId, String actorType, String actorId);

    Set<PermissionCode> findPermissionCodes(TenantId tenantId, String actorType, String actorId);
}
