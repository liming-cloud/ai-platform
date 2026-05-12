package org.fujay.ai.platform.tenant.domain.repository;

import java.util.List;
import org.fujay.ai.platform.tenant.domain.model.IamMenu;
import org.fujay.ai.platform.tenant.domain.model.IamPermission;
import org.fujay.ai.platform.tenant.domain.model.IamRole;

public interface TenantAuthorizationRepository {

    List<IamRole> findRolesByUserId(Long tenantId, Long userId);

    List<IamPermission> findPermissionsByUserId(Long tenantId, Long userId);

    List<IamMenu> findMenusByUserId(Long tenantId, Long userId);
}
