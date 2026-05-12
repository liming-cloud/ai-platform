package org.fujay.ai.platform.tenant.domain.model;

import java.util.List;
import org.fujay.ai.platform.shared.ddd.ValueObject;

public record RoleAccess(IamRole role, List<IamPermission> permissions, List<IamMenu> menus) implements ValueObject {
    public RoleAccess {
        permissions = List.copyOf(permissions == null ? List.of() : permissions);
        menus = List.copyOf(menus == null ? List.of() : menus);
    }
}
