package com.aiplatform.foundation.organizationaccess.application.authorization.port.out;

import com.aiplatform.foundation.organizationaccess.application.authorization.dto.TenantCustomRoleSummary;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;

/** 当前租户自定义角色目录的受限分页读取端口。 */
public interface TenantCustomRoleDirectory {

    /** 按稳定排序读取一页当前租户自定义角色。 */
    PageResult<TenantCustomRoleSummary> page(TenantId tenantId, PageQuery page);
}
