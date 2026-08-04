package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto.TenantLifecycleSummary;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;

/**
 * 平台租户生命周期最小读取投影端口。
 */
public interface TenantLifecycleReadPort {

    /**
     * 按受限分页边界读取最小生命周期投影。
     */
    PageResult<TenantLifecycleSummary> page(PageQuery query);
}
