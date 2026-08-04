package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.repository;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto.TenantLifecycleSummary;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantLifecycleReadPort;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantLifecycleReadMapper;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.util.Objects;

/**
 * PostgreSQL 生命周期最小读取投影适配器。
 *
 * <p>只映射已选择的展示字段与分页边界，不重建聚合，也不读取初始管理员或成员事实。</p>
 */
public final class PostgresTenantLifecycleReadAdapter implements TenantLifecycleReadPort {

    private final TenantLifecycleReadMapper mapper;

    public PostgresTenantLifecycleReadAdapter(TenantLifecycleReadMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "生命周期读取映射器不能为空");
    }

    @Override
    public PageResult<TenantLifecycleSummary> page(PageQuery query) {
        query = Objects.requireNonNull(query, "分页查询不能为空");
        var summaries = mapper.selectPage(query.offset(), query.pageSize()).stream()
                .map(row -> new TenantLifecycleSummary(
                        row.getTenantId(),
                        row.getTenantCode(),
                        row.getDisplayName(),
                        row.getStatus(),
                        row.getVersion(),
                        row.getCreatedAt(),
                        row.getLastChangedAt()))
                .toList();
        return PageResult.of(summaries, query, mapper.count());
    }
}
