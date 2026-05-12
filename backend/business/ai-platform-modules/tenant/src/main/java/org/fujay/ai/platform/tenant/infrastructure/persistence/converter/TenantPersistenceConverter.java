package org.fujay.ai.platform.tenant.infrastructure.persistence.converter;

import org.fujay.ai.platform.tenant.domain.aggregate.Tenant;
import org.fujay.ai.platform.tenant.domain.model.IamMenu;
import org.fujay.ai.platform.tenant.domain.model.IamPermission;
import org.fujay.ai.platform.tenant.domain.model.IamRole;
import org.fujay.ai.platform.tenant.domain.model.TenantUser;
import org.fujay.ai.platform.tenant.domain.model.UserCredential;
import org.fujay.ai.platform.tenant.infrastructure.persistence.po.IamMenuPO;
import org.fujay.ai.platform.tenant.infrastructure.persistence.po.IamPermissionPO;
import org.fujay.ai.platform.tenant.infrastructure.persistence.po.IamRolePO;
import org.fujay.ai.platform.tenant.infrastructure.persistence.po.IamUserCredentialPO;
import org.fujay.ai.platform.tenant.infrastructure.persistence.po.IamUserPO;
import org.fujay.ai.platform.tenant.infrastructure.persistence.po.PlatformTenantPO;

public final class TenantPersistenceConverter {

    private TenantPersistenceConverter() {
    }

    public static Tenant toTenant(PlatformTenantPO po) {
        return new Tenant(po.getId(), po.getTenantCode(), po.getTenantName(), po.getStatus());
    }

    public static TenantUser toTenantUser(IamUserPO po) {
        return new TenantUser(
                po.getId(),
                po.getTenantId(),
                po.getUsername(),
                po.getDisplayName(),
                po.getStatus(),
                po.isSystemAccount());
    }

    public static UserCredential toCredential(IamUserCredentialPO po) {
        return new UserCredential(po.getId(), po.getTenantId(), po.getUserId(), po.getPasswordHash());
    }

    public static IamRole toIamRole(IamRolePO po) {
        return new IamRole(po.getId(), po.getTenantId(), po.getRoleCode(), po.getRoleName(), po.getStatus());
    }

    public static IamPermission toIamPermission(IamPermissionPO po) {
        return new IamPermission(
                po.getId(), po.getTenantId(), po.getPermissionCode(), po.getPermissionName(), po.getStatus());
    }

    public static IamMenu toIamMenu(IamMenuPO po) {
        return new IamMenu(po.getId(), po.getTenantId(), po.getMenuCode(), po.getMenuName(), po.getParentCode(),
                po.getRoutePath(), po.getComponentKey(), po.getPlatformScope(), po.getSortOrder(), po.getStatus());
    }
}
