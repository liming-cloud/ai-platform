package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper;

import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.projection.TenantLifecycleSummaryRow;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;

/**
 * 生命周期最小读取投影的分页查询映射器。
 */
public interface TenantLifecycleReadMapper {

    /**
     * 按最后变更时间稳定排序后读取一页最小投影。
     */
    IPage<TenantLifecycleSummaryRow> selectPage(IPage<TenantLifecycleSummaryRow> page,
            @Param("queryPattern") String queryPattern, @Param("status") String status);
}
