package com.aiplatform.foundation.evaluationoperations.application;

import com.aiplatform.foundation.evaluationoperations.api.OperationsQueryUseCase;
import com.aiplatform.foundation.evaluationoperations.api.model.ConcurrentQueryQuotaView;
import com.aiplatform.foundation.evaluationoperations.api.model.DailyQueryQuotaView;
import com.aiplatform.foundation.evaluationoperations.api.model.ListQueryAuditRecordsQuery;
import com.aiplatform.foundation.evaluationoperations.api.model.QueryAuditRecordPage;
import com.aiplatform.foundation.evaluationoperations.api.model.QueryAuditRecordView;
import com.aiplatform.foundation.evaluationoperations.api.model.QueryUsageQuery;
import com.aiplatform.foundation.evaluationoperations.api.model.QueryUsageView;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.trustedquery.api.operations.QueryAuditFact;
import com.aiplatform.foundation.trustedquery.api.operations.QueryOperationsFactSource;
import com.aiplatform.foundation.trustedquery.api.operations.QueryUsageFactQuery;
import com.aiplatform.foundation.trustedquery.application.port.out.QueryQuotaPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * 在评测与运营边界内执行权限、时间范围和脱敏映射。
 */
public final class DefaultOperationsQueryService
        implements OperationsQueryUseCase {

    private static final PermissionCode USAGE_READ =
            new PermissionCode("usage:read");
    private static final PermissionCode AUDIT_READ =
            new PermissionCode("audit:read");
    private static final Duration MAXIMUM_RANGE = Duration.ofDays(31);

    private final QueryOperationsFactSource facts;
    private final QueryQuotaPolicy quota;
    private final Clock clock;

    public DefaultOperationsQueryService(
            QueryOperationsFactSource facts,
            QueryQuotaPolicy quota,
            Clock clock) {
        this.facts = Objects.requireNonNull(facts, "查询运营事实源不能为空");
        this.quota = Objects.requireNonNull(quota, "查询配额策略不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    @Override
    public QueryUsageView usage(QueryUsageQuery query) {
        Objects.requireNonNull(query, "查询用量请求不能为空");
        requirePermission(query.context().hasPermission(USAGE_READ));
        var now = clock.instant();
        var currentPeriodStart = utcDayStart(now);
        var range = resolveRange(query.from(), query.to(), currentPeriodStart);
        var measured = facts.summarize(new QueryUsageFactQuery(
                query.context().tenantId(),
                range.from(),
                range.to(),
                currentPeriodStart,
                currentPeriodStart.plus(Duration.ofDays(1)),
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
                        currentPeriodStart.plus(Duration.ofDays(1))),
                new ConcurrentQueryQuotaView(
                        measured.concurrentUsed(),
                        quota.concurrentQueryLimit()),
                now);
    }

    @Override
    public QueryAuditRecordPage audit(ListQueryAuditRecordsQuery query) {
        Objects.requireNonNull(query, "查询审计请求不能为空");
        requirePermission(query.context().hasPermission(AUDIT_READ));
        if (query.limit() < 1 || query.limit() > 100) {
            throw new IllegalArgumentException("分页条数必须在 1 到 100 之间");
        }
        var page = facts.listAuditRecords(
                query.context().tenantId(),
                query.cursor(),
                query.limit());
        return new QueryAuditRecordPage(
                page.items().stream()
                        .map(DefaultOperationsQueryService::toView)
                        .toList(),
                page.nextCursor());
    }

    private static QueryAuditRecordView toView(QueryAuditFact fact) {
        return new QueryAuditRecordView(
                fact.queryRunId(),
                fact.requestedBy().toString(),
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

    private static TimeRange resolveRange(
            Instant requestedFrom,
            Instant requestedTo,
            Instant defaultFrom) {
        if (requestedFrom == null && requestedTo == null) {
            return new TimeRange(
                    defaultFrom,
                    defaultFrom.plus(Duration.ofDays(1)));
        }
        if (requestedFrom == null || requestedTo == null) {
            throw new IllegalArgumentException("统计开始和结束时间必须同时提供");
        }
        var duration = Duration.between(requestedFrom, requestedTo);
        if (duration.isZero()
                || duration.isNegative()
                || duration.compareTo(MAXIMUM_RANGE) > 0) {
            throw new IllegalArgumentException("统计时间范围必须大于零且不超过三十一天");
        }
        return new TimeRange(requestedFrom, requestedTo);
    }

    private static Instant utcDayStart(Instant value) {
        return value.atOffset(ZoneOffset.UTC)
                .toLocalDate()
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);
    }

    private static void requirePermission(boolean granted) {
        if (!granted) {
            throw new OperationsPermissionDeniedException();
        }
    }

    private record TimeRange(Instant from, Instant to) {
    }
}
