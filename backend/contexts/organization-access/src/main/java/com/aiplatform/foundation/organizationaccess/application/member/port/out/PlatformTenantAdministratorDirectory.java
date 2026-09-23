package com.aiplatform.foundation.organizationaccess.application.member.port.out;

import com.aiplatform.foundation.organizationaccess.application.authorization.dto.PlatformAccountCandidate;
import com.aiplatform.foundation.organizationaccess.application.member.dto.TenantAdministratorSummary;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;

/** 平台范围租户管理员目录与候选目录读取端口。 */
public interface PlatformTenantAdministratorDirectory {

    PageResult<TenantAdministratorSummary> page(TenantId tenantId, String query, String status, PageQuery page);

    PageResult<PlatformAccountCandidate> candidates(
            TenantId tenantId,
            String query,
            PageQuery page);
}
