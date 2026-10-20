package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto.TenantLifecycleSummary;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto.TenantDirectoryFilter;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto.TenantLifecycleView;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto.TenantQuotaView;
import java.util.Optional;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;

/**
 * 平台租户生命周期最小读取投影端口。
 */
public interface TenantLifecycleReadPort {

    /**
     * 按受限分页边界读取最小生命周期投影。
     */
    PageResult<TenantLifecycleSummary> page(PageQuery query, TenantDirectoryFilter filter);

    /** 只读取指定租户的生命周期展示事实。 */
    Optional<TenantLifecycleView> find(long tenantId);

    /** 在同一事务内锁定并确认活动状态，避免保存声明与停用互相穿越。 */
    void requireActive(java.util.Set<Long> tenantIds);

    /** 读取已保存的独立配额事实，不将配置当成使用量。 */
    Optional<TenantQuotaView> quotas(long tenantId);
}
