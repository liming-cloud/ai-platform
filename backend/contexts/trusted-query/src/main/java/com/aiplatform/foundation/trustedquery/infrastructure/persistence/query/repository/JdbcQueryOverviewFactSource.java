package com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.repository;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.trustedquery.application.operations.facade.QueryOperationsSummaryFacts;
import com.aiplatform.foundation.trustedquery.application.operations.facade.QueryDailyOperationsFacts;
import com.aiplatform.foundation.trustedquery.application.operations.facade.QueryOverviewFactSource;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 只读取查询运行状态与 Token 计数列的 PostgreSQL 事实源。 */
public final class JdbcQueryOverviewFactSource implements QueryOverviewFactSource {

    private static final String AGGREGATE = """
            COUNT(*) accepted_queries,
            COUNT(*) FILTER (WHERE status = 'ANSWERED') answered_queries,
            COUNT(*) FILTER (WHERE status = 'REFUSED') refused_queries,
            COUNT(*) FILTER (WHERE status = 'FAILED') failed_queries,
            COUNT(*) FILTER (WHERE status = 'RUNNING') running_queries,
            COALESCE(SUM(total_tokens), 0) total_tokens
            """;

    private final JdbcClient jdbc;

    public JdbcQueryOverviewFactSource(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "JDBC 客户端不能为空");
    }

    @Override
    public QueryOperationsSummaryFacts platformSummary(Instant from, Instant to) {
        var summary = jdbc.sql("SELECT " + AGGREGATE + " FROM trusted_query.query_run"
                        + " WHERE created_at >= :from AND created_at <= :to")
                .param("from", Timestamp.from(from))
                .param("to", Timestamp.from(to))
                .query((row, rowNumber) -> facts(row))
                .single();
        return withDaily(summary, daily(null, from, to));
    }

    @Override
    public QueryOperationsSummaryFacts tenantSummary(TenantId tenantId, Instant from, Instant to) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        var summary = jdbc.sql("SELECT " + AGGREGATE + " FROM trusted_query.query_run"
                        + " WHERE tenant_id = :tenantId AND created_at >= :from AND created_at <= :to")
                .param("tenantId", tenantId.value())
                .param("from", Timestamp.from(from))
                .param("to", Timestamp.from(to))
                .query((row, rowNumber) -> facts(row))
                .single();
        return withDaily(summary, daily(tenantId, from, to));
    }

    @Override
    public Map<TenantId, QueryOperationsSummaryFacts> tenantSummaries(
            List<TenantId> tenantIds,
            Instant from,
            Instant to) {
        if (tenantIds.isEmpty()) {
            return Map.of();
        }
        var ids = tenantIds.stream().map(TenantId::value).toList();
        var rows = jdbc.sql("SELECT tenant_id, " + AGGREGATE + " FROM trusted_query.query_run"
                        + " WHERE tenant_id IN (:tenantIds) AND created_at >= :from AND created_at <= :to"
                        + " GROUP BY tenant_id")
                .param("tenantIds", ids)
                .param("from", Timestamp.from(from))
                .param("to", Timestamp.from(to))
                .query((row, rowNumber) -> Map.entry(
                        new TenantId(row.getLong("tenant_id")), facts(row)))
                .list();
        var summaries = new LinkedHashMap<TenantId, QueryOperationsSummaryFacts>();
        rows.forEach(row -> summaries.put(row.getKey(), row.getValue()));
        return Map.copyOf(summaries);
    }

    @Override
    public Set<TenantId> attentionTenants(Instant from, Instant to) {
        return Set.copyOf(jdbc.sql("""
                        SELECT tenant_id
                        FROM trusted_query.query_run
                        WHERE created_at >= :from AND created_at <= :to
                        GROUP BY tenant_id
                        HAVING COUNT(*) FILTER (WHERE status = 'FAILED') > 0
                            OR MAX(daily_query_ordinal::double precision / daily_query_limit) >= 0.8
                        """)
                .param("from", Timestamp.from(from))
                .param("to", Timestamp.from(to))
                .query((row, rowNumber) -> new TenantId(row.getLong("tenant_id")))
                .list());
    }

    private static QueryOperationsSummaryFacts facts(java.sql.ResultSet row) throws java.sql.SQLException {
        return new QueryOperationsSummaryFacts(
                row.getLong("accepted_queries"),
                row.getLong("answered_queries"),
                row.getLong("refused_queries"),
                row.getLong("failed_queries"),
                row.getLong("running_queries"),
                row.getLong("total_tokens"));
    }

    private List<QueryDailyOperationsFacts> daily(TenantId tenantId, Instant from, Instant to) {
        var tenantPredicate = tenantId == null ? "" : " AND tenant_id = :tenantId";
        var statement = jdbc.sql("""
                SELECT (created_at AT TIME ZONE 'UTC')::date AS day,
                       COUNT(*) AS accepted,
                       COUNT(*) FILTER (WHERE status = 'ANSWERED') AS answered,
                       COUNT(*) FILTER (WHERE status = 'REFUSED') AS refused,
                       COUNT(*) FILTER (WHERE status = 'FAILED') AS failed,
                       COALESCE(SUM(total_tokens), 0) AS total_tokens
                FROM trusted_query.query_run
                WHERE created_at >= :from AND created_at <= :to
                """ + tenantPredicate + " GROUP BY day ORDER BY day")
                .param("from", Timestamp.from(from))
                .param("to", Timestamp.from(to));
        if (tenantId != null) {
            statement = statement.param("tenantId", tenantId.value());
        }
        return statement.query((row, rowNumber) -> new QueryDailyOperationsFacts(
                row.getObject("day", java.time.LocalDate.class), row.getLong("accepted"),
                row.getLong("answered"), row.getLong("refused"), row.getLong("failed"),
                row.getLong("total_tokens"))).list();
    }

    private static QueryOperationsSummaryFacts withDaily(
            QueryOperationsSummaryFacts summary, List<QueryDailyOperationsFacts> daily) {
        return new QueryOperationsSummaryFacts(
                summary.acceptedQueries(), summary.answeredQueries(), summary.refusedQueries(),
                summary.failedQueries(), summary.runningQueries(), summary.totalTokens(), daily);
    }
}
