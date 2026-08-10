package com.aiplatform.foundation.organizationaccess.application.member.port.out;

import com.aiplatform.foundation.organizationaccess.application.member.dto.TenantMemberSummary;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;

/** 当前租户成员目录的受限分页读取端口。 */
public interface TenantMemberDirectory {

    /** 只读取当前租户内的最小成员投影及其分页元数据。 */
    PageResult<TenantMemberSummary> page(TenantId tenantId, PageQuery page);
}
