package com.aiplatform.identity.application.port;

import com.aiplatform.identity.application.result.IamMenuConfigurationResult;
import com.aiplatform.identity.application.result.IamPermissionResult;
import com.aiplatform.identity.application.result.IamRoleResult;
import com.aiplatform.identity.application.result.IamTenantFeatureResult;
import com.aiplatform.identity.application.result.IamTenantResult;
import com.aiplatform.identity.application.result.IamUserConfigurationPage;
import com.aiplatform.shared.kernel.TenantId;
import java.util.List;

/**
 * IAM 配置只读查询端口，供系统设置读取 DB 中的菜单、功能、角色和授权事实。
 */
public interface IamConfigurationQueryPort {
    List<IamTenantResult> findTenants();

    List<IamMenuConfigurationResult> findMenus(TenantId tenantId);

    List<IamTenantFeatureResult> findTenantFeatures(TenantId tenantId);

    List<IamRoleResult> findRoles(TenantId tenantId);

    IamUserConfigurationPage findUsers(TenantId tenantId, int page, int pageSize);

    List<IamPermissionResult> findPermissions(TenantId tenantId);

    List<IamMenuConfigurationResult> findRoleMenus(TenantId tenantId, String roleId);

    List<IamPermissionResult> findRolePermissions(TenantId tenantId, String roleId);
}
