package com.aiplatform.identity.application;

import com.aiplatform.identity.application.port.IamConfigurationQueryPort;
import com.aiplatform.identity.application.result.IamMenuConfigurationResult;
import com.aiplatform.identity.application.result.IamPermissionResult;
import com.aiplatform.identity.application.result.IamRoleResult;
import com.aiplatform.identity.application.result.IamTenantFeatureResult;
import com.aiplatform.identity.application.result.IamTenantResult;
import com.aiplatform.identity.application.result.IamUserConfigurationPage;
import com.aiplatform.shared.kernel.TenantId;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * IAM 配置只读应用服务，保持配置读取 API 不绕过 identity-access 应用边界。
 */
@Service
public class IamConfigurationQueryApplicationService {
    private final IamConfigurationQueryPort queryPort;

    public IamConfigurationQueryApplicationService(IamConfigurationQueryPort queryPort) {
        this.queryPort = Objects.requireNonNull(queryPort, "IAM 配置查询端口不能为空");
    }

    public List<IamTenantResult> listTenants() {
        return queryPort.findTenants();
    }

    public List<IamMenuConfigurationResult> listMenus(TenantId tenantId) {
        return queryPort.findMenus(requireTenant(tenantId));
    }

    public List<IamTenantFeatureResult> listTenantFeatures(TenantId tenantId) {
        return queryPort.findTenantFeatures(requireTenant(tenantId));
    }

    public List<IamRoleResult> listRoles(TenantId tenantId) {
        return queryPort.findRoles(requireTenant(tenantId));
    }

    public IamUserConfigurationPage listUsers(TenantId tenantId, int page, int pageSize) {
        if (page < 1) {
            throw new IllegalArgumentException("页码必须大于 0");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("每页条数必须为 1 到 100");
        }
        return queryPort.findUsers(requireTenant(tenantId), page, pageSize);
    }

    public List<IamPermissionResult> listPermissions(TenantId tenantId) {
        return queryPort.findPermissions(requireTenant(tenantId));
    }

    public List<IamMenuConfigurationResult> listRoleMenus(TenantId tenantId, String roleId) {
        return queryPort.findRoleMenus(requireTenant(tenantId), requireText(roleId, "角色 ID"));
    }

    public List<IamPermissionResult> listRolePermissions(TenantId tenantId, String roleId) {
        return queryPort.findRolePermissions(requireTenant(tenantId), requireText(roleId, "角色 ID"));
    }

    private static TenantId requireTenant(TenantId tenantId) {
        return Objects.requireNonNull(tenantId, "租户 ID 不能为空");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
