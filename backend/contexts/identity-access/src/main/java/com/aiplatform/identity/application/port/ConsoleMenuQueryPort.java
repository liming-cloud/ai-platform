package com.aiplatform.identity.application.port;

import com.aiplatform.identity.application.result.AuthorizedConsoleMenuSnapshot;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.model.PermissionCode;
import java.util.Set;

/**
 * 控制台菜单查询端口，从 DB 中恢复角色菜单、权限和租户功能共同决定的菜单快照。
 */
public interface ConsoleMenuQueryPort {
    AuthorizedConsoleMenuSnapshot findAuthorizedMenus(
            TenantId tenantId,
            String actorType,
            String actorId,
            Set<String> roleCodes,
            Set<PermissionCode> permissionCodes
    );
}
