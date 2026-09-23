package com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.repository;

import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.mapper.QueryRunMapper;
import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.entity.QueryRunEntity;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.trustedquery.application.operations.facade.QueryAuditFact;
import com.aiplatform.foundation.trustedquery.application.operations.facade.QueryOperationsFactSource;
import com.aiplatform.foundation.trustedquery.application.operations.facade.QueryUsageFactQuery;
import com.aiplatform.foundation.trustedquery.application.operations.facade.QueryUsageFacts;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.Objects;

/**
 * 从查询运行唯一事实生成运营投影，数据库选择列禁止包含敏感正文。
 */
public final class PostgresQueryOperationsFactSource
        implements QueryOperationsFactSource {

    private final QueryRunMapper mapper;

    public PostgresQueryOperationsFactSource(QueryRunMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "查询运行映射器不能为空");
    }

    @Override
    public QueryUsageFacts summarize(QueryUsageFactQuery query) {
        Objects.requireNonNull(query, "查询用量事实请求不能为空");
        var row = mapper.summarizeUsage(
                query.tenantId().value(),
                query.from(),
                query.to(),
                query.currentPeriodStart(),
                query.currentPeriodEnd(),
                query.observedAt());
        if (row == null) {
            throw new IllegalStateException("查询用量汇总没有返回结果");
        }
        return new QueryUsageFacts(
                row.getAcceptedQueries(),
                row.getAnsweredQueries(),
                row.getRefusedQueries(),
                row.getFailedQueries(),
                row.getRunningQueries(),
                row.getInputTokens(),
                row.getOutputTokens(),
                row.getTotalTokens(),
                row.getDailyUsed(),
                row.getConcurrentUsed());
    }

    @Override
    public PageResult<QueryAuditFact> listAuditRecords(
            TenantId tenantId,
            PageQuery pageQuery) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(pageQuery, "分页请求不能为空");
        var page = mapper.selectPage(
                new Page<QueryRunEntity>(
                        pageQuery.pageNumber(),
                        pageQuery.pageSize()),
                Wrappers.<QueryRunEntity>lambdaQuery()
                        .eq(QueryRunEntity::getTenantId, tenantId.value())
                        .orderByDesc(
                                QueryRunEntity::getCreatedAt,
                                QueryRunEntity::getId));
        var items = page.getRecords().stream()
                .map(PostgresQueryOperationsFactSource::toFact)
                .toList();
        return PageResult.of(items, pageQuery, page.getTotal());
    }

    private static QueryAuditFact toFact(QueryRunEntity row) {
        return new QueryAuditFact(
                Long.toString(row.getId()),
                row.getRequestedBy(),
                Long.toString(row.getAssistantVersionId()),
                row.getQuestionDigest(),
                row.getStatus(),
                row.getRefusalReason(),
                row.getInputTokens(),
                row.getOutputTokens(),
                row.getTotalTokens(),
                row.getCreatedAt(),
                row.getCompletedAt());
    }

}
