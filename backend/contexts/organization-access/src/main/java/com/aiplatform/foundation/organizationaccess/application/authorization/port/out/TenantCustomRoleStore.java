package com.aiplatform.foundation.organizationaccess.application.authorization.port.out;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantCustomRole;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;

/** 租户自定义角色建立所需的名称约束读取与原子写入端口。 */
public interface TenantCustomRoleStore {

    /** 判断当前租户是否已存在同名活动自定义角色。 */
    boolean hasActiveName(TenantId tenantId, String name);

    /** 原子写入已经通过领域不变量校验的角色聚合根。 */
    void insert(TenantCustomRole role);
}
