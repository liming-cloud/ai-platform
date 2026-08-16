package com.aiplatform.foundation.organizationaccess.application.operations.facade;

import com.aiplatform.foundation.organizationaccess.application.operations.dto.PlatformOrganizationOperationsFacts;
import com.aiplatform.foundation.organizationaccess.application.operations.dto.TenantOperationsIdentity;
import com.aiplatform.foundation.organizationaccess.application.operations.dto.TenantOrganizationOperationsFacts;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;

/**
 * 向运营上下文提供组织边界拥有的脱敏计数；实现不得返回登录凭据或成员明细。
 */
public interface OrganizationOperationsFactSource {

    PlatformOrganizationOperationsFacts platformSummary();

    TenantOrganizationOperationsFacts tenantSummary(TenantId tenantId);

    PageResult<TenantOperationsIdentity> listTenants(PageQuery page);
}
