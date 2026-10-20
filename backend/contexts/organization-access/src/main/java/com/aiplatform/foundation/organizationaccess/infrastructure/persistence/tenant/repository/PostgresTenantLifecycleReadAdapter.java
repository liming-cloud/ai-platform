package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.repository;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto.TenantLifecycleSummary;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto.TenantDirectoryFilter;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto.TenantLifecycleView;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto.TenantQuotaView;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantProvisioningMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantQuotaMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.Optional;
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
    private final TenantProvisioningMapper lifecycles;
    private final TenantQuotaMapper quotas;

    public PostgresTenantLifecycleReadAdapter(TenantLifecycleReadMapper mapper,
            TenantProvisioningMapper lifecycles, TenantQuotaMapper quotas) {
        this.mapper = Objects.requireNonNull(mapper, "生命周期读取映射器不能为空");
        this.lifecycles = Objects.requireNonNull(lifecycles);
        this.quotas = Objects.requireNonNull(quotas);
    }

    @Override
    public PageResult<TenantLifecycleSummary> page(PageQuery query, TenantDirectoryFilter filter) {
        query = Objects.requireNonNull(query, "分页查询不能为空");
        filter = Objects.requireNonNull(filter, "目录过滤条件不能为空");
        // 查询作为字面子串绑定；用户输入不能扩大为 SQL 通配符或片段。
        var pattern = filter.query().isEmpty() ? "" : "%" + filter.query()
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        var page = mapper.selectPage(new Page<>(query.pageNumber(), query.pageSize()), pattern, filter.status());
        var summaries = page.getRecords().stream()
                .map(row -> new TenantLifecycleSummary(
                        row.getTenantId(),
                        row.getTenantCode(),
                        row.getDisplayName(),
                        row.getStatus(),
                        row.getVersion(),
                        row.getCreatedAt(),
                        row.getLastChangedAt(), row.getAdministratorCount()))
                .toList();
        return PageResult.of(summaries, query, page.getTotal());
    }

    @Override
    public void requireActive(java.util.Set<Long> tenantIds) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("活动租户复核必须在声明事务内执行");
        for (var id : java.util.Objects.requireNonNull(tenantIds).stream().sorted().toList()) {
            if (mapper.lockActive(id) == null) throw new IllegalArgumentException("拟授权租户不存在或不是活动状态");
        }
    }

    @Override
    public Optional<TenantLifecycleView> find(long tenantId) {
        return Optional.ofNullable(lifecycles.selectById(tenantId)).map(row -> new TenantLifecycleView(
                row.tenantId(), row.tenantCode(), row.displayName(), row.initialAdministratorSubjectId(),
                row.status(), row.version()));
    }

    @Override
    public Optional<TenantQuotaView> quotas(long tenantId) {
        return Optional.ofNullable(quotas.selectById(tenantId)).map(row -> row.toDomain()).map(value ->
                new TenantQuotaView(value.knowledgeBases(), value.documentsPerKnowledgeBase(),
                        Long.toString(value.bytesPerKnowledgeBase()), Long.toString(value.maxFileBytes()),
                        Long.toString(value.maxTextBytes()), value.version()));
    }
}
