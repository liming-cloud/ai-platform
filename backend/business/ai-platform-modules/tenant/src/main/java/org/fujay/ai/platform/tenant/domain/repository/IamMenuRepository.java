package org.fujay.ai.platform.tenant.domain.repository;

import java.util.List;
import java.util.Optional;
import org.fujay.ai.platform.tenant.domain.model.IamMenu;

public interface IamMenuRepository {

    List<IamMenu> list(Long tenantId);

    Optional<IamMenu> findByCode(Long tenantId, String menuCode);

    List<IamMenu> findByCodes(Long tenantId, List<String> menuCodes);

    Optional<IamMenu> save(IamMenu menu);
}
