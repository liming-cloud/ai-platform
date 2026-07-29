package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.repository;

import com.aiplatform.foundation.knowledgedata.application.datasource.dto.GovernedFileDataSourceSummary;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.GovernedFileDataSourceReadPort;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.entity.GovernedFileDataSourceSummaryRow;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.GovernedFileDataSourceReadMapper;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Objects;

/** 使用单个 PostgreSQL 读取快照恢复 FILE 来源与其当前已发布产物事实。 */
public final class PostgresGovernedFileDataSourceReadAdapter
        implements GovernedFileDataSourceReadPort {

    private final GovernedFileDataSourceReadMapper mapper;

    public PostgresGovernedFileDataSourceReadAdapter(GovernedFileDataSourceReadMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "FILE 来源读取映射器不能为空");
    }

    @Override
    public PageResult<GovernedFileDataSourceSummary> list(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            PageQuery page) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(knowledgeSpaceId, "知识空间标识不能为空");
        Objects.requireNonNull(page, "分页参数不能为空");
        var offset = Math.multiplyExact(page.pageNumber() - 1, page.pageSize());
        var rows = mapper.selectPage(
                tenantId.value(), knowledgeSpaceId.value(), offset, page.pageSize());
        return PageResult.of(
                rows.stream().map(PostgresGovernedFileDataSourceReadAdapter::toSummary).toList(),
                page,
                mapper.count(tenantId.value(), knowledgeSpaceId.value()));
    }

    private static GovernedFileDataSourceSummary toSummary(GovernedFileDataSourceSummaryRow row) {
        return new GovernedFileDataSourceSummary(
                Long.toString(row.getDataSourceId()),
                Long.toString(row.getKnowledgeSpaceId()),
                row.getDisplayName(),
                row.getStatus(),
                row.getVersion(),
                row.getFailedOperation(),
                row.getPublishedArtifactId() == null ? null : Long.toString(row.getPublishedArtifactId()),
                row.getPublishedQualityStatus(),
                row.getPublishedAt());
    }
}
