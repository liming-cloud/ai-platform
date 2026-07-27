package com.aiplatform.foundation.evaluationoperations.application;

import com.aiplatform.foundation.evaluationoperations.api.OperationsQueryUseCase;
import com.aiplatform.foundation.evaluationoperations.api.model.ConcurrentQueryQuotaView;
import com.aiplatform.foundation.evaluationoperations.api.model.DailyQueryQuotaView;
import com.aiplatform.foundation.evaluationoperations.api.model.ListQueryAuditRecordsQuery;
import com.aiplatform.foundation.evaluationoperations.api.model.QueryAuditRecordView;
import com.aiplatform.foundation.evaluationoperations.api.model.QueryUsageQuery;
import com.aiplatform.foundation.evaluationoperations.api.model.QueryUsageView;
import com.aiplatform.foundation.evaluationoperations.domain.authorization.OperationsAccessPolicy;
import com.aiplatform.foundation.evaluationoperations.domain.usage.OperationsQuotaPolicy;
import com.aiplatform.foundation.evaluationoperations.domain.usage.ReportingPeriod;
import com.aiplatform.foundation.trustedquery.application.operations.dto.QueryAuditFact;
import com.aiplatform.foundation.trustedquery.application.operations.facade.QueryOperationsFactSource;
import com.aiplatform.foundation.trustedquery.application.operations.query.QueryUsageFactQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.time.Clock;
import java.util.Objects;

/**
 * 在评测与运营边界内执行权限、时间范围和脱敏映射。
 */
public final class DefaultOperationsQueryService
        implements OperationsQueryUseCase {

    private final QueryOperationsFactSource facts;
    private final OperationsQuotaPolicy quota;
    private final OperationsAccessPolicy access;
    private final Clock clock;

    public DefaultOperationsQueryService(
            QueryOperationsFactSource facts,
            OperationsQuotaPolicy quota,
            Clock clock) {
        this.facts = Objects.requireNonNull(facts, "查询运营事实源不能为空");
        this.quota = Objects.requireNonNull(quota, "查询配额策略不能为空");
        this.access = new OperationsAccessPolicy();
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    @Override
    public QueryUsageView usage(QueryUsageQuery query) {
        Objects.requireNonNull(query, "查询用量请求不能为空");
        access.requireUsageRead(query.context());
        var now = clock.instant();
        var currentPeriod = ReportingPeriod.utcDayContaining(now);
        var range = ReportingPeriod.requestedOrCurrentDay(
                query.from(),
                query.to(),
                now);
        var measured = facts.summarize(new QueryUsageFactQuery(
                query.context().tenantId(),
                range.from(),
                range.to(),
                currentPeriod.from(),
                currentPeriod.to(),
                now));
        return new QueryUsageView(
                range.from(),
                range.to(),
                measured.acceptedQueries(),
                measured.answeredQueries(),
                measured.refusedQueries(),
                measured.failedQueries(),
                measured.runningQueries(),
                measured.inputTokens(),
                measured.outputTokens(),
                measured.totalTokens(),
                new DailyQueryQuotaView(
                        measured.dailyUsed(),
                        quota.dailyQueryLimit(),
                        currentPeriod.to()),
                new ConcurrentQueryQuotaView(
                        measured.concurrentUsed(),
                        quota.concurrentQueryLimit()),
                now);
    }

    @Override
    public PageResult<QueryAuditRecordView> audit(ListQueryAuditRecordsQuery query) {
        Objects.requireNonNull(query, "查询审计请求不能为空");
        access.requireAuditRead(query.context());
        var page = facts.listAuditRecords(
                query.context().tenantId(),
                query.page());
        return PageResult.of(
                page.items().stream()
                        .map(DefaultOperationsQueryService::toView)
                        .toList(),
                query.page(),
                page.totalElements());
    }

    private static QueryAuditRecordView toView(QueryAuditFact fact) {
        return new QueryAuditRecordView(
                fact.queryRunId(),
                Long.toString(fact.requestedBy()),
                fact.assistantVersionId(),
                fact.questionDigest(),
                fact.status(),
                fact.refusalReason(),
                fact.inputTokens(),
                fact.outputTokens(),
                fact.totalTokens(),
                fact.createdAt(),
                fact.completedAt());
    }

}
