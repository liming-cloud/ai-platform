package org.fujay.ai.platform.tenant.domain.repository;

import java.util.List;
import java.util.Optional;
import org.fujay.ai.platform.tenant.domain.model.IamPermission;

public interface IamPermissionRepository {

    List<IamPermission> list(Long tenantId);

    Optional<IamPermission> findByCode(Long tenantId, String permissionCode);

    List<IamPermission> findByCodes(Long tenantId, List<String> permissionCodes);

    Optional<IamPermission> save(IamPermission permission);
}
