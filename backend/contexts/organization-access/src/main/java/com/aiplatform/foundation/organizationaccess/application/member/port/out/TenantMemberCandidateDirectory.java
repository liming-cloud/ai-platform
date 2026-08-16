package com.aiplatform.foundation.organizationaccess.application.member.port.out;

import com.aiplatform.foundation.organizationaccess.application.member.dto.TenantMemberCandidate;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;

/** 搜索尚未加入当前租户的已启用账户，禁止返回凭据和账户治理信息。 */
@FunctionalInterface
public interface TenantMemberCandidateDirectory {

    PageResult<TenantMemberCandidate> searchEligible(
            TenantId tenantId,
            String query,
            PageQuery page);
}
