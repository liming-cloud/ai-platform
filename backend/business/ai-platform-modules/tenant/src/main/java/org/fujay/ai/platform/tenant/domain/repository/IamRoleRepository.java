package org.fujay.ai.platform.tenant.domain.repository;

import java.util.List;
import java.util.Optional;
import org.fujay.ai.platform.tenant.domain.model.IamMenu;
import org.fujay.ai.platform.tenant.domain.model.IamPermission;
import org.fujay.ai.platform.tenant.domain.model.IamRole;
import org.fujay.ai.platform.tenant.domain.model.RoleAccess;

public interface IamRoleRepository {

    Optional<IamRole> findById(Long tenantId, Long roleId);

    Optional<IamRole> findByCode(Long tenantId, String roleCode);

    List<IamRole> list(Long tenantId);

    List<IamPermission> findPermissionsByRoleId(Long tenantId, Long roleId);

    List<IamMenu> findMenusByRoleId(Long tenantId, Long roleId);

    Optional<IamRole> save(IamRole role);

    RoleAccess replaceRoleAccess(IamRole role, List<IamPermission> permissions, List<IamMenu> menus);
}
